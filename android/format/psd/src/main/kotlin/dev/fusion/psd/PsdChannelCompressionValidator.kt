// SPDX-License-Identifier: Apache-2.0
package dev.fusion.psd

/**
 * Bounded PSD channel compression validator. Validates PSD compression
 * codes (0=Raw, 1=RLE, 2=ZIP, 3=ZIP+Prediction) and computes expected
 * uncompressed channel byte size within bounded dimensions.
 */
class PsdChannelCompressionValidator private constructor() {
    companion object {
        const val MAX_DIM = 30_000
        val ALLOWED_DEPTHS = setOf(1, 8, 16, 32)
        val ALLOWED_COMPRESSIONS = setOf(0, 1, 2, 3)

        fun expectedUncompressedBytes(width: Int, height: Int, depthBits: Int, compression: Int): Long {
            require(width in 1..MAX_DIM && height in 1..MAX_DIM) { "dimensions out of bounds" }
            require(depthBits in ALLOWED_DEPTHS) { "unsupported depth: $depthBits" }
            require(compression in ALLOWED_COMPRESSIONS) { "unsupported compression: $compression" }
            val bitsPerRow = width.toLong() * depthBits.toLong()
            val bytesPerRow = (bitsPerRow + 7L) / 8L
            return bytesPerRow * height.toLong()
        }
    }
}
