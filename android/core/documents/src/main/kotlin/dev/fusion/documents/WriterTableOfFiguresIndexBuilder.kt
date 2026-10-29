// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Automatic **Table of Figures**, **Table of Tables**, and **Back-of-Book Subject Index**
 * generator collapsing contiguous page runs (`pp. 12–15`) for Fusion Writer long-form publishing.
 */
object WriterTableOfFiguresIndexBuilder {
    const val SCOPE =
        "Automatic Table of Figures/Tables seq-numbering generator and back-of-book alphabetical index page-run collapser."

    data class CaptionAnchor(val category: String, val captionText: String, val pageNumber: Int)
    data class IndexMark(val primaryTerm: String, val subTerm: String? = null, val pageNumber: Int)

    fun buildListOfCaptions(captions: List<CaptionAnchor>, category: String): List<String> {
        return captions
            .filter { it.category.equals(category, ignoreCase = true) }
            .mapIndexed { idx, c ->
                require(c.pageNumber >= 1)
                "${category.replaceFirstChar { it.uppercase() }} ${idx + 1}: ${c.captionText.trim()} .... ${c.pageNumber}"
            }
    }

    fun buildBackOfBookIndex(marks: List<IndexMark>): List<String> {
        val grouped = marks.groupBy { it.primaryTerm.trim() }
        val lines = ArrayList<String>()
        for (primary in grouped.keys.sortedBy { it.lowercase() }) {
            val items = grouped[primary]!!
            val directPages = items.filter { it.subTerm.isNullOrBlank() }.map { it.pageNumber }.distinct().sorted()
            if (directPages.isNotEmpty()) {
                lines.add("$primary, ${collapsePageRuns(directPages)}")
            } else {
                lines.add(primary)
            }
            val subs = items.filter { !it.subTerm.isNullOrBlank() }.groupBy { it.subTerm!!.trim() }
            for (sub in subs.keys.sortedBy { it.lowercase() }) {
                val pages = subs[sub]!!.map { it.pageNumber }.distinct().sorted()
                lines.add("  $sub, ${collapsePageRuns(pages)}")
            }
        }
        return lines
    }

    fun collapsePageRuns(sortedPages: List<Int>): String {
        if (sortedPages.isEmpty()) return ""
        val parts = ArrayList<String>()
        var start = sortedPages[0]
        var end = start
        for (i in 1 until sortedPages.size) {
            val p = sortedPages[i]
            if (p == end + 1) end = p
            else {
                parts.add(if (start == end) "$start" else "$start–$end")
                start = p; end = p
            }
        }
        parts.add(if (start == end) "$start" else "$start–$end")
        return parts.joinToString(", ")
    }
}
