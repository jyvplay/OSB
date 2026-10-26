// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.pdf

/**
 * PDF name-object codec pinned to ISO 32000-1 section 7.3.5:
 *
 * - A name object is a solidus '/' followed by a sequence of regular
 *   characters. The NUMBER SIGN '#' acts as an escape: #XX with two hex
 *   digits stands for any byte, and '#' itself SHALL be written as #23.
 * - "The token #00 shall not be used" — a #00 escape refuses.
 * - Regular characters exclude whitespace (NUL, HT, LF, FF, CR, SP) and
 *   delimiters ( ) < > [ ] { } / % — those must be #-escaped when they
 *   appear in a name.
 * - Spec Table 4 examples are the pinned vectors:
 *   /Name1 -> "Name1"; /paired#28#29parentheses -> "paired()parentheses";
 *   /The_Key_of_F#23_Minor -> "The_Key_of_F#_Minor"; /A#42 -> "AB".
 *
 * decode() takes the token including the leading '/'; encode() emits
 * one. Pure JVM logic; refusal is IllegalArgumentException. No PDF
 * file structure, xref, or rendering claims.
 */
object PdfName {
    private const val DELIMITERS = "()<>[]{}/%"
    private const val WHITESPACE = "\u0000\t\n\u000C\r "

    fun decode(token: String): String {
        require(token.startsWith("/")) { "name must start with '/'" }
        val body = token.substring(1)
        val sb = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '#') {
                require(i + 3 <= body.length) { "truncated # escape" }
                val hi = Character.digit(body[i + 1], 16)
                val lo = Character.digit(body[i + 2], 16)
                require(hi >= 0 && lo >= 0) { "bad hex in # escape" }
                val v = (hi shl 4) or lo
                require(v != 0) { "the token #00 shall not be used (ISO 32000-1 7.3.5)" }
                sb.append(v.toChar())
                i += 3
            } else {
                require(!WHITESPACE.contains(c) && !DELIMITERS.contains(c)) { "unescaped delimiter/whitespace 0x%02X".format(c.code) }
                require(c.code in 0x21..0x7E) { "byte 0x%02X must be #-escaped".format(c.code) }
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    fun encode(name: String): String {
        val sb = StringBuilder(name.length + 1).append('/')
        for (c in name) {
            require(c.code in 1..255) { "name byte out of 1..255" }
            if (c == '#' || c.code < 0x21 || c.code > 0x7E || DELIMITERS.contains(c)) {
                sb.append("#%02X".format(c.code))
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }
}
