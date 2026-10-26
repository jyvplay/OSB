package dev.fusion.ooxml

/**
 * R1C1 <-> A1 reference-style conversion (the alternate reference style
 * SpreadsheetML applications expose; [ColLetters] declared it out of its
 * own scope — this kernel is that complement).
 *
 * Absolute form: `R5C3` == `C5` (row 5, column 3). Relative form:
 * `R[2]C[-1]` means two rows down, one column left of an ANCHOR cell
 * that the caller must supply; `R` or `C` alone (offset 0) is the
 * same row/column. Mixed forms (`R5C[2]`) are allowed per the style.
 *
 * Declared narrowings: single cells only (ranges compose via SheetRange
 * after conversion); results must stay inside the grid (1..1048576 rows,
 * 1..16384 columns) or refuse — relative references never wrap.
 */
object R1c1Ref {
    const val SCOPE = "Single-cell R1C1 <-> A1 conversion; no ranges; offsets never wrap the grid."
    private val FORM = Regex("R(\\[(-?\\d{1,7})\\]|(\\d{1,7}))?C(\\[(-?\\d{1,5})\\]|(\\d{1,5}))?")

    data class Ref(val rowAbs: Int?, val rowOff: Int, val colAbs: Int?, val colOff: Int)

    fun parse(text: String): Ref {
        val m = FORM.matchEntire(text) ?: throw IllegalArgumentException("'$text' is not an R1C1 reference")
        val rowBracket = m.groupValues[2]
        val rowPlain = m.groupValues[3]
        val colBracket = m.groupValues[5]
        val colPlain = m.groupValues[6]
        val rowAbs = rowPlain.ifEmpty { null }?.toInt()
        val colAbs = colPlain.ifEmpty { null }?.toInt()
        if (rowAbs != null) require(rowAbs in 1..SheetRange.MAX_ROW) { "row $rowAbs outside the grid" }
        if (colAbs != null) require(colAbs in 1..ColLetters.MAX_COLUMN) { "column $colAbs outside the grid" }
        return Ref(
            rowAbs = rowAbs, rowOff = rowBracket.ifEmpty { null }?.toInt() ?: 0,
            colAbs = colAbs, colOff = colBracket.ifEmpty { null }?.toInt() ?: 0
        )
    }

    /** Resolve against an anchor (1-based col/row); absolute parts ignore the anchor. */
    fun toA1(text: String, anchorCol: Int, anchorRow: Int): String {
        require(anchorCol in 1..ColLetters.MAX_COLUMN && anchorRow in 1..SheetRange.MAX_ROW) { "anchor outside grid" }
        val r = parse(text)
        val row = r.rowAbs ?: (anchorRow + r.rowOff)
        val col = r.colAbs ?: (anchorCol + r.colOff)
        require(row in 1..SheetRange.MAX_ROW) { "resolved row $row leaves the grid (no wrapping)" }
        require(col in 1..ColLetters.MAX_COLUMN) { "resolved column $col leaves the grid (no wrapping)" }
        return ColLetters.toLetters(col) + row
    }

    /** Absolute R1C1 for an A1 cell. */
    fun fromA1(a1: String): String {
        val cell = SheetRange.parseCell(a1)
        return "R${cell.row}C${cell.col}"
    }

    /** Relative R1C1 for an A1 cell as seen from an anchor. */
    fun relativeFromA1(a1: String, anchorCol: Int, anchorRow: Int): String {
        val cell = SheetRange.parseCell(a1)
        val dr = cell.row - anchorRow
        val dc = cell.col - anchorCol
        val rPart = if (dr == 0) "R" else "R[$dr]"
        val cPart = if (dc == 0) "C" else "C[$dc]"
        return rPart + cPart
    }
}
