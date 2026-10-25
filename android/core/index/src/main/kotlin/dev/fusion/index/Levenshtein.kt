package dev.fusion.index

/**
 * Bounded Levenshtein distance for index candidate verification
 * (distinct from find's FuzzyScore subsequence RANKING: this is the
 * exact edit-distance VERIFY step after Trigram retrieval).
 *
 * [within] computes min(distance, k+1) with the banded algorithm —
 * O(k·n) and early exit, so a large k cannot be smuggled in as a
 * performance foot-gun; distance() is the unbounded classic for
 * small strings with an explicit length cap.
 *
 * Declared narrowings: code-UNIT edits (callers normalize first; the
 * grapheme question is declared, same stance as Monogram); cap 512
 * on unbounded inputs.
 */
object Levenshtein {
    const val SCOPE = "Exact edit distance (banded within-k + capped classic); code-unit edits declared."

    fun distance(a: String, b: String): Int {
        require(a.length <= 512 && b.length <= 512) { "use within(k) beyond 512 chars" }
        if (a == b) return 0
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }

    /** True when distance(a,b) <= k; banded, early-exiting. */
    fun within(a: String, b: String, k: Int): Boolean {
        require(k >= 0) { "negative bound" }
        if (kotlin.math.abs(a.length - b.length) > k) return false
        if (k == 0) return a == b
        val inf = k + 1
        var prev = IntArray(b.length + 1) { if (it <= k) it else inf }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1) { inf }
            val lo = maxOf(1, i - k); val hi = minOf(b.length, i + k)
            if (i - 1 <= k) cur[0] = i else cur[0] = inf
            var rowMin = cur[0]
            for (j in lo..hi) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(
                    if (cur[j - 1] < inf) cur[j - 1] + 1 else inf,
                    if (prev[j] < inf) prev[j] + 1 else inf,
                    if (prev[j - 1] < inf) prev[j - 1] + cost else inf
                ).coerceAtMost(inf)
                rowMin = minOf(rowMin, cur[j])
            }
            if (rowMin >= inf) return false
            prev = cur
        }
        return prev[b.length] <= k
    }
}
