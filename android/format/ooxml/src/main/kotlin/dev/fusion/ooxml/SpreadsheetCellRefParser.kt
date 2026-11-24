// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Bounded A1-style spreadsheet cell reference parser. Parses references like
 * `A1`, `$B$12`, `XFD1048576` into 0-based column and row indices within
 * Excel bounds ([MAX_COL_INDEX] = 16383, [MAX_ROW_INDEX] = 1048575).
 */
class SpreadsheetCellRefParser private constructor() {
    companion object {
        const val MAX_COL_INDEX = 16383 // XFD
        const val MAX_ROW_INDEX = 1048575 // 1048576 - 1
        private val REF_RE = Regex("^(\\\$?)([A-Z]{1,3})(\\\$?)([1-9][0-9]{0,6})$")

        data class CellRef(
            val col: Int,
            val row: Int,
            val colAbsolute: Boolean,
            val rowAbsolute: Boolean
        )

        fun parse(ref: String): CellRef {
            val m = REF_RE.matchEntire(ref) ?: throw IllegalArgumentException("invalid cell ref: $ref")
            val colAbs = m.groupValues[1] == "$"
            val colLetters = m.groupValues[2]
            val rowAbs = m.groupValues[3] == "$"
            val rowNum = m.groupValues[4].toInt()

            var col = 0
            for (ch in colLetters) {
                col = col * 26 + (ch - 'A' + 1)
            }
            col -= 1
            val row = rowNum - 1
            require(col in 0..MAX_COL_INDEX) { "column out of bounds: $colLetters" }
            require(row in 0..MAX_ROW_INDEX) { "row out of bounds: $rowNum" }
            return CellRef(col, row, colAbs, rowAbs)
        }
    }
}
