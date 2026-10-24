package dev.fusion.editor

/**
 * Fixed tab-stop arithmetic for the editor's plain-text views
 * (complements IndentDetect, which DETECTS a file's style — this
 * kernel renders and measures once a tab size is chosen).
 *
 * Model: stops at every multiple of `tabSize` columns; a TAB at
 * column c advances to the next stop strictly greater than c.
 * [expand] replaces each TAB with the exact run of spaces that lands
 * on that stop, so expanded text has identical column geometry —
 * executed as the roundtrip property width(expand(s)) == width-with-
 * tabs(s).
 *
 * Declared narrowings: one-column characters only (wide-character
 * columns compose with the documents LineWidth kernel at the caller);
 * single LINES only — callers split on newlines first, and embedded
 * newlines refuse.
 */
object TabStops {
    const val SCOPE = "Fixed stops on one line of 1-column chars; detection is IndentDetect's job, widths LineWidth's."

    fun nextStop(column: Int, tabSize: Int): Int {
        require(column >= 0) { "negative column" }
        require(tabSize > 0) { "tab size $tabSize" }
        return (column / tabSize + 1) * tabSize
    }

    fun expand(line: String, tabSize: Int): String {
        require(tabSize > 0) { "tab size $tabSize" }
        require('\n' !in line && '\r' !in line) { "one line at a time — split on newlines first" }
        val sb = StringBuilder(line.length)
        var col = 0
        for (ch in line) {
            if (ch == '\t') {
                val target = nextStop(col, tabSize)
                repeat(target - col) { sb.append(' ') }
                col = target
            } else {
                sb.append(ch)
                col++
            }
        }
        return sb.toString()
    }

    /** Display width of a line whose TABs jump to stops. */
    fun width(line: String, tabSize: Int): Int {
        require('\n' !in line && '\r' !in line) { "one line at a time" }
        require(tabSize > 0) { "tab size $tabSize" }
        var col = 0
        for (ch in line) col = if (ch == '\t') nextStop(col, tabSize) else col + 1
        return col
    }

    /** Column of the character at [index], accounting for earlier TABs. */
    fun columnAt(line: String, index: Int, tabSize: Int): Int {
        require(index in 0..line.length) { "index $index outside the line" }
        return width(line.substring(0, index), tabSize)
    }
}
