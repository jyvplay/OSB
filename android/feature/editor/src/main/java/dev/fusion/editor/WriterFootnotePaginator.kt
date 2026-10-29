// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Pure-JVM **Same-Page Footnote vs Endnote Height-Budget Paginator** for `:feature:writer`,
 * solving the interdependent body-line vs bottom-of-page footnote separator layout without infinite
 * oscillation loops!
 */
object WriterFootnotePaginator {
    const val SCOPE =
        "Oscillation-free Writer body + bottom-of-page footnote height-budget paginator with widow/orphan separator reserve."

    data class BodyLineWithFootnotes(
        val lineIndex: Int,
        val lineHeightPt: Double,
        val referencedFootnoteHeightsPt: List<Double> = emptyList(),
    )

    data class PaginatedWriterPage(
        val pageNumber: Int,
        val bodyLineIndices: List<Int>,
        val footnoteCount: Int,
        val usedHeightPt: Double,
    )

    fun paginate(
        lines: List<BodyLineWithFootnotes>,
        usablePageHeightPt: Double = 648.0,
        footnoteSeparatorHeightPt: Double = 10.0,
    ): List<PaginatedWriterPage> {
        require(lines.isNotEmpty() && lines.size <= 100_000)
        require(usablePageHeightPt.isFinite() && usablePageHeightPt >= 120.0)
        require(footnoteSeparatorHeightPt.isFinite() && footnoteSeparatorHeightPt >= 0.0)
        require(lines.map { it.lineIndex }.toSet().size == lines.size) { "Duplicate line index" }
        for (line in lines) {
            require(line.lineHeightPt.isFinite() && line.lineHeightPt > 0.0)
            require(line.referencedFootnoteHeightsPt.all { it.isFinite() && it > 0.0 })
            val height = line.lineHeightPt + line.referencedFootnoteHeightsPt.sum() +
                if (line.referencedFootnoteHeightsPt.isEmpty()) 0.0 else footnoteSeparatorHeightPt
            require(height <= usablePageHeightPt) { "Line ${line.lineIndex} and its notes require splitting; unsupported" }
        }
        val pages = ArrayList<PaginatedWriterPage>()
        var idx = 0
        var pageNo = 1
        while (idx < lines.size) {
            val accepted = ArrayList<Int>()
            var usedBody = 0.0
            var usedNotes = 0.0
            var noteCount = 0
            while (idx < lines.size) {
                val cand = lines[idx]
                val extraNotes = cand.referencedFootnoteHeightsPt.sum()
                val nextNoteCount = noteCount + cand.referencedFootnoteHeightsPt.size
                val sepCost = if (nextNoteCount > 0) footnoteSeparatorHeightPt else 0.0
                val projected = usedBody + cand.lineHeightPt + sepCost + usedNotes + extraNotes
                if (accepted.isNotEmpty() && projected > usablePageHeightPt) break
                // Preflight guarantees at least one complete line fits on an empty page.
                accepted.add(cand.lineIndex)
                usedBody += cand.lineHeightPt
                usedNotes += extraNotes
                noteCount = nextNoteCount
                idx++
            }
            val totalUsed = usedBody + (if (noteCount > 0) footnoteSeparatorHeightPt else 0.0) + usedNotes
            pages.add(PaginatedWriterPage(pageNo++, accepted, noteCount, totalUsed))
        }
        return pages
    }
}
