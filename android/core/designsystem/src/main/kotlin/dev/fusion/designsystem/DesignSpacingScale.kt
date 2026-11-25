// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import kotlin.math.abs

/**
 * Bounded 4dp-grid spacing scale quantizer.
 */
class DesignSpacingScale private constructor() {
    companion object {
        val SCALE_DP = listOf(4, 8, 12, 16, 24, 32, 48, 64)

        fun snapDp(dp: Int): Int {
            require(dp >= 0) { "negative dp" }
            return SCALE_DP.minByOrNull { abs(it - dp) } ?: 4
        }
    }
}
