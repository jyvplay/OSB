package dev.fusion.testing

/**
 * Clean-room Deterministic Counterexample Shrinker for Property-Based Host Contract Suites
 * (QuickCheck/Hypothesis-inspired; Apache-2.0).
 *
 * Given a failing list or integer input, deterministically binary-chunks and delta-debugs (`ddmin`)
 * down to a locally minimal failing counterexample!
 */
object DeterministicPropertyShrinker {
    const val SCOPE =
        "ddmin delta-debugging list & integer counterexample shrinker for deterministic host property testing"

    data class ShrinkResult<T>(
        val minimalCounterexample: T,
        val shrinkStepsAccepted: Int,
        val predicateEvaluations: Int,
    )

    fun shrinkInt(failingValue: Int, failsPredicate: (Int) -> Boolean): ShrinkResult<Int> {
        require(failsPredicate(failingValue)) { "Initial value $failingValue does not fail predicate" }
        var cur = failingValue
        var steps = 0
        var evals = 1
        if (cur != 0 && failsPredicate(0).also { evals++ }) {
            return ShrinkResult(0, 1, evals)
        }
        var improved = true
        while (improved && cur != 0) {
            improved = false
            val candidates = buildList {
                add(cur / 2)
                if (cur > 0) add(cur - 1) else add(cur + 1)
            }.distinct().filter { it != cur }
            for (c in candidates) {
                evals++
                if (failsPredicate(c)) {
                    cur = c
                    steps++
                    improved = true
                    break
                }
            }
        }
        return ShrinkResult(cur, steps, evals)
    }

    /** Zeller `ddmin` minimal failing subsequence shrinker. */
    fun <E> shrinkList(failingList: List<E>, failsPredicate: (List<E>) -> Boolean): ShrinkResult<List<E>> {
        require(failsPredicate(failingList)) { "Initial list does not fail predicate" }
        var cur = failingList
        var granularity = 2
        var steps = 0
        var evals = 1

        while (cur.size >= 2) {
            val n = cur.size
            val chunkSize = (n / granularity).coerceAtLeast(1)
            var reduced = false
            var start = 0
            while (start < n) {
                val end = minOf(n, start + chunkSize)
                val complement = cur.subList(0, start) + cur.subList(end, n)
                evals++
                if (failsPredicate(complement)) {
                    cur = complement
                    granularity = (granularity - 1).coerceAtLeast(2)
                    steps++
                    reduced = true
                    break
                }
                start = end
            }
            if (!reduced) {
                if (granularity >= n) break
                granularity = minOf(n, granularity * 2)
            }
        }
        return ShrinkResult(cur, steps, evals)
    }
}
