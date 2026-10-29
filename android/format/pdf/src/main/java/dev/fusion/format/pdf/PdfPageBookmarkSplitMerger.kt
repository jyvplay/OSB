// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.pdf

/**
 * Clean-room PDF Page Range Split / Extract / Reorder Planner & Hierarchical `/Outlines` Bookmark
 * Dictionary Synthesizer.
 */
object PdfPageBookmarkSplitMerger {
    const val SCOPE =
        "Pure-JVM 1-based PDF page range expression parser (e.g. '1-3, 7, 10-8'), reorder/split planner, and PDF /Outlines bookmark dictionary builder."

    data class BookmarkNode(val title: String, val targetPage1Based: Int, val children: List<BookmarkNode> = emptyList())

    /**
     * Parses 1-based page range specs including descending ranges (`"1-3, 8, 6-5"`) against
     * `totalPages`.
     */
    fun parsePageSelection(spec: String, totalPages: Int): List<Int> {
        require(totalPages >= 1 && spec.isNotBlank()) { "invalid PDF page selection request" }
        val out = ArrayList<Int>()
        for (part in spec.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
            if ('-' in part) {
                val (aStr, bStr) = part.split('-', limit = 2).map { it.trim() }
                val a = aStr.toInt(); val b = bStr.toInt()
                require(a in 1..totalPages && b in 1..totalPages) { "page range '$part' outside 1..$totalPages" }
                if (a <= b) out.addAll(a..b) else out.addAll(a downTo b)
            } else {
                val p = part.toInt()
                require(p in 1..totalPages) { "page $p outside 1..$totalPages" }
                out.add(p)
            }
        }
        return out
    }

    fun buildOutlinesObjects(bookmarks: List<BookmarkNode>, totalPages: Int, baseObjId: Int = 50): List<String> {
        require(bookmarks.isNotEmpty() && baseObjId >= 2) { "bookmarks required" }
        val objs = ArrayList<String>()
        val rootId = baseObjId
        var nextId = baseObjId + 1
        val topIds = bookmarks.map { nextId++ }
        objs.add("$rootId 0 obj\n<< /Type /Outlines /First ${topIds.first()} 0 R /Last ${topIds.last()} 0 R /Count ${bookmarks.size} >>\nendobj")
        for ((idx, b) in bookmarks.withIndex()) {
            require(b.targetPage1Based in 1..totalPages) { "bookmark '${b.title}' points outside 1..$totalPages" }
            val curId = topIds[idx]
            val prev = if (idx > 0) " /Prev ${topIds[idx - 1]} 0 R" else ""
            val next = if (idx < topIds.lastIndex) " /Next ${topIds[idx + 1]} 0 R" else ""
            val safeTitle = b.title.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
            objs.add("$curId 0 obj\n<< /Title ($safeTitle) /Parent $rootId 0 R$prev$next /Dest [${b.targetPage1Based - 1} /Fit] >>\nendobj")
        }
        return objs
    }
}
