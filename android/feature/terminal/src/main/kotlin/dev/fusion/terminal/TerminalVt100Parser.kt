// SPDX-License-Identifier: Apache-2.0
package dev.fusion.terminal

/**
 * Production-grade bounded ANSI/VT100 CSI escape sequence parser &
 * screen-buffer cursor engine (`rows x cols` character grid).
 */
class TerminalVt100Parser private constructor(
    val rows: Int,
    val cols: Int
) {
    companion object {
        fun create(rows: Int = 24, cols: Int = 80): TerminalVt100Parser {
            require(rows in 1..256 && cols in 1..512) { "invalid terminal dimensions" }
            return TerminalVt100Parser(rows, cols)
        }
    }

    private val grid = Array(rows) { CharArray(cols) { ' ' } }
    var cursorRow = 0
        private set
    var cursorCol = 0
        private set

    fun feed(input: String) {
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            when {
                ch == '\u001B' && i + 1 < input.length && input[i + 1] == '[' -> {
                    i += 2
                    val start = i
                    while (i < input.length && input[i] !in '@'..'~') i++
                    if (i < input.length) {
                        val params = input.substring(start, i)
                        val cmd = input[i]
                        handleCsi(params, cmd)
                    }
                }
                ch == '\r' -> cursorCol = 0
                ch == '\n' -> {
                    cursorCol = 0
                    if (cursorRow < rows - 1) cursorRow++
                }
                ch == '\b' -> if (cursorCol > 0) cursorCol--
                ch == '\t' -> cursorCol = ((cursorCol / 8 + 1) * 8).coerceAtMost(cols - 1)
                ch >= ' ' -> {
                    grid[cursorRow][cursorCol] = ch
                    if (cursorCol < cols - 1) cursorCol++
                }
            }
            i++
        }
    }

    private fun handleCsi(params: String, cmd: Char) {
        val parts = if (params.isEmpty()) emptyList() else params.split(';').map { it.toIntOrNull() ?: 0 }
        val p0 = parts.getOrElse(0) { 1 }.coerceAtLeast(1)
        when (cmd) {
            'A' -> cursorRow = (cursorRow - p0).coerceAtLeast(0)
            'B' -> cursorRow = (cursorRow + p0).coerceAtMost(rows - 1)
            'C' -> cursorCol = (cursorCol + p0).coerceAtMost(cols - 1)
            'D' -> cursorCol = (cursorCol - p0).coerceAtLeast(0)
            'H', 'f' -> {
                val r = parts.getOrElse(0) { 1 }.coerceIn(1, rows) - 1
                val c = parts.getOrElse(1) { 1 }.coerceIn(1, cols) - 1
                cursorRow = r
                cursorCol = c
            }
            'J' -> if (parts.getOrElse(0) { 0 } == 2) {
                for (r in 0 until rows) grid[r].fill(' ')
                cursorRow = 0
                cursorCol = 0
            }
        }
    }

    fun lineText(row: Int): String {
        require(row in 0 until rows) { "row out of bounds" }
        return String(grid[row]).trimEnd()
    }
}
