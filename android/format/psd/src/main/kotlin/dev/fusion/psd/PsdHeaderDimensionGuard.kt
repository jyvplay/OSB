// SPDX-License-Identifier: Apache-2.0
package dev.fusion.psd

/**
 * Bounded PSD header signature, version, channel, and dimension validator.
 */
class PsdHeaderDimensionGuard private constructor() {
    companion object {
        fun isValid(sig: String, version: Int, channels: Int, width: Int, height: Int): Boolean {
            if (sig != "8BPS") return false
            if (version != 1 && version != 2) return false
            if (channels !in 1..56) return false
            val maxDim = if (version == 1) 30_000 else 300_000
            return width in 1..maxDim && height in 1..maxDim
        }
    }
}
