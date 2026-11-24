// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

/**
 * Bounded OCR preprocessing pipeline for grayscale images represented as
 * byte arrays (0-255 per pixel). Applies: (1) optional grayscale
 * confirmation (pass-through), (2) threshold binarization at a given
 * threshold, (3) optional horizontal deskew detection by row-darkness
 * variance (declared approximation, not full Hough transform), and
 * (4) normalization to a target width while preserving aspect ratio with
 * padding. All output is bounded to [MAX_PIXELS] total pixels.
 *
 * Declared narrowings: input at most [MAX_INPUT] bytes; image dimensions
 * at most [MAX_DIM] x [MAX_DIM]; threshold in 0..255; target width at
 * most [MAX_WIDTH]; deskew is a declared approximation (row variance only,
 * no rotation matrix); no native OpenCV/Tesseract dependency — this is a
 * pure host-side preprocessing stage.
 */
class OcrPreprocessing private constructor() {
    companion object {
        const val MAX_INPUT = 1 << 20  // 1 MiB
        const val MAX_DIM = 4096
        const val MAX_PIXELS = 1 << 18  // 256K pixels
        const val MAX_WIDTH = 4096

        data class Preprocessed(
            val width: Int,
            val height: Int,
            val pixels: ByteArray,   // 0 or 255 after threshold
            val deskewAngleDeg: Double
        )

        fun empty(): OcrPreprocessing = OcrPreprocessing()
    }

    fun preprocess(
        pixels: ByteArray,
        width: Int,
        height: Int,
        threshold: Int = 128,
        targetWidth: Int = 0
    ): Preprocessed {
        require(pixels.size <= MAX_INPUT) { "input exceeds $MAX_INPUT bytes" }
        require(width > 0 && height > 0 && width <= MAX_DIM && height <= MAX_DIM) {
            "invalid dimensions"
        }
        require(pixels.size == width * height) { "pixel count mismatch" }
        require(threshold in 0..255) { "threshold out of range" }
        require(targetWidth == 0 || (targetWidth > 0 && targetWidth <= MAX_WIDTH)) {
            "targetWidth out of bounds"
        }

        // Binarize
        val binary = ByteArray(pixels.size)
        for (i in pixels.indices) {
            binary[i] = if ((pixels[i].toInt() and 0xFF) >= threshold) 255 else 0
        }

        // Deskew angle (declared approximation: row darkness variance)
        val angle = deskewAngle(binary, width, height)

        // Normalize width with padding if requested
        var outW = width
        var outH = height
        var out = binary
        if (targetWidth > 0 && targetWidth != width) {
            val scale = targetWidth.toDouble() / width
            outW = targetWidth
            outH = (height * scale).toInt().coerceIn(1, MAX_DIM)
            out = padOrScale(binary, width, height, outW, outH)
        }

        require(out.size <= MAX_PIXELS) { "output exceeds $MAX_PIXELS pixels" }
        return Preprocessed(outW, outH, out, angle)
    }

    fun thresholdOnly(pixels: ByteArray, threshold: Int): ByteArray {
        require(threshold in 0..255) { "threshold out of range" }
        return pixels.map { if ((it.toInt() and 0xFF) >= threshold) 255 else 0 }.toByteArray()
    }

    private fun deskewAngle(pixels: ByteArray, width: Int, height: Int): Double {
        // Approximation: compute mean darkness per row, then variance of
        // row means as a skew proxy. Not a true deskew — declared limit.
        val rowSums = DoubleArray(height)
        for (y in 0 until height) {
            var sum = 0
            for (x in 0 until width) {
                sum += (pixels[y * width + x].toInt() and 0xFF)
            }
            rowSums[y] = sum / width
        }
        val mean = rowSums.average()
        var variance = 0.0
        for (v in rowSums) {
            variance += (v - mean) * (v - mean)
        }
        variance /= height
        // Map variance to a small angle (declared heuristic)
        return (variance / 1_000_000.0).coerceIn(-5.0, 5.0)
    }

    private fun padOrScale(
        src: ByteArray, srcW: Int, srcH: Int, dstW: Int, dstH: Int
    ): ByteArray {
        val dst = ByteArray(dstW * dstH) { 0 }
        val xScale = srcW.toDouble() / dstW
        val yScale = srcH.toDouble() / dstH
        for (dy in 0 until dstH) {
            for (dx in 0 until dstW) {
                val sx = (dx * xScale).toInt().coerceIn(0, srcW - 1)
                val sy = (dy * yScale).toInt().coerceIn(0, srcH - 1)
                dst[dy * dstW + dx] = src[sy * srcW + sx]
            }
        }
        return dst
    }

    private fun Double.coerceIn(min: Double, max: Double): Double =
        if (this < min) min else if (this > max) max else this
    private fun Int.coerceIn(min: Int, max: Int): Int =
        if (this < min) min else if (this > max) max else this
}
