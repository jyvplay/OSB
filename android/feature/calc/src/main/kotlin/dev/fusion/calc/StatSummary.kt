// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

/**
 * Exact-enough descriptive statistics over a small sample set.
 *
 * Sits next to [Calculator] (expression evaluation): this answers "describe these
 * numbers" (count, sum, mean, population variance, min, max, median) rather than
 * "evaluate this expression". Mean and variance use Welford's online update so a
 * large-magnitude sample never loses its small deviations to catastrophic
 * cancellation the way a naive sum-of-squares would; median sorts a copy, so the
 * caller's list is never mutated. NaN, infinities, and empty input are refused:
 * a summary containing NaN would silently poison every downstream consumer, and
 * there is no honest mean of zero samples.
 *
 * Everything is plain Kotlin with no Android dependency, so these summaries stay
 * unit-testable on the JVM.
 */
object StatSummary {

    /** Descriptive statistics for one sample set. Population variance (÷n). */
    data class Stats(
        val count: Int,
        val sum: Double,
        val mean: Double,
        val variance: Double,
        val min: Double,
        val max: Double,
        val median: Double,
    )

    /**
     * Summarizes [values]. Throws [IllegalArgumentException] when the list is
     * empty or contains NaN or infinite values.
     */
    fun summarize(values: List<Double>): Stats {
        require(values.isNotEmpty()) { "no samples" }
        require(values.all { it.isFinite() }) { "non-finite sample" }
        var mean = 0.0
        var m2 = 0.0
        var sum = 0.0
        var lo = Double.POSITIVE_INFINITY
        var hi = Double.NEGATIVE_INFINITY
        values.forEachIndexed { i, v ->
            sum += v
            if (v < lo) lo = v
            if (v > hi) hi = v
            val delta = v - mean
            mean += delta / (i + 1)
            m2 += delta * (v - mean)
        }
        val sorted = values.sorted()
        val n = sorted.size
        val median = if (n % 2 == 1) sorted[n / 2]
        else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        return Stats(n, sum, mean, m2 / n, lo, hi, median)
    }
}
