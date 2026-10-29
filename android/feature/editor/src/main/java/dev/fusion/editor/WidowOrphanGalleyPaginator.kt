package dev.fusion.editor

/**
 * Clean-room Typst-inspired Galley-to-Page Paginator (Apache-2.0).
 *
 * Breaks rich paragraphs, headings, non-splittable figures/tables, and bottom-anchored
 * footnotes across fixed-height pages while enforcing:
 * - **Orphan control** (`orphanMinLines >= 2` at bottom of page),
 * - **Widow control** (`widowMinLines >= 2` at top of next page),
 * - **Keep-with-next** on headings (a heading is never stranded at the bottom of a page
 *   without at least `orphanMinLines` of the following body block),
 * - **Footnote reservation** (`footnoteRuleHeightPt` + footnote lines deducted dynamically from
 *   the same page on which their in-text reference line lands!).
 */
object WidowOrphanGalleyPaginator {
    const val SCOPE =
        "Typst-inspired widow/orphan & keep-with-next galley paginator with same-page footnote budget & rule reservation"

    sealed interface GalleyBlock {
        val id: String

        data class Heading(
            override val id: String,
            val text: String,
            val lineHeightPt: Double = 22.0,
            val keepWithNext: Boolean = true,
        ) : GalleyBlock

        data class Paragraph(
            override val id: String,
            val lines: List<String>,
            val lineHeightPt: Double = 14.0,
            val footnoteByLineIndex: Map<Int, List<String>> = emptyMap(),
        ) : GalleyBlock {
            init {
                require(lines.isNotEmpty()) { "Paragraph '$id' must have >= 1 line" }
            }
        }

        data class UnbreakableBox(
            override val id: String,
            val label: String,
            val heightPt: Double,
        ) : GalleyBlock
    }

    data class PageConfig(
        val contentHeightPt: Double = 648.0, // 9 in @ 72 pt/in
        val orphanMinLines: Int = 2,
        val widowMinLines: Int = 2,
        val footnoteLineHeightPt: Double = 11.0,
        val footnoteRuleHeightPt: Double = 6.0,
    ) {
        init {
            require(contentHeightPt >= 72.0) { "contentHeightPt too small" }
            require(orphanMinLines >= 1 && widowMinLines >= 1)
        }
    }

    data class PlacedSlice(
        val blockId: String,
        val kind: String,
        val lines: List<String>,
        val heightPt: Double,
    )

    data class PaginatedPage(
        val pageNumber: Int,
        val slices: List<PlacedSlice>,
        val footnotes: List<String>,
        val usedBodyHeightPt: Double,
        val usedFootnoteHeightPt: Double,
        val remainingHeightPt: Double,
    )

    data class PaginationResult(
        val pages: List<PaginatedPage>,
        val widowViolations: Int,
        val orphanViolations: Int,
        val strandedHeadings: Int,
    )

    fun paginate(blocks: List<GalleyBlock>, config: PageConfig = PageConfig()): PaginationResult {
        require(blocks.isNotEmpty()) { "Document galley requires >= 1 block" }
        val pages = mutableListOf<PaginatedPage>()
        var curSlices = mutableListOf<PlacedSlice>()
        var curFootnotes = mutableListOf<String>()
        var usedBody = 0.0

        fun footnoteHeight(fns: List<String>): Double =
            if (fns.isEmpty()) 0.0 else config.footnoteRuleHeightPt + fns.size * config.footnoteLineHeightPt

        fun remaining(): Double = config.contentHeightPt - usedBody - footnoteHeight(curFootnotes)

        fun flushPage() {
            if (curSlices.isNotEmpty() || curFootnotes.isNotEmpty()) {
                val fnH = footnoteHeight(curFootnotes)
                pages.add(
                    PaginatedPage(
                        pageNumber = pages.size + 1,
                        slices = curSlices.toList(),
                        footnotes = curFootnotes.toList(),
                        usedBodyHeightPt = usedBody,
                        usedFootnoteHeightPt = fnH,
                        remainingHeightPt = (config.contentHeightPt - usedBody - fnH).coerceAtLeast(0.0),
                    ),
                )
                curSlices = mutableListOf()
                curFootnotes = mutableListOf()
                usedBody = 0.0
            }
        }

        var bIdx = 0
        while (bIdx < blocks.size) {
            when (val blk = blocks[bIdx]) {
                is GalleyBlock.Heading -> {
                    val minNextPt = if (blk.keepWithNext && bIdx + 1 < blocks.size) {
                        minimumOpeningHeight(blocks[bIdx + 1], config)
                    } else {
                        0.0
                    }
                    if (curSlices.isNotEmpty() && blk.lineHeightPt + minNextPt > remaining() + 1e-9) {
                        flushPage()
                    }
                    curSlices.add(PlacedSlice(blk.id, "HEADING", listOf(blk.text), blk.lineHeightPt))
                    usedBody += blk.lineHeightPt
                    bIdx++
                }
                is GalleyBlock.UnbreakableBox -> {
                    require(blk.heightPt <= config.contentHeightPt) {
                        "UnbreakableBox '${blk.id}' (${blk.heightPt}pt) exceeds page height (${config.contentHeightPt}pt)"
                    }
                    if (curSlices.isNotEmpty() && blk.heightPt > remaining() + 1e-9) {
                        flushPage()
                    }
                    curSlices.add(PlacedSlice(blk.id, "BOX", listOf(blk.label), blk.heightPt))
                    usedBody += blk.heightPt
                    bIdx++
                }
                is GalleyBlock.Paragraph -> {
                    var start = 0
                    val total = blk.lines.size
                    while (start < total) {
                        val rem = total - start
                        // Determine maximum k in 1..rem that fits on current page INCLUDING its triggered footnotes
                        var maxFit = 0
                        val pendingFn = mutableListOf<String>()
                        for (k in 1..rem) {
                            val lineIdx = start + k - 1
                            val extraFns = blk.footnoteByLineIndex[lineIdx].orEmpty()
                            val candidateFns = curFootnotes + pendingFn + extraFns
                            val neededBody = usedBody + k * blk.lineHeightPt
                            val neededTotal = neededBody + footnoteHeight(candidateFns)
                            if (neededTotal <= config.contentHeightPt + 1e-9) {
                                maxFit = k
                                pendingFn.addAll(extraFns)
                            } else {
                                break
                            }
                        }

                        var take = maxFit
                        if (take < rem) {
                            // Paragraph will split across pages: enforce orphan (>= orphanMinLines) and widow (>= widowMinLines)!
                            val tail = rem - take
                            if (take > 0 && tail < config.widowMinLines) {
                                val adjusted = rem - config.widowMinLines
                                take = if (adjusted >= config.orphanMinLines) adjusted else 0
                            }
                            if (take in 1 until config.orphanMinLines && rem >= config.orphanMinLines) {
                                take = 0
                            }
                        }

                        if (take == 0) {
                            if (curSlices.isNotEmpty()) {
                                // Pull back any immediately preceding keep-with-next headings so they aren't stranded!
                                val pulledHeadings = mutableListOf<PlacedSlice>()
                                while (curSlices.isNotEmpty() && curSlices.last().kind == "HEADING") {
                                    val h = curSlices.removeLast()
                                    usedBody -= h.heightPt
                                    pulledHeadings.add(0, h)
                                }
                                if (curSlices.isNotEmpty()) {
                                    flushPage()
                                    for (h in pulledHeadings) {
                                        curSlices.add(h)
                                        usedBody += h.heightPt
                                    }
                                    continue
                                } else {
                                    for (h in pulledHeadings) {
                                        curSlices.add(h)
                                        usedBody += h.heightPt
                                    }
                                    // Empty page except heading — force at least 1 line so pagination always makes progress
                                    take = maxFit.coerceAtLeast(1)
                                }
                            } else {
                                take = 1
                            }
                        }

                        val sliceLines = blk.lines.subList(start, start + take)
                        for (offset in 0 until take) {
                            curFootnotes.addAll(blk.footnoteByLineIndex[start + offset].orEmpty())
                        }
                        val h = take * blk.lineHeightPt
                        curSlices.add(PlacedSlice(blk.id, "PARAGRAPH", sliceLines, h))
                        usedBody += h
                        start += take
                        if (start < total) flushPage()
                    }
                    bIdx++
                }
            }
        }
        flushPage()

        // Audit post-conditions across all emitted pages
        var widows = 0
        var orphans = 0
        var stranded = 0
        val paraChunksByBlock = pages.flatMapIndexed { pIdx, p -> p.slices.map { pIdx to it } }
            .filter { it.second.kind == "PARAGRAPH" }
            .groupBy { it.second.blockId }
        for ((_, chunks) in paraChunksByBlock) {
            if (chunks.size >= 2) {
                if (chunks.first().second.lines.size < config.orphanMinLines) orphans++
                if (chunks.last().second.lines.size < config.widowMinLines) widows++
            }
        }
        for (p in pages) {
            if (p.slices.lastOrNull()?.kind == "HEADING") stranded++
        }
        return PaginationResult(pages, widows, orphans, stranded)
    }

    private fun minimumOpeningHeight(next: GalleyBlock, config: PageConfig): Double = when (next) {
        is GalleyBlock.Paragraph -> {
            val needLines = minOf(next.lines.size, config.orphanMinLines)
            val fns = (0 until needLines).flatMap { next.footnoteByLineIndex[it].orEmpty() }
            val fnH = if (fns.isEmpty()) 0.0 else config.footnoteRuleHeightPt + fns.size * config.footnoteLineHeightPt
            needLines * next.lineHeightPt + fnH
        }
        is GalleyBlock.Heading -> next.lineHeightPt
        is GalleyBlock.UnbreakableBox -> next.heightPt
    }
}
