package dev.fusion.lens

/**
 * UPC-E <-> UPC-A expansion (GS1 zero-suppression: the 6-digit body
 * expands by the last-digit rule — 0/1/2: mfr = d1 d2 lastdigit,
 * product = 00 d3 d4 d5? No: canonical table below; 3: mfr d1..d3,
 * product 000 d4 d5; 4: mfr d1..d4, product 0000 d5; 5-9: mfr
 * d1..d5, product 0000 lastdigit). Check digit carries over from the
 * expanded UPC-A ([Ean13] computes it on the 0-prefixed form).
 *
 * Declared narrowings: number system 0 only (NS 1 exists; refused by
 * name); imaging stays out (same stance as Ean13/QrVersion).
 */
object UpcE {
    const val SCOPE = "Zero-suppression expansion table, NS 0 only; check digits ride on Ean13's algebra."

    /** Expand a 6-digit UPC-E body to the 11-digit UPC-A body (no check digit). */
    fun expandBody(body6: String): String {
        require(body6.length == 6 && body6.all { it.isDigit() }) { "need exactly 6 digits" }
        val d = body6.map { it - '0' }
        return when (val last = d[5]) {
            0, 1, 2 -> "0" + d[0] + d[1] + last + "0000" + d[2] + d[3] + d[4]
            3 -> "0" + d[0] + d[1] + d[2] + "00000" + d[3] + d[4]
            4 -> "0" + d[0] + d[1] + d[2] + d[3] + "00000" + d[4]
            else -> "0" + d[0] + d[1] + d[2] + d[3] + d[4] + "0000" + last
        }
    }

    /** Full UPC-E (NS 0 + body + check) to full 12-digit UPC-A. */
    fun toUpcA(upcE8: String): String {
        require(upcE8.length == 8 && upcE8.all { it.isDigit() }) { "UPC-E is 8 digits (NS+body+check)" }
        require(upcE8[0] == '0') { "number system " + upcE8[0] + " refused (NS 0 only)" }
        val expanded = expandBody(upcE8.substring(1, 7))
        val check = Ean13.checkDigit("0" + expanded.substring(0, 11))
        require(check == upcE8[7] - '0') { "UPC-E check digit disagrees with the expanded form" }
        return expanded.substring(0, 11) + check
    }
}
