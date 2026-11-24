// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * Bounded Markdown ATX heading extractor (`#` through `######`).
 */
class MarkdownHeadingExtractor private constructor() {
    companion object {
        const val MAX_LINES = 20_000
        private val HEADING_RE = Regex("^(#{1,6})\\s+(.+?)\\s*#*$")

        data class Heading(val level: Int, val title: String, val lineIndex: Int)

        fun extract(markdown: String): List<Heading> {
            val lines = markdown.split('\n')
            require(lines.size <= MAX_LINES) { "too many lines" }
            val out = mutableListOf<Heading>()
            for (i in lines.indices) {
                val m = HEADING_RE.matchEntire(lines[i].trimEnd('\r')) ?: continue
                out.add(Heading(m.groupValues[1].length, m.groupValues[2].trim(), i))
            }
            return out
        }
    }
}
