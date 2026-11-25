// SPDX-License-Identifier: Apache-2.0
package dev.fusion.find

/**
 * Bounded search snippet window extractor.
 */
class FindSnippetHighlighter private constructor() {
    companion object {
        fun extractSnippet(text: String, matchStart: Int, matchLen: Int, contextChars: Int): String {
            require(matchStart >= 0 && matchLen >= 0 && contextChars >= 0) { "negative arg" }
            val s = (matchStart - contextChars).coerceAtLeast(0)
            val e = (matchStart + matchLen + contextChars).coerceAtMost(text.length)
            return if (s > e) "" else text.substring(s, e)
        }
    }
}
