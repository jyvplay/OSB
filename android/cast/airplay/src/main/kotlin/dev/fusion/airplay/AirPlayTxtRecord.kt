// SPDX-License-Identifier: Apache-2.0
package dev.fusion.airplay

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Clean-room RFC 6763 DNS-SD `_airplay._tcp` binary TXT record codec & 64-bit `features=` bitmask parser (`AirPlayTxtRecord`).
 */
object AirPlayTxtRecord {
    const val SCOPE =
        "RFC 6763 DNS-SD length-prefixed TXT record encoder/decoder + AirPlay 2-part 64-bit hex features=0xLOW,0xHIGH capability parser; FairPlay DRM auth out of scope."

    const val FEAT_VIDEO = 1L shl 0
    const val FEAT_PHOTO = 1L shl 1
    const val FEAT_HLS = 1L shl 4
    const val FEAT_SCREEN = 1L shl 7
    const val FEAT_AUDIO = 1L shl 9

    fun encodeDnsTxt(entries: Map<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        for ((k, v) in entries) {
            require(k.isNotEmpty() && !k.contains('=')) { "Invalid RFC 6763 TXT key: '$k'" }
            val kv = "$k=$v".toByteArray(StandardCharsets.UTF_8)
            require(kv.size <= 255) { "RFC 6763 TXT entry exceeds 255 bytes: $k" }
            baos.write(kv.size)
            baos.write(kv)
        }
        return baos.toByteArray()
    }

    fun decodeDnsTxt(raw: ByteArray): Map<String, String> {
        val out = linkedMapOf<String, String>()
        var i = 0
        while (i < raw.size) {
            val len = raw[i++].toInt() and 0xFF
            require(i + len <= raw.size) { "Truncated RFC 6763 TXT string at offset ${i - 1}" }
            if (len > 0) {
                val s = String(raw, i, len, StandardCharsets.UTF_8)
                val eq = s.indexOf('=')
                if (eq > 0) out.putIfAbsent(s.substring(0, eq), s.substring(eq + 1))
                else out.putIfAbsent(s, "")
            }
            i += len
        }
        return out
    }

    fun parseFeaturesMask(featuresValue: String): Long {
        val parts = featuresValue.split(',').map { it.trim().removePrefix("0x").removePrefix("0X") }
        require(parts.size in 1..2) { "Invalid AirPlay features field: $featuresValue" }
        val lo = parts[0].toULong(16).toLong() and 0xFFFF_FFFFL
        val hi = if (parts.size == 2) parts[1].toULong(16).toLong() and 0xFFFF_FFFFL else 0L
        return lo or (hi shl 32)
    }
}
