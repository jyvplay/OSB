package dev.fusion.terminal

/**
 * CSI cursor-movement sequences (ECMA-48 / xterm: finals A-G for
 * relative moves, H for absolute position) — AnsiSgr owns the `m`
 * final; this kernel owns ONLY the cursor finals, keeping the two
 * dispatch tables disjoint by construction.
 *
 * Semantics: missing or 0 parameters default to 1 (ECMA-48 rule);
 * H takes `row;col`, 1-based; [apply] clamps to the screen bounds
 * (terminals clamp, they do not scroll on cursor moves).
 *
 * Declared narrowings: the seven finals below only; scroll regions,
 * tab stops (TabStops kernel) and save/restore are out of scope and
 * refused by final byte.
 */
object CsiCursor {
    const val SCOPE = "CSI A-H cursor finals with ECMA-48 defaults and clamping; SGR stays in AnsiSgr."

    data class Move(val final: Char, val a: Int, val b: Int)

    private val SEQ = Regex("\u001B\\[(\\d*)(?:;(\\d*))?([A-H])")

    fun parse(text: String): Move {
        val m = SEQ.matchEntire(text) ?: throw IllegalArgumentException("not a CSI cursor sequence")
        val final = m.groupValues[3][0]
        val a = m.groupValues[1].ifEmpty { "1" }.toInt().let { if (it == 0) 1 else it }
        val b = m.groupValues[2].ifEmpty { "1" }.toInt().let { if (it == 0) 1 else it }
        if (final != 'H') require(m.groupValues[2].isEmpty()) { "two parameters only valid for H" }
        return Move(final, a, b)
    }

    /** Apply to a 1-based (row, col) on a rows x cols screen, clamping. */
    fun apply(move: Move, row: Int, col: Int, rows: Int, cols: Int): Pair<Int, Int> {
        require(rows > 0 && cols > 0) { "screen ${rows}x$cols" }
        require(row in 1..rows && col in 1..cols) { "cursor ($row,$col) off screen" }
        fun cr(r: Int) = r.coerceIn(1, rows)
        fun cc(c: Int) = c.coerceIn(1, cols)
        return when (move.final) {
            'A' -> cr(row - move.a) to col
            'B' -> cr(row + move.a) to col
            'C' -> row to cc(col + move.a)
            'D' -> row to cc(col - move.a)
            'E' -> cr(row + move.a) to 1
            'F' -> cr(row - move.a) to 1
            'G' -> row to cc(move.a)
            'H' -> cr(move.a) to cc(move.b)
            else -> throw IllegalArgumentException("final '${move.final}' out of scope")
        }
    }
}
