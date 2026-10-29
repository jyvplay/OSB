// SPDX-License-Identifier: Apache-2.0
package dev.fusion.terminal

/**
 * ECMA-48 / ITU-T T.416 **24-bit Truecolor SGR** (`ESC[38;2;R;G;Bm`, `ESC[48;2;R;G;Bm`, bold,
 * underline, reset) terminal parser + bounded scrollback grid for Fusion Terminal.
 */
object AnsiSgrTruecolorScrollbackBuffer {
    const val SCOPE =
        "ECMA-48 / T.416 24-bit RGB SGR (38;2;r;g;b / 48;2;r;g;b) terminal escape parser and bounded cell scrollback buffer."

    data class StyledCell(
        val ch: Char = ' ',
        val fgRgb24: Int = 0xE6EDF3,
        val bgRgb24: Int = 0x0D1117,
        val bold: Boolean = false,
        val underline: Boolean = false,
    )

    class TerminalGrid(val cols: Int = 80, val maxScrollbackLines: Int = 500) {
        private val lines = ArrayDeque<Array<StyledCell>>()
        private var curRow = Array(cols) { StyledCell() }
        private var cursorCol = 0
        private var fg = 0xE6EDF3
        private var bg = 0x0D1117
        private var bold = false
        private var underline = false

        fun feed(ansiStream: String) {
            var i = 0
            while (i < ansiStream.length) {
                val c = ansiStream[i]
                if (c == '\u001B' && i + 1 < ansiStream.length && ansiStream[i + 1] == '[') {
                    val mIdx = ansiStream.indexOf('m', i + 2)
                    if (mIdx > i + 1) {
                        applySgr(ansiStream.substring(i + 2, mIdx))
                        i = mIdx + 1
                        continue
                    }
                }
                when (c) {
                    '\r' -> cursorCol = 0
                    '\n' -> pushLine()
                    else -> {
                        if (cursorCol >= cols) pushLine()
                        curRow[cursorCol++] = StyledCell(c, fg, bg, bold, underline)
                    }
                }
                i++
            }
        }

        fun snapshotVisibleLines(): List<List<StyledCell>> =
            (lines.map { it.toList() } + listOf(curRow.toList()))

        private fun pushLine() {
            lines.addLast(curRow)
            while (lines.size > maxScrollbackLines) lines.removeFirst()
            curRow = Array(cols) { StyledCell() }
            cursorCol = 0
        }

        private fun applySgr(spec: String) {
            if (spec.isEmpty()) { resetSgr(); return }
            val parts = spec.split(';').map { it.toIntOrNull() ?: 0 }
            var k = 0
            while (k < parts.size) {
                when (parts[k]) {
                    0 -> resetSgr()
                    1 -> bold = true
                    4 -> underline = true
                    22 -> bold = false
                    24 -> underline = false
                    38 -> if (k + 4 < parts.size && parts[k + 1] == 2) {
                        fg = ((parts[k + 2].coerceIn(0, 255)) shl 16) or
                            ((parts[k + 3].coerceIn(0, 255)) shl 8) or
                            parts[k + 4].coerceIn(0, 255)
                        k += 4
                    }
                    48 -> if (k + 4 < parts.size && parts[k + 1] == 2) {
                        bg = ((parts[k + 2].coerceIn(0, 255)) shl 16) or
                            ((parts[k + 3].coerceIn(0, 255)) shl 8) or
                            parts[k + 4].coerceIn(0, 255)
                        k += 4
                    }
                }
                k++
            }
        }

        private fun resetSgr() { fg = 0xE6EDF3; bg = 0x0D1117; bold = false; underline = false }
    }
}
