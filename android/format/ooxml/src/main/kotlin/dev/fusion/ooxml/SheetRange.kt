package dev.fusion.ooxml

/**
 * A1-style cell and range references (ECMA-376 Part 1 §18.17.2 ST_Ref /
 * ST_CellRef), built on [ColLetters] for the column alphabet.
 *
 * Shapes accepted: a single cell `C5` or a rectangular range `A1:B2`.
 * Ranges normalize so the stored corners are top-left:bottom-right
 * regardless of input corner order (`B2:A1` normalizes to `A1:B2`),
 * matching how SpreadsheetML consumers canonicalize dimension refs.
 *
 * Declared narrowings: no `$` absolute markers, no sheet prefixes
 * (`Sheet1!A1`), no whole-row/column refs (`A:A`, `1:1`), no 3-D refs —
 * each is refused loudly. Rows 1..1048576 (2^20, the SpreadsheetML grid).
 */
object SheetRange {
    const val SCOPE = "Plain cells and rectangular ranges only; $, sheet prefixes, A:A/1:1 and 3-D refs refused."
    const val MAX_ROW = 1048576

    data class Cell(val col: Int, val row: Int) {
        fun toA1(): String = ColLetters.toLetters(col) + row
    }

    data class Range(val top: Cell, val bottom: Cell) {
        val width: Int get() = bottom.col - top.col + 1
        val height: Int get() = bottom.row - top.row + 1
        val cells: Long get() = width.toLong() * height
        fun toA1(): String = if (top == bottom) top.toA1() else top.toA1() + ":" + bottom.toA1()
    }

    fun parseCell(ref: String): Cell {
        require(ref.isNotEmpty()) { "empty reference" }
        require('$' !in ref) { "absolute markers are out of scope" }
        require('!' !in ref) { "sheet prefixes are out of scope" }
        val split = ref.indexOfFirst { it.isDigit() }
        require(split > 0) { "'$ref' lacks a column or a row part" }
        val letters = ref.substring(0, split)
        val rowPart = ref.substring(split)
        require(rowPart.all { it.isDigit() }) { "'$ref' mixes letters into the row part" }
        require(rowPart.length <= 7 && rowPart[0] != '0') { "row '$rowPart' malformed" }
        val row = rowPart.toInt()
        require(row in 1..MAX_ROW) { "row $row outside 1..$MAX_ROW" }
        return Cell(ColLetters.fromLetters(letters), row)
    }

    fun parse(ref: String): Range {
        val colon = ref.indexOf(':')
        if (colon < 0) {
            val c = parseCell(ref)
            return Range(c, c)
        }
        require(ref.indexOf(':', colon + 1) < 0) { "3-D or multi-colon refs are out of scope" }
        val a = parseCell(ref.substring(0, colon))
        val b = parseCell(ref.substring(colon + 1))
        return Range(
            Cell(minOf(a.col, b.col), minOf(a.row, b.row)),
            Cell(maxOf(a.col, b.col), maxOf(a.row, b.row))
        )
    }

    fun contains(r: Range, cell: Cell): Boolean =
        cell.col in r.top.col..r.bottom.col && cell.row in r.top.row..r.bottom.row

    /** Intersection, or null when the ranges do not overlap. */
    fun intersect(a: Range, b: Range): Range? {
        val c1 = maxOf(a.top.col, b.top.col); val c2 = minOf(a.bottom.col, b.bottom.col)
        val r1 = maxOf(a.top.row, b.top.row); val r2 = minOf(a.bottom.row, b.bottom.row)
        return if (c1 <= c2 && r1 <= r2) Range(Cell(c1, r1), Cell(c2, r2)) else null
    }

    /** Smallest range covering both (bounding box). */
    fun union(a: Range, b: Range): Range = Range(
        Cell(minOf(a.top.col, b.top.col), minOf(a.top.row, b.top.row)),
        Cell(maxOf(a.bottom.col, b.bottom.col), maxOf(a.bottom.row, b.bottom.row))
    )
}
