package dev.fusion.windowing

/**
 * Aspect-ratio snapping for free-form window resize: find the nearest
 * common ratio (16:9, 4:3, 3:2, 1:1, 21:9 and their inverses) by
 * log-ratio distance, and snap when within tolerance — log distance
 * treats 16:9 vs 9:16 symmetrically, which linear difference does not
 * (that choice is the kernel and is tested).
 *
 * Declared narrowings: the pinned ratio table (data, small on
 * purpose); tolerance is caller-supplied in log space (0.05 ≈ 5%);
 * snapping yields the corrected height for a kept width (callers
 * preferring kept-height invert the arguments).
 */
object AspectSnap {
    const val SCOPE = "Nearest common ratio by symmetric log distance; pinned table; corrected-height output."

    val RATIOS: List<Pair<Int, Int>> = listOf(16 to 9, 4 to 3, 3 to 2, 1 to 1, 21 to 9, 9 to 16, 3 to 4, 2 to 3, 9 to 21)

    fun nearest(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0) { "degenerate window ${width}x$height" }
        val r = width.toDouble() / height
        return RATIOS.minByOrNull { (w, h) -> kotlin.math.abs(Math.log(r / (w.toDouble() / h))) }!!
    }

    /** Corrected height if the nearest ratio is within [logTolerance]; null = no snap. */
    fun snapHeight(width: Int, height: Int, logTolerance: Double = 0.05): Int? {
        require(logTolerance > 0) { "tolerance $logTolerance" }
        val (rw, rh) = nearest(width, height)
        val target = rw.toDouble() / rh
        val dist = kotlin.math.abs(Math.log(width.toDouble() / height / target))
        if (dist > logTolerance) return null
        return Math.round(width / target).toInt().coerceAtLeast(1)
    }
}
