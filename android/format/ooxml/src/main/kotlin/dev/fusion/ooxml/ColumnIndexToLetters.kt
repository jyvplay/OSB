// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Bounded 0-based spreadsheet column index [0, 16383] to Excel letters (`A`..`XFD`).
 */
class ColumnIndexToLetters private constructor() {
    companion object {
        const val MAX_COL_INDEX = 16383

        fun toLetters(colIndex: Int): String {
            require(colIndex in 0..MAX_COL_INDEX) { "colIndex out of bounds: $colIndex" }
            val sb = StringBuilder()
            var n = colIndex + 1
            while (n > 0) {
                val rem = (n - 1) % 26
                sb.append(('A'.code + rem).toChar())
                n = (n - 1) / 26
            }
            return sb.reverse().toString()
        }
    }
}
