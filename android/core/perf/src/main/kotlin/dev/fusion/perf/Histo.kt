package dev.fusion.perf

/**
 * Fixed-boundary latency histograms (complementing [Percentile]:
 * nearest-rank answers "what value at p"; a histogram answers "how
 * many in each band" with EXPLICIT half-open buckets [b_i, b_{i+1})
 * plus an unbounded overflow band).
 *
 * Boundaries must be strictly increasing; counts are exact longs; the
 * executed invariant is total conservation: sum(counts) + overflow ==
 * samples seen, under any input order.
 *
 * Declared narrowings: static boundaries only (no auto-scaling — a
 * dashboard that rescales its own buckets destroys comparability
 * across runs, which is the property this kernel exists to protect).
 */
object Histo {
    const val SCOPE = "Static half-open buckets + overflow with exact conservation; no auto-scaling by design."

    data class H(val boundaries: LongArray, val counts: LongArray, val overflow: Long, val total: Long)

    fun create(boundaries: LongArray): H {
        require(boundaries.size >= 2) { "need at least two boundaries" }
        for (i in 1 until boundaries.size) require(boundaries[i] > boundaries[i - 1]) {
            "boundaries must strictly increase at index $i"
        }
        return H(boundaries.copyOf(), LongArray(boundaries.size - 1), 0, 0)
    }

    fun add(h: H, value: Long): H {
        require(value >= h.boundaries[0]) { "value $value below the first boundary ${h.boundaries[0]}" }
        var idx = -1
        for (i in 0 until h.counts.size) {
            if (value < h.boundaries[i + 1]) { idx = i; break }
        }
        val counts = h.counts.copyOf()
        var overflow = h.overflow
        if (idx >= 0) counts[idx]++ else overflow++
        return H(h.boundaries, counts, overflow, h.total + 1)
    }

    fun addAll(h: H, values: LongArray): H {
        var cur = h
        for (v in values) cur = add(cur, v)
        return cur
    }

    fun conserved(h: H): Boolean = h.counts.sum() + h.overflow == h.total

    /** Index of the bucket holding [value], or -1 for overflow. */
    fun bucketOf(h: H, value: Long): Int {
        require(value >= h.boundaries[0]) { "value below range" }
        for (i in 0 until h.counts.size) if (value < h.boundaries[i + 1]) return i
        return -1
    }
}
