// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import kotlin.math.roundToInt

/**
 * ECMA-376 WordprocessingML `<w:tblGrid>` + `<w:gridSpan>` + `<w:vMerge>` AutoFit column-width
 * solver (`dxa` twentieths-of-a-point) for Fusion Writer tables.
 */
object DocxTableLayoutGridEngine {
    const val SCOPE =
        "ECMA-376 WordprocessingML <w:tblGrid>/<w:gridCol> AutoFit dxa column-width solver supporting gridSpan and vMerge."

    data class CellSpec(
        val text: String,
        val gridSpan: Int = 1,
        val vMergeRestart: Boolean = false,
        val vMergeContinue: Boolean = false,
        val explicitWidthDxa: Int? = null,
    )

    data class TableGridPlan(
        val columnCount: Int,
        val colWidthsDxa: List<Int>,
        val totalWidthDxa: Int,
        val tblXml: String,
    )

    fun solveAndRenderTableXml(
        rows: List<List<CellSpec>>,
        pageContentWidthDxa: Int = 9360, // 6.5 inches * 1440 dxa/inch
        cellPaddingDxa: Int = 160,
    ): TableGridPlan {
        require(rows.isNotEmpty() && pageContentWidthDxa >= 720)
        val numCols = rows.maxOf { row -> row.sumOf { it.gridSpan.coerceAtLeast(1) } }
        require(numCols in 1..64)

        val minCol = IntArray(numCols) { 360 }
        val prefCol = IntArray(numCols) { 720 }

        for (row in rows) {
            var cIdx = 0
            for (cell in row) {
                val span = cell.gridSpan.coerceAtLeast(1)
                if (cIdx + span > numCols) break
                val longestWord = cell.text.split(Regex("\\s+")).maxOfOrNull { it.length } ?: 1
                val cellMin = longestWord * 120 + cellPaddingDxa
                val cellPref = cell.explicitWidthDxa ?: (cell.text.length * 110 + cellPaddingDxa).coerceAtLeast(cellMin)
                if (span == 1) {
                    if (cellMin > minCol[cIdx]) minCol[cIdx] = cellMin
                    if (cellPref > prefCol[cIdx]) prefCol[cIdx] = cellPref
                } else {
                    val share = (cellPref + span - 1) / span
                    for (k in 0 until span) if (share > prefCol[cIdx + k]) prefCol[cIdx + k] = share
                }
                cIdx += span
            }
        }

        val prefSum = prefCol.sum().coerceAtLeast(1)
        val widths = IntArray(numCols)
        var allocated = 0
        for (i in 0 until numCols) {
            val scaled = ((prefCol[i].toDouble() / prefSum.toDouble()) * pageContentWidthDxa).roundToInt()
            widths[i] = scaled.coerceAtLeast(240)
            allocated += widths[i]
        }
        // Adjust last column so exact sum matches pageContentWidthDxa when feasible
        val diff = pageContentWidthDxa - allocated
        if (widths[numCols - 1] + diff >= 240) {
            widths[numCols - 1] += diff
        }

        val sb = StringBuilder()
        sb.append("<w:tbl xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:tblGrid>")
        for (w in widths) sb.append("<w:gridCol w:w=\"$w\"/>")
        sb.append("</w:tblGrid>")
        for (row in rows) {
            sb.append("<w:tr>")
            var colCursor = 0
            for (cell in row) {
                val span = cell.gridSpan.coerceAtLeast(1)
                val spanW = (0 until span).sumOf { k -> widths.getOrElse(colCursor + k) { 360 } }
                sb.append("<w:tc><w:tcPr><w:tcW w:w=\"$spanW\" w:type=\"dxa\"/>")
                if (span > 1) sb.append("<w:gridSpan w:val=\"$span\"/>")
                if (cell.vMergeRestart) sb.append("<w:vMerge w:val=\"restart\"/>")
                else if (cell.vMergeContinue) sb.append("<w:vMerge/>")
                sb.append("</w:tcPr><w:p><w:r><w:t>${escXml(cell.text)}</w:t></w:r></w:p></w:tc>")
                colCursor += span
            }
            sb.append("</w:tr>")
        }
        sb.append("</w:tbl>")
        return TableGridPlan(numCols, widths.toList(), widths.sum(), sb.toString())
    }

    private fun escXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
