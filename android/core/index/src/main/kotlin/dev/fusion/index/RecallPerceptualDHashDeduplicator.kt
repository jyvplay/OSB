// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

/**
 * 64-bit horizontal gradient difference hash (**dHash**) perceptual image & video-keyframe
 * near-duplicate detector (`9×8` luminance bilinear downsample + Hamming distance) preventing
 * **Recall** from storing redundant responsive/thumbnail variants of the same image.
 */
object RecallPerceptualDHashDeduplicator {
    const val SCOPE =
        "64-bit 9x8 horizontal luminance gradient perceptual dHash calculator and Hamming-distance near-duplicate clusterer for Recall images."

    fun computeDHash64FromLuma(luma: ByteArray, width: Int, height: Int): Long {
        require(width >= 9 && height >= 8 && luma.size == width * height) {
            "Image luma plane must be at least 9x8 (got ${width}x${height})"
        }
        val grid = IntArray(9 * 8)
        for (gy in 0 until 8) {
            val sy = (gy * height) / 8
            for (gx in 0 until 9) {
                val sx = (gx * width) / 9
                grid[gy * 9 + gx] = luma[sy * width + sx].toInt() and 0xFF
            }
        }
        var hash = 0L
        var bit = 0
        for (gy in 0 until 8) {
            for (gx in 0 until 8) {
                if (grid[gy * 9 + gx] > grid[gy * 9 + gx + 1]) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        return hash
    }

    fun hammingDistance64(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    fun isNearDuplicate(a: Long, b: Long, maxDistanceBits: Int = 8): Boolean =
        hammingDistance64(a, b) <= maxDistanceBits
}
