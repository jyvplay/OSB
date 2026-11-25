// SPDX-License-Identifier: Apache-2.0
package dev.fusion.find

/**
 * Bounded whitespace and case-fold normalizer for search indexing.
 */
class FindCaseFoldNormalizer private constructor() {
    companion object {
        fun normalize(text: String): String =
            text.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}
