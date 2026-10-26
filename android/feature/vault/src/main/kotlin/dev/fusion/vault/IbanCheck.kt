package dev.fusion.vault

/**
 * IBAN validation per ISO 13616 (mod-97-10 check as profiled by the SWIFT
 * IBAN Registry; ISO 7064 underneath).
 *
 * Algorithm: move the first four characters to the end, map A..Z to 10..35,
 * and the resulting integer must satisfy n mod 97 == 1. Check digits 00,
 * 01 and 99 can never occur in a valid IBAN (registry rule). Electronic
 * format has no separators; paper format groups by four.
 *
 * Declared narrowings: country lengths are enforced for an explicit
 * 12-entry registry subset; other country codes are REFUSED (not passed
 * through) so a wrong length can never slip by as "unknown country". The
 * full 80+-entry registry is data, not logic — extending the table is a
 * follow-up, and the refusal names it. BBAN national formats beyond length
 * are out of scope. ISO 13616 itself is paywalled; the SWIFT registry
 * publishes the normative lengths used here.
 */
object IbanCheck {
    const val SCOPE = "mod-97 + length for a declared 12-country subset; unknown countries refused, BBAN formats out of scope."

    val LENGTHS = mapOf(
        "BE" to 16, "CH" to 21, "DE" to 22, "DK" to 18, "ES" to 24, "FR" to 27,
        "GB" to 22, "IE" to 22, "IT" to 27, "NL" to 18, "NO" to 15, "SE" to 24
    )

    fun electronic(raw: String): String = raw.filter { !it.isWhitespace() }.uppercase()

    fun mod97(iban: String): Int {
        val rearranged = iban.substring(4) + iban.substring(0, 4)
        var rem = 0
        for (c in rearranged) {
            rem = when (c) {
                in '0'..'9' -> (rem * 10 + (c - '0')) % 97
                in 'A'..'Z' -> (rem * 100 + (c - 'A' + 10)) % 97
                else -> throw IllegalArgumentException("'$c' is not valid in an IBAN")
            }
        }
        return rem
    }

    fun validate(raw: String): String {
        val iban = electronic(raw)
        require(iban.length >= 5) { "IBAN too short" }
        val cc = iban.substring(0, 2)
        require(cc.all { it in 'A'..'Z' }) { "country code '$cc' is not alphabetic" }
        val expected = LENGTHS[cc] ?: throw IllegalArgumentException(
            "country '$cc' is outside this kernel's declared registry subset — refused, not guessed"
        )
        require(iban.length == expected) { "$cc IBAN must be $expected chars, got ${iban.length}" }
        val check = iban.substring(2, 4)
        require(check.all { it.isDigit() }) { "check digits '$check' are not numeric" }
        require(check != "00" && check != "01" && check != "99") { "check digits $check are impossible per the registry" }
        require(mod97(iban) == 1) { "mod-97 check failed" }
        return iban
    }

    fun isValid(raw: String): Boolean = try { validate(raw); true } catch (_: IllegalArgumentException) { false }

    /** Compute the two check digits for a country + BBAN. */
    fun checkDigits(cc: String, bban: String): String {
        require(cc.length == 2 && cc.all { it in 'A'..'Z' }) { "country '$cc'" }
        require(LENGTHS.containsKey(cc)) { "country '$cc' outside the declared subset" }
        val rem = mod97(cc + "00" + bban.uppercase().also { b -> require(b.isNotEmpty() && b.all { it.isLetterOrDigit() }) { "BBAN '$bban'" } })
        val digits = 98 - rem
        return "%02d".format(digits)
    }

    fun paper(raw: String): String = electronic(raw).chunked(4).joinToString(" ")
}
