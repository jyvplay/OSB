// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clean-room RFC 6962 §3.2 Certificate Transparency `SignedCertificateTimestamp` (SCT v1)
 * wire parser, multi-operator quorum gate, and emergency CA SPKI SHA-256 blocklist.
 *
 * Grounded in the `2026-10-23` Tails `7.15.1` / `CVE-2026-113470` incident (8 low-entropy
 * eMudhra TLS subordinate CAs mitigated by CT enforcement + rapid CA SPKI distrust):
 * - Refuses any certificate chain containing a distrusted leaf/intermediate/root SPKI SHA-256
 *   digest (`Verdict.REJECT_CA_DISTRUSTED`) before even consulting SCTs.
 * - Parses exact RFC 6962 v1 binary SCT structures (`version = 0`, 32-byte `log_id`, 64-bit
 *   big-endian `timestampMs`, length-prefixed `extensions`, `DigitallySigned` hash/sig header +
 *   non-empty signature with zero trailing bytes).
 * - Enforces CA/Browser Forum / Chrome / Apple CT diversity policy:
 *   - `certLifetimeDays <= 180` -> `>= 2` valid SCTs from `>= 2` distinct CT log operators,
 *   - `certLifetimeDays > 180` -> `>= 3` valid SCTs from `>= 2` distinct CT log operators,
 *   - Rejects future-dated SCTs beyond `MAX_CLOCK_SKEW_MS` (`5` minutes).
 */
object CtSctPolicy {
    const val SCOPE =
        "RFC 6962 §3.2 binary SCT v1 wire parser + multi-operator diversity quorum + emergency CA SPKI SHA-256 blocklist; X.509 TBSCertificate ASN.1 reconstruction and ECDSA/RSA signature verification over MerkleTreeLeaf out of scope."

    const val SCT_V1 = 0
    const val LOG_ID_BYTES = 32
    const val MAX_SCT_BYTES = 4_096
    const val MAX_CLOCK_SKEW_MS = 5 * 60_000L

    enum class HashAlgorithm(val code: Int) {
        SHA256(4);
        companion object {
            fun fromCode(c: Int): HashAlgorithm = entries.firstOrNull { it.code == c }
                ?: throw IllegalArgumentException("unsupported RFC 6962 SCT hash algorithm $c (SHA256=4 required)")
        }
    }

    enum class SignatureAlgorithm(val code: Int) {
        RSA(1), ECDSA(3);
        companion object {
            fun fromCode(c: Int): SignatureAlgorithm = entries.firstOrNull { it.code == c }
                ?: throw IllegalArgumentException("unsupported RFC 6962 SCT signature algorithm $c")
        }
    }

    data class ParsedSct(
        val version: Int,
        val logIdHex: String,
        val timestampMs: Long,
        val extensionsLength: Int,
        val hashAlg: HashAlgorithm,
        val sigAlg: SignatureAlgorithm,
        val signatureLength: Int,
    )

    data class TrustedLog(
        val logIdHex: String,
        val operatorName: String,
        val retiredAfterMs: Long? = null,
    ) {
        val normalizedId: String = normalizeSha256Hex(logIdHex)
        init {
            require(operatorName.isNotBlank()) { "operatorName must not be blank" }
        }
    }

    enum class Verdict {
        COMPLIANT,
        REJECT_CA_DISTRUSTED,
        REJECT_INSUFFICIENT_SCTS,
        REJECT_SINGLE_OPERATOR,
    }

    data class QuorumDecision(
        val verdict: Verdict,
        val acceptedLogIds: List<String>,
        val distinctOperators: Set<String>,
        val rejectedReasons: List<String>,
    )

    fun parseSct(bytes: ByteArray): ParsedSct {
        // Minimum v1 SCT: 1 (ver) + 32 (log_id) + 8 (ts) + 2 (ext_len) + 1 (hash) + 1 (sig) + 2 (sig_len) + 1 = 48 bytes
        require(bytes.size in 48..MAX_SCT_BYTES) {
            "SCT length ${bytes.size} outside 48..$MAX_SCT_BYTES"
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val version = buf.get().toInt() and 0xFF
        require(version == SCT_V1) { "unsupported SCT version $version (expected v1=0)" }

        val logId = ByteArray(LOG_ID_BYTES)
        buf.get(logId)
        val logIdHex = toLowerHex(logId)

        val timestampMs = buf.long
        require(timestampMs > 0L) { "non-positive SCT timestamp $timestampMs" }

        val extLen = buf.short.toInt() and 0xFFFF
        require(buf.remaining() >= extLen + 4) { "SCT extensions overrun buffer (extLen=$extLen)" }
        buf.position(buf.position() + extLen)

        val hashAlg = HashAlgorithm.fromCode(buf.get().toInt() and 0xFF)
        val sigAlg = SignatureAlgorithm.fromCode(buf.get().toInt() and 0xFF)
        val sigLen = buf.short.toInt() and 0xFFFF
        require(sigLen > 0) { "empty SCT signature" }
        require(buf.remaining() == sigLen) {
            "SCT signature length mismatch: declared $sigLen, remaining ${buf.remaining()}"
        }
        return ParsedSct(version, logIdHex, timestampMs, extLen, hashAlg, sigAlg, sigLen)
    }

    fun evaluate(
        chainSpkiSha256Hex: List<String>,
        sctPayloads: List<ByteArray>,
        trustedLogs: List<TrustedLog>,
        revokedCaSpkiSha256Hex: Set<String>,
        nowEpochMs: Long,
        certLifetimeDays: Int,
    ): QuorumDecision {
        require(chainSpkiSha256Hex.isNotEmpty()) { "certificate chain SPKI list must not be empty" }
        require(nowEpochMs > 0L) { "nowEpochMs must be > 0" }
        require(certLifetimeDays in 1..825) { "certLifetimeDays $certLifetimeDays outside 1..825" }

        val normChain = chainSpkiSha256Hex.map(::normalizeSha256Hex)
        val normRevoked = revokedCaSpkiSha256Hex.map(::normalizeSha256Hex).toSet()
        val distrustedHit = normChain.firstOrNull { it in normRevoked }
        if (distrustedHit != null) {
            return QuorumDecision(
                verdict = Verdict.REJECT_CA_DISTRUSTED,
                acceptedLogIds = emptyList(),
                distinctOperators = emptySet(),
                rejectedReasons = listOf("chain SPKI $distrustedHit matches emergency CA blocklist"),
            )
        }

        val logsById = trustedLogs.associateBy { it.normalizedId }
        val acceptedLogs = LinkedHashSet<String>()
        val operators = LinkedHashSet<String>()
        val reasons = ArrayList<String>()

        for ((idx, raw) in sctPayloads.withIndex()) {
            val parsed = runCatching { parseSct(raw) }.getOrElse { e ->
                reasons.add("sct[$idx] malformed: ${e.message}")
                continue
            }
            val log = logsById[parsed.logIdHex]
            if (log == null) {
                reasons.add("sct[$idx] unknown log ${parsed.logIdHex}")
                continue
            }
            if (parsed.timestampMs > nowEpochMs + MAX_CLOCK_SKEW_MS) {
                reasons.add("sct[$idx] future timestamp ${parsed.timestampMs}")
                continue
            }
            if (log.retiredAfterMs != null && parsed.timestampMs > log.retiredAfterMs) {
                reasons.add("sct[$idx] issued after log retirement ${log.retiredAfterMs}")
                continue
            }
            if (acceptedLogs.add(parsed.logIdHex)) {
                operators.add(log.operatorName)
            } else {
                reasons.add("sct[$idx] duplicate log ${parsed.logIdHex}")
            }
        }

        val requiredScts = if (certLifetimeDays <= 180) 2 else 3
        return when {
            acceptedLogs.size < requiredScts -> QuorumDecision(
                Verdict.REJECT_INSUFFICIENT_SCTS,
                acceptedLogs.toList(),
                operators,
                reasons,
            )
            operators.size < 2 -> QuorumDecision(
                Verdict.REJECT_SINGLE_OPERATOR,
                acceptedLogs.toList(),
                operators,
                reasons + "fewer than 2 distinct CT log operators",
            )
            else -> QuorumDecision(Verdict.COMPLIANT, acceptedLogs.toList(), operators, reasons)
        }
    }

    private fun normalizeSha256Hex(raw: String): String {
        val clean = raw.trim().lowercase()
        require(clean.length == 64 && clean.all { it in '0'..'9' || it in 'a'..'f' }) {
            "expected 64-char SHA-256 hex digest, got '$raw'"
        }
        return clean
    }

    private fun toLowerHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        val hex = "0123456789abcdef"
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            append(hex[v ushr 4])
            append(hex[v and 0x0F])
        }
    }
}
