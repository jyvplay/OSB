// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

/**
 * R146 — bounded natural-duration parser ("in 90s", "1h30m", "2 d 4 h").
 *
 * Accepts an optional `in ` prefix followed by one or more number+unit pairs
 * in any order, each pair optionally separated by spaces, and returns the
 * total in whole milliseconds. Units: `ms`, `s`, `m`, `h`, `d`, `w`. The
 * parser is deliberately locale-free English token matching — it never
 * guesses at words it does not know, and an unknown token refuses instead of
 * being skipped.
 *
 * Declared narrowings: at most [MAX_COMPONENTS] components; each number
 * 0..[MAX_COMPONENT]; total <= [MAX_TOTAL_MS] (30 days); negative numbers
 * and fractional values refused; `in ` prefix appears at most once and only
 * at the start; all whitespace is removed before tokenising, so `1h 30m`
 * and `1h30m` are the same phrase.
 */
object DurationPhraseParser {
    const val SCOPE =
        "Deterministic number+unit duration phrase folding to whole milliseconds; no locale, no calendar, no clock."

    const val MAX_COMPONENTS = 8
    const val MAX_COMPONENT = 1_000_000_000L
    const val MAX_TOTAL_MS = 30L * 86_400_000L

    private val UNIT_MS = mapOf(
        "ms" to 1L,
        "s" to 1_000L,
        "m" to 60_000L,
        "h" to 3_600_000L,
        "d" to 86_400_000L,
        "w" to 604_800_000L,
    )

    fun parse(phrase: String): Long {
        require(phrase.length <= 64) { "phrase too long" }
        var rest = phrase.trim()
        if (rest.startsWith("in ")) {
            rest = rest.substring(3).trim()
        }
        require(rest.isNotEmpty()) { "empty duration phrase" }
        val compact = rest.replace(" ", "")
        require(compact.isNotEmpty()) { "empty duration phrase" }

        var total = 0L
        var components = 0
        var i = 0
        while (i < compact.length) {
            val digitStart = i
            while (i < compact.length && compact[i] in '0'..'9') i++
            require(i > digitStart) { "expected a number at position $digitStart" }
            val numberText = compact.substring(digitStart, i)
            require(numberText.length <= 10) { "number too long" }
            val number = numberText.toLongOrNull()
                ?: throw IllegalArgumentException("number overflow")
            require(number in 0..MAX_COMPONENT) { "component out of range" }
            val unitStart = i
            while (i < compact.length && compact[i] !in '0'..'9') i++
            require(i > unitStart) { "expected a unit after $numberText" }
            val unit = compact.substring(unitStart, i)
            val factor = UNIT_MS[unit] ?: throw IllegalArgumentException("unknown unit: $unit")
            total = try {
                Math.addExact(total, Math.multiplyExact(number, factor))
            } catch (e: ArithmeticException) {
                throw IllegalArgumentException("duration overflows", e)
            }
            require(total <= MAX_TOTAL_MS) { "duration over 30 days" }
            components++
            require(components <= MAX_COMPONENTS) { "too many components" }
        }
        return total
    }
}
