// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

/**
 * American Soundex for the search index, pinned to the NARA coding
 * rules (archives.gov "Soundex System"):
 *
 * - Code = first letter + three digits; pad with zeros, truncate at 4.
 * - Digits: B F P V = 1; C G J K Q S X Z = 2; D T = 3; L = 4; M N = 5;
 *   R = 6. A E I O U Y H W are not coded.
 * - Side-by-side rule: letters with the same code number adjacent in
 *   the ORIGINAL name yield one digit; this rule includes the first
 *   letter (Pfister -> P236, not P123).
 * - H/W rule: "If 'H' or 'W' separate two consonants that have the same
 *   soundex code, the consonant to the right ... is not coded"
 *   (Ashcraft -> A261, not A226).
 * - Vowel rule: a vowel (A E I O U and Y) between two same-code
 *   consonants separates them, so both are coded (Tymczak -> T522).
 *
 * The NARA worked examples are the pinned vectors. Pure JVM logic over
 * ASCII letters; refusal is IllegalArgumentException (non-letters must
 * be stripped by the caller — silently skipping them would hide data).
 */
object Soundex {
    private fun code(c: Char): Int = when (c) {
        'B', 'F', 'P', 'V' -> 1
        'C', 'G', 'J', 'K', 'Q', 'S', 'X', 'Z' -> 2
        'D', 'T' -> 3
        'L' -> 4
        'M', 'N' -> 5
        'R' -> 6
        'A', 'E', 'I', 'O', 'U', 'Y' -> 0  // vowel separator
        'H', 'W' -> -1                      // transparent
        else -> throw IllegalArgumentException("non-letter '$c'")
    }

    fun encode(name: String): String {
        require(name.isNotEmpty()) { "empty name" }
        val upper = name.uppercase()
        for (c in upper) code(c) // validate every char up front
        val first = upper[0]
        val sb = StringBuilder().append(first)
        var lastCode = code(first) // first letter participates in the side-by-side rule
        for (i in 1 until upper.length) {
            when (val c = code(upper[i])) {
                -1 -> Unit            // H/W: transparent, lastCode survives
                0 -> lastCode = 0     // vowel: separates, next same-code consonant recodes
                else -> {
                    if (c != lastCode) sb.append(c)
                    lastCode = c
                }
            }
            if (sb.length == 4) break
        }
        while (sb.length < 4) sb.append('0')
        return sb.toString()
    }

    /** True when two names share a Soundex bucket (search fallback). */
    fun matches(a: String, b: String): Boolean = encode(a) == encode(b)
}
