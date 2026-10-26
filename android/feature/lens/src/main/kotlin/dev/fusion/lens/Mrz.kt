package dev.fusion.lens

/**
 * Machine-readable-zone check digits (ICAO Doc 9303 Part 3 §4.9): weights
 * 7-3-1 repeating; characters value 0-9 as themselves, A-Z as 10-35, the
 * filler `<` as 0; the check digit is the weighted sum mod 10.
 *
 * TD3 (passport) line 2 layout validated here: document number [0..9) +
 * check [9], nationality [10..13), birth date [13..19) + check [19],
 * sex [20], expiry [21..27) + check [27], personal number [28..42) +
 * check [42], composite check [43] over positions 0..10 + 13..20 +
 * 21..43 (the §4.9 composite span).
 *
 * Declared narrowings: check-digit math and TD3-line-2 structure only —
 * no OCR, no date plausibility beyond digit shape, no TD1/TD2 layouts
 * yet, and a validation PASS is never an identity claim (the lens
 * surface shows "digits consistent", nothing more).
 */
object Mrz {
    const val SCOPE = "ICAO 9303 7-3-1 check digits + TD3 line-2 structure; no OCR, no identity claims."
    private val WEIGHTS = intArrayOf(7, 3, 1)

    fun value(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'A'..'Z' -> c - 'A' + 10
        '<' -> 0
        else -> throw IllegalArgumentException("'$c' is not an MRZ character (0-9, A-Z, <)")
    }

    fun checkDigit(field: String): Int {
        require(field.isNotEmpty()) { "empty MRZ field" }
        var sum = 0
        for (i in field.indices) sum += value(field[i]) * WEIGHTS[i % 3]
        return sum % 10
    }

    fun verify(field: String, digit: Char): Boolean {
        require(digit in '0'..'9') { "check digit '$digit' is not a digit" }
        return checkDigit(field) == digit - '0'
    }

    data class Td3(
        val documentNumber: String, val nationality: String, val birth: String,
        val sex: Char, val expiry: String, val personalNumber: String
    )

    fun validateTd3Line2(line: String): Td3 {
        require(line.length == 44) { "TD3 line 2 must be 44 characters, got ${line.length}" }
        line.forEach { value(it) }  // charset gate
        require(verify(line.substring(0, 9), line[9])) { "document-number check digit fails" }
        require(verify(line.substring(13, 19), line[19])) { "birth-date check digit fails" }
        require(verify(line.substring(21, 27), line[27])) { "expiry-date check digit fails" }
        require(verify(line.substring(28, 42), line[42])) { "personal-number check digit fails" }
        val composite = line.substring(0, 10) + line.substring(13, 20) + line.substring(21, 43)
        require(verify(composite, line[43])) { "composite check digit fails" }
        require(line[20] in charArrayOf('M', 'F', '<')) { "sex field '${line[20]}'" }
        return Td3(
            documentNumber = line.substring(0, 9).trimEnd('<'),
            nationality = line.substring(10, 13),
            birth = line.substring(13, 19),
            sex = line[20],
            expiry = line.substring(21, 27),
            personalNumber = line.substring(28, 42).trimEnd('<')
        )
    }
}
