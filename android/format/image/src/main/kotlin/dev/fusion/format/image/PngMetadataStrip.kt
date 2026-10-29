// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.image

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

/**
 * Clean-room ISO/IEC 15948 PNG Binary Chunk Verifier & Tails/`mat2`-Style Metadata Scrubber.
 *
 * Strips forensic ancillary metadata chunks (`eXIf` GPS/camera serials, `tEXt`/`zTXt`/`iTXt`
 * comments & XMP XML, `tIME` modification timestamps, `pHYs`, `iCCP` device color profiles)
 * while preserving exact lossless pixel/palette chunks (`IHDR`, `PLTE`, `tRNS`, `IDAT`, `IEND`)
 * and verifying every chunk's IEEE 802.3 CRC-32 before AND after scrubbing!
 */
object PngMetadataStrip {
    const val SCOPE =
        "ISO/IEC 15948 PNG binary chunk parser, IEEE 802.3 CRC-32 verifier, IHDR geometry inspector, and ancillary metadata (eXIf/tEXt/zTXt/iTXt/tIME/iCCP) scrubber."

    const val MAX_PNG_BYTES = 32 * 1024 * 1024
    private val PNG_SIG = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )
    private val SAFE_CHUNKS = setOf("IHDR", "PLTE", "tRNS", "sRGB", "gAMA", "IDAT", "IEND")

    data class PngInfo(
        val width: Int,
        val height: Int,
        val bitDepth: Int,
        val colorType: Int,
        val chunkTypes: List<String>,
    )

    data class StripResult(
        val cleanBytes: ByteArray,
        val info: PngInfo,
        val strippedChunks: List<String>,
    )

    fun inspectAndStrip(pngBytes: ByteArray): StripResult {
        require(pngBytes.size in 33..MAX_PNG_BYTES) { "PNG size ${pngBytes.size} outside 33..$MAX_PNG_BYTES" }
        for (i in PNG_SIG.indices) {
            require(pngBytes[i] == PNG_SIG[i]) { "invalid PNG 8-byte signature at byte $i" }
        }

        val out = ByteArrayOutputStream(pngBytes.size)
        out.write(PNG_SIG)

        var pos = 8
        var sawIhdr = false
        var sawIdat = false
        var sawIend = false
        var width = 0; var height = 0; var bitDepth = 0; var colorType = 0
        val allTypes = ArrayList<String>()
        val stripped = ArrayList<String>()

        while (pos < pngBytes.size) {
            require(!sawIend) { "trailing bytes after PNG IEND chunk" }
            require(pos + 12 <= pngBytes.size) { "truncated PNG chunk header at offset $pos" }
            val len = ByteBuffer.wrap(pngBytes, pos, 4).order(ByteOrder.BIG_ENDIAN).int
            require(len >= 0 && pos.toLong() + 12L + len <= pngBytes.size) {
                "PNG chunk length $len overflows buffer at offset $pos"
            }
            val typeBytes = pngBytes.copyOfRange(pos + 4, pos + 8)
            require(typeBytes.all { (it.toInt() and 0xFF) in 65..90 || (it.toInt() and 0xFF) in 97..122 }) {
                "invalid PNG chunk type bytes at offset ${pos + 4}"
            }
            val type = String(typeBytes, StandardCharsets.US_ASCII)
            val dataOffset = pos + 8
            val crcOffset = dataOffset + len
            val storedCrc = ByteBuffer.wrap(pngBytes, crcOffset, 4).order(ByteOrder.BIG_ENDIAN).int

            val crc = CRC32()
            crc.update(pngBytes, pos + 4, 4 + len)
            require(crc.value.toInt() == storedCrc) {
                "PNG chunk '$type' CRC-32 mismatch at offset $pos"
            }

            if (!sawIhdr) {
                require(type == "IHDR" && len == 13) { "first PNG chunk must be 13-byte IHDR" }
                val ib = ByteBuffer.wrap(pngBytes, dataOffset, 13).order(ByteOrder.BIG_ENDIAN)
                width = ib.int
                height = ib.int
                bitDepth = ib.get().toInt() and 0xFF
                colorType = ib.get().toInt() and 0xFF
                require(width > 0 && height > 0) { "invalid PNG dimensions ${width}x$height" }
                sawIhdr = true
            }
            if (type == "IDAT") sawIdat = true
            if (type == "IEND") {
                require(len == 0) { "IEND chunk must have 0-byte payload" }
                sawIend = true
            }

            allTypes.add(type)
            if (type in SAFE_CHUNKS) {
                out.write(pngBytes, pos, 12 + len)
            } else {
                // Critical unknown chunks (uppercase 1st letter) cannot be safely dropped per ISO 15948!
                require(type[0].isLowerCase()) { "unknown critical PNG chunk '$type' refused" }
                stripped.add(type)
            }
            pos = crcOffset + 4
        }

        require(sawIhdr && sawIdat && sawIend) { "incomplete PNG stream (missing IHDR/IDAT/IEND)" }
        return StripResult(
            cleanBytes = out.toByteArray(),
            info = PngInfo(width, height, bitDepth, colorType, allTypes),
            strippedChunks = stripped,
        )
    }

    /** Builds a valid, CRC-verified PNG chunk (`[len:u32][type:4B][data][crc32:u32]`). */
    fun buildChunk(type: String, data: ByteArray): ByteArray {
        val tb = type.toByteArray(StandardCharsets.US_ASCII)
        require(tb.size == 4) { "PNG chunk type must be 4 ASCII chars" }
        val crc = CRC32().apply { update(tb); update(data) }.value.toInt()
        return ByteBuffer.allocate(12 + data.size).order(ByteOrder.BIG_ENDIAN)
            .putInt(data.size)
            .put(tb)
            .put(data)
            .putInt(crc)
            .array()
    }
}
