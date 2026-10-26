// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Luhn mod-10 check (ISO/IEC 7812-1 check digit) powering the privacy
 * module's PAN redaction gate.
 *
 * Algorithm (as standardized): from the rightmost digit moving left,
 * double every second digit; if doubling yields > 9 subtract 9; the sum
 * of all digits must be 0 mod 10. The Luhn check detects every
 * single-digit error and all adjacent transpositions except 09<->90.
 *
 * maskPan() applies the PCI DSS display rule — at most the first six
 * and last four digits may be shown — and only treats a digit run as a
 * PAN when it is 13..19 digits long AND Luhn-valid, so ordinary numbers
 * (timestamps, ids) are not destroyed by over-eager masking.
 *
 * Pure JVM logic; refusal is IllegalArgumentException. This is a
 * redaction aid, not a payment feature: no card data is stored,
 * transmitted, or validated against any issuer.
 */
object LuhnCheck {
    fun isValid(digits: String): Boolean {
        require(digits.isNotEmpty() && digits.all { it in '0'..'9' }) { "Luhn input must be digits" }
        var sum = 0
        var double = false
        for (i in digits.length - 1 downTo 0) {
            var d = digits[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }

    /** The digit that makes partial+digit Luhn-valid. */
    fun checkDigit(partial: String): Int {
        require(partial.isNotEmpty() && partial.all { it in '0'..'9' }) { "Luhn input must be digits" }
        for (d in 0..9) {
            if (isValid(partial + d)) return d
        }
        throw IllegalStateException("unreachable: some digit always closes a Luhn sum")
    }

    /** PCI DSS display rule: first 6 + last 4 visible, middle starred. */
    fun maskPan(pan: String): String {
        require(pan.length in 13..19 && pan.all { it in '0'..'9' }) { "PAN must be 13..19 digits" }
        require(isValid(pan)) { "not Luhn-valid; refusing to treat as a PAN" }
        return pan.take(6) + "*".repeat(pan.length - 10) + pan.takeLast(4)
    }

    /** Scrub every Luhn-valid 13..19 digit run in free text; leaves the rest alone. */
    fun scrubText(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (text[i] in '0'..'9') {
                var j = i
                while (j < text.length && text[j] in '0'..'9') j++
                val run = text.substring(i, j)
                sb.append(if (run.length in 13..19 && isValid(run)) maskPan(run) else run)
                i = j
            } else {
                sb.append(text[i])
                i++
            }
        }
        return sb.toString()
    }
}
