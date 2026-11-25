// SPDX-License-Identifier: Apache-2.0
package dev.fusion.document

/**
 * Production-grade Markdown ATX heading & unique-slug TOC extractor
 * that ignores fenced code blocks.
 */
class DocumentTocExtractor private constructor() {
    companion object {
        data class TocEntry(val level: Int, val title: String, val slug: String)

        private fun baseSlug(title: String): String =
            title.lowercase()
                .replace(Regex("[^a-z0-9\\s-]"), "")
                .trim()
                .replace(Regex("\\s+"), "-")

        fun extractToc(markdown: String): List<TocEntry> {
            val out = mutableListOf<TocEntry>()
            val seenSlugs = HashMap<String, Int>()
            var inFence = false

            for (rawLine in markdown.lines()) {
                val trimmed = rawLine.trim()
                if (trimmed.startsWith("```")) {
                    inFence = !inFence
                    continue
                }
                if (inFence) continue
                val m = Regex("^(#{1,6})\\s+(.+)$").find(trimmed) ?: continue
                val level = m.groupValues[1].length
                val title = m.groupValues[2].trim()
                val base = baseSlug(title)
                val count = seenSlugs[base] ?: 0
                seenSlugs[base] = count + 1
                val slug = if (count == 0) base else "$base-$count"
                out.add(TocEntry(level, title, slug))
            }
            return out
        }
    }
}
