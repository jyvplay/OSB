// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.image

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * W3C PNG (ISO/IEC 15948) binary chunk parser, IEEE 802.3 CRC-32 verifier, and privacy metadata
 * chunk stripper removing `eXIf`, `tEXt`, `zTXt`, `iTXt`, and `tIME` chunks while preserving
 * `IHDR`, `PLTE`, `tRNS`, `gAMA`, `sRGB`, `IDAT`, and `IEND`.
 */
object PngChunkSanitizerAndCrcCodec {
    const val SCOPE =
        "ISO/IEC 15948 PNG chunk CRC-32 verifier and privacy metadata stripper (removing eXIf, tEXt, zTXt, iTXt, tIME)."

    private val PNG_SIG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val SENSITIVE_CHUNKS = setOf("eXIf", "tEXt", "zTXt", "iTXt", "tIME", "pHYs")

    data class SanitizedPngResult(
        val cleanBytes: ByteArray,
        val keptChunkTypes: List<String>,
        val strippedChunkTypes: List<String>,
    )

    fun sanitizePng(pngBytes: ByteArray): SanitizedPngResult {
        require(pngBytes.size >= 33 && pngBytes.sliceArray(0 until 8).contentEquals(PNG_SIG)) {
            "Invalid PNG 8-byte signature"
        }
        val out = ByteArrayOutputStream()
        out.write(PNG_SIG)
        val kept = ArrayList<String>()
        val stripped = ArrayList<String>()
        var pos = 8
        var sawIend = false

        while (pos + 12 <= pngBytes.size) {
            val len = readU32Be(pngBytes, pos)
            require(len >= 0 && pos + 12L + len <= pngBytes.size) { "Truncated PNG chunk at $pos" }
            val typeStr = String(pngBytes, pos + 4, 4, Charsets.ISO_8859_1)
            val expectedCrc = readU32Be(pngBytes, pos + 8 + len)
            val crcCalc = CRC32().apply { update(pngBytes, pos + 4, 4 + len) }.value.toInt()
            require(crcCalc == expectedCrc) { "Corrupted PNG CRC-32 in chunk '$typeStr'" }

            if (typeStr in SENSITIVE_CHUNKS) {
                stripped.add(typeStr)
            } else {
                out.write(pngBytes, pos, 12 + len)
                kept.add(typeStr)
            }
            pos += 12 + len
            if (typeStr == "IEND") { sawIend = true; break }
        }
        require(sawIend) { "PNG missing terminal IEND chunk" }
        return SanitizedPngResult(out.toByteArray(), kept, stripped)
    }

    fun makeChunk(typeFourCc: String, data: ByteArray): ByteArray {
        require(typeFourCc.length == 4)
        val typeBytes = typeFourCc.toByteArray(Charsets.ISO_8859_1)
        val crc = CRC32().apply { update(typeBytes); update(data) }.value.toInt()
        val out = ByteArrayOutputStream(12 + data.size)
        writeU32Be(out, data.size)
        out.write(typeBytes)
        out.write(data)
        writeU32Be(out, crc)
        return out.toByteArray()
    }

    private fun readU32Be(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun writeU32Be(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 24) and 0xFF); out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF); out.write(v and 0xFF)
    }
}
