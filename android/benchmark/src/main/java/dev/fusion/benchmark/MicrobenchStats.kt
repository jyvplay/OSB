// SPDX-License-Identifier: Apache-2.0
package dev.fusion.benchmark

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Clean-room microbenchmark statistical summary, Tukey `1.5 * IQR` outlier trimmer, Median Absolute Deviation
 * (`MAD`), and Welch's unequal-variance $t$-test regression detector (`MicrobenchStats`).
 *
 * Powers `:benchmark` deterministic CI performance regression checks without external math libraries!
 */
object MicrobenchStats {
    const val SCOPE =
        "Microbenchmark statistics engine (median, MAD, IQR, Tukey 1.5*IQR outlier trimming, Welch unequal-variance t-statistic & Satterthwaite df, regression verdict); Android Macrobenchmark Perfetto trace capture out of scope."

    data class Summary(
        val count: Int,
        val min: Double,
        val median: Double,
        val mean: Double,
        val stdDev: Double,
        val iqr: Double,
        val mad: Double,
    )

    data class Comparison(
        val baselineMedian: Double,
        val candidateMedian: Double,
        val relativeDeltaRatio: Double,
        val welchT: Double,
        val satterthwaiteDf: Double,
        val regressed: Boolean,
    )

    fun summarize(samples: DoubleArray): Summary {
        require(samples.size >= 2) { "Benchmark summary requires >= 2 samples" }
        samples.forEach { require(it.isFinite() && it >= 0.0) { "Invalid benchmark sample: $it" } }
        val sorted = samples.sortedArray()
        val n = sorted.size
        val med = quantileSorted(sorted, 0.50)
        val q1 = quantileSorted(sorted, 0.25)
        val q3 = quantileSorted(sorted, 0.75)
        val absDevs = DoubleArray(n) { abs(sorted[it] - med) }.sortedArray()
        val mad = quantileSorted(absDevs, 0.50)
        val mean = sorted.average()
        val variance = sorted.sumOf { (it - mean).pow(2) } / (n - 1).toDouble()
        return Summary(
            count = n,
            min = sorted.first(),
            median = med,
            mean = mean,
            stdDev = sqrt(variance),
            iqr = q3 - q1,
            mad = mad,
        )
    }

    fun trimTukeyOutliers(samples: DoubleArray, fenceMultiplier: Double = 1.5): DoubleArray {
        require(samples.size >= 4) { "Tukey outlier trimming requires >= 4 samples" }
        val sorted = samples.sortedArray()
        val q1 = quantileSorted(sorted, 0.25)
        val q3 = quantileSorted(sorted, 0.75)
        val iqr = q3 - q1
        val lo = q1 - fenceMultiplier * iqr
        val hi = q3 + fenceMultiplier * iqr
        val kept = sorted.filter { it in lo..hi }.toDoubleArray()
        return if (kept.size >= 2) kept else sorted
    }

    /**
     * Evaluates whether [candidateNs] exhibits a statistically significant slowdown over [baselineNs]
     * exceeding both [minRelativeRegression] (e.g. `+5%`) AND `welchT >= criticalT` (default `2.576`,
     * two-sided `p < 0.01` normal/large-df threshold).
     */
    fun compareBaselineToCandidate(
        baselineNs: DoubleArray,
        candidateNs: DoubleArray,
        minRelativeRegression: Double = 0.05,
        criticalT: Double = 2.576,
    ): Comparison {
        val b = summarize(baselineNs)
        val c = summarize(candidateNs)
        val v1 = (b.stdDev * b.stdDev) / b.count.toDouble()
        val v2 = (c.stdDev * c.stdDev) / c.count.toDouble()
        val se = sqrt(v1 + v2)
        val welchT = if (se <= 1e-15) {
            if (c.mean > b.mean) Double.POSITIVE_INFINITY else if (c.mean < b.mean) Double.NEGATIVE_INFINITY else 0.0
        } else {
            (c.mean - b.mean) / se
        }
        val df = if (v1 + v2 <= 1e-15) {
            (b.count + c.count - 2).toDouble()
        } else {
            val num = (v1 + v2).pow(2)
            val den = (v1.pow(2) / (b.count - 1)) + (v2.pow(2) / (c.count - 1))
            if (den <= 1e-30) (b.count + c.count - 2).toDouble() else num / den
        }
        val relDelta = if (b.median <= 0.0) 0.0 else (c.median - b.median) / b.median
        val regressed = relDelta >= minRelativeRegression && welchT >= criticalT
        return Comparison(b.median, c.median, relDelta, welchT, df, regressed)
    }

    private fun quantileSorted(sorted: DoubleArray, p: Double): Double {
        val pos = p * (sorted.size - 1)
        val lo = pos.toInt()
        val hi = (lo + 1).coerceAtMost(sorted.lastIndex)
        val frac = pos - lo
        return sorted[lo] * (1.0 - frac) + sorted[hi] * frac
    }
}
