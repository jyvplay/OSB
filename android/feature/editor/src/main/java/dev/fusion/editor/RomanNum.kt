package dev.fusion.editor

/**
 * Roman numerals for writer list numbering (ECMA-376 `upperRoman` /
 * `lowerRoman` numFmt values render exactly these strings).
 *
 * Contract: strict canonical subtractive form only — the unique minimal
 * spelling using M CM D CD C XC L XL X IX V IV I. Range 1..3999 (no bar
 * notation). `from` accepts ONLY the canonical uppercase spelling; additive
 * variants (IIII), illegal subtractions (IM, VX), lowercase and empty input
 * are refused. Lowercase output is a presentation transform of `to`.
 */
object RomanNum {
    const val SCOPE = "Canonical subtractive Roman numerals 1..3999 only; additive/clock variants refused."
    private val VALUES = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    private val GLYPHS = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")

    fun to(n: Int): String {
        require(n in 1..3999) { "value $n outside 1..3999" }
        val sb = StringBuilder()
        var rest = n
        for (i in VALUES.indices) {
            while (rest >= VALUES[i]) { sb.append(GLYPHS[i]); rest -= VALUES[i] }
        }
        return sb.toString()
    }

    fun from(s: String): Int {
        require(s.isNotEmpty()) { "empty numeral" }
        require(s.all { it in "MDCLXVI" }) { "non-Roman character in '$s'" }
        var i = 0
        var total = 0
        var gi = 0
        while (i < s.length) {
            while (gi < GLYPHS.size && !s.startsWith(GLYPHS[gi], i)) gi++
            require(gi < GLYPHS.size) { "'$s' is not in canonical order" }
            var reps = 0
            while (s.startsWith(GLYPHS[gi], i)) {
                i += GLYPHS[gi].length; total += VALUES[gi]; reps++
                val maxReps = if (GLYPHS[gi].length == 2 || GLYPHS[gi] in arrayOf("D", "L", "V")) 1 else 3
                require(reps <= maxReps) { "'$s' repeats ${GLYPHS[gi]} beyond canonical form" }
            }
            gi++
        }
        require(to(total) == s) { "'$s' is not the canonical spelling of $total" }
        return total
    }

    fun toLower(n: Int): String = to(n).lowercase()
}
