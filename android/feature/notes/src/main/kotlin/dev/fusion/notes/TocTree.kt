// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * Hierarchical Table-of-Contents section numbering & unique jump-anchor builder.
 *
 * Unlike lossy TOC generators that silently collapse `H1 -> H3` jumps, [TocTree] computes
 * canonical legal section numbers (`1`, `1.1`, `1.1.1`), deduplicates GitHub-style anchors
 * (`overview`, `overview-2`), and records explicit [SkippedLevelGap] audit notices whenever
 * a heading jumps deeper by more than 1 level or starts deeper than level 1.
 */
object TocTree {
    const val SCOPE =
        "Heading levels 1..6 legal section numbering, deduplicated ASCII/Unicode anchors, and skipped-level audit; Markdown parsing lives in MarkdownDoc."

    const val MAX_HEADINGS = 4_096

    data class HeadingInput(val level: Int, val title: String, val line: Int = 1)

    data class SkippedLevelGap(val index: Int, val previousLevel: Int, val actualLevel: Int, val line: Int)

    data class Entry(
        val level: Int,
        val number: String,
        val title: String,
        val anchor: String,
        val line: Int,
    )

    data class DocumentToc(
        val entries: List<Entry>,
        val skippedGaps: List<SkippedLevelGap>,
    )

    fun build(headings: List<HeadingInput>): DocumentToc {
        require(headings.size <= MAX_HEADINGS) { "heading count ${headings.size} exceeds $MAX_HEADINGS" }
        val counters = IntArray(6)
        val entries = ArrayList<Entry>(headings.size)
        val gaps = ArrayList<SkippedLevelGap>()
        val anchorCounts = HashMap<String, Int>()
        var prevLevel = 0

        for ((idx, h) in headings.withIndex()) {
            require(h.level in 1..6) { "heading level ${h.level} outside 1..6 at index $idx" }
            val trimmed = h.title.trim()
            require(trimmed.isNotEmpty()) { "blank heading title at index $idx" }
            require(h.line >= 1) { "line ${h.line} must be >= 1" }

            if (prevLevel == 0 && h.level > 1) {
                gaps.add(SkippedLevelGap(idx, 0, h.level, h.line))
            } else if (prevLevel > 0 && h.level > prevLevel + 1) {
                gaps.add(SkippedLevelGap(idx, prevLevel, h.level, h.line))
            }

            // Fill any intermediate uninitialized parent level with 1 so legal numbers stay non-zero (`1.0.1` -> `1.1.1` avoided? No: keep exact `1.0.1` so the gap is visible in numbering too!)
            val pos = h.level - 1
            counters[pos] += 1
            for (deeper in (pos + 1) until 6) counters[deeper] = 0

            val firstNonZero = counters.indexOfFirst { it > 0 }.let { if (it < 0) pos else it }
            val numStr = (firstNonZero..pos).joinToString(".") { counters[it].toString() }

            val baseSlug = slugify(trimmed)
            val seen = (anchorCounts[baseSlug] ?: 0) + 1
            anchorCounts[baseSlug] = seen
            val uniqueAnchor = if (seen == 1) baseSlug else "$baseSlug-$seen"

            entries.add(Entry(h.level, numStr, trimmed, uniqueAnchor, h.line))
            prevLevel = h.level
        }
        return DocumentToc(entries, gaps)
    }

    fun slugify(title: String): String {
        val sb = StringBuilder(title.length)
        var prevDash = false
        for (ch in title.lowercase()) {
            when {
                ch.isLetterOrDigit() -> {
                    sb.append(ch)
                    prevDash = false
                }
                (ch.isWhitespace() || ch == '-' || ch == '_') && sb.isNotEmpty() && !prevDash -> {
                    sb.append('-')
                    prevDash = true
                }
            }
        }
        val clean = sb.toString().trim('-')
        return clean.ifEmpty { "section" }
    }
}
