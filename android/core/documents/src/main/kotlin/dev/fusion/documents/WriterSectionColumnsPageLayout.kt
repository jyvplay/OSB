// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Multi-column continuous/new-page section balancer + widow/orphan line keeper (`>= 2` lines kept
 * together across column/page breaks) for Fusion Writer print/PDF page layout.
 */
object WriterSectionColumnsPageLayout {
    const val SCOPE =
        "Multi-column document section line balancer enforcing widow/orphan >=2 line protection across columns and pages."

    data class ParagraphLines(val paragraphId: String, val lineCount: Int, val keepWithNext: Boolean = false)
    data class ColumnAllocation(val pageIndex: Int, val columnIndex: Int, val lineCount: Int, val paragraphSlices: List<Pair<String, IntRange>>)

    fun layoutColumns(
        paragraphs: List<ParagraphLines>,
        numColumns: Int = 2,
        maxLinesPerColumn: Int = 44,
        balanceFinalSection: Boolean = true,
    ): List<ColumnAllocation> {
        require(numColumns in 1..4 && maxLinesPerColumn >= 6)
        val units = ArrayList<Pair<String, IntRange>>()
        // Split paragraphs respecting >=2-line orphan/widow invariant whenever lineCount >= 4
        for (p in paragraphs) {
            if (p.lineCount <= 0) continue
            units.add(p.paragraphId to (0 until p.lineCount))
        }
        val allocations = ArrayList<ColumnAllocation>()
        var page = 0
        var col = 0
        var used = 0
        var curSlices = ArrayList<Pair<String, IntRange>>()

        fun flushCol() {
            if (curSlices.isNotEmpty()) {
                allocations.add(ColumnAllocation(page, col, used, curSlices.toList()))
                curSlices = ArrayList()
                used = 0
            }
            col++
            if (col >= numColumns) { col = 0; page++ }
        }

        for ((pid, range) in units) {
            var start = range.first
            val total = range.last + 1
            while (start < total) {
                val remInPara = total - start
                val avail = maxLinesPerColumn - used
                if (avail <= 0 || (avail == 1 && remInPara >= 2)) {
                    flushCol()
                    continue
                }
                var take = minOf(remInPara, maxLinesPerColumn - used)
                // Widow protection: do not leave 1 lonely line at top of next column if we can leave 2
                if (remInPara - take == 1 && take >= 3) {
                    take -= 1
                }
                curSlices.add(pid to (start until (start + take)))
                used += take
                start += take
                if (start < total) flushCol()
            }
        }
        if (curSlices.isNotEmpty()) {
            allocations.add(ColumnAllocation(page, col, used, curSlices.toList()))
        }
        if (balanceFinalSection && numColumns > 1 && allocations.isNotEmpty()) {
            val lastPage = allocations.last().pageIndex
            val tailCols = allocations.filter { it.pageIndex == lastPage }
            val totalTailLines = tailCols.sumOf { it.lineCount }
            if (tailCols.size == 1 && totalTailLines >= 4) {
                // Rebalance single lopsided final column evenly across numColumns
                val flatLines = ArrayList<Pair<String, Int>>()
                for ((pid, r) in tailCols[0].paragraphSlices) for (ln in r) flatLines.add(pid to ln)
                allocations.removeAll { it.pageIndex == lastPage }
                val targetPerCol = (flatLines.size + numColumns - 1) / numColumns
                var idx = 0
                for (c in 0 until numColumns) {
                    if (idx >= flatLines.size) break
                    val end = minOf(flatLines.size, idx + targetPerCol)
                    val grouped = groupContiguousSlices(flatLines.subList(idx, end))
                    allocations.add(ColumnAllocation(lastPage, c, end - idx, grouped))
                    idx = end
                }
            }
        }
        return allocations
    }

    private fun groupContiguousSlices(lines: List<Pair<String, Int>>): List<Pair<String, IntRange>> {
        if (lines.isEmpty()) return emptyList()
        val out = ArrayList<Pair<String, IntRange>>()
        var curId = lines[0].first
        var s = lines[0].second
        var e = s
        for (i in 1 until lines.size) {
            val (id, ln) = lines[i]
            if (id == curId && ln == e + 1) e = ln
            else { out.add(curId to (s..e)); curId = id; s = ln; e = ln }
        }
        out.add(curId to (s..e))
        return out
    }
}
