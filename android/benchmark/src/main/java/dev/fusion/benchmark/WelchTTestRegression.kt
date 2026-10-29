// SPDX-License-Identifier: Apache-2.0
package dev.fusion.benchmark

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Clean-room **Welch's Two-Sample Unequal-Variance `t`-Test + Welch-Satterthwaite Degrees of Freedom
 * + Pooled Cohen's `d` Effect-Size Benchmark Regression Gate**.
 *
 * Prevents noisy microbenchmark jitter from causing false-positive CI failures while reliably
 * flagging statistically & practically significant (`|t| >= criticalT` AND `cohensD >= minEffectSize`)
 * latency regressions!
 */
object WelchTTestRegression {
    const val SCOPE =
        "Clean-room Welch unequal-variance t-test, Welch-Satterthwaite degrees of freedom, and Cohen's d benchmark regression gate."

    enum class Verdict { PASS_NO_REGRESSION, SIGNIFICANT_IMPROVEMENT, SIGNIFICANT_REGRESSION }

    data class Comparison(
        val baselineMean: Double,
        val candidateMean: Double,
        val deltaPercent: Double,
        val tStatistic: Double,
        val degreesOfFreedom: Double,
        val cohensD: Double,
        val verdict: Verdict,
    )

    fun evaluate(
        baselineSamplesNs: List<Double>,
        candidateSamplesNs: List<Double>,
        alphaTwoSided99: Boolean = false,
        minPracticalEffectD: Double = 0.5,
    ): Comparison {
        require(baselineSamplesNs.size >= 3 && candidateSamplesNs.size >= 3) {
            "Welch t-test requires >= 3 samples per arm"
        }
        val n1 = baselineSamplesNs.size.toDouble()
        val n2 = candidateSamplesNs.size.toDouble()
        val m1 = baselineSamplesNs.sum() / n1
        val m2 = candidateSamplesNs.sum() / n2
        val v1 = baselineSamplesNs.sumOf { (it - m1).pow(2) } / (n1 - 1.0)
        val v2 = candidateSamplesNs.sumOf { (it - m2).pow(2) } / (n2 - 1.0)

        val seSq = v1 / n1 + v2 / n2
        if (seSq <= 1e-18) {
            val deltaPct = if (m1 == 0.0) 0.0 else ((m2 - m1) / m1) * 100.0
            val v = when {
                m2 > m1 * 1.02 -> Verdict.SIGNIFICANT_REGRESSION
                m2 < m1 * 0.98 -> Verdict.SIGNIFICANT_IMPROVEMENT
                else -> Verdict.PASS_NO_REGRESSION
            }
            return Comparison(m1, m2, deltaPct, 0.0, n1 + n2 - 2.0, 0.0, v)
        }
        val tStat = (m2 - m1) / sqrt(seSq)
        val dfNum = seSq.pow(2)
        val dfDen = (v1 / n1).pow(2) / (n1 - 1.0) + (v2 / n2).pow(2) / (n2 - 1.0)
        val df = dfNum / dfDen

        val pooledSd = sqrt(((n1 - 1.0) * v1 + (n2 - 1.0) * v2) / (n1 + n2 - 2.0)).coerceAtLeast(1e-12)
        val d = (m2 - m1) / pooledSd
        val deltaPct = if (m1 == 0.0) 0.0 else ((m2 - m1) / m1) * 100.0

        // Conservative finite-sample critical t approximation (Cornish-Fisher 1st-order z + (z^3 + z)/(4*df))
        val z = if (alphaTwoSided99) 2.5758 else 1.95996
        val tCrit = z + (z.pow(3) + z) / (4.0 * df.coerceAtLeast(2.0))

        val verdict = when {
            tStat >= tCrit && d >= minPracticalEffectD -> Verdict.SIGNIFICANT_REGRESSION
            tStat <= -tCrit && abs(d) >= minPracticalEffectD -> Verdict.SIGNIFICANT_IMPROVEMENT
            else -> Verdict.PASS_NO_REGRESSION
        }
        return Comparison(m1, m2, deltaPct, tStat, df, d, verdict)
    }
}
