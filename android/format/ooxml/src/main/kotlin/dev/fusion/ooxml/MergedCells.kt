package dev.fusion.ooxml

/**
 * Merged-cell region rules (ECMA-376 §18.3.1.55 mergeCells: regions are
 * rectangular ranges that MUST NOT overlap; a merge of a single cell is
 * meaningless and refused; only the top-left cell of a merge carries
 * content).
 *
 * [validate] enforces pairwise disjointness via [SheetRange.intersect];
 * [anchorOf] answers which cell a covered cell's content lives in;
 * [isCovered] marks cells hidden by a merge.
 *
 * Declared narrowings: region GEOMETRY only — no style propagation, no
 * row/col insertion rewriting (that is the next layer and takes these
 * validated regions as input).
 */
object MergedCells {
    const val SCOPE = "Merge-region geometry: disjointness, anchors, coverage; no styles, no insert rewriting."

    fun validate(regions: List<SheetRange.Range>): List<SheetRange.Range> {
        regions.forEach { require(it.cells > 1) { "${it.toA1()} merges a single cell" } }
        for (i in regions.indices) for (j in i + 1 until regions.size) {
            val x = SheetRange.intersect(regions[i], regions[j])
            require(x == null) { "merges ${regions[i].toA1()} and ${regions[j].toA1()} overlap at ${x!!.toA1()}" }
        }
        return regions
    }

    fun anchorOf(regions: List<SheetRange.Range>, cell: SheetRange.Cell): SheetRange.Cell {
        for (r in regions) if (SheetRange.contains(r, cell)) return r.top
        return cell
    }

    fun isCovered(regions: List<SheetRange.Range>, cell: SheetRange.Cell): Boolean =
        regions.any { SheetRange.contains(it, cell) && it.top != cell }

    /** Total cells hidden by merges (anchors stay visible). */
    fun hiddenCells(regions: List<SheetRange.Range>): Long =
        validate(regions).sumOf { it.cells - 1 }
}
