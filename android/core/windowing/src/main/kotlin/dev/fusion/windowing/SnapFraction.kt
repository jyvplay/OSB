package dev.fusion.windowing

/**
 * Integer snap-layout arithmetic for the desktop windowing surface.
 *
 * The one invariant that matters: for any total width and any column
 * count, the produced spans TILE EXACTLY — they are non-negative,
 * adjacent, and sum to the total, with rounding remainders distributed
 * by the floor-boundary rule span(k) = floor(total*(k+1)/n) -
 * floor(total*k/n). That property is executed over a sweep, not assumed.
 *
 * Complements WindowEngine (which places windows) — this kernel only
 * computes the geometry lattice.
 *
 * Declared narrowings: 1-D integer spans only; no gaps/gutters, no
 * minimum-width policy (callers clamp), no DPI awareness (pixels in,
 * pixels out).
 */
object SnapFraction {
    const val SCOPE = "Exact integer tiling arithmetic only; placement policy lives in WindowEngine."

    fun boundary(total: Int, k: Int, n: Int): Int {
        require(total >= 0) { "total $total" }
        require(n > 0) { "columns $n" }
        require(k in 0..n) { "boundary index $k outside 0..$n" }
        return ((total.toLong() * k) / n).toInt()
    }

    fun span(total: Int, k: Int, n: Int): Int {
        require(k in 0 until n) { "column $k outside 0 until $n" }
        return boundary(total, k + 1, n) - boundary(total, k, n)
    }

    fun spans(total: Int, n: Int): List<Int> {
        require(n > 0) { "columns $n" }   // an empty range must not silently skip validation
        return (0 until n).map { span(total, it, n) }
    }

    /** Half-split helper: (left, right) with left+right == total always. */
    fun halves(total: Int): Pair<Int, Int> {
        val s = spans(total, 2)
        return s[0] to s[1]
    }

    /** Which column a pixel x falls in. */
    fun columnAt(total: Int, x: Int, n: Int): Int {
        require(x in 0 until total) { "x $x outside 0 until $total" }
        for (k in 0 until n) if (x < boundary(total, k + 1, n)) return k
        return n - 1
    }
}
