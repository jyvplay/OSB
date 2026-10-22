// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.image

/** GIF header facts plus an exact frame count. No pixels are decoded. */
data class GifInfo(val version: String, val width: Int, val height: Int, val frames: Int, val hasGlobalTable: Boolean)

/**
 * GIF87a/89a structural walk: validates the header and logical screen
 * descriptor, skips the global color table, then walks blocks counting
 * image descriptors (frames) and skipping extensions, image data, and the
 * trailer. LZW payloads are never decoded — frame data is skipped by its
 * sub-block chain, so a corrupt pixel stream cannot corrupt the count, only
 * truncate the walk (which refuses). Plaintext/application/comment
 * extensions are skipped, never interpreted.
 */
object GifInspect {
    const val MAX_BYTES = 64 * 1024 * 1024
    const val MAX_FRAMES = 1024

    fun parse(bytes: ByteArray): GifInfo {
        require(bytes.size in 13..MAX_BYTES)
        val magic = String(bytes, 0, 6, Charsets.US_ASCII)
        require(magic == "GIF87a" || magic == "GIF89a")
        val w = u16(bytes, 6); val h = u16(bytes, 8)
        require(w in 1..65535 && h in 1..65535)
        val packed = bytes[10].toInt() and 0xFF
        val hasGct = packed and 0x80 != 0
        var pos = 13
        if (hasGct) {
            val entries = 1 shl ((packed and 0x07) + 1)
            pos += entries * 3
            require(pos <= bytes.size)
        }
        var frames = 0
        while (true) {
            require(pos < bytes.size)
            when (bytes[pos].toInt() and 0xFF) {
                0x3B -> {
                    require(pos == bytes.size - 1) { "trailing bytes" }
                    require(frames >= 1)
                    return GifInfo(magic, w, h, frames, hasGct)
                }
                0x21 -> { // extension: label + sub-blocks
                    require(pos + 2 <= bytes.size)
                    pos = skipSubBlocks(bytes, pos + 2)
                }
                0x2C -> { // image descriptor: 9 fixed bytes + optional local table + data
                    require(pos + 10 <= bytes.size)
                    frames++
                    require(frames <= MAX_FRAMES)
                    val lp = bytes[pos + 9].toInt() and 0xFF
                    pos += 10
                    if (lp and 0x80 != 0) {
                        pos += (1 shl ((lp and 0x07) + 1)) * 3
                        require(pos <= bytes.size)
                    }
                    require(pos < bytes.size) // lzw minimum code size
                    pos = skipSubBlocks(bytes, pos + 1)
                }
                else -> throw IllegalArgumentException("bad block at $pos")
            }
        }
    }

    private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun skipSubBlocks(b: ByteArray, pos: Int): Int {
        var p = pos
        while (true) {
            require(p < b.size)
            val n = b[p].toInt() and 0xFF
            p++
            if (n == 0) return p
            p += n
            require(p <= b.size)
        }
    }
}
