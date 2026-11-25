// SPDX-License-Identifier: Apache-2.0
package dev.fusion.pdf

/**
 * Bounded PDF page range parser (e.g. `"1-3,5,7-9"` -> `[1, 2, 3, 5, 7, 8, 9]`).
 */
class PdfPageRangeParser private constructor() {
    companion object {
        const val MAX_SPEC_LEN = 512
        const val MAX_PAGES = 10_000

        fun parse(spec: String, maxPage: Int): List<Int> {
            require(spec.isNotEmpty() && spec.length <= MAX_SPEC_LEN) { "spec length out of bounds" }
            require(maxPage in 1..MAX_PAGES) { "maxPage out of bounds" }
            val pages = sortedSetOf<Int>()
            for (part in spec.split(',')) {
                val token = part.trim()
                require(token.isNotEmpty()) { "empty range token" }
                if ('-' in token) {
                    val bounds = token.split('-')
                    require(bounds.size == 2) { "invalid range: $token" }
                    val start = bounds[0].trim().toInt()
                    val end = bounds[1].trim().toInt()
                    require(start in 1..end && end <= maxPage) { "range out of bounds: $token" }
                    for (p in start..end) pages.add(p)
                } else {
                    val p = token.toInt()
                    require(p in 1..maxPage) { "page out of bounds: $p" }
                    pages.add(p)
                }
            }
            return pages.toList()
        }
    }
}
