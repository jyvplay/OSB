// SPDX-License-Identifier: Apache-2.0
package dev.fusion.terminal

/**
 * Clean-room ECMA-48 / VT100 2D Screen Grid & Bounded Scrollback Ring Buffer.
 *
 * Drives Fusion Terminal viewport state on JVM & Android Compose without native `libtermux` JNI:
 * - Processes printable UTF-16 BMP characters, `\r`, `\n`, `\b`, `\t` (8-column tab stops),
 *   and ANSI CSI sequences: cursor movement (`A`/`B`/`C`/`D`/`H`/`f`/`G`), erase in display (`J`:
 *   `0` below, `1` above, `2` all), erase in line (`K`: `0` to end, `1` from start, `2` whole line),
 *   and SGR attributes (`0` reset, `1` bold, `30..37` fg, `40..47` bg, `90..97` bright fg).
 * - Evicts scrolled-off top lines into a bounded `maxScrollbackLines` ring buffer so infinite
 *   `yes` output never exhausts heap memory!
 */
object VtScreenBuf {
    const val SCOPE =
        "ECMA-48 / VT100 2D terminal cell grid + bounded scrollback ring buffer (CSI cursor A/B/C/D/G/H, ED J, EL K, SGR 16-color/bold); alternate screen (?1049h) and mouse tracking out of scope."

    data class Style(
        val fgAnsi: Int = -1,
        val bgAnsi: Int = -1,
        val bold: Boolean = false,
    )

    data class Cell(val ch: Char = ' ', val style: Style = Style())

    class TerminalBuffer(
        val cols: Int,
        val rows: Int,
        val maxScrollbackLines: Int = 500,
    ) {
        init {
            require(cols in 4..512 && rows in 2..256) { "invalid terminal geometry ${cols}x$rows" }
            require(maxScrollbackLines in 0..10_000) { "invalid maxScrollbackLines $maxScrollbackLines" }
        }

        var cursorCol: Int = 0
            private set
        var cursorRow: Int = 0
            private set
        var currentStyle: Style = Style()
            private set

        private val grid: Array<Array<Cell>> = Array(rows) { Array(cols) { Cell() } }
        private val scrollback = ArrayDeque<String>()

        val scrollbackLines: List<String> get() = scrollback.toList()

        fun feed(input: String) {
            var i = 0
            while (i < input.length) {
                when (val c = input[i]) {
                    '\u001B' -> {
                        if (i + 1 < input.length && input[i + 1] == '[') {
                            i = processCsi(input, i + 2)
                            continue
                        }
                        i++
                    }
                    '\r' -> { cursorCol = 0; i++ }
                    '\n' -> { lineFeed(); i++ }
                    '\b' -> { if (cursorCol > 0) cursorCol--; i++ }
                    '\t' -> {
                        val nextStop = ((cursorCol / 8) + 1) * 8
                        cursorCol = nextStop.coerceAtMost(cols - 1)
                        i++
                    }
                    else -> {
                        if (c.code >= 0x20) putChar(c)
                        i++
                    }
                }
            }
        }

        fun cellAt(row: Int, col: Int): Cell = grid[row][col]

        fun renderScreenLines(): List<String> =
            grid.map { row -> row.joinToString("") { it.ch.toString() }.trimEnd() }

        private fun putChar(c: Char) {
            if (cursorCol >= cols) {
                cursorCol = 0
                lineFeed()
            }
            grid[cursorRow][cursorCol] = Cell(c, currentStyle)
            cursorCol++
        }

        private fun lineFeed() {
            if (cursorRow + 1 < rows) {
                cursorRow++
            } else {
                if (maxScrollbackLines > 0) {
                    if (scrollback.size >= maxScrollbackLines) scrollback.removeFirst()
                    scrollback.addLast(grid[0].joinToString("") { it.ch.toString() }.trimEnd())
                }
                for (r in 1 until rows) grid[r - 1] = grid[r]
                grid[rows - 1] = Array(cols) { Cell() }
            }
        }

        private fun processCsi(s: String, start: Int): Int {
            var p = start
            while (p < s.length && s[p].code !in 0x40..0x7E) p++
            if (p >= s.length) return s.length
            val finalByte = s[p]
            val paramStr = s.substring(start, p).removePrefix("?")
            val params = if (paramStr.isEmpty()) emptyList() else paramStr.split(';').map { it.toIntOrNull() ?: 0 }

            fun p1(default: Int = 1): Int = (params.getOrNull(0) ?: default).let { if (it <= 0) default else it }

            when (finalByte) {
                'A' -> cursorRow = (cursorRow - p1()).coerceAtLeast(0)
                'B' -> cursorRow = (cursorRow + p1()).coerceAtMost(rows - 1)
                'C' -> cursorCol = (cursorCol + p1()).coerceAtMost(cols - 1)
                'D' -> cursorCol = (cursorCol - p1()).coerceAtLeast(0)
                'G' -> cursorCol = (p1() - 1).coerceIn(0, cols - 1)
                'H', 'f' -> {
                    val r = (params.getOrNull(0)?.takeIf { it > 0 } ?: 1) - 1
                    val c = (params.getOrNull(1)?.takeIf { it > 0 } ?: 1) - 1
                    cursorRow = r.coerceIn(0, rows - 1)
                    cursorCol = c.coerceIn(0, cols - 1)
                }
                'J' -> {
                    when (params.getOrElse(0) { 0 }) {
                        0 -> {
                            for (c in cursorCol until cols) grid[cursorRow][c] = Cell()
                            for (r in (cursorRow + 1) until rows) grid[r] = Array(cols) { Cell() }
                        }
                        1 -> {
                            for (r in 0 until cursorRow) grid[r] = Array(cols) { Cell() }
                            for (c in 0..cursorCol.coerceAtMost(cols - 1)) grid[cursorRow][c] = Cell()
                        }
                        2, 3 -> {
                            for (r in 0 until rows) grid[r] = Array(cols) { Cell() }
                        }
                    }
                }
                'K' -> {
                    when (params.getOrElse(0) { 0 }) {
                        0 -> for (c in cursorCol.coerceAtMost(cols - 1) until cols) grid[cursorRow][c] = Cell()
                        1 -> for (c in 0..cursorCol.coerceAtMost(cols - 1)) grid[cursorRow][c] = Cell()
                        2 -> grid[cursorRow] = Array(cols) { Cell() }
                    }
                }
                'm' -> {
                    val codes = if (params.isEmpty()) listOf(0) else params
                    for (code in codes) {
                        currentStyle = when (code) {
                            0 -> Style()
                            1 -> currentStyle.copy(bold = true)
                            22 -> currentStyle.copy(bold = false)
                            in 30..37, in 90..97 -> currentStyle.copy(fgAnsi = code)
                            39 -> currentStyle.copy(fgAnsi = -1)
                            in 40..47, in 100..107 -> currentStyle.copy(bgAnsi = code)
                            49 -> currentStyle.copy(bgAnsi = -1)
                            else -> currentStyle
                        }
                    }
                }
            }
            return p + 1
        }
    }
}
