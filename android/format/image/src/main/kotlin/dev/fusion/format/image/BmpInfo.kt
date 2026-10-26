// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.image

/** BMP header facts. No pixels are decoded. */
data class BmpInfo(
    val width: Int,
    val height: Int,
    val topDown: Boolean,
    val bpp: Int,
    val compression: Int,
    val dataOffset: Int,
    val fileBytes: Int,
)

/**
 * BMP structural header reader: `BM` magic, declared file size matched
 * exactly against the input, data-offset sanity, BITMAPINFOHEADER only
 * (OS2/V4/V5 headers refused), planes == 1, bpp in {1,4,8,24,32},
 * compression BI_RGB(0)/BI_BITFIELDS(3) only (RLE refused). Negative
 * height reports top-down orientation. Pixel data is never touched.
 */
object BmpInspect {
    const val MAX_DIM = 32768

    fun parse(bytes: ByteArray): BmpInfo {
        require(bytes.size >= 54) { "under 54 bytes" }
        require(bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) { "bad magic" }
        val declared = u32(bytes, 2).toInt()
        require(declared == bytes.size) { "declared $declared != actual ${bytes.size}" }
        val dataOffset = u32(bytes, 10).toInt()
        require(dataOffset in 54..bytes.size) { "data offset $dataOffset out of range" }
        require(u32(bytes, 14) == 40L) { "only BITMAPINFOHEADER admitted" }
        val width = s32(bytes, 18)
        val rawHeight = s32(bytes, 22)
        require(width in 1..MAX_DIM) { "width $width outside 1..$MAX_DIM" }
        require(rawHeight != 0 && rawHeight in -MAX_DIM..MAX_DIM) { "bad height $rawHeight" }
        require(u16(bytes, 26) == 1) { "planes != 1" }
        val bpp = u16(bytes, 28)
        require(bpp == 1 || bpp == 4 || bpp == 8 || bpp == 24 || bpp == 32) { "bpp $bpp refused" }
        val compression = u32(bytes, 30).toInt()
        require(compression == 0 || compression == 3) { "compression $compression refused (RLE out)" }
        return BmpInfo(width, kotlin.math.abs(rawHeight), rawHeight < 0, bpp, compression, dataOffset, bytes.size)
    }

    private fun u16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, at: Int): Long =
        (b[at].toLong() and 0xFF) or ((b[at + 1].toLong() and 0xFF) shl 8) or
            ((b[at + 2].toLong() and 0xFF) shl 16) or ((b[at + 3].toLong() and 0xFF) shl 24)

    private fun s32(b: ByteArray, at: Int): Int = u32(b, at).toInt()
}
