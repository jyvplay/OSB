// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

/**
 * E.164 normalization for dial/SMS strings: visual separators stripped,
 * `00` converted to `+`, one trunk `0` stripped when an explicit default
 * country code is supplied. Grammar-only — there is no numbering-plan
 * database here, so local numbers without a default code are refused
 * rather than guessed. Output is canonical `+` plus 7..15 digits.
 */
object E164Format {
    const val MIN_DIGITS = 7
    const val MAX_DIGITS = 15
    private val CC = Regex("\\+[1-9][0-9]{0,2}")
    private val SEPS = setOf(' ', '-', '.', '(', ')', '/')

    fun normalize(raw: String, defaultCc: String? = null): String {
        require(raw.length in 1..64) { "input length ${raw.length} outside 1..64" }
        if (defaultCc != null) require(CC.matches(defaultCc)) { "bad defaultCc" }
        val t = raw.trim()
        require(t.isNotEmpty()) { "blank input" }
        val plus = t.startsWith('+')
        require(t.count { it == '+' } <= 1 && (!t.contains('+') || plus)) { "misplaced +" }
        val body = if (plus) t.drop(1) else t
        require(body.isNotEmpty() && body.all { it.isDigit() || it in SEPS }) { "bad characters" }
        val digits = body.filter { it.isDigit() }
        val intl = when {
            plus -> digits
            digits.startsWith("00") -> digits.drop(2)
            defaultCc != null -> {
                val local = if (digits.startsWith('0')) digits.drop(1) else digits
                defaultCc.drop(1) + local
            }
            else -> throw IllegalArgumentException("no country context")
        }
        require(intl.length in MIN_DIGITS..MAX_DIGITS) { "digits ${intl.length} outside $MIN_DIGITS..$MAX_DIGITS" }
        require(!intl.startsWith('0')) { "leading 0 after country code" }
        return "+$intl"
    }
}
