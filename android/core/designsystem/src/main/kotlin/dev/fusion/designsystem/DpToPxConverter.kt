// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import kotlin.math.roundToInt

/**
 * Bounded dp-to-px converter with round-half-up semantics.
 */
class DpToPxConverter private constructor() {
    companion object {
        fun dpToPx(dp: Double, density: Double): Int {
            require(dp.isFinite() && dp >= 0.0) { "invalid dp" }
            require(density.isFinite() && density > 0.0) { "invalid density" }
            return (dp * density).roundToInt()
        }
    }
}
