// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.image

import java.io.ByteArrayOutputStream

/**
 * ITU-T T.81 / JFIF JPEG marker-segment stream lossless metadata stripper removing `APP1`
 * (EXIF / GPS / XMP), `APP13` (Photoshop IPTC), and `COM` (comment) markers while preserving
 * `APP0` (`JFIF`), `DQT`, `DHT`, `SOF*`, and entropy-coded `SOS..EOI` scan pixels without re-compression!
 */
object JpegExifAppMarkerSanitizer {
    const val SCOPE =
        "Lossless ITU-T T.81 JPEG marker segment stripper removing APP1 (EXIF/GPS/XMP), APP13 (IPTC), and COM without pixel re-encode."

    data class SanitizedJpegResult(
        val cleanBytes: ByteArray,
        val strippedMarkersHex: List<String>,
        val preservedScanBytes: Int,
    )

    fun stripMetadataLossless(jpegBytes: ByteArray): SanitizedJpegResult {
        require(jpegBytes.size >= 4 && (jpegBytes[0].toInt() and 0xFF) == 0xFF && (jpegBytes[1].toInt() and 0xFF) == 0xD8) {
            "Missing JPEG SOI (0xFFD8) marker"
        }
        val out = ByteArrayOutputStream(jpegBytes.size)
        out.write(0xFF); out.write(0xD8)
        val stripped = ArrayList<String>()
        var pos = 2
        var scanLen = 0

        while (pos < jpegBytes.size) {
            require((jpegBytes[pos].toInt() and 0xFF) == 0xFF) { "Expected 0xFF marker prefix at $pos" }
            while (pos < jpegBytes.size && (jpegBytes[pos].toInt() and 0xFF) == 0xFF) pos++
            require(pos < jpegBytes.size) { "Truncated JPEG marker" }
            val marker = jpegBytes[pos].toInt() and 0xFF
            pos++

            if (marker == 0xD9) { // EOI
                out.write(0xFF); out.write(0xD9)
                break
            }
            if (marker == 0x01 || marker in 0xD0..0xD7) { // Standalone TEM / RSTn
                out.write(0xFF); out.write(marker)
                continue
            }
            require(pos + 2 <= jpegBytes.size) { "Truncated JPEG segment length" }
            val segLen = ((jpegBytes[pos].toInt() and 0xFF) shl 8) or (jpegBytes[pos + 1].toInt() and 0xFF)
            require(segLen >= 2 && pos + segLen <= jpegBytes.size) { "Invalid JPEG segment length $segLen" }

            val isSensitiveMeta = (marker in 0xE1..0xEF && marker != 0xE0) || marker == 0xFE // APP1..APP15, COM
            if (isSensitiveMeta) {
                stripped.add("0xFF%02X".format(marker))
                pos += segLen
            } else {
                out.write(0xFF); out.write(marker)
                out.write(jpegBytes, pos, segLen)
                pos += segLen
                if (marker == 0xDA) { // SOS: copy remaining entropy-coded data up to EOI verbatim
                    val rem = jpegBytes.size - pos
                    out.write(jpegBytes, pos, rem)
                    scanLen = rem
                    break
                }
            }
        }
        return SanitizedJpegResult(out.toByteArray(), stripped, scanLen)
    }
}
