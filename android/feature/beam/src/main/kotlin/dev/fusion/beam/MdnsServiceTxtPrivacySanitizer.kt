// SPDX-License-Identifier: Apache-2.0
package dev.fusion.beam

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * RFC 6763 **DNS-SD / mDNS TXT Record Privacy Sanitizer** for Fusion Beam local peer discovery:
 * strips MAC/serial/hostname PII keys (`mac`, `serial`, `imei`, `hostname`, `user`), enforces the
 * RFC 6763 §6.3 `<= 255 B` per-entry & `<= 1300 B` total TXT wire budget, and rotates ephemeral
 * 8-hex session pseudonyms.
 */
object MdnsServiceTxtPrivacySanitizer {
    const val SCOPE =
        "RFC 6763 DNS-SD TXT wire-format encoder and PII stripper removing MAC/serial/hostname leaks for Fusion Beam."

    private val FORBIDDEN_KEYS = setOf("mac", "hwaddr", "serial", "sn", "imei", "hostname", "user", "device_name", "udid")

    data class SanitizedTxtWire(
        val wireBytes: ByteArray,
        val keptPairs: Map<String, String>,
        val strippedKeys: List<String>,
        val ephemeralPeerTag: String,
    )

    fun buildSanitizedTxt(rawAttributes: Map<String, String>, rotationEpochBucket: Long, secretSalt: String): SanitizedTxtWire {
        val tag = MessageDigest.getInstance("SHA-256")
            .digest("$secretSalt|$rotationEpochBucket".toByteArray(Charsets.UTF_8))
            .take(4).joinToString("") { "%02x".format(it) }

        val kept = LinkedHashMap<String, String>()
        val stripped = ArrayList<String>()
        kept["txtvers"] = "1"
        kept["peer"] = "beam-$tag"

        for ((k, v) in rawAttributes) {
            val cleanK = k.trim().lowercase()
            if (cleanK.isEmpty() || '=' in cleanK) continue
            if (cleanK in FORBIDDEN_KEYS || MAC_REGEX.containsMatchIn(v)) {
                stripped.add(k)
            } else {
                kept[cleanK] = v.take(180)
            }
        }

        val out = ByteArrayOutputStream()
        for ((k, v) in kept) {
            val kvBytes = "$k=$v".toByteArray(Charsets.UTF_8)
            require(kvBytes.size <= 255) { "RFC 6763 TXT string exceeds 255 bytes" }
            if (out.size() + 1 + kvBytes.size > 1300) break
            out.write(kvBytes.size)
            out.write(kvBytes)
        }
        return SanitizedTxtWire(out.toByteArray(), kept, stripped, "beam-$tag")
    }

    private val MAC_REGEX = Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")
}
