package dev.fusion.editor

/**
 * Go-to-line input parsing (`12`, `12:34`, `:34`, `+5`, `-3`) with
 * document-bounds clamping reported, never silent: the result carries
 * both the REQUESTED and the CLAMPED positions so the status bar can
 * say "line 999 clamped to 120".
 *
 * Declared narrowings: 1-based line/column convention; relative moves
 * need the current line; empty input refuses (a no-op go-to is a UI
 * bug worth surfacing).
 */
object GoToLine {
    const val SCOPE = "line[:col] and +/-rel parsing with clamping REPORTED; 1-based; empty input refuses."

    data class Target(val requestedLine: Int, val line: Int, val column: Int, val clamped: Boolean)

    fun parse(input: String, currentLine: Int, lineCount: Int): Target {
        require(lineCount >= 1) { "empty document" }
        require(currentLine in 1..lineCount) { "current line out of bounds" }
        val s = input.trim()
        require(s.isNotEmpty()) { "empty go-to input" }
        var linePart = s
        var col = 1
        val colon = s.indexOf(':')
        if (colon >= 0) {
            linePart = s.substring(0, colon)
            val colText = s.substring(colon + 1)
            require(colText.isNotEmpty() && colText.all { it.isDigit() }) { "column '" + colText + "' malformed" }
            col = colText.toInt()
            require(col >= 1) { "column is 1-based" }
        }
        val requested = when {
            linePart.isEmpty() -> currentLine
            linePart.startsWith("+") -> currentLine + parseNum(linePart.substring(1))
            linePart.startsWith("-") -> currentLine - parseNum(linePart.substring(1))
            else -> parseNum(linePart).also { require(it >= 1) { "line is 1-based" } }
        }
        val clampedLine = requested.coerceIn(1, lineCount)
        return Target(requested, clampedLine, col, clampedLine != requested)
    }

    private fun parseNum(s: String): Int {
        require(s.isNotEmpty() && s.all { it.isDigit() } && s.length <= 9) { "number '" + s + "' malformed" }
        return s.toInt()
    }
}
