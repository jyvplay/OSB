package dev.fusion.perf

/**
 * Nearest-rank percentiles for performance summaries (the NIST/classic
 * definition: the P-th percentile of n sorted values is the value at
 * rank ceil(P/100 * n), 1-based).
 *
 * This definition never interpolates — every reported latency is a
 * latency that actually occurred, which is the property perf dashboards
 * need (p99 must be a real observation, not an average of two).
 *
 * Declared narrowings: nearest-rank ONLY (no linear interpolation, no
 * exclusive/inclusive variants — those are different estimators and a
 * caller who wants them must say so in code, not get them by accident);
 * input must already be sorted and is verified, not re-sorted silently.
 */
object Percentile {
    const val SCOPE = "Nearest-rank only; every result is an actual observation; sortedness verified, never assumed."

    fun nearestRank(sorted: List<Long>, p: Double): Long {
        require(sorted.isNotEmpty()) { "empty sample" }
        require(p > 0.0 && p <= 100.0) { "percentile $p outside (0, 100]" }
        for (i in 1 until sorted.size) require(sorted[i - 1] <= sorted[i]) {
            "input not sorted at index $i — sort explicitly, this kernel never re-orders evidence"
        }
        // Multiply BEFORE dividing: p/100 is not exactly representable for most
        // p, and ceil amplifies that upward drift into an off-by-one rank
        // (p=55, n=100 produced rank 56 in the first execution of this round).
        // p*n stays exact for the integral-valued doubles that reach this API,
        // and the single division keeps the error below the ceil threshold.
        val rank = Math.ceil(p * sorted.size / 100.0).toInt()
        return sorted[rank - 1]
    }

    data class Summary(val p50: Long, val p95: Long, val p99: Long, val min: Long, val max: Long, val n: Int)

    fun summarize(sorted: List<Long>): Summary = Summary(
        p50 = nearestRank(sorted, 50.0),
        p95 = nearestRank(sorted, 95.0),
        p99 = nearestRank(sorted, 99.0),
        min = sorted.first(), max = sorted.last(), n = sorted.size
    )
}
