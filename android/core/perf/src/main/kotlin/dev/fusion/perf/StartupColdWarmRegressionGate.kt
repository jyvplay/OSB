package dev.fusion.perf

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Clean-room Non-Parametric Mann-Whitney U + Percentile Startup & Jank Regression Gate
 * (`RG-05`, Apache-2.0).
 *
 * Detects statistically significant (`z >= 1.96`, `p < 0.05`) AND practical (`medianDeltaPct > maxRegressionPct`)
 * startup/frame-time regressions without assuming Gaussian normality on bimodal cold-start distributions!
 */
object StartupColdWarmRegressionGate {
    const val SCOPE =
        "Mann-Whitney U z-score + p50/p95/p99 cold/warm startup & frame-time CI regression gate (RG-05)"

    data class RegressionVerdict(
        val passed: Boolean,
        val baselineP50Ms: Double,
        val candidateP50Ms: Double,
        val candidateP95Ms: Double,
        val candidateP99Ms: Double,
        val relativeMedianDeltaPct: Double,
        val mannWhitneyZ: Double,
        val reason: String,
    )

    fun evaluate(
        baselineSamplesMs: List<Double>,
        candidateSamplesMs: List<Double>,
        maxAllowedMedianRegressionPct: Double = 5.0,
        hardCeilingP99Ms: Double = 900.0,
    ): RegressionVerdict {
        require(baselineSamplesMs.size >= 5 && candidateSamplesMs.size >= 5) {
            "Regression gate requires >= 5 samples per cohort"
        }
        val baseSorted = baselineSamplesMs.sorted()
        val candSorted = candidateSamplesMs.sorted()
        val b50 = percentile(baseSorted, 0.50)
        val c50 = percentile(candSorted, 0.50)
        val c95 = percentile(candSorted, 0.95)
        val c99 = percentile(candSorted, 0.99)
        val deltaPct = if (b50 == 0.0) 0.0 else ((c50 - b50) / b50) * 100.0

        // Mann-Whitney U: count how often candidate > baseline (+0.5 on ties)
        val n1 = baseSorted.size
        val n2 = candSorted.size
        var uCandSlower = 0.0
        for (c in candSorted) {
            for (b in baseSorted) {
                if (c > b) uCandSlower += 1.0 else if (c == b) uCandSlower += 0.5
            }
        }
        val meanU = (n1.toDouble() * n2.toDouble()) / 2.0
        val stdU = sqrt((n1.toDouble() * n2.toDouble() * (n1 + n2 + 1).toDouble()) / 12.0)
        val z = if (stdU == 0.0) 0.0 else (uCandSlower - meanU) / stdU

        val p99Exceeded = c99 > hardCeilingP99Ms
        val significantRegression = z >= 1.96 && deltaPct > maxAllowedMedianRegressionPct
        val passed = !p99Exceeded && !significantRegression
        val reason = when {
            p99Exceeded -> "P99_CEILING_EXCEEDED(${"%.1f".format(c99)}ms > ${hardCeilingP99Ms}ms)"
            significantRegression -> "SIGNIFICANT_STARTUP_REGRESSION(+${"%.2f".format(deltaPct)}%, z=${"%.2f".format(z)})"
            else -> "WITHIN_PERF_BUDGET"
        }
        return RegressionVerdict(passed, b50, c50, c95, c99, deltaPct, z, reason)
    }

    private fun percentile(sorted: List<Double>, q: Double): Double {
        val idx = (ceil(q * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return sorted[idx]
    }
}
