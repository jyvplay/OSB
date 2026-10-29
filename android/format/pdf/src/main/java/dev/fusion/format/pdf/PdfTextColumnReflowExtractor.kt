// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.pdf

/**
 * Multi-column XY-cut reading-order reflow extractor for academic 2-column & newspaper PDFs,
 * preventing left-column and right-column lines at identical `y` baselines from interleaving.
 */
object PdfTextColumnReflowExtractor {
    const val SCOPE =
        "2-column / multi-column XY-cut PDF positioned text-span reading-order sorter with full-width title spanning."

    data class PositionedSpan(
        val xLeftPt: Double,
        val xRightPt: Double,
        val yTopFromPageTopPt: Double,
        val text: String,
    )

    fun extractReadingOrderParagraphs(
        spans: List<PositionedSpan>,
        pageWidthPt: Double = 612.0,
        minColumnGutterPt: Double = 14.0,
    ): List<String> {
        if (spans.isEmpty()) return emptyList()
        val midX = pageWidthPt / 2.0
        val halfGutter = minColumnGutterPt / 2.0

        val spanningFullWidth = ArrayList<PositionedSpan>()
        val leftCol = ArrayList<PositionedSpan>()
        val rightCol = ArrayList<PositionedSpan>()

        for (s in spans) {
            when {
                s.xLeftPt < midX - halfGutter && s.xRightPt > midX + halfGutter -> spanningFullWidth.add(s)
                s.xRightPt <= midX + halfGutter -> leftCol.add(s)
                else -> rightCol.add(s)
            }
        }

        // If single-column document, sort strictly top-to-bottom then left-to-right
        if (leftCol.isEmpty() || rightCol.isEmpty()) {
            return groupIntoLines(spans).map { it.second }
        }

        // Partition around full-width header/section banners by vertical band
        val ordered = ArrayList<String>()
        val firstColTop = minOf(leftCol.minOf { it.yTopFromPageTopPt }, rightCol.minOf { it.yTopFromPageTopPt })
        val headerBanners = spanningFullWidth.filter { it.yTopFromPageTopPt <= firstColTop }
        val footerBanners = spanningFullWidth.filter { it.yTopFromPageTopPt > firstColTop }

        ordered.addAll(groupIntoLines(headerBanners).map { it.second })
        ordered.addAll(groupIntoLines(leftCol).map { it.second })
        ordered.addAll(groupIntoLines(rightCol).map { it.second })
        ordered.addAll(groupIntoLines(footerBanners).map { it.second })
        return ordered
    }

    private fun groupIntoLines(items: List<PositionedSpan>, lineTolerancePt: Double = 3.5): List<Pair<Double, String>> {
        if (items.isEmpty()) return emptyList()
        val sorted = items.sortedWith(compareBy<PositionedSpan> { it.yTopFromPageTopPt }.thenBy { it.xLeftPt })
        val lines = ArrayList<Pair<Double, ArrayList<PositionedSpan>>>()
        for (sp in sorted) {
            val existing = lines.lastOrNull { kotlin.math.abs(it.first - sp.yTopFromPageTopPt) <= lineTolerancePt }
            if (existing != null) existing.second.add(sp)
            else lines.add(sp.yTopFromPageTopPt to arrayListOf(sp))
        }
        return lines.map { (y, row) ->
            y to row.sortedBy { it.xLeftPt }.joinToString(" ") { it.text.trim() }.trim()
        }.filter { it.second.isNotEmpty() }
    }
}
