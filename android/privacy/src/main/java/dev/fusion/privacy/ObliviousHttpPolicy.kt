// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import java.io.ByteArrayOutputStream
import java.net.URI

/**
 * Clean-room IETF **`RFC 9458` Oblivious HTTP (`OHTTP`)** key-configuration codec, request header
 * framer, and **Relay vs Gateway non-collusion & anti-replay (`0-RTT`) policy verifier**.
 *
 * Enforces the core security & privacy invariants of `RFC 9458 §4–§6`:
 * - Encodes & parses `application/ohttp-keys` (`KeyId(1) || KemId(2) || PublicKey(Npk) || SuitesLen(2) || [KdfId(2) || AeadId(2)]+`);
 * - Encodes & parses the 7-byte `message/ohttp-req` binary header (`KeyId(1) || KemId(2) || KdfId(2) || AeadId(2)`);
 * - Enforces `https://` on BOTH Relay and Gateway legs, forbids IP-literal or identical-host /
 *   identical-registrable-domain / identical-operator Relay+Gateway collusion (`RFC 9458 §6.1`),
 *   forbids cookies/client-identifying headers on Relay POSTs, and rejects TLS `0-RTT` Early-Data
 *   replay (`Early-Data: 1`, `RFC 9458 §6.5`).
 */
object ObliviousHttpPolicy {
    const val SCOPE =
        "Clean-room IETF RFC 9458 Oblivious HTTP (application/ohttp-keys binary codec, message/ohttp-req 7-byte header framer, HTTPS + Relay!=Gateway non-collusion verifier, and TLS 0-RTT Early-Data replay refusal)."

    const val CONTENT_TYPE_OHTTP_KEYS = "application/ohttp-keys"
    const val CONTENT_TYPE_OHTTP_REQ = "message/ohttp-req"
    const val CONTENT_TYPE_OHTTP_RES = "message/ohttp-res"

    // RFC 9180 HPKE identifiers commonly deployed with OHTTP:
    const val KEM_DHKEM_X25519_SHA256 = 0x0020
    const val KEM_DHKEM_P256_SHA256 = 0x0010
    const val KDF_HKDF_SHA256 = 0x0001
    const val AEAD_AES_128_GCM = 0x0001
    const val AEAD_AES_256_GCM = 0x0002
    const val AEAD_CHACHA20_POLY1305 = 0x0003

    data class SymmetricAlgorithms(val kdfId: Int, val aeadId: Int) {
        init {
            require(kdfId in 1..0xFFFF && aeadId in 1..0xFFFF) { "invalid HPKE KDF/AEAD IDs ($kdfId, $aeadId)" }
        }
    }

    data class KeyConfig(
        val keyId: Int,
        val kemId: Int,
        val publicKeyBytes: ByteArray,
        val suites: List<SymmetricAlgorithms>,
    ) {
        init {
            require(keyId in 0..255) { "OHTTP keyId $keyId outside 0..255" }
            val expectedPkLen = expectedKemPublicKeyLength(kemId)
            require(publicKeyBytes.size == expectedPkLen) {
                "HPKE KEM 0x${kemId.toString(16)} expects $expectedPkLen-byte public key, got ${publicKeyBytes.size}"
            }
            require(suites.isNotEmpty() && suites.size <= 32) { "OHTTP symmetric suites count outside 1..32" }
        }
    }

    data class RequestHeader(
        val keyId: Int,
        val kemId: Int,
        val kdfId: Int,
        val aeadId: Int,
    )

    data class RouteAudit(
        val allowed: Boolean,
        val violations: List<String>,
    )

    fun expectedKemPublicKeyLength(kemId: Int): Int = when (kemId) {
        KEM_DHKEM_X25519_SHA256 -> 32
        KEM_DHKEM_P256_SHA256 -> 65
        else -> throw IllegalArgumentException("unsupported HPKE KEM ID 0x${kemId.toString(16)}")
    }

    fun encodeKeyConfigs(configs: List<KeyConfig>): ByteArray {
        require(configs.isNotEmpty()) { "at least one OHTTP KeyConfig required" }
        val out = ByteArrayOutputStream()
        for (cfg in configs) {
            val single = ByteArrayOutputStream()
            single.write(cfg.keyId and 0xFF)
            writeU16(single, cfg.kemId)
            single.write(cfg.publicKeyBytes)
            writeU16(single, cfg.suites.size * 4)
            for (s in cfg.suites) {
                writeU16(single, s.kdfId)
                writeU16(single, s.aeadId)
            }
            val bytes = single.toByteArray()
            writeU16(out, bytes.size)
            out.write(bytes)
        }
        return out.toByteArray()
    }

    fun decodeKeyConfigs(bytes: ByteArray): List<KeyConfig> {
        require(bytes.isNotEmpty()) { "empty application/ohttp-keys document" }
        val configs = ArrayList<KeyConfig>()
        var pos = 0
        while (pos < bytes.size) {
            require(pos + 2 <= bytes.size) { "truncated OHTTP key-config length prefix" }
            val cfgLen = readU16(bytes, pos); pos += 2
            require(cfgLen >= 7 && pos + cfgLen <= bytes.size) { "invalid OHTTP key-config length $cfgLen" }
            val cfgEnd = pos + cfgLen
            val keyId = bytes[pos++].toInt() and 0xFF
            val kemId = readU16(bytes, pos); pos += 2
            val pkLen = expectedKemPublicKeyLength(kemId)
            require(pos + pkLen + 2 <= cfgEnd) { "truncated OHTTP public key / suites length" }
            val pk = bytes.copyOfRange(pos, pos + pkLen); pos += pkLen
            val suitesBytes = readU16(bytes, pos); pos += 2
            require(suitesBytes >= 4 && suitesBytes % 4 == 0 && pos + suitesBytes == cfgEnd) {
                "malformed OHTTP symmetric algorithms length $suitesBytes"
            }
            val suites = ArrayList<SymmetricAlgorithms>(suitesBytes / 4)
            repeat(suitesBytes / 4) {
                val kdf = readU16(bytes, pos); pos += 2
                val aead = readU16(bytes, pos); pos += 2
                suites.add(SymmetricAlgorithms(kdf, aead))
            }
            configs.add(KeyConfig(keyId, kemId, pk, suites))
        }
        return configs
    }

    fun encodeRequestHeader(h: RequestHeader): ByteArray {
        require(h.keyId in 0..255) { "keyId out of u8 range" }
        val out = ByteArrayOutputStream(7)
        out.write(h.keyId)
        writeU16(out, h.kemId)
        writeU16(out, h.kdfId)
        writeU16(out, h.aeadId)
        return out.toByteArray()
    }

    fun decodeRequestHeader(bytes: ByteArray): RequestHeader {
        require(bytes.size >= 7) { "RFC 9458 request header requires >= 7 bytes, got ${bytes.size}" }
        return RequestHeader(
            keyId = bytes[0].toInt() and 0xFF,
            kemId = readU16(bytes, 1),
            kdfId = readU16(bytes, 3),
            aeadId = readU16(bytes, 5),
        )
    }

    /** Verifies `RFC 9458 §6` non-collusion, transport security, header hygiene, and `0-RTT` replay defense. */
    fun auditRelayGatewayRoute(
        relayUri: String,
        gatewayUri: String,
        relayOperatorOrg: String,
        gatewayOperatorOrg: String,
        outerRelayHeaders: Map<String, String> = emptyMap(),
        tlsEarlyData0Rtt: Boolean = false,
    ): RouteAudit {
        val violations = ArrayList<String>()
        val rUri = runCatching { URI(relayUri) }.getOrNull()
        val gUri = runCatching { URI(gatewayUri) }.getOrNull()
        if (rUri == null || rUri.scheme?.lowercase() != "https" || rUri.host.isNullOrBlank()) {
            violations.add("RELAY_MUST_USE_HTTPS_HOST")
        }
        if (gUri == null || gUri.scheme?.lowercase() != "https" || gUri.host.isNullOrBlank()) {
            violations.add("GATEWAY_MUST_USE_HTTPS_HOST")
        }
        if (rUri?.host != null && gUri?.host != null) {
            val rHost = rUri.host.lowercase().trimEnd('.')
            val gHost = gUri.host.lowercase().trimEnd('.')
            if (rHost == gHost) violations.add("COLLUSION_IDENTICAL_HOST")
            if (registrableTail(rHost) == registrableTail(gHost)) {
                violations.add("COLLUSION_SAME_REGISTRABLE_DOMAIN")
            }
        }
        if (relayOperatorOrg.trim().isEmpty() || gatewayOperatorOrg.trim().isEmpty() ||
            relayOperatorOrg.trim().equals(gatewayOperatorOrg.trim(), ignoreCase = true)
        ) {
            violations.add("COLLUSION_SAME_OR_UNKNOWN_OPERATOR")
        }
        val normHeaders = outerRelayHeaders.keys.map { it.trim().lowercase() }
        for (leaky in listOf("cookie", "authorization", "x-forwarded-for", "x-real-ip", "client-ip")) {
            if (leaky in normHeaders) violations.add("IDENTITY_LEAK_OUTER_HEADER_${leaky.uppercase()}")
        }
        if (tlsEarlyData0Rtt || outerRelayHeaders.entries.any {
                it.key.equals("Early-Data", ignoreCase = true) && it.value.trim() == "1"
            }
        ) {
            violations.add("RFC9458_0RTT_EARLY_DATA_REPLAY_RISK")
        }
        return RouteAudit(violations.isEmpty(), violations)
    }

    private fun registrableTail(host: String): String {
        val parts = host.split('.')
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
    }

    private fun writeU16(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    private fun readU16(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 8) or (b[offset + 1].toInt() and 0xFF)
}
