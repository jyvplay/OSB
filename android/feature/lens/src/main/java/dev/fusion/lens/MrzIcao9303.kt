// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

/**
 * Clean-room ICAO Doc 9303 Machine Readable Zone (`MRZ`) parser & `[7, 3, 1]` check-digit verifier (`MrzIcao9303`).
 *
 * Powers `:feature:lens` 100% offline travel-document OCR verification (TD3 `2 x 44` passports and
 * TD1 `3 x 30` ID cards) without cloud OCR telemetry:
 * - ICAO Doc 9303 Part 3 §4.9 `[7, 3, 1]` mod-10 weighted checksum (`'<' = 0`, `'0'..'9' = 0..9`, `'A'..'Z' = 10..35`).
 * - Verifies document-number check digit, birth-date check digit, expiry-date check digit, AND the
 *   overall **composite check digit** so single/multi-character OCR misreads fail closed!
 */
object MrzIcao9303 {
    const val SCOPE =
        "ICAO Doc 9303 TD3 (2x44 passport) & TD1 (3x30 ID card) MRZ parser + [7,3,1] mod-10 field & composite check-digit verifier; BAC/PACE NFC chip crypto out of scope."

    private val WEIGHTS = intArrayOf(7, 3, 1)
    private val VALID_CHARS = Regex("^[A-Z0-9<]+$")

    enum class DocFormat { TD3_PASSPORT, TD1_ID_CARD }

    data class MrzRecord(
        val format: DocFormat,
        val documentCode: String,
        val issuingState: String,
        val documentNumber: String,
        val nationality: String,
        val birthDateYyMmDd: String,
        val sex: String,
        val expiryDateYyMmDd: String,
        val surname: String,
        val givenNames: String,
        val checksumValid: Boolean,
    )

    fun computeCheckDigit(field: String): Char {
        var sum = 0
        for ((idx, ch) in field.withIndex()) {
            val v = when (ch) {
                '<' -> 0
                in '0'..'9' -> ch - '0'
                in 'A'..'Z' -> ch - 'A' + 10
                else -> throw IllegalArgumentException("Invalid ICAO 9303 MRZ character '$ch'")
            }
            sum += v * WEIGHTS[idx % 3]
        }
        return ('0'.code + (sum % 10)).toChar()
    }

    fun parse(linesInput: List<String>): MrzRecord {
        val lines = linesInput.map { it.trim() }.filter { it.isNotEmpty() }
        lines.forEach { require(VALID_CHARS.matches(it)) { "MRZ contains illegal character: $it" } }
        return when {
            lines.size == 2 && lines.all { it.length == 44 } -> parseTd3(lines[0], lines[1])
            lines.size == 3 && lines.all { it.length == 30 } -> parseTd1(lines[0], lines[1], lines[2])
            else -> throw IllegalArgumentException(
                "Unsupported MRZ geometry (${lines.size} lines of lengths ${lines.map { it.length }})",
            )
        }
    }

    private fun parseTd3(l1: String, l2: String): MrzRecord {
        val docCode = l1.substring(0, 2).trimEnd('<')
        val state = l1.substring(2, 5).trimEnd('<')
        val (surname, given) = splitNames(l1.substring(5, 44))

        val docNumRaw = l2.substring(0, 9)
        val docChk = l2[9]
        val nat = l2.substring(10, 13).trimEnd('<')
        val dob = l2.substring(13, 19)
        val dobChk = l2[19]
        val sex = l2.substring(20, 21)
        val exp = l2.substring(21, 27)
        val expChk = l2[27]
        val personal = l2.substring(28, 42)
        val personalChk = l2[42]
        val compositeChk = l2[43]

        val personalOk = (personal.all { it == '<' } && (personalChk == '<' || personalChk == '0')) ||
            computeCheckDigit(personal) == personalChk
        val compositeInput = l2.substring(0, 10) + l2.substring(13, 20) + l2.substring(21, 43)
        val valid = computeCheckDigit(docNumRaw) == docChk &&
            isDate6(dob) && computeCheckDigit(dob) == dobChk &&
            isDate6(exp) && computeCheckDigit(exp) == expChk &&
            personalOk &&
            computeCheckDigit(compositeInput) == compositeChk

        return MrzRecord(
            format = DocFormat.TD3_PASSPORT,
            documentCode = docCode,
            issuingState = state,
            documentNumber = docNumRaw.trimEnd('<'),
            nationality = nat,
            birthDateYyMmDd = dob,
            sex = sex,
            expiryDateYyMmDd = exp,
            surname = surname,
            givenNames = given,
            checksumValid = valid,
        )
    }

    private fun parseTd1(l1: String, l2: String, l3: String): MrzRecord {
        val docCode = l1.substring(0, 2).trimEnd('<')
        val state = l1.substring(2, 5).trimEnd('<')
        val docNumRaw = l1.substring(5, 14)
        val docChk = l1[14]
        val dob = l2.substring(0, 6)
        val dobChk = l2[6]
        val sex = l2.substring(7, 8)
        val exp = l2.substring(8, 14)
        val expChk = l2[14]
        val nat = l2.substring(15, 18).trimEnd('<')
        val compositeChk = l2[29]
        val (surname, given) = splitNames(l3)

        val compositeInput = l1.substring(5, 30) + l2.substring(0, 7) + l2.substring(8, 15) + l2.substring(18, 29)
        val valid = computeCheckDigit(docNumRaw) == docChk &&
            isDate6(dob) && computeCheckDigit(dob) == dobChk &&
            isDate6(exp) && computeCheckDigit(exp) == expChk &&
            computeCheckDigit(compositeInput) == compositeChk

        return MrzRecord(
            format = DocFormat.TD1_ID_CARD,
            documentCode = docCode,
            issuingState = state,
            documentNumber = docNumRaw.trimEnd('<'),
            nationality = nat,
            birthDateYyMmDd = dob,
            sex = sex,
            expiryDateYyMmDd = exp,
            surname = surname,
            givenNames = given,
            checksumValid = valid,
        )
    }

    private fun splitNames(nameZone: String): Pair<String, String> {
        val parts = nameZone.split("<<", limit = 2)
        val sur = parts[0].replace('<', ' ').trim()
        val giv = parts.getOrElse(1) { "" }.replace('<', ' ').replace(Regex("\\s+"), " ").trim()
        return sur to giv
    }

    private fun isDate6(s: String): Boolean {
        if (s.length != 6 || !s.all(Char::isDigit)) return false
        val mm = s.substring(2, 4).toInt()
        val dd = s.substring(4, 6).toInt()
        return mm in 1..12 && dd in 1..31
    }
}
