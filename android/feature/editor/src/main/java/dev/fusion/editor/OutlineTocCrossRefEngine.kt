// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Clean-room hierarchical outline auto-numbering (`1`, `1.1`, `1.1.2`), Table-of-Contents
 * generator with dot-leader page numbers, and `[[#anchor]]` / `{REF anchor}` / `{PAGEREF anchor}`
 * cross-reference resolver reporting dangling bookmark references.
 */
object OutlineTocCrossRefEngine {
    const val SCOPE =
        "Pure-JVM Writer hierarchical outline numbering, dot-leader Table of Contents builder, and bookmark REF/PAGEREF cross-reference resolver."

    data class HeadingEntry(
        val level: Int,
        val numberPrefix: String,
        val title: String,
        val anchorId: String,
        val pageNumber: Int,
    )

    data class ResolvedDocument(
        val headings: List<HeadingEntry>,
        val tocLines: List<String>,
        val resolvedBody: String,
        val unresolvedAnchors: List<String>,
    )

    private val HEADING_RE = Regex("^(#{1,6})\\s+(.+?)(?:\\s*\\{#([A-Za-z0-9_-]+)\\})?\\s*$")
    private val REF_RE = Regex("\\{(REF|PAGEREF)\\s+([A-Za-z0-9_-]+)\\}")

    fun buildOutlineAndResolve(markdown: String, wordsPerPage: Int = 250): ResolvedDocument {
        require(wordsPerPage in 20..5000) { "wordsPerPage outside 20..5000" }
        val counters = IntArray(6)
        val headings = ArrayList<HeadingEntry>()
        val byAnchor = LinkedHashMap<String, HeadingEntry>()
        var cumulativeWords = 0

        for (rawLine in markdown.lines()) {
            val m = HEADING_RE.matchEntire(rawLine)
            if (m != null) {
                val lvl = m.groupValues[1].length
                counters[lvl - 1]++
                for (k in lvl until 6) counters[k] = 0
                val num = (0 until lvl).map { counters[it].coerceAtLeast(1) }.joinToString(".")
                val title = m.groupValues[2].trim()
                val explicitId = m.groupValues[3].takeIf { it.isNotEmpty() }
                val slug = explicitId ?: slugify(title)
                val page = (cumulativeWords / wordsPerPage) + 1
                val entry = HeadingEntry(lvl, num, title, slug, page)
                headings.add(entry)
                byAnchor[slug] = entry
            }
            cumulativeWords += rawLine.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        }

        val toc = headings.map { h ->
            val indent = "  ".repeat(h.level - 1)
            val left = "$indent${h.numberPrefix} ${h.title}"
            val dots = ".".repeat((56 - left.length).coerceAtLeast(3))
            "$left $dots ${h.pageNumber}"
        }

        val unresolved = LinkedHashSet<String>()
        val resolved = REF_RE.replace(markdown) { m ->
            val kind = m.groupValues[1]
            val target = m.groupValues[2]
            val hit = byAnchor[target]
            if (hit == null) {
                unresolved.add(target)
                "Error! Reference source '$target' not found."
            } else if (kind == "PAGEREF") {
                hit.pageNumber.toString()
            } else {
                "Section ${hit.numberPrefix} (${hit.title})"
            }
        }
        return ResolvedDocument(headings, toc, resolved, unresolved.toList())
    }

    private fun slugify(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "section" }
}
