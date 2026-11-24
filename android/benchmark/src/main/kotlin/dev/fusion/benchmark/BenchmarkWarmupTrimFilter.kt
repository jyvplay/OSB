// SPDX-License-Identifier: Apache-2.0
package dev.fusion.benchmark

/**
 * Bounded benchmark warmup trim filter. Drops the first [warmupCount] samples
 * and computes count, min, max, and mean over the remaining finite samples.
 */
class BenchmarkWarmupTrimFilter private constructor() {
    companion object {
        const val MAX_SAMPLES = 100_000

        data class Summary(
            val count: Int,
            val min: Double,
            val max: Double,
            val mean: Double
        )

        fun summarize(samples: List<Double>, warmupCount: Int): Summary {
            require(samples.size <= MAX_SAMPLES) { "too many samples" }
            require(warmupCount >= 0 && warmupCount < samples.size) { "invalid warmupCount" }
            val kept = samples.subList(warmupCount, samples.size)
            var minVal = Double.POSITIVE_INFINITY
            var maxVal = Double.NEGATIVE_INFINITY
            var sum = 0.0
            for (v in kept) {
                require(v.isFinite()) { "non-finite sample: $v" }
                if (v < minVal) minVal = v
                if (v > maxVal) maxVal = v
                sum += v
            }
            return Summary(kept.size, minVal, maxVal, sum / kept.size)
        }
    }
}
