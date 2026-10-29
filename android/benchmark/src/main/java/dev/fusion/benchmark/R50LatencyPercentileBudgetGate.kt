// SPDX-License-Identifier: Apache-2.0
package dev.fusion.benchmark

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Clean-room Micro/Macro-Benchmark $p_{50} / p_{95} / p_{99}$ Nanosecond Frame-Budget Gate &
 * Welch's Two-Sample $t$-Test Performance Regression Detector.
 */
object R50LatencyPercentileBudgetGate {
    const val SCOPE =
        "Pure-JVM p50/p95/p99 latency percentile calculator and Welch two-sample t-statistic frame-budget regression gate."

    data class BenchmarkVerdict(
        val p50Micros: Double,
        val p95Micros: Double,
        val p99Micros: Double,
        val welchT: Double,
        val withinFrameBudget: Boolean,
    )

    fun evaluateSamples(
        baselineMicros: DoubleArray,
        candidateMicros: DoubleArray,
        p95BudgetMicros: Double = 16_667.0, // 60 Hz frame budget
    ): BenchmarkVerdict {
        require(baselineMicros.size >= 4 && candidateMicros.size >= 4 && p95BudgetMicros > 0.0) {
            "at least 4 samples required per benchmark cohort"
        }
        val sorted = candidateMicros.sortedArray()
        fun pct(p: Double): Double {
            val idx = (ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)
            return sorted[idx]
        }
        val m1 = baselineMicros.average(); val m2 = candidateMicros.average()
        val v1 = baselineMicros.sumOf { (it - m1) * (it - m1) } / (baselineMicros.size - 1)
        val v2 = candidateMicros.sumOf { (it - m2) * (it - m2) } / (candidateMicros.size - 1)
        val se = sqrt(v1 / baselineMicros.size + v2 / candidateMicros.size)
        val welchT = if (se > 1e-12) (m2 - m1) / se else 0.0
        val p95 = pct(0.95)
        return BenchmarkVerdict(pct(0.50), p95, pct(0.99), welchT, p95 <= p95BudgetMicros)
    }
}
