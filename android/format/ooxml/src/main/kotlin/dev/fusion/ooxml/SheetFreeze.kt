package dev.fusion.ooxml

/**
 * Frozen-pane GEOMETRY (ECMA-376 pane element, state="frozen"): with
 * `frozenCols` columns and `frozenRows` rows pinned, every cell falls
 * in exactly one of four panes, and the scrollable region's home cell
 * is (frozenCols+1, frozenRows+1).
 *
 * Declared narrowings: geometry only — the cut R40 PaneSplit twips
 * formula stays cut (its published sources self-contradict); split
 * PIXEL positions are a rendering concern. Frozen counts must leave
 * at least one scrollable row and column.
 */
object SheetFreeze {
    const val SCOPE = "Frozen-pane cell classification only; twips/pixel split positions remain out (cut upheld)."

    enum class Pane { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    data class Freeze(val frozenCols: Int, val frozenRows: Int) {
        init {
            require(frozenCols >= 0 && frozenRows >= 0) { "negative freeze" }
            require(frozenCols < ColLetters.MAX_COLUMN) { "no scrollable column remains" }
            require(frozenRows < SheetRange.MAX_ROW) { "no scrollable row remains" }
            require(frozenCols > 0 || frozenRows > 0) { "freeze with nothing frozen" }
        }
    }

    fun paneOf(f: Freeze, cell: SheetRange.Cell): Pane {
        val leftOfSplit = cell.col <= f.frozenCols
        val aboveSplit = cell.row <= f.frozenRows
        return when {
            aboveSplit && leftOfSplit -> Pane.TOP_LEFT
            aboveSplit -> Pane.TOP_RIGHT
            leftOfSplit -> Pane.BOTTOM_LEFT
            else -> Pane.BOTTOM_RIGHT
        }
    }

    /** Home cell of the scrollable (bottom-right) pane. */
    fun scrollHome(f: Freeze): SheetRange.Cell = SheetRange.Cell(f.frozenCols + 1, f.frozenRows + 1)

    fun frozenCellCount(f: Freeze, visibleCols: Int, visibleRows: Int): Long {
        require(visibleCols > f.frozenCols && visibleRows > f.frozenRows) { "viewport smaller than the freeze" }
        return visibleCols.toLong() * visibleRows - (visibleCols - f.frozenCols).toLong() * (visibleRows - f.frozenRows)
    }
}
