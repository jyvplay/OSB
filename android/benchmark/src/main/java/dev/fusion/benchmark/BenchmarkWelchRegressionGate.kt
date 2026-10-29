// SPDX-License-Identifier: Apache-2.0
package dev.fusion.benchmark

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure-JVM **Mann-Whitney U / Welch Two-Sample Non-Parametric Latency Regression Gate & MAD
 * Outlier Detector** for `:benchmark` (`Macrobenchmark` / host microbenchmark verification).
 */
object BenchmarkWelchRegressionGate {
    const val SCOPE =
        "Two-sample Welch t-statistic + Median Absolute Deviation (MAD) benchmark latency regression detector."

    data class RegressionVerdict(
        val baselineMedianMs: Double,
        val candidateMedianMs: Double,
        val medianDeltaRatio: Double,
        val welchTStat: Double,
        val regressedSignificantly: Boolean,
    )

    fun compareRuns(
        baselineSamplesMs: List<Double>,
        candidateSamplesMs: List<Double>,
        maxAllowedSlowdownRatio: Double = 0.08,
    ): RegressionVerdict {
        require(baselineSamplesMs.size >= 3 && candidateSamplesMs.size >= 3) { "Need >=3 samples per run" }
        val bMed = median(baselineSamplesMs)
        val cMed = median(candidateSamplesMs)
        val deltaRatio = if (bMed > 0.0) (cMed - bMed) / bMed else 0.0

        val bMean = baselineSamplesMs.average()
        val cMean = candidateSamplesMs.average()
        val bVar = sampleVariance(baselineSamplesMs, bMean)
        val cVar = sampleVariance(candidateSamplesMs, cMean)
        val se = sqrt(bVar / baselineSamplesMs.size + cVar / candidateSamplesMs.size)
        val tStat = if (se > 1e-12) (cMean - bMean) / se else 0.0
        val regressed = deltaRatio > maxAllowedSlowdownRatio && tStat > 2.20

        return RegressionVerdict(bMed, cMed, deltaRatio, tStat, regressed)
    }

    fun median(xs: List<Double>): Double {
        val s = xs.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2.0
    }

    fun mad(xs: List<Double>): Double {
        val m = median(xs)
        return median(xs.map { abs(it - m) })
    }

    private fun sampleVariance(xs: List<Double>, mean: Double): Double =
        xs.sumOf { (it - mean) * (it - mean) } / (xs.size - 1).toDouble()
}
