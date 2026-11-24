// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Bounded plain-text normalizer for a narrow ASCII-compatible subset of
 * Unicode normalization. Strips zero-width code points (U+200B–U+200D,
 * U+FEFF), collapses runs of ASCII whitespace (space, tab, CR, LF) to a
 * single space, trims leading/trailing whitespace, and optionally lowercases
 * ASCII letters A–Z. Non-ASCII bytes (above U+007F) are passed through
 * unchanged — full NFC/NFD/NFKC/NFKD decomposition is not performed here;
 * ICU or the platform normalizer is required for complete Unicode.
 *
 * Declared narrowings: input at most [MAX_INPUT] chars; only ASCII A–Z are
 * lowercased (no Unicode case folding); whitespace collapsing covers only
 * U+0009, U+000A, U+000D, U+0020 (no non-breaking spaces, no Unicode spaces);
 * zero-width stripping covers only U+200B, U+200C, U+200D, U+FEFF.
 */
class PlainTextNormalizer private constructor(
    private val lowercase: Boolean
) {
    companion object {
        const val MAX_INPUT = 1 << 16  // 64 KiB chars
        private val ZERO_WIDTH = setOf(0x200B, 0x200C, 0x200D, 0xFEFF)
        private val WS = setOf(0x09, 0x0A, 0x0D, 0x20)

        fun plain(): PlainTextNormalizer = PlainTextNormalizer(false)
        fun lowered(): PlainTextNormalizer = PlainTextNormalizer(true)
    }

    fun normalize(input: String): String {
        require(input.length <= MAX_INPUT) { "input exceeds $MAX_INPUT chars" }
        val sb = StringBuilder()
        var prevWs = false
        var started = false
        for (i in input.indices) {
            val cp = input.codePointAt(i)
            if (cp in ZERO_WIDTH) continue
            val ch = if (lowercase && cp in 'A'.code..'Z'.code) (cp + 32).toChar() else cp.toChar()
            if (ch.code.toInt() in WS) {
                if (started && !prevWs) { sb.append(' '); prevWs = true }
            } else {
                sb.append(ch); started = true; prevWs = false
            }
        }
        var result = sb.toString()
        if (result.endsWith(" ")) result = result.dropLast(1)
        return result
    }

    fun normalizeWhitespaceOnly(input: String): String = plain().normalize(input)
}
