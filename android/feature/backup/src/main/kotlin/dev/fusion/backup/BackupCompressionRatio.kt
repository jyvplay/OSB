// SPDX-License-Identifier: Apache-2.0
package dev.fusion.backup

/**
 * Bounded backup compression ratio calculator.
 */
class BackupCompressionRatio private constructor() {
    companion object {
        fun ratio(compressedBytes: Long, originalBytes: Long): Double {
            require(compressedBytes >= 0L && originalBytes >= 0L) { "negative bytes" }
            if (originalBytes == 0L) return 1.0
            return (compressedBytes.toDouble() / originalBytes.toDouble()).coerceIn(0.0, 1.0)
        }
    }
}
