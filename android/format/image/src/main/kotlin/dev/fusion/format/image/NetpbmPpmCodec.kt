// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.image

import java.io.ByteArrayOutputStream

/**
 * Clean-room Lossless **Netpbm Binary PPM (`P6` RGB24) / PGM (`P5` Gray8) Codec & Floyd-Steinberg
 * Error-Diffusion Monochrome Ditherer**.
 */
object NetpbmPpmCodec {
    const val SCOPE =
        "Clean-room Netpbm binary P6 RGB24 / P5 Gray8 codec and Floyd-Steinberg 7/16-3/16-5/16-1/16 error-diffusion ditherer."

    const val MAX_PIXELS = 4_194_304

    data class RgbImage(val width: Int, val height: Int, val rgbBytes: ByteArray) {
        init {
            require(width >= 1 && height >= 1 && width.toLong() * height <= MAX_PIXELS) { "invalid dimensions ${width}x$height" }
            require(rgbBytes.size == width * height * 3) { "rgbBytes length != 3 * width * height" }
        }
    }

    fun encodeP6(img: RgbImage): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("P6\n${img.width} ${img.height}\n255\n".toByteArray(Charsets.US_ASCII))
        out.write(img.rgbBytes)
        return out.toByteArray()
    }

    fun decodeP6(bytes: ByteArray): RgbImage {
        var pos = 0
        fun nextToken(): String {
            while (pos < bytes.size) {
                val c = bytes[pos].toInt().toChar()
                if (c == '#') {
                    while (pos < bytes.size && bytes[pos] != '\n'.code.toByte()) pos++
                } else if (c.isWhitespace()) {
                    pos++
                } else break
            }
            val start = pos
            while (pos < bytes.size && !bytes[pos].toInt().toChar().isWhitespace() && bytes[pos] != '#'.code.toByte()) pos++
            require(pos > start) { "truncated PPM header" }
            return String(bytes, start, pos - start, Charsets.US_ASCII)
        }
        val magic = nextToken()
        require(magic == "P6") { "expected P6 magic, got '$magic'" }
        val w = nextToken().toInt()
        val h = nextToken().toInt()
        val maxVal = nextToken().toInt()
        require(maxVal == 255) { "only maxval=255 supported, got $maxVal" }
        require(pos < bytes.size && bytes[pos].toInt().toChar().isWhitespace()) { "missing single whitespace delimiter after maxval" }
        pos++ // consume exactly 1 whitespace byte before binary raster!
        val expected = w * h * 3
        require(pos + expected == bytes.size) { "PPM raster byte size mismatch (${bytes.size - pos} != $expected)" }
        return RgbImage(w, h, bytes.copyOfRange(pos, bytes.size))
    }

    /** Applies classic Floyd-Steinberg `[7/16, 3/16, 5/16, 1/16]` error diffusion to grayscale `[0..255]`, returning `0` or `255` bytes. */
    fun floydSteinbergDither(width: Int, height: Int, gray8: ByteArray): ByteArray {
        require(width >= 1 && height >= 1 && gray8.size == width * height) { "invalid grayscale dimensions" }
        val work = DoubleArray(gray8.size) { (gray8[it].toInt() and 0xFF).toDouble() }
        val out = ByteArray(gray8.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val oldVal = work[idx]
                val newVal = if (oldVal >= 128.0) 255.0 else 0.0
                out[idx] = newVal.toInt().toByte()
                val err = oldVal - newVal
                if (x + 1 < width) work[idx + 1] += err * (7.0 / 16.0)
                if (y + 1 < height) {
                    if (x > 0) work[idx + width - 1] += err * (3.0 / 16.0)
                    work[idx + width] += err * (5.0 / 16.0)
                    if (x + 1 < width) work[idx + width + 1] += err * (1.0 / 16.0)
                }
            }
        }
        return out
    }
}
