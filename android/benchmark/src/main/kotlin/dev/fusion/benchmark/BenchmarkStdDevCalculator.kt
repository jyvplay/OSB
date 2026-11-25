// SPDX-License-Identifier: Apache-2.0
package dev.fusion.benchmark

import kotlin.math.sqrt

/**
 * Bounded sample mean and population standard deviation calculator.
 */
class BenchmarkStdDevCalculator private constructor() {
    companion object {
        fun stats(samples: List<Double>): Pair<Double, Double> {
            require(samples.isNotEmpty()) { "empty samples" }
            var sum = 0.0
            for (v in samples) {
                require(v.isFinite()) { "non-finite sample" }
                sum += v
            }
            val mean = sum / samples.size
            var sq = 0.0
            for (v in samples) {
                val d = v - mean
                sq += d * d
            }
            return mean to sqrt(sq / samples.size)
        }
    }
}
