package dev.fusion.format.image

/**
 * BMP file-header + info-header parsing (the BITMAPFILEHEADER `BM` +
 * BITMAPINFOHEADER layout; the 40-byte info header is the same
 * structure IcoDir recognizes INSIDE icons — this kernel owns the
 * standalone .bmp shape).
 *
 * Fields enforced: magic `BM`; u32le file size matching the buffer;
 * u32le pixel-data offset inside the file and past both headers;
 * biSize 40; width > 0; height nonzero (negative = top-down, legal,
 * surfaced as [topDown]); planes 1; bitCount in {1,4,8,16,24,32}.
 *
 * Declared narrowings: headers only — no pixel decoding, no palette
 * reading, no BITMAPV4/V5 extensions (biSize != 40 refuses by name).
 */
object BmpHead {
    const val SCOPE = "BM + 40-byte info header fields only; no pixels/palette; V4/V5 refused by biSize."

    data class Head(
        val fileSize: Long, val dataOffset: Long, val width: Int, val height: Int,
        val topDown: Boolean, val bitCount: Int, val compression: Long
    )

    private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, i: Int): Long {
        var v = 0L
        for (k in 3 downTo 0) v = (v shl 8) or (b[i + k].toLong() and 0xFF)
        return v
    }
    private fun i32(b: ByteArray, i: Int): Int = u32(b, i).toInt()

    fun parse(bytes: ByteArray): Head {
        require(bytes.size >= 54) { "shorter than BMP headers (54 bytes)" }
        require(bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) { "not a BMP (magic)" }
        val fileSize = u32(bytes, 2)
        require(fileSize == bytes.size.toLong()) { "header claims $fileSize bytes, buffer has ${bytes.size}" }
        val dataOffset = u32(bytes, 10)
        require(dataOffset in 54..bytes.size.toLong()) { "pixel offset $dataOffset outside the file" }
        val biSize = u32(bytes, 14)
        require(biSize == 40L) { "biSize $biSize is not BITMAPINFOHEADER (V4/V5 out of declared scope)" }
        val width = i32(bytes, 18)
        val heightRaw = i32(bytes, 22)
        require(width > 0) { "width $width" }
        require(heightRaw != 0) { "zero height" }
        require(u16(bytes, 26) == 1) { "planes must be 1" }
        val bits = u16(bytes, 28)
        require(bits in intArrayOf(1, 4, 8, 16, 24, 32)) { "bitCount $bits invalid" }
        return Head(
            fileSize, dataOffset, width,
            kotlin.math.abs(heightRaw), heightRaw < 0, bits, u32(bytes, 30)
        )
    }

    /** Row stride in bytes: pixel rows pad to 4-byte boundaries. */
    fun stride(width: Int, bitCount: Int): Int {
        require(width > 0 && bitCount in intArrayOf(1, 4, 8, 16, 24, 32)) { "bad geometry" }
        return ((width * bitCount + 31) / 32) * 4
    }
}
