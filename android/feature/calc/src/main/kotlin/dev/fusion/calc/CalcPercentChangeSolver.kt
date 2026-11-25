// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

import kotlin.math.abs

/**
 * Bounded percentage change calculator with zero-denominator guard.
 */
class CalcPercentChangeSolver private constructor() {
    companion object {
        fun percentChange(oldVal: Double, newVal: Double): Double {
            require(oldVal.isFinite() && newVal.isFinite()) { "non-finite input" }
            require(oldVal != 0.0) { "division by zero: oldVal == 0" }
            return ((newVal - oldVal) / abs(oldVal)) * 100.0
        }
    }
}
