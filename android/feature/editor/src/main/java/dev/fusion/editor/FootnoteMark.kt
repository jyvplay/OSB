package dev.fusion.editor

/**
 * Footnote reference marks for the writer surface.
 *
 * Symbol style follows the traditional sequence as codified by the
 * Chicago Manual of Style: * † ‡ § ‖ ¶, doubling for the second cycle
 * (** †† …), tripling for the third. Superscript-digit style maps 0-9 to
 * the Unicode superscript code points (note ¹ ² ³ live outside the
 * U+2070 block — that irregularity is exactly what the tests pin).
 *
 * Declared narrowings: two styles only (symbols, superscript digits);
 * letter styles belong to list numbering, not footnotes, and are refused
 * by absence. Symbol marks cap at 18 (three cycles) — a document with
 * more symbol footnotes than that must switch styles, loudly.
 */
object FootnoteMark {
    const val SCOPE = "CMOS symbol cycle (cap 18) + Unicode superscript digits; no letter styles."
    private val SYMBOLS = arrayOf("*", "\u2020", "\u2021", "\u00A7", "\u2016", "\u00B6")
    private val SUPER = charArrayOf('\u2070', '\u00B9', '\u00B2', '\u00B3', '\u2074', '\u2075', '\u2076', '\u2077', '\u2078', '\u2079')

    fun symbol(n: Int): String {
        require(n in 1..18) { "symbol mark $n outside 1..18 (three cycles of six)" }
        val tier = (n - 1) / 6 + 1
        val sym = SYMBOLS[(n - 1) % 6]
        return sym.repeat(tier)
    }

    fun superscript(n: Int): String {
        require(n >= 1) { "footnote number $n must be positive" }
        return n.toString().map { SUPER[it - '0'] }.joinToString("")
    }

    /** Inverse of [superscript]; refuses non-superscript characters. */
    fun fromSuperscript(s: String): Int {
        require(s.isNotEmpty()) { "empty mark" }
        val digits = s.map {
            val d = SUPER.indexOf(it)
            require(d >= 0) { "'$it' is not a superscript digit" }
            d
        }
        require(!(digits.size > 1 && digits[0] == 0)) { "leading superscript zero" }
        return digits.fold(0) { acc, d -> acc * 10 + d }
    }
}
