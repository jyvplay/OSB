// SPDX-License-Identifier: Apache-2.0
package dev.fusion.find

import java.nio.charset.StandardCharsets

/**
 * Clean-room Boyer-Moore-Horspool (`BMH`) sublinear byte/UTF-8 substring search engine (`BoyerMooreHorspool`).
 */
object BoyerMooreHorspool {
    const val SCOPE =
        "Boyer-Moore-Horspool 256-entry bad-byte skip-table substring & binary pattern matcher (findAll non-overlapping & overlapping); regex NFA out of scope."

    class CompiledPattern(val needle: ByteArray) {
        private val shift = IntArray(256)

        init {
            require(needle.isNotEmpty()) { "Needle must not be empty" }
            val m = needle.size
            shift.fill(m)
            for (i in 0 until m - 1) {
                shift[needle[i].toInt() and 0xFF] = m - 1 - i
            }
        }

        fun findAll(haystack: ByteArray, allowOverlap: Boolean = false): IntArray {
            val n = haystack.size
            val m = needle.size
            if (m > n) return IntArray(0)
            val hits = ArrayList<Int>()
            val lastIdx = m - 1
            val lastByte = needle[lastIdx]
            var pos = 0
            while (pos <= n - m) {
                val tailByte = haystack[pos + lastIdx]
                if (tailByte == lastByte && regionMatches(haystack, pos)) {
                    hits.add(pos)
                    pos += if (allowOverlap) 1 else m
                } else {
                    pos += shift[tailByte.toInt() and 0xFF]
                }
            }
            return hits.toIntArray()
        }

        private fun regionMatches(h: ByteArray, offset: Int): Boolean {
            for (k in 0 until needle.size - 1) {
                if (h[offset + k] != needle[k]) return false
            }
            return true
        }
    }

    fun findUtf8ByteOffsets(text: String, pattern: String): IntArray =
        CompiledPattern(pattern.toByteArray(StandardCharsets.UTF_8))
            .findAll(text.toByteArray(StandardCharsets.UTF_8))
}
