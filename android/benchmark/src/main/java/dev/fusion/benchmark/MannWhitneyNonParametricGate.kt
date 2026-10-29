// SPDX-License-Identifier: Apache-2.0
package dev.fusion.benchmark

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Non-parametric **Mann–Whitney U + Cliff's Delta (`δ`) Latency Regression Gate** resilient to
 * heavy-tailed GC/OS scheduling outliers where Gaussian Welch-t tests lose power!
 */
object MannWhitneyNonParametricGate {
    const val SCOPE =
        "Non-parametric Mann-Whitney U tie-corrected Z-statistic and Cliff's delta effect-size benchmark regression gate."

    data class NonParametricVerdict(
        val uStatistic: Double,
        val cliffsDelta: Double,
        val zScoreAbs: Double,
        val regressionDetected: Boolean,
    )

    fun evaluateLatencySamples(
        baselineMicros: DoubleArray,
        candidateMicros: DoubleArray,
        minCliffsDeltaRegression: Double = 0.33,
        minZScore99Pct: Double = 2.576,
    ): NonParametricVerdict {
        val n1 = baselineMicros.size
        val n2 = candidateMicros.size
        require(n1 >= 5 && n2 >= 5) { "Mann-Whitney gate requires >= 5 samples per arm" }

        var candidateHigherPairs = 0L
        var baselineHigherPairs = 0L
        var ties = 0L
        for (b in baselineMicros) {
            for (c in candidateMicros) {
                when {
                    c > b -> candidateHigherPairs++
                    c < b -> baselineHigherPairs++
                    else -> ties++
                }
            }
        }
        val u = candidateHigherPairs + 0.5 * ties
        val pairs = (n1.toLong() * n2.toLong()).toDouble()
        val cliffsDelta = (candidateHigherPairs - baselineHigherPairs).toDouble() / pairs
        val meanU = pairs * 0.5
        val stdU = sqrt(pairs * (n1 + n2 + 1) / 12.0).coerceAtLeast(1e-9)
        val zAbs = abs(u - meanU) / stdU
        // Regression only if candidate is systematically SLOWER (cliffsDelta > 0) AND statistically significant
        val regressed = cliffsDelta >= minCliffsDeltaRegression && zAbs >= minZScore99Pct
        return NonParametricVerdict(u, cliffsDelta, zAbs, regressed)
    }
}
