package dev.fusion.launcher

/**
 * Launcher grid geometry: linear tile index <-> (row, column) with
 * LTR/RTL mirroring and fixed-capacity paging.
 *
 * Model: `columns` tiles per row, row-major order; in RTL layouts the
 * VISUAL column mirrors (visualCol = columns - 1 - logicalCol) while
 * the index order — and therefore persistence — never changes, which
 * is exactly how Android mirrors grids. Paging slices indices into
 * fixed-size pages of rows*columns.
 *
 * Executed property: index -> (page, row, visualCol) -> index is the
 * identity over a full sweep in both directions.
 *
 * Declared narrowings: geometry only — no drag precedence, no gap
 * compaction policy, no icon sizes (PinnedCatalog owns what is pinned;
 * this kernel owns only where index k sits).
 */
object GridPos {
    const val SCOPE = "Row-major grid math with RTL visual mirroring and paging; no drag/compaction policy."

    data class Pos(val page: Int, val row: Int, val visualCol: Int)

    fun toPos(index: Int, columns: Int, rowsPerPage: Int, rtl: Boolean): Pos {
        require(index >= 0) { "negative index" }
        require(columns > 0 && rowsPerPage > 0) { "grid ${columns}x$rowsPerPage" }
        val perPage = columns * rowsPerPage
        val page = index / perPage
        val inPage = index % perPage
        val row = inPage / columns
        val logicalCol = inPage % columns
        return Pos(page, row, if (rtl) columns - 1 - logicalCol else logicalCol)
    }

    fun toIndex(pos: Pos, columns: Int, rowsPerPage: Int, rtl: Boolean): Int {
        require(columns > 0 && rowsPerPage > 0) { "grid ${columns}x$rowsPerPage" }
        require(pos.page >= 0 && pos.row in 0 until rowsPerPage && pos.visualCol in 0 until columns) {
            "position $pos outside a ${columns}x$rowsPerPage page"
        }
        val logicalCol = if (rtl) columns - 1 - pos.visualCol else pos.visualCol
        return pos.page * columns * rowsPerPage + pos.row * columns + logicalCol
    }

    fun pageCount(items: Int, columns: Int, rowsPerPage: Int): Int {
        require(items >= 0) { "negative item count" }
        require(columns > 0 && rowsPerPage > 0) { "grid ${columns}x$rowsPerPage" }
        val perPage = columns * rowsPerPage
        return if (items == 0) 1 else (items + perPage - 1) / perPage
    }
}
