// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

/**
 * Bounded Boyer-Moore-Horspool substring search. Builds a bad-character
 * shift table from the needle and scans the haystack left to right,
 * skipping forward by the precomputed shift on mismatch. Returns the first
 * index of the needle in the haystack, or -1 if absent. The empty needle
 * matches at index 0.
 *
 * Declared narrowings: haystack at most [MAX_HAYSTACK] chars; needle at most
 * [MAX_NEEDLE] chars and must not be longer than the haystack for a match;
 * the alphabet is treated as 256 symbols (extended ASCII / byte values);
 * Unicode code points above 255 are compared by their low byte only — full
 * Unicode-aware matching is not attempted here. The search is case-sensitive.
 */
class BoyerMooreSearch private constructor(
    private val needle: String,
    private val shiftTable: IntArray
) {
    companion object {
        const val MAX_HAYSTACK = 1 << 20  // 1 MiB chars
        const val MAX_NEEDLE = 4096
        const val ALPHABET = 256

        fun from(needle: String): BoyerMooreSearch {
            require(needle.length <= MAX_NEEDLE) { "needle exceeds $MAX_NEEDLE chars" }
            val table = IntArray(ALPHABET) { needle.length }
            for (i in 0 until needle.length - 1) {
                table[needle.codePointAt(i).toInt() and 0xFF] = needle.length - 1 - i
            }
            return BoyerMooreSearch(needle, table)
        }
    }

    fun indexOf(haystack: String): Int {
        require(haystack.length <= MAX_HAYSTACK) { "haystack exceeds $MAX_HAYSTACK chars" }
        val n = needle.length
        if (n == 0) return 0
        if (haystack.length < n) return -1
        var i = n - 1
        while (i < haystack.length) {
            var j = n - 1
            while (j >= 0 && haystack.codePointAt(i - (n - 1 - j)).toInt() and 0xFF == needle.codePointAt(j).toInt() and 0xFF) {
                j--
            }
            if (j < 0) return i - n + 1
            i += shiftTable[haystack.codePointAt(i).toInt() and 0xFF]
        }
        return -1
    }

    fun contains(haystack: String): Boolean = indexOf(haystack) >= 0
    fun needleLength(): Int = needle.length
}
