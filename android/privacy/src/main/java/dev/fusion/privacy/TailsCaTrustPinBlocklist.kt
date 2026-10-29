// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import java.time.LocalDate

/**
 * Clean-room Tails `7.7`–`7.15.1` (`2026-10-23` emergency release) Certificate Authority Emergency
 * Distrust Gate & UEFI/Boot Certificate Expiry Auditor.
 *
 * Implements two real-world 2026 Tails protections:
 * 1. **System-Wide Compromised CA Distrust (`Tails 7.15.1` / `CVE-2026-113470` class)**:
 *    Blocks TLS certificate chains containing any revoked/compromised root or intermediate CA
 *    (matched by SubjectPublicKeyInfo SHA-256 hex pin or normalized Issuer Organization/CN) —
 *    essential when routing over Tor where malicious exit nodes can exploit rogue CA intermediates.
 * 2. **Boot Trust-Anchor Expiry Warning (`Tails 7.7+`, Microsoft/UEFI 2011 -> June 2026 rollover)**:
 *    Flags outdated or imminently expiring Secure Boot / Verified Boot certificates.
 */
object TailsCaTrustPinBlocklist {
    data class CertChainNode(
        val subjectCn: String,
        val issuerOrganization: String,
        val spkiSha256Hex: String,
        val notAfterIsoDate: String,
    ) {
        init {
            require(spkiSha256Hex.matches(Regex("[0-9a-fA-F]{64}"))) {
                "SPKI SHA-256 pin must be 64 hex chars"
            }
        }
    }

    val DEFAULT_DISTRUSTED_ORGANIZATIONS: Set<String> = setOf(
        "emudhra",
        "emudhra technologies limited",
        "emsign",
        "trustcor systems",
        "diginotar",
        "symantec corporation",
        "woSign ca limited",
        "cnnnic",
    ).map { it.lowercase() }.toSet()

    enum class ChainDecision {
        TRUSTED,
        BLOCKED_EMERGENCY_CA_DISTRUST,
        BLOCKED_SPKI_PIN_REVOKED,
        BLOCKED_CERTIFICATE_EXPIRED,
    }

    data class ChainAuditResult(
        val decision: ChainDecision,
        val offendingNodeIndex: Int?,
        val reason: String,
    )

    data class BootCertStatus(
        val label: String,
        val expiresOn: LocalDate,
        val expired: Boolean,
        val expiringWithin90Days: Boolean,
        val requiresRolloverUpdate: Boolean,
    )

    fun evaluateTlsChain(
        chainLeafToRoot: List<CertChainNode>,
        today: LocalDate = LocalDate.of(2026, 10, 27),
        revokedSpkiPinsHex: Set<String> = emptySet(),
        distrustedOrgsLower: Set<String> = DEFAULT_DISTRUSTED_ORGANIZATIONS,
    ): ChainAuditResult {
        require(chainLeafToRoot.isNotEmpty()) { "TLS certificate chain cannot be empty" }
        val normalizedPins = revokedSpkiPinsHex.map { it.lowercase() }.toSet()

        for ((idx, node) in chainLeafToRoot.withIndex()) {
            if (node.spkiSha256Hex.lowercase() in normalizedPins) {
                return ChainAuditResult(
                    ChainDecision.BLOCKED_SPKI_PIN_REVOKED,
                    idx,
                    "SPKI SHA-256 pin ${node.spkiSha256Hex.take(16)}… is in emergency revocation blocklist",
                )
            }
            val orgLower = node.issuerOrganization.trim().lowercase()
            val cnLower = node.subjectCn.trim().lowercase()
            if (distrustedOrgsLower.any { orgLower.contains(it) || cnLower.contains(it) }) {
                return ChainAuditResult(
                    ChainDecision.BLOCKED_EMERGENCY_CA_DISTRUST,
                    idx,
                    "CA '${node.issuerOrganization} / ${node.subjectCn}' is distrusted system-wide (Tails 7.15.1 emergency policy)",
                )
            }
            val exp = LocalDate.parse(node.notAfterIsoDate)
            if (exp.isBefore(today)) {
                return ChainAuditResult(
                    ChainDecision.BLOCKED_CERTIFICATE_EXPIRED,
                    idx,
                    "Certificate '${node.subjectCn}' expired on $exp (today=$today)",
                )
            }
        }
        return ChainAuditResult(ChainDecision.TRUSTED, null, "TLS chain passed emergency CA & expiry checks")
    }

    fun auditBootCertificates(
        bootCerts: List<Pair<String, String>>, // Pair(label, expiryIsoDate)
        today: LocalDate = LocalDate.of(2026, 10, 27),
    ): List<BootCertStatus> = bootCerts.map { (label, iso) ->
        val exp = LocalDate.parse(iso)
        val expired = !exp.isAfter(today)
        val soon = !expired && !exp.isAfter(today.plusDays(90))
        val legacy2011 = label.contains("2011")
        BootCertStatus(
            label = label,
            expiresOn = exp,
            expired = expired,
            expiringWithin90Days = soon,
            requiresRolloverUpdate = expired || soon || legacy2011,
        )
    }
}
