// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

/**
 * Bounded binary-search prefix range finder over a sorted string list.
 */
class IndexPrefixRangeBounds private constructor() {
    companion object {
        fun prefixRange(sorted: List<String>, prefix: String): IntRange {
            var lo = 0
            var hi = sorted.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (sorted[mid] < prefix) lo = mid + 1 else hi = mid
            }
            val start = lo
            hi = sorted.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (sorted[mid].startsWith(prefix)) lo = mid + 1 else hi = mid
            }
            return start until lo
        }
    }
}
