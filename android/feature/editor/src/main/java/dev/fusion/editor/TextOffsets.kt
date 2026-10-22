// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Plain-text offset math: line starts, offset to line/column, word bounds.
 * Lines split on `\n` (a `\r` before `\n` counts as line content, matching
 * the editor's visible caret columns); columns and offsets are UTF-16 code
 * units, documented rather than hidden. Caps input at 1M chars.
 */
object TextOffsets {
    const val MAX_CHARS = 1_000_000

    private fun checkText(text: String) = require(text.length <= MAX_CHARS) {
        "text over $MAX_CHARS chars"
    }

    /** Start offset of every line, always beginning with 0. */
    fun lineStarts(text: String): IntArray {
        checkText(text)
        val starts = mutableListOf(0)
        text.forEachIndexed { i, c -> if (c == '\n') starts += i + 1 }
        return starts.toIntArray()
    }

    /** 0-based (line, column) for [offset] in 0..length. */
    fun toLineCol(text: String, offset: Int): Pair<Int, Int> {
        checkText(text)
        require(offset in 0..text.length) { "offset $offset outside 0..${text.length}" }
        val starts = lineStarts(text)
        var line = 0
        while (line + 1 < starts.size && starts[line + 1] <= offset) line++
        return line to (offset - starts[line])
    }

    /** Offset for a 0-based (line, column); columns clamp to the line end. */
    fun toOffset(text: String, line: Int, col: Int): Int {
        checkText(text)
        require(line >= 0 && col >= 0) { "negative line/col" }
        val starts = lineStarts(text)
        require(line < starts.size) { "line $line outside 0..${starts.size - 1}" }
        val end = if (line + 1 < starts.size) starts[line + 1] - 1 else text.length
        return (starts[line] + col).coerceAtMost(end)
    }

    /**
     * `[start, end)` of the `[\p{L}\p{N}_]+` run under [offset], or null when
     * the probe lands on a non-word char. At offset == length the last char
     * is probed; empty text yields null.
     */
    fun wordBounds(text: String, offset: Int): Pair<Int, Int>? {
        checkText(text)
        require(offset in 0..text.length) { "offset $offset outside 0..${text.length}" }
        if (text.isEmpty()) return null
        val probe = if (offset == text.length) offset - 1 else offset
        if (!isWord(text[probe])) return null
        var s = probe
        while (s > 0 && isWord(text[s - 1])) s--
        var e = probe + 1
        while (e < text.length && isWord(text[e])) e++
        return s to e
    }

    private fun isWord(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
}
