// SPDX-License-Identifier: Apache-2.0
package dev.fusion.benchmark

import kotlin.math.ceil

/**
 * Clean-room Cold/Warm Startup & `JankStats` Frame-Duration Percentile Analyzer (`RG-01`).
 *
 * Computes exact nearest-rank `P50`, `P90`, `P95`, and `P99` latencies plus Android Vitals
 * slow-frame (`> 16.67 ms` @ 60 Hz) and frozen-frame (`> 700.0 ms`) ratios!
 */
object StartupFrameP99Analyzer {
    data class FrameDistributionSummary(
        val sampleCount: Int,
        val p50Ms: Double,
        val p90Ms: Double,
        val p95Ms: Double,
        val p99Ms: Double,
        val slowFrameCount: Int,
        val frozenFrameCount: Int,
        val slowFrameFraction: Double,
        val passesDesktopJankBudget: Boolean, // P95 <= frameBudgetMs && frozen == 0 && slowFraction <= 0.03
    )

    fun analyzeFrames(
        frameDurationsMs: List<Double>,
        targetRefreshHz: Int = 60,
    ): FrameDistributionSummary {
        require(frameDurationsMs.isNotEmpty() && frameDurationsMs.all { it >= 0.0 && it.isFinite() }) {
            "frameDurationsMs must be non-empty non-negative finite numbers"
        }
        require(targetRefreshHz in 24..360) { "invalid targetRefreshHz $targetRefreshHz" }
        val sorted = frameDurationsMs.sorted()
        val budgetMs = 1000.0 / targetRefreshHz
        val slow = sorted.count { it > budgetMs }
        val frozen = sorted.count { it > 700.0 }
        val slowFrac = slow.toDouble() / sorted.size

        fun pct(p: Double): Double {
            val idx = (ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)
            return sorted[idx]
        }

        val p95 = pct(0.95)
        return FrameDistributionSummary(
            sampleCount = sorted.size,
            p50Ms = pct(0.50),
            p90Ms = pct(0.90),
            p95Ms = p95,
            p99Ms = pct(0.99),
            slowFrameCount = slow,
            frozenFrameCount = frozen,
            slowFrameFraction = slowFrac,
            passesDesktopJankBudget = p95 <= budgetMs && frozen == 0 && slowFrac <= 0.03,
        )
    }
}
