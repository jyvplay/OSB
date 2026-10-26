// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/** One unified hunk: 1-based old/new starts + counts, prefixed body lines. */
data class Hunk(val oldStart: Int, val oldCount: Int, val newStart: Int, val newCount: Int, val lines: List<String>)

/**
 * Unified-hunk rendering over [LineDiff] output: change runs joined with
 * N context lines (overlaps merged), `@@ -a,b +c,d @@` headers, body
 * lines prefixed ` `/`-`/`+`. Rendering only — the diff itself stays
 * with LineDiff. Expects LineDiff order (removals before additions
 * within a run, which its backtrack guarantees); hand-built misordered
 * runs render as given. Empty-side starts follow unified convention (the
 * line before the hunk, 0-based-0 at file top). Caps: 32 context lines.
 */
object DiffHunks {
    const val MAX_CONTEXT = 32

    fun render(diff: List<DiffLine>, context: Int = 3): List<Hunk> {
        require(context in 0..MAX_CONTEXT) { "context $context outside 0..$MAX_CONTEXT" }
        if (diff.none { it.change != LineChange.SAME }) return emptyList()
        // Old/new 1-based numbers per DiffLine index.
        val oldNo = IntArray(diff.size)
        val newNo = IntArray(diff.size)
        var o = 1
        var n = 1
        for (i in diff.indices) {
            when (diff[i].change) {
                LineChange.SAME -> { oldNo[i] = o++; newNo[i] = n++ }
                LineChange.REMOVED -> { oldNo[i] = o++; newNo[i] = -1 }
                LineChange.ADDED -> { oldNo[i] = -1; newNo[i] = n++ }
            }
        }
        val changed = diff.indices.filter { diff[it].change != LineChange.SAME }
        val spans = mutableListOf<IntRange>()
        var s = maxOf(0, changed.first() - context)
        var e = minOf(diff.lastIndex, changed.first() + context)
        for (c in changed.drop(1)) {
            val ns = maxOf(0, c - context)
            val ne = minOf(diff.lastIndex, c + context)
            if (ns <= e + 1) e = maxOf(e, ne) else {
                spans += s..e; s = ns; e = ne
            }
        }
        spans += s..e
        return spans.map { span ->
            // LineDiff keeps line terminators; unified body lines carry none.
            val body = span.map { i ->
                val t = diff[i].text.removeSuffix("\n").removeSuffix("\r")
                when (diff[i].change) {
                    LineChange.SAME -> " " + t
                    LineChange.REMOVED -> "-" + t
                    LineChange.ADDED -> "+" + t
                }
            }
            val olds = span.filter { oldNo[it] > 0 }
            val news = span.filter { newNo[it] > 0 }
            val oStart = if (olds.isEmpty()) oBefore(oldNo, span.first) else oldNo[olds.first()]
            val nStart = if (news.isEmpty()) nBefore(newNo, span.first) else newNo[news.first()]
            Hunk(oStart, olds.size, nStart, news.size, body)
        }
    }

    private fun oBefore(oldNo: IntArray, from: Int): Int {
        var k = from - 1
        while (k >= 0 && oldNo[k] < 0) k--
        return if (k < 0) 0 else oldNo[k]
    }

    private fun nBefore(newNo: IntArray, from: Int): Int {
        var k = from - 1
        while (k >= 0 && newNo[k] < 0) k--
        return if (k < 0) 0 else newNo[k]
    }

    /** `@@ -a,b +c,d @@` header plus body, trailing newline. */
    fun format(hunk: Hunk): String = buildString {
        append("@@ -")
        append(hunk.oldStart)
        append(',')
        append(hunk.oldCount)
        append(" +")
        append(hunk.newStart)
        append(',')
        append(hunk.newCount)
        appendLine(" @@")
        for (l in hunk.lines) appendLine(l)
    }
}
