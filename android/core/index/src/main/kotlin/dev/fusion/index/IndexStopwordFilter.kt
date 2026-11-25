// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

/**
 * Bounded stopword filter for search tokenization.
 */
class IndexStopwordFilter private constructor() {
    companion object {
        val STOPWORDS = setOf("a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in", "is", "it", "of", "on", "or", "that", "the", "to", "with")

        fun filter(tokens: List<String>): List<String> =
            tokens.filter { it.lowercase() !in STOPWORDS }
    }
}
