package dev.fusion.ooxml

/**
 * SpreadsheetML column letters: bijective base-26 over A..Z.
 *
 * Primary text: ECMA-376 Part 1 §18.17.2 / A1-style references. Columns run
 * A..XFD, i.e. 1..16384 (2^14) in the current SpreadsheetML grid.
 *
 * Declared narrowings: letters only, no row component, no $ absolute marks,
 * no R1C1 style. Lowercase input is canonicalised to uppercase (A1 refs are
 * case-insensitive in SpreadsheetML); anything else is refused.
 */
object ColLetters {
    const val SCOPE = "Column letters A..XFD <-> 1..16384 only; rows, $, ranges and R1C1 are out of scope."
    const val MAX_COLUMN = 16384
    const val MAX_LETTERS = "XFD"

    fun toLetters(index: Int): String {
        require(index in 1..MAX_COLUMN) { "column index $index outside 1..$MAX_COLUMN" }
        var n = index
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A' + rem))
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    fun fromLetters(raw: String): Int {
        val s = raw.uppercase()
        require(s.isNotEmpty() && s.length <= 3) { "column letters '$raw' must be 1..3 letters" }
        require(s.all { it in 'A'..'Z' }) { "column letters '$raw' contain a non-letter" }
        var n = 0
        for (c in s) n = n * 26 + (c - 'A' + 1)
        require(n <= MAX_COLUMN) { "column '$raw' is beyond $MAX_LETTERS" }
        return n
    }
}
