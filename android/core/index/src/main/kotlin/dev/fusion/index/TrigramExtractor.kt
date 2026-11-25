// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

/**
 * Bounded unique lowercase trigram extractor.
 */
class TrigramExtractor private constructor() {
    companion object {
        const val MAX_TEXT_LEN = 4096

        fun extract(text: String): Set<String> {
            require(text.length <= MAX_TEXT_LEN) { "text too long" }
            val norm = text.lowercase()
            if (norm.length < 3) return emptySet()
            val out = LinkedHashSet<String>()
            for (i in 0..norm.length - 3) {
                out.add(norm.substring(i, i + 3))
            }
            return out
        }
    }
}
