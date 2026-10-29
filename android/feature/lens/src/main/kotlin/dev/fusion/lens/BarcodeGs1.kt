// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

/**
 * Clean-room GS1 General Specifications Barcode Check-Digit Verifier (`EAN-8`, `UPC-A`, `EAN-13`,
 * `GTIN-14`, `SSCC-18`) & GS1-128 Application Identifier (`AI`) Element String Parser.
 *
 * Enables Lens camera scanning to verify retail/medical/logistics barcodes offline with zero
 * cloud lookup.
 */
object BarcodeGs1 {
    const val SCOPE =
        "GS1 Mod-10 check-digit calculator/validator (EAN-8/UPC-A/EAN-13/GTIN-14/SSCC-18) + parenthesized/FNC1 GS1-128 AI (01/10/11/17/21/30) parser; Reed-Solomon 2D QR pixel decoding out of scope."

    const val FNC1: Char = '\u001D' // ASCII GS (Group Separator, 0x1D)

    private val VALID_GTIN_LENGTHS = setOf(8, 12, 13, 14, 18)

    data class Gs1Parsed(val elements: Map<String, String>) {
        val gtin14: String? get() = elements["01"]
        val expiryYymmdd: String? get() = elements["17"]
        val batchLot: String? get() = elements["10"]
        val serial: String? get() = elements["21"]
    }

    /** Computes the GS1 Modulo-10 check digit for a `(N-1)`-digit numeric payload (`7, 11, 12, 13, 17` digits). */
    fun computeCheckDigit(payloadWithoutCheck: String): Char {
        require(payloadWithoutCheck.isNotEmpty() && payloadWithoutCheck.all { it.isDigit() }) {
            "GS1 payload must contain only ASCII digits: '$payloadWithoutCheck'"
        }
        require((payloadWithoutCheck.length + 1) in VALID_GTIN_LENGTHS) {
            "unsupported GS1 total length ${payloadWithoutCheck.length + 1}"
        }
        // Rightmost payload digit (immediately left of check digit) ALWAYS receives weight 3, alternating 3, 1, 3, 1...
        var sum = 0
        var weightThree = true
        for (i in payloadWithoutCheck.lastIndex downTo 0) {
            val d = payloadWithoutCheck[i] - '0'
            sum += if (weightThree) d * 3 else d
            weightThree = !weightThree
        }
        val check = (10 - (sum % 10)) % 10
        return ('0'.code + check).toChar()
    }

    fun isValidGs1Code(digits: String): Boolean {
        if (digits.length !in VALID_GTIN_LENGTHS || !digits.all { it.isDigit() }) return false
        return computeCheckDigit(digits.substring(0, digits.length - 1)) == digits.last()
    }

    fun normalizeToGtin14(code: String): String {
        require(code.length in setOf(8, 12, 13, 14) && isValidGs1Code(code)) {
            "invalid GTIN/EAN/UPC '$code'"
        }
        return code.padStart(14, '0')
    }

    /**
     * Parses human-readable `"(01)09506000134352(17)271231(10)LOT42(21)SN99"` or raw FNC1 `\u001D`-delimited
     * GS1-128 element strings, validating `(01)` GTIN-14 check digits and `(17)`/`(11)` `YYMMDD` calendar dates!
     */
    fun parseElementString(raw: String): Gs1Parsed {
        val s = raw.trim().removePrefix("]C1")
        require(s.isNotEmpty() && s.length <= 512) { "invalid GS1 element string length" }
        val out = LinkedHashMap<String, String>()

        if (s.startsWith("(")) {
            val re = Regex("""\((\d{2,4})\)([^()]+)""")
            var pos = 0
            for (m in re.findAll(s)) {
                require(m.range.first == pos) { "unparsed characters in GS1 string near offset $pos" }
                recordAi(out, m.groupValues[1], m.groupValues[2])
                pos = m.range.last + 1
            }
            require(pos == s.length) { "trailing unparsed characters in GS1 string" }
        } else {
            var i = 0
            while (i < s.length) {
                if (s[i] == FNC1) { i++; continue }
                require(i + 2 <= s.length) { "truncated GS1 AI at offset $i" }
                val ai2 = s.substring(i, i + 2)
                when (ai2) {
                    "01", "02" -> {
                        require(i + 16 <= s.length) { "truncated AI ($ai2) GTIN-14" }
                        recordAi(out, ai2, s.substring(i + 2, i + 16))
                        i += 16
                    }
                    "11", "15", "17" -> {
                        require(i + 8 <= s.length) { "truncated AI ($ai2) YYMMDD date" }
                        recordAi(out, ai2, s.substring(i + 2, i + 8))
                        i += 8
                    }
                    "10", "21", "30" -> {
                        val fnc = s.indexOf(FNC1, i + 2)
                        val end = if (fnc < 0) s.length else fnc
                        recordAi(out, ai2, s.substring(i + 2, end))
                        i = if (fnc < 0) s.length else fnc + 1
                    }
                    else -> throw IllegalArgumentException("unsupported unparenthesized GS1 AI '$ai2'")
                }
            }
        }
        require(out.isNotEmpty()) { "no GS1 Application Identifiers parsed" }
        return Gs1Parsed(out)
    }

    private fun recordAi(out: MutableMap<String, String>, ai: String, value: String) {
        require(ai !in out) { "duplicate GS1 AI ($ai)" }
        when (ai) {
            "01", "02" -> require(value.length == 14 && isValidGs1Code(value)) {
                "invalid GTIN-14 check digit in AI ($ai): '$value'"
            }
            "11", "15", "17" -> {
                require(value.length == 6 && value.all { it.isDigit() }) { "AI ($ai) date must be 6 digits YYMMDD" }
                val mm = value.substring(2, 4).toInt()
                val dd = value.substring(4, 6).toInt()
                require(mm in 1..12 && dd in 0..31) { "invalid month/day in AI ($ai) '$value'" }
            }
            "10", "21" -> require(value.length in 1..20) { "AI ($ai) length ${value.length} outside 1..20" }
            "30" -> require(value.length in 1..8 && value.all { it.isDigit() }) { "AI (30) count must be 1..8 digits" }
        }
        out[ai] = value
    }
}
