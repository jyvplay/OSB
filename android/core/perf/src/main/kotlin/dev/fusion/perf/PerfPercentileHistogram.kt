// SPDX-License-Identifier: Apache-2.0
package dev.fusion.perf

/**
 * Production-grade bounded sliding-window P50/P95/P99 latency &
 * 60fps frame-jank (>16.67ms) histogram.
 */
class PerfPercentileHistogram private constructor(private val maxSamples: Int) {
    companion object {
        const val JANK_THRESHOLD_MS = 16.67

        fun create(maxSamples: Int = 1024): PerfPercentileHistogram {
            require(maxSamples in 1..65_536) { "maxSamples out of bounds" }
            return PerfPercentileHistogram(maxSamples)
        }
    }

    private val samples = ArrayDeque<Double>()

    fun recordMs(durationMs: Double) {
        require(durationMs >= 0.0) { "negative duration" }
        if (samples.size == maxSamples) samples.removeFirst()
        samples.addLast(durationMs)
    }

    fun percentile(p: Double): Double {
        require(p in 0.0..100.0) { "percentile out of bounds" }
        if (samples.isEmpty()) return 0.0
        val sorted = samples.sorted()
        val idx = (((p / 100.0) * (sorted.size - 1)).toInt()).coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    fun jankRate(): Double {
        if (samples.isEmpty()) return 0.0
        val janks = samples.count { it > JANK_THRESHOLD_MS }
        return janks.toDouble() / samples.size.toDouble()
    }
}
