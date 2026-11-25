// SPDX-License-Identifier: Apache-2.0
package dev.fusion.perf

import kotlin.math.ceil

/**
 * Bounded nearest-rank percentile estimator over latency samples.
 */
class PercentileLatencyEstimator private constructor() {
    companion object {
        const val MAX_SAMPLES = 100_000

        fun percentile(samples: List<Double>, p: Double): Double {
            require(samples.isNotEmpty() && samples.size <= MAX_SAMPLES) { "samples size out of bounds" }
            require(p > 0.0 && p <= 100.0) { "percentile out of bounds: $p" }
            val sorted = samples.sorted()
            val rank = ceil((p / 100.0) * sorted.size).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }
    }
}
