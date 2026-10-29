package dev.fusion.format.image

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

/**
 * Clean-room Outbound Image Privacy Metadata Scrubber (Apache-2.0) for JPEG (`FFD8..FFD9`)
 * and PNG (`89 50 4E 47`) files.
 *
 * Strips GPS/EXIF/XMP/IPTC/Comment segments (`APP1` Exif/XMP, `APP13` IPTC, `COM`, PNG `eXIf`,
 * `tEXt`, `zTXt`, `iTXt`, `tIME`) while preserving pixel bitstreams and `APP2` ICC color profiles /
 * PNG `iCCP`/`sRGB`/`gAMA` chunks!
 */
object ExifGpsPrivacyScrubber {
    const val SCOPE =
        "Lossless JPEG (APP1 Exif/XMP, APP13 IPTC, COM) & PNG (eXIf/tEXt/iTXt/zTXt/tIME) GPS/metadata scrubber preserving ICC & pixels"

    data class ScrubResult(
        val format: String,
        val sanitizedBytes: ByteArray,
        val removedSegments: List<String>,
        val bytesRemoved: Int,
    )

    private val PNG_SIG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val STRIPPED_PNG_CHUNKS = setOf("eXIf", "tEXt", "zTXt", "iTXt", "tIME")

    fun scrub(imageBytes: ByteArray): ScrubResult = when {
        isPng(imageBytes) -> scrubPng(imageBytes)
        isJpeg(imageBytes) -> scrubJpeg(imageBytes)
        else -> throw IllegalArgumentException("Unsupported image header (expected PNG or JPEG)")
    }

    private fun isPng(b: ByteArray): Boolean = b.size >= 8 && PNG_SIG.indices.all { b[it] == PNG_SIG[it] }
    private fun isJpeg(b: ByteArray): Boolean = b.size >= 4 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()

    private fun scrubPng(bytes: ByteArray): ScrubResult {
        val out = ByteArrayOutputStream(bytes.size)
        out.write(PNG_SIG)
        val removed = mutableListOf<String>()
        var pos = 8
        while (pos + 12 <= bytes.size) {
            val len = ByteBuffer.wrap(bytes, pos, 4).order(ByteOrder.BIG_ENDIAN).int
            require(len >= 0 && pos.toLong() + 12L + len <= bytes.size) { "Corrupt PNG chunk length $len at $pos" }
            val chunkType = String(bytes, pos + 4, 4, StandardCharsets.US_ASCII)
            val totalChunkBytes = 12 + len
            if (chunkType in STRIPPED_PNG_CHUNKS) {
                removed.add(chunkType)
            } else {
                // Verify CRC32 before copying
                val expectedCrc = ByteBuffer.wrap(bytes, pos + 8 + len, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFF_FFFFL
                val actualCrc = CRC32().apply { update(bytes, pos + 4, 4 + len) }.value
                require(expectedCrc == actualCrc) { "PNG chunk '$chunkType' CRC mismatch" }
                out.write(bytes, pos, totalChunkBytes)
            }
            pos += totalChunkBytes
            if (chunkType == "IEND") break
        }
        val clean = out.toByteArray()
        return ScrubResult("PNG", clean, removed, bytes.size - clean.size)
    }

    private fun scrubJpeg(bytes: ByteArray): ScrubResult {
        val out = ByteArrayOutputStream(bytes.size)
        out.write(0xFF); out.write(0xD8)
        val removed = mutableListOf<String>()
        var pos = 2
        while (pos + 1 < bytes.size) {
            if (bytes[pos] != 0xFF.toByte()) {
                out.write(bytes, pos, bytes.size - pos)
                break
            }
            while (pos < bytes.size && bytes[pos] == 0xFF.toByte()) pos++
            if (pos >= bytes.size) break
            val marker = bytes[pos++].toInt() and 0xFF
            if (marker == 0xD9) { // EOI
                out.write(0xFF); out.write(0xD9)
                break
            }
            if (marker == 0xDA) { // SOS: copy header + entropy-coded scan through end of stream
                out.write(0xFF); out.write(marker)
                out.write(bytes, pos, bytes.size - pos)
                break
            }
            if (marker in 0xD0..0xD7 || marker == 0x01) {
                out.write(0xFF); out.write(marker)
                continue
            }
            require(pos + 2 <= bytes.size) { "Truncated JPEG segment length" }
            val segLen = ((bytes[pos].toInt() and 0xFF) shl 8) or (bytes[pos + 1].toInt() and 0xFF)
            require(segLen >= 2 && pos + segLen <= bytes.size) { "Invalid JPEG segment length $segLen" }
            val shouldStrip = marker == 0xE1 /* APP1 Exif/XMP */ ||
                marker == 0xED /* APP13 IPTC */ ||
                marker == 0xFE /* COM comment */
            if (shouldStrip) {
                removed.add("0x%02X".format(marker))
            } else {
                out.write(0xFF); out.write(marker)
                out.write(bytes, pos, segLen)
            }
            pos += segLen
        }
        val clean = out.toByteArray()
        return ScrubResult("JPEG", clean, removed, bytes.size - clean.size)
    }
}
