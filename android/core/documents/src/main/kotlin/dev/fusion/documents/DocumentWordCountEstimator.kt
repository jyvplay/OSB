// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Bounded document word, line, and character counter.
 */
class DocumentWordCountEstimator private constructor() {
    companion object {
        const val MAX_DOC_LEN = 1 shl 22

        data class Stats(val chars: Int, val words: Int, val lines: Int)

        fun count(text: String): Stats {
            require(text.length <= MAX_DOC_LEN) { "document too large" }
            if (text.isEmpty()) return Stats(0, 0, 0)
            var words = 0
            var inWord = false
            var lines = 1
            for (ch in text) {
                if (ch == '\n') lines++
                if (ch.isWhitespace()) {
                    inWord = false
                } else if (!inWord) {
                    inWord = true
                    words++
                }
            }
            return Stats(text.length, words, lines)
        }
    }
}
