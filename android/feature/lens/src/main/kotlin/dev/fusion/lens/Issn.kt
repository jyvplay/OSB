package dev.fusion.lens

/**
 * ISSN check digits (ISO 3297: weights 8..2 over seven digits, check =
 * 11 - (sum mod 11), with 10 written as X — the mod-11 family, FIFTH
 * checksum algebra in this repo after Luhn, MRZ 7-3-1, GS1 1-3 and
 * Damm, each with different detection guarantees).
 *
 * Declared narrowings: ISSN-8 only; the hyphen is display (NNNN-NNNC);
 * ISSN-L linking and the EAN-13 "977" bridge are separate mappings,
 * named not implemented.
 */
object Issn {
    const val SCOPE = "ISO 3297 mod-11 check digits; display hyphen; 977-bridge and ISSN-L named, not implemented."

    fun checkChar(body7: String): Char {
        require(body7.length == 7 && body7.all { it.isDigit() }) { "need exactly 7 digits" }
        var sum = 0
        for (i in 0 until 7) sum += (body7[i] - '0') * (8 - i)
        val r = (11 - sum % 11) % 11
        return if (r == 10) 'X' else ('0' + r)
    }

    fun isValid(issn: String): Boolean {
        val s = issn.replace("-", "")
        if (s.length != 8) return false
        if (!s.substring(0, 7).all { it.isDigit() }) return false
        val c = s[7].uppercaseChar()
        if (!(c.isDigit() || c == 'X')) return false
        return checkChar(s.substring(0, 7)) == c
    }

    fun format(issn8: String): String {
        val s = issn8.replace("-", "").uppercase()
        require(s.length == 8 && isValid(s)) { "'" + issn8 + "' is not a valid ISSN" }
        return s.substring(0, 4) + "-" + s.substring(4)
    }
}
