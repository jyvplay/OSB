package dev.fusion.ooxml

/**
 * Absolute/mixed A1 references — the `$` markers [SheetRange] declared
 * out of its scope (ECMA-376 §18.17.2 / shared-formula semantics:
 * `$A$1` fully absolute, `$A1` column-absolute, `A$1` row-absolute).
 *
 * The flags matter for formula FILL: a relative part shifts with the
 * anchor delta, an absolute part does not — [shift] executes exactly
 * that rule and refuses to leave the grid.
 *
 * Declared narrowings: single cells only; ranges compose via SheetRange
 * after stripping; R1C1 lives in [R1c1Ref].
 */
object AbsRef {
    const val SCOPE = "Single-cell $ markers + fill-shift semantics only; ranges/R1C1 live in their own kernels."

    data class Ref(val col: Int, val row: Int, val colAbs: Boolean, val rowAbs: Boolean) {
        fun toA1(): String =
            (if (colAbs) "$" else "") + ColLetters.toLetters(col) + (if (rowAbs) "$" else "") + row
    }

    fun parse(text: String): Ref {
        require(text.isNotEmpty()) { "empty reference" }
        var i = 0
        val colAbs = text[i] == '$'
        if (colAbs) i++
        val letterStart = i
        while (i < text.length && text[i].isLetter()) i++
        require(i > letterStart) { "'$text' lacks column letters" }
        val col = ColLetters.fromLetters(text.substring(letterStart, i))
        val rowAbs = i < text.length && text[i] == '$'
        if (rowAbs) i++
        val rowPart = text.substring(i)
        require(rowPart.isNotEmpty() && rowPart.all { it.isDigit() } && rowPart[0] != '0') { "row '$rowPart' malformed" }
        val row = rowPart.toInt()
        require(row in 1..SheetRange.MAX_ROW) { "row $row outside the grid" }
        return Ref(col, row, colAbs, rowAbs)
    }

    /** Fill semantics: relative parts shift by the delta, absolute parts do not. */
    fun shift(ref: Ref, dCol: Int, dRow: Int): Ref {
        val col = if (ref.colAbs) ref.col else ref.col + dCol
        val row = if (ref.rowAbs) ref.row else ref.row + dRow
        require(col in 1..ColLetters.MAX_COLUMN) { "shift leaves the grid (column $col)" }
        require(row in 1..SheetRange.MAX_ROW) { "shift leaves the grid (row $row)" }
        return Ref(col, row, ref.colAbs, ref.rowAbs)
    }
}
