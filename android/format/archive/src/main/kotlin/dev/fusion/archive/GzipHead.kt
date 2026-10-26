// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

import java.util.zip.CRC32

/** Gzip member header facts plus the deflate-stream offset. */
data class GzipHead(
    val mtime: Long,
    val xfl: Int,
    val os: Int,
    val name: String?,
    val headerLength: Int,
)

/**
 * Gzip member-header reader (RFC 1952 §2.3): magic, CM == 8 (deflate),
 * flag-walked extras (FEXTRA counted-skipped, FNAME captured latin-1,
 * FCOMMENT skipped, FHCRC verified against the header CRC16), reserved
 * flag bits refused. Returns the header length so the caller finds the
 * deflate stream; decompression and the CRC32/ISIZE trailer stay with
 * the caller. Multi-member layout is caller duty. Caps: 64 KiB header,
 * 256-char name.
 */
object GzipInspect {
    const val MAX_HEAD = 64 * 1024
    const val MAX_NAME = 256

    fun parse(bytes: ByteArray): GzipHead {
        require(bytes.size >= 10) { "under 10 bytes" }
        require(bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) { "bad magic" }
        require(bytes[2] == 8.toByte()) { "CM ${bytes[2]} != 8" }
        val flg = bytes[3].toInt() and 0xFF
        require(flg and 0xE0 == 0) { "reserved flag bits" }
        val mtime = u32(bytes, 4)
        val xfl = bytes[8].toInt() and 0xFF
        val os = bytes[9].toInt() and 0xFF
        var i = 10
        if (flg and 0x04 != 0) {
            require(i + 2 <= bytes.size) { "truncated FEXTRA length" }
            val xlen = u16(bytes, i)
            i += 2
            require(i + xlen <= bytes.size) { "truncated FEXTRA" }
            i += xlen
        }
        var name: String? = null
        if (flg and 0x08 != 0) {
            val start = i
            while (true) {
                require(i < bytes.size) { "unterminated FNAME" }
                require(i - start <= MAX_NAME) { "FNAME over $MAX_NAME chars" }
                if (bytes[i] == 0.toByte()) break
                i++
            }
            name = String(bytes, start, i - start, Charsets.ISO_8859_1)
            i++
        }
        if (flg and 0x10 != 0) {
            while (true) {
                require(i < bytes.size) { "unterminated FCOMMENT" }
                if (bytes[i] == 0.toByte()) break
                i++
            }
            i++
        }
        if (flg and 0x02 != 0) {
            require(i + 2 <= bytes.size) { "truncated FHCRC" }
            val crc = CRC32()
            crc.update(bytes, 0, i)
            require((crc.value and 0xFFFF).toInt() == u16(bytes, i)) { "FHCRC mismatch" }
            i += 2
        }
        require(i <= MAX_HEAD) { "header over $MAX_HEAD bytes" }
        require(i < bytes.size) { "no deflate payload" }
        return GzipHead(mtime, xfl, os, name, i)
    }

    private fun u16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, at: Int): Long =
        (b[at].toLong() and 0xFF) or ((b[at + 1].toLong() and 0xFF) shl 8) or
            ((b[at + 2].toLong() and 0xFF) shl 16) or ((b[at + 3].toLong() and 0xFF) shl 24)
}
