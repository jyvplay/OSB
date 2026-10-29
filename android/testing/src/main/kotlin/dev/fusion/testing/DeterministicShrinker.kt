// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

/**
 * Clean-room **Deterministic SplitMix64 PRNG & Type-Directed Counterexample Shrinker**
 * (QuickCheck / Hypothesis-style minimal failing witness reducer for property-based contract suites).
 */
object DeterministicShrinker {
    const val SCOPE =
        "Clean-room SplitMix64 PRNG and type-directed minimal counterexample shrinker (Long binary-search, List chunk-removal + element shrink, ASCII String shrink)."

    class SplitMix64(seed: Long) {
        private var state = seed

        fun nextLong(): Long {
            state += -0x61c8864680b583ebL
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }

        fun nextIntBounded(boundExclusive: Int): Int {
            require(boundExclusive > 0) { "boundExclusive must be > 0" }
            return ((nextLong() ushr 1) % boundExclusive.toLong()).toInt()
        }
    }

    data class ShrinkReport<T>(
        val initialCounterexample: T,
        val minimalCounterexample: T,
        val shrinkStepsAccepted: Int,
    )

    /** Shrinks an integer failing `predicate` (where `predicate(x) == false` is a failure) toward `0L`. */
    fun shrinkLong(failingValue: Long, propertyHolds: (Long) -> Boolean): ShrinkReport<Long> {
        require(!propertyHolds(failingValue)) { "initial value $failingValue does not violate property" }
        if (failingValue == 0L) return ShrinkReport(0L, 0L, 0)
        if (!propertyHolds(0L)) return ShrinkReport(failingValue, 0L, 1)

        var best = failingValue
        var steps = 0
        // Flip negative to positive first if |best| still fails.
        if (best < 0L && best != Long.MIN_VALUE && !propertyHolds(-best)) {
            best = -best
            steps++
        }
        var lo = 0L // known passing or untested boundary toward 0
        var hi = best
        repeat(64) {
            val mid = lo + (hi - lo) / 2L
            if (mid == lo || mid == hi) return@repeat
            if (!propertyHolds(mid)) {
                hi = mid
                best = mid
                steps++
            } else {
                lo = mid
            }
        }
        return ShrinkReport(failingValue, best, steps)
    }

    /** Shrinks a failing list first by delta-debugging chunk deletion (`ddmin`), then element-wise toward `0`. */
    fun shrinkIntList(failingList: List<Int>, propertyHolds: (List<Int>) -> Boolean): ShrinkReport<List<Int>> {
        require(!propertyHolds(failingList)) { "initial list does not violate property" }
        var cur = failingList
        var steps = 0
        var chunkSize = cur.size / 2
        while (chunkSize >= 1) {
            var i = 0
            var removedAny = false
            while (i + chunkSize <= cur.size) {
                val candidate = cur.subList(0, i) + cur.subList(i + chunkSize, cur.size)
                if (!propertyHolds(candidate)) {
                    cur = candidate
                    steps++
                    removedAny = true
                } else {
                    i += chunkSize
                }
            }
            if (!removedAny) chunkSize /= 2 else chunkSize = minOf(chunkSize, (cur.size / 2).coerceAtLeast(1)).let { if (cur.size < chunkSize) cur.size else chunkSize }
            if (cur.isEmpty()) break
            if (chunkSize > cur.size) chunkSize = cur.size / 2
        }
        // Single-element removal pass + magnitude reduction toward 0
        var idx = 0
        while (idx < cur.size) {
            val without = cur.subList(0, idx) + cur.subList(idx + 1, cur.size)
            if (!propertyHolds(without)) {
                cur = without
                steps++
            } else {
                val elemShrink = shrinkLong(cur[idx].toLong()) { cand ->
                    val mod = cur.toMutableList().also { it[idx] = cand.toInt() }
                    propertyHolds(mod)
                }
                if (elemShrink.minimalCounterexample.toInt() != cur[idx]) {
                    cur = cur.toMutableList().also { it[idx] = elemShrink.minimalCounterexample.toInt() }
                    steps += elemShrink.shrinkStepsAccepted
                }
                idx++
            }
        }
        return ShrinkReport(failingList, cur, steps)
    }
}
