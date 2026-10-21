// SPDX-License-Identifier: Apache-2.0
package dev.fusion.terminal

/**
 * R28 EXECUTED host kernel. Bounded terminal scrollback: a ring of printable
 * lines with exact greedy reflow on width change. No PTY, no escape-sequence
 * interpretation, no shell — storage + wrapping only. Controls (other than
 * the newline that separates pushes) are refused, never silently stripped.
 */
class Scrollback private constructor(private val lines: List<String>, val truncatedCols: Long) {
    companion object {
        const val MAX_LINES = 2000
        const val MAX_COLS = 256
        const val MIN_WIDTH = 8

        fun empty(): Scrollback = Scrollback(emptyList(), 0L)

        private fun checkLine(line: String) {
            require('\t' !in line && line.all { it == '\n' || (it.code in 0x20..0x7e) || it.code >= 0xa0 }) {
                "Scrollback lines must be printable (no tabs or C0/C1 controls)"
            }
        }
    }

    fun size(): Int = lines.size
    fun line(i: Int): String = lines[i]

    /** Append [text], splitting on '\n'. Over-long lines truncate at MAX_COLS and count. */
    fun push(text: String): Scrollback {
        require(text.length <= 65536) { "Push exceeds 64 KiB" }
        var dropped = truncatedCols
        val added = text.split('\n').map { raw ->
            checkLine(raw)
            if (raw.length > MAX_COLS) {
                dropped += (raw.length - MAX_COLS).toLong()
                raw.take(MAX_COLS)
            } else raw
        }
        return Scrollback((lines + added).takeLast(MAX_LINES), dropped)
    }

    /**
     * Greedy reflow to [width] columns: every stored line rewraps; empty lines
     * survive as empty lines. Reflow is exact and idempotent at a fixed width.
     */
    fun reflow(width: Int): Scrollback {
        require(width in MIN_WIDTH..MAX_COLS) { "Width must be $MIN_WIDTH..$MAX_COLS" }
        val out = mutableListOf<String>()
        for (line in lines) {
            if (line.isEmpty()) {
                out.add("")
                continue
            }
            var rest = line
            while (rest.length > width) {
                out.add(rest.take(width))
                rest = rest.drop(width)
            }
            out.add(rest)
        }
        return Scrollback(out.takeLast(MAX_LINES), truncatedCols)
    }

    /** Last [rows] lines (the visible window). */
    fun window(rows: Int): List<String> {
        require(rows in 1..MAX_LINES) { "rows out of range" }
        return lines.takeLast(rows)
    }
}
