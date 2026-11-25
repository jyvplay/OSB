// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Production-grade OOXML Excel A1-style cell coordinate decoder (`A1`..`XFD1048576`).
 */
class OoxmlCellRefDecoder private constructor() {
    companion object {
        const val MAX_COL = 16_383 // XFD (0-based)
        const val MAX_ROW = 1_048_575 // 1048576 (0-based)

        fun decode(cellRef: String): Pair<Int, Int> {
            val m = Regex("^([A-Za-z]{1,3})(\\d{1,7})$").matchEntire(cellRef.trim())
                ?: throw IllegalArgumentException("invalid cellRef: $cellRef")
            val colStr = m.groupValues[1].uppercase()
            val row1 = m.groupValues[2].toInt()
            require(row1 in 1..(MAX_ROW + 1)) { "row out of bounds: $row1" }

            var col = 0
            for (ch in colStr) {
                col = col * 26 + (ch - 'A' + 1)
            }
            val col0 = col - 1
            require(col0 in 0..MAX_COL) { "col out of bounds: $col0" }
            return (row1 - 1) to col0
        }
    }
}
