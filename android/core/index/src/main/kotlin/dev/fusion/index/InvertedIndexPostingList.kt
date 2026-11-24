// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

/**
 * Bounded sorted posting list intersection using O(N + M) two-pointer merge.
 */
class InvertedIndexPostingList private constructor() {
    companion object {
        const val MAX_POSTINGS = 100_000

        fun intersect(a: List<Int>, b: List<Int>): List<Int> {
            require(a.size <= MAX_POSTINGS && b.size <= MAX_POSTINGS) { "posting list too large" }
            val result = mutableListOf<Int>()
            var i = 0
            var j = 0
            while (i < a.size && j < b.size) {
                val va = a[i]
                val vb = b[j]
                when {
                    va == vb -> {
                        if (result.isEmpty() || result.last() != va) result.add(va)
                        i++
                        j++
                    }
                    va < vb -> i++
                    else -> j++
                }
            }
            return result
        }
    }
}
