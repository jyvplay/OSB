// SPDX-License-Identifier: Apache-2.0
package dev.fusion.photos

import java.io.ByteArrayOutputStream
import java.util.Collections

/**
 * Lossless JPEG / EXIF / GPS / XMP / IPTC segment inspector & metadata cleaner (`:feature:photos`).
 *
 * Complements `:format:image`'s PNG chunk stripper by providing non-re-encoding ITU-T T.81 JPEG marker parsing:
 * validates SOI (`FF D8`) through `SOS` (`FF DA`) and `EOI` (`FF D9`), extracts frame geometry (`SOF0`..`SOF3`),
 * drops metadata-bearing segments (`APP1` EXIF/GPS/XMP, `APP2`..`APP15` except optional ICC, and `COM` `FF FE`),
 * emits minimal neutral JFIF `APP0`, and copies the entropy-coded `SOS..EOI` scan stream byte-for-byte without
 * lossy decompression/re-quantization.
 *
 * Honest boundary: Camera sensor PRNU noise, quantization table fingerprints, faces/landmarks inside pixels, and
 * steganography survive lossless container stripping. Orientation tags in `APP1` are removed rather than pixels rotated.
 */
object PhotoMetadataCleaner {
    const val MAX_JPEG_BYTES = 16 * 1024 * 1024

    data class CleanedJpeg(
        val width: Int,
        val height: Int,
        val components: Int,
        val removedMarkers: List<String>,
        val preservedMarkers: List<String>,
        private val bytesCopy: ByteArray
    ) {
        val bytes: ByteArray get() = bytesCopy.copyOf()
        val byteLength: Int get() = bytesCopy.size
    }

    data class AlbumItem(
        val id: String,
        val title: String,
        val width: Int,
        val height: Int,
        val album: String,
        val strippedSegments: Int,
        val aspectPreset: String = "Original"
    ) {
        init {
            require(id.isNotBlank() && title.length in 1..128 && width in 1..16384 && height in 1..16384)
        }
    }

    private fun markerName(code: Int): String = when (code) {
        0xE0 -> "APP0"
        0xE1 -> "APP1(EXIF/XMP)"
        in 0xE2..0xEF -> "APP${code - 0xE0}"
        0xFE -> "COM"
        0xDB -> "DQT"
        0xC4 -> "DHT"
        0xDD -> "DRI"
        0xC0 -> "SOF0"
        0xC1 -> "SOF1"
        0xC2 -> "SOF2"
        else -> "0x%02X".format(code)
    }

    fun inspectAndCleanJpeg(input: ByteArray): CleanedJpeg {
        require(input.size in 4..MAX_JPEG_BYTES) { "JPEG size outside 4B..16MiB bounds: ${input.size}" }
        require((input[0].toInt() and 0xFF) == 0xFF && (input[1].toInt() and 0xFF) == 0xD8) {
            "Missing JPEG SOI (FF D8)"
        }
        val out = ByteArrayOutputStream(input.size)
        out.write(0xFF); out.write(0xD8)
        // Minimal neutral 1:1 pixel-aspect JFIF APP0 header (no thumbnail, no DPI fingerprint)
        val neutralJfif = byteArrayOf(
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
            'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0x00,
            0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00
        )
        out.write(neutralJfif)

        var pos = 2
        var width = 0
        var height = 0
        var components = 0
        var sawSos = false
        val removed = ArrayList<String>()
        val preserved = ArrayList<String>()

        while (pos < input.size) {
            require((input[pos].toInt() and 0xFF) == 0xFF) { "Expected 0xFF marker prefix at offset $pos" }
            while (pos < input.size && (input[pos].toInt() and 0xFF) == 0xFF) pos++
            require(pos < input.size) { "Truncated marker at EOF" }
            val marker = input[pos++].toInt() and 0xFF
            require(marker != 0x00) { "Unexpected byte-stuffed 0xFF00 outside entropy scan" }
            if (marker == 0xD9) break // EOI before SOS is invalid unless SOS was seen
            if (marker in 0xD0..0xD7 || marker == 0x01) continue // Standalone restart / TEM

            require(pos + 2 <= input.size) { "Truncated segment length for marker 0x%02X".format(marker) }
            val segLen = ((input[pos].toInt() and 0xFF) shl 8) or (input[pos + 1].toInt() and 0xFF)
            require(segLen >= 2 && pos + segLen <= input.size) {
                "Invalid segment length $segLen for marker 0x%02X".format(marker)
            }

            if (marker in setOf(0xC0, 0xC1, 0xC2)) {
                require(segLen >= 8) { "Truncated SOF header" }
                height = ((input[pos + 3].toInt() and 0xFF) shl 8) or (input[pos + 4].toInt() and 0xFF)
                width = ((input[pos + 5].toInt() and 0xFF) shl 8) or (input[pos + 6].toInt() and 0xFF)
                components = input[pos + 7].toInt() and 0xFF
                require(width in 1..8192 && height in 1..8192 && width.toLong() * height.toLong() <= 16_777_216L && components in setOf(1, 3)) {
                    "Unsupported JPEG dimensions/components: ${width}x${height}x$components"
                }
            }

            if (marker == 0xDA) {
                // Start of Scan: verify unescaped EOI (FF D9) exists in remaining stream and copy exact scan through EOI.
                require(width > 0 && height > 0) { "SOS encountered before valid SOF frame header" }
                out.write(0xFF); out.write(marker)
                out.write(input, pos, segLen)
                pos += segLen
                val scanStart = pos
                var eoiEnd = -1
                var scanCursor = scanStart
                while (scanCursor < input.size) {
                    val b = input[scanCursor++].toInt() and 0xFF
                    if (b == 0xFF) {
                        while (scanCursor < input.size && (input[scanCursor].toInt() and 0xFF) == 0xFF) scanCursor++
                        require(scanCursor < input.size) { "Truncated escape inside SOS entropy stream" }
                        val next = input[scanCursor++].toInt() and 0xFF
                        if (next == 0xD9) {
                            eoiEnd = scanCursor
                            break
                        }
                    }
                }
                require(eoiEnd > scanStart) { "Missing JPEG EOI (FF D9) terminator" }
                if (eoiEnd < input.size) {
                    removed.add("TRAILING_BYTES(${input.size - eoiEnd}B)")
                }
                out.write(input, scanStart, eoiEnd - scanStart)
                sawSos = true
                break
            }

            val strip = marker == 0xFE || marker in 0xE0..0xEF
            if (strip) {
                if (marker != 0xE0) removed.add(markerName(marker))
            } else {
                preserved.add(markerName(marker))
                out.write(0xFF); out.write(marker)
                out.write(input, pos, segLen)
            }
            pos += segLen
        }
        require(sawSos) { "Incomplete JPEG stream: missing SOS scan" }
        return CleanedJpeg(
            width = width,
            height = height,
            components = components,
            removedMarkers = Collections.unmodifiableList(removed),
            preservedMarkers = Collections.unmodifiableList(preserved),
            bytesCopy = out.toByteArray()
        )
    }

    /** Extract raw SOS..EOI compressed scan slice so callers can prove lossless bitstream identity. */
    fun extractEntropyScan(jpeg: ByteArray): ByteArray {
        var pos = 2
        while (pos + 4 <= jpeg.size) {
            if ((jpeg[pos].toInt() and 0xFF) != 0xFF) { pos++; continue }
            while (pos < jpeg.size && (jpeg[pos].toInt() and 0xFF) == 0xFF) pos++
            if (pos >= jpeg.size) break
            val m = jpeg[pos++].toInt() and 0xFF
            if (m in 0xD0..0xD9 || m == 0x01) continue
            val len = ((jpeg[pos].toInt() and 0xFF) shl 8) or (jpeg[pos + 1].toInt() and 0xFF)
            if (m == 0xDA) return jpeg.copyOfRange(pos + len, jpeg.size)
            pos += len
        }
        error("No SOS marker found")
    }
}
