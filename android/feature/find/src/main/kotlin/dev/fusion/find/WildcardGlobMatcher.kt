// SPDX-License-Identifier: Apache-2.0
package dev.fusion.find

/**
 * Bounded linear-time wildcard glob matcher supporting `*` and `?`.
 */
class WildcardGlobMatcher private constructor() {
    companion object {
        const val MAX_LEN = 1024

        fun matches(pattern: String, text: String): Boolean {
            require(pattern.length <= MAX_LEN && text.length <= MAX_LEN) { "input too long" }
            var p = 0
            var t = 0
            var starIdx = -1
            var matchIdx = 0
            while (t < text.length) {
                when {
                    p < pattern.length && (pattern[p] == '?' || pattern[p] == text[t]) -> {
                        p++
                        t++
                    }
                    p < pattern.length && pattern[p] == '*' -> {
                        starIdx = p
                        matchIdx = t
                        p++
                    }
                    starIdx != -1 -> {
                        p = starIdx + 1
                        matchIdx++
                        t = matchIdx
                    }
                    else -> return false
                }
            }
            while (p < pattern.length && pattern[p] == '*') p++
            return p == pattern.length
        }
    }
}
