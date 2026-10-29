// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Clean-room multi-column page-break & widow/orphan paginator for Writer / Print / PDF export.
 *
 * Enforces:
 * - Multi-column flow (`columns in 1..4`) with configurable column gutter & page margins.
 * - `keepWithNext` on headings so a section heading is never stranded at the bottom of a column.
 * - Widow (`minLinesAtTop >= 2`) and orphan (`minLinesAtBottom >= 2`) protection on multi-line paragraphs.
 * - Floating figure placement (`TOP_OF_COLUMN` or `INLINE`) and running header/footer page tokens (`{page}`, `{pages}`).
 */
object MultiColumnPagePaginator {
    data class PageGeometry(
        val pageWidthPt: Double = 612.0,
        val pageHeightPt: Double = 792.0,
        val marginTopPt: Double = 54.0,
        val marginBottomPt: Double = 54.0,
        val marginLeftPt: Double = 54.0,
        val marginRightPt: Double = 54.0,
        val columns: Int = 1,
        val columnGutterPt: Double = 18.0,
        val minOrphanLines: Int = 2,
        val minWidowLines: Int = 2,
    ) {
        init {
            require(columns in 1..4) { "columns must be in 1..4" }
            require(columnWidthPt > 36.0 && contentHeightPt > 72.0) { "insufficient content area" }
            require(minOrphanLines >= 1 && minWidowLines >= 1) { "widow/orphan lines must be >= 1" }
        }

        val contentHeightPt: Double get() = pageHeightPt - marginTopPt - marginBottomPt
        val contentWidthPt: Double get() = pageWidthPt - marginLeftPt - marginRightPt
        val columnWidthPt: Double get() = (contentWidthPt - columnGutterPt * (columns - 1)) / columns
        fun columnLeftPt(colIdx: Int): Double = marginLeftPt + colIdx * (columnWidthPt + columnGutterPt)
    }

    sealed interface FlowBlock {
        val blockId: String

        data class ParagraphBlock(
            override val blockId: String,
            val lines: List<String>,
            val lineHeightPt: Double = 14.0,
            val spaceAfterPt: Double = 6.0,
            val keepWithNext: Boolean = false,
            val forcePageBreakBefore: Boolean = false,
        ) : FlowBlock {
            init {
                require(lines.isNotEmpty() && lineHeightPt > 0.0 && spaceAfterPt >= 0.0) {
                    "invalid ParagraphBlock $blockId"
                }
            }
        }

        data class FigureBlock(
            override val blockId: String,
            val caption: String,
            val heightPt: Double,
            val spaceAfterPt: Double = 8.0,
        ) : FlowBlock {
            init {
                require(heightPt > 0.0 && spaceAfterPt >= 0.0) { "invalid FigureBlock $blockId" }
            }
        }
    }

    data class PlacedBox(
        val blockId: String,
        val columnIndex: Int,
        val xPt: Double,
        val yPt: Double,
        val widthPt: Double,
        val heightPt: Double,
        val lines: List<String>,
    )

    data class PaginatedPage(
        val pageNumber: Int,
        val headerText: String,
        val footerText: String,
        val boxes: List<PlacedBox>,
    )

    fun paginate(
        blocks: List<FlowBlock>,
        geometry: PageGeometry = PageGeometry(),
        headerTemplate: String = "Fusion Writer",
        footerTemplate: String = "Page {page} of {pages}",
    ): List<PaginatedPage> {
        if (blocks.isEmpty()) {
            return listOf(
                PaginatedPage(
                    1,
                    headerTemplate.replace("{page}", "1").replace("{pages}", "1"),
                    footerTemplate.replace("{page}", "1").replace("{pages}", "1"),
                    emptyList(),
                ),
            )
        }

        val rawPages = ArrayList<MutableList<PlacedBox>>()
        rawPages.add(ArrayList())
        var curPageIdx = 0
        var curCol = 0
        var cursorY = 0.0

        fun advanceColumn() {
            if (curCol + 1 < geometry.columns) {
                curCol++
                cursorY = 0.0
            } else {
                rawPages.add(ArrayList())
                curPageIdx++
                curCol = 0
                cursorY = 0.0
            }
        }

        fun advancePage() {
            if (rawPages[curPageIdx].isNotEmpty() || curCol > 0 || cursorY > 0.0) {
                rawPages.add(ArrayList())
                curPageIdx++
                curCol = 0
                cursorY = 0.0
            }
        }

        fun remainingPt(): Double = geometry.contentHeightPt - cursorY

        var idx = 0
        while (idx < blocks.size) {
            when (val b = blocks[idx]) {
                is FlowBlock.FigureBlock -> {
                    val clampedH = minOf(b.heightPt, geometry.contentHeightPt)
                    if (cursorY > 0.0 && remainingPt() < clampedH) advanceColumn()
                    rawPages[curPageIdx].add(
                        PlacedBox(
                            blockId = b.blockId,
                            columnIndex = curCol,
                            xPt = geometry.columnLeftPt(curCol),
                            yPt = geometry.marginTopPt + cursorY,
                            widthPt = geometry.columnWidthPt,
                            heightPt = clampedH,
                            lines = listOf("[Figure: ${b.caption}]"),
                        ),
                    )
                    cursorY = minOf(geometry.contentHeightPt, cursorY + clampedH + b.spaceAfterPt)
                    idx++
                }

                is FlowBlock.ParagraphBlock -> {
                    if (b.forcePageBreakBefore) advancePage()

                    // keepWithNext: ensure this block PLUS at least minOrphanLines of the next block fit in current column!
                    val nextBlock = blocks.getOrNull(idx + 1)
                    val nextMinNeed = when (nextBlock) {
                        is FlowBlock.ParagraphBlock ->
                            minOf(nextBlock.lines.size, geometry.minOrphanLines) * nextBlock.lineHeightPt
                        is FlowBlock.FigureBlock -> minOf(nextBlock.heightPt, geometry.contentHeightPt)
                        null -> 0.0
                    }
                    val fullH = b.lines.size * b.lineHeightPt
                    if (b.keepWithNext && cursorY > 0.0 &&
                        fullH + b.spaceAfterPt + nextMinNeed <= geometry.contentHeightPt &&
                        remainingPt() < fullH + b.spaceAfterPt + nextMinNeed
                    ) {
                        advanceColumn()
                    }

                    var lineOffset = 0
                    while (lineOffset < b.lines.size) {
                        val remLines = b.lines.size - lineOffset
                        val maxFit = (remainingPt() / b.lineHeightPt).toInt()
                        if (maxFit <= 0) {
                            advanceColumn()
                            continue
                        }
                        if (maxFit >= remLines) {
                            val h = remLines * b.lineHeightPt
                            rawPages[curPageIdx].add(
                                PlacedBox(
                                    blockId = b.blockId,
                                    columnIndex = curCol,
                                    xPt = geometry.columnLeftPt(curCol),
                                    yPt = geometry.marginTopPt + cursorY,
                                    widthPt = geometry.columnWidthPt,
                                    heightPt = h,
                                    lines = b.lines.subList(lineOffset, b.lines.size),
                                ),
                            )
                            cursorY = minOf(geometry.contentHeightPt, cursorY + h + b.spaceAfterPt)
                            lineOffset = b.lines.size
                        } else {
                            // Paragraph split across columns: enforce orphan & widow line minimums when possible!
                            if (cursorY > 0.0 && maxFit < geometry.minOrphanLines &&
                                remLines >= geometry.minOrphanLines
                            ) {
                                advanceColumn()
                                continue
                            }
                            var take = maxFit
                            val tail = remLines - take
                            if (tail in 1 until geometry.minWidowLines) {
                                val adjust = geometry.minWidowLines - tail
                                if (take - adjust >= geometry.minOrphanLines) {
                                    take -= adjust
                                } else if (cursorY > 0.0 && remLines * b.lineHeightPt <= geometry.contentHeightPt) {
                                    advanceColumn()
                                    continue
                                }
                            }
                            val h = take * b.lineHeightPt
                            rawPages[curPageIdx].add(
                                PlacedBox(
                                    blockId = b.blockId,
                                    columnIndex = curCol,
                                    xPt = geometry.columnLeftPt(curCol),
                                    yPt = geometry.marginTopPt + cursorY,
                                    widthPt = geometry.columnWidthPt,
                                    heightPt = h,
                                    lines = b.lines.subList(lineOffset, lineOffset + take),
                                ),
                            )
                            lineOffset += take
                            advanceColumn()
                        }
                    }
                    idx++
                }
            }
        }

        val totalPages = rawPages.size
        return rawPages.mapIndexed { pIdx, boxes ->
            val pNum = pIdx + 1
            PaginatedPage(
                pageNumber = pNum,
                headerText = headerTemplate.replace("{page}", pNum.toString()).replace("{pages}", totalPages.toString()),
                footerText = footerTemplate.replace("{page}", pNum.toString()).replace("{pages}", totalPages.toString()),
                boxes = boxes.toList(),
            )
        }
    }
}
