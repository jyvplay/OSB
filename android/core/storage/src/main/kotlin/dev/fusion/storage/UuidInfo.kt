// SPDX-License-Identifier: Apache-2.0
package dev.fusion.storage

/**
 * UUID classifier pinned to RFC 9562 (May 2024, obsoletes RFC 4122):
 *
 * - Canonical text form: 8-4-4-4-12 lowercase-insensitive hex, 36 chars
 *   with hyphens at offsets 8, 13, 18, 23.
 * - The variant lives in the top bits of octet 8: 0xxx = NCS legacy,
 *   10xx = OSF DCE / RFC 9562, 110x = Microsoft COM, 111x = reserved.
 * - The version is the high nibble of octet 6 and is only meaningful for
 *   the 10xx variant.
 * - UUIDv7 (RFC 9562 section 5.7) stores a 48-bit big-endian Unix
 *   timestamp in milliseconds in octets 0..5; the RFC's own test vector
 *   017F22E2-79B0-7CC3-98C4-DC0C0C07398F decodes to 1645557742000 ms.
 * - Nil UUID is all zeros; Max UUID is all ones (section 5.9/5.10).
 *
 * Pure JVM logic; refusal is IllegalArgumentException. No generation —
 * classification and timestamp extraction only.
 */
object UuidInfo {
    enum class Variant { NCS, RFC9562, MICROSOFT, RESERVED }

    data class Info(
        val hex: String,          // 32 lowercase hex chars, no hyphens
        val variant: Variant,
        val version: Int?,        // null unless variant == RFC9562
        val isNil: Boolean,
        val isMax: Boolean,
    ) {
        /** Unix milliseconds for RFC9562 v7 only; null otherwise. */
        val unixMs: Long? get() =
            if (variant == Variant.RFC9562 && version == 7) hex.substring(0, 12).toLong(16) else null
    }

    fun parse(text: String): Info {
        require(text.length == 36) { "canonical UUID must be 36 chars, got ${text.length}" }
        val sb = StringBuilder(32)
        for (i in text.indices) {
            val c = text[i]
            if (i == 8 || i == 13 || i == 18 || i == 23) {
                require(c == '-') { "expected hyphen at offset $i" }
            } else {
                require(c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F') { "non-hex char '$c' at offset $i" }
                sb.append(c.lowercaseChar())
            }
        }
        val hex = sb.toString()
        val isNil = hex.all { it == '0' }
        val isMax = hex.all { it == 'f' }
        val octet8 = hex.substring(16, 18).toInt(16)
        val variant = when {
            (octet8 and 0x80) == 0 -> Variant.NCS
            (octet8 and 0x40) == 0 -> Variant.RFC9562
            (octet8 and 0x20) == 0 -> Variant.MICROSOFT
            else -> Variant.RESERVED
        }
        val version = if (variant == Variant.RFC9562) hex.substring(12, 13).toInt(16) else null
        return Info(hex, variant, version, isNil, isMax)
    }
}
