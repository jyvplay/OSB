// SPDX-License-Identifier: Apache-2.0
package dev.fusion.perf

/**
 * Bounded exponential moving average (EMA) filter.
 */
class PerfMovingAverage private constructor(private val alpha: Double) {
    companion object {
        fun create(alpha: Double): PerfMovingAverage {
            require(alpha > 0.0 && alpha <= 1.0 && alpha.isFinite()) { "alpha out of bounds" }
            return PerfMovingAverage(alpha)
        }
    }

    private var value: Double? = null

    fun update(sample: Double): Double {
        require(sample.isFinite()) { "non-finite sample" }
        val prev = value
        val next = if (prev == null) sample else alpha * sample + (1.0 - alpha) * prev
        value = next
        return next
    }
}
