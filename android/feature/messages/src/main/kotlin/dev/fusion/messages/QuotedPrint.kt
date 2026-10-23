// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

/**
 * Quoted-printable codec pinned to RFC 2045 section 6.7:
 *
 * - Rule 1: "=XX" with two UPPERCASE hexadecimal digits represents an
 *   octet. This decoder is strict and refuses lowercase escape digits
 *   (encoders "MUST" use uppercase; accepting lowercase is listed only
 *   as a robustness option, which a strict kernel declines).
 * - Rule 5: a '=' at the end of a line is a soft line break and is
 *   deleted together with the line break. Both CRLF and bare LF input
 *   line endings are accepted on decode; hard breaks decode to CRLF.
 * - Rule 2: literal characters 33..126 except '=' (61) stand for
 *   themselves; space (32) and tab (9) are allowed inside a line.
 * - Encoded lines must not be longer than 76 characters (decode refuses
 *   longer lines; encode soft-wraps before the limit).
 *
 * Pure JVM logic over Strings of Latin-1 octets; refusal is
 * IllegalArgumentException. Charset handling is out of scope.
 */
object QuotedPrint {
    const val MAX_LINE = 76

    fun decode(text: String): String {
        val lines = text.split("\r\n", "\n")
        val sb = StringBuilder(text.length)
        for ((idx, line) in lines.withIndex()) {
            require(line.length <= MAX_LINE) { "encoded line longer than $MAX_LINE chars" }
            var soft = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                if (c == '=') {
                    if (i == line.length - 1) {
                        require(idx < lines.size - 1) { "soft break at end of input" }
                        soft = true
                        i++
                    } else {
                        require(i + 3 <= line.length) { "truncated escape" }
                        val hi = hexUpper(line[i + 1])
                        val lo = hexUpper(line[i + 2])
                        sb.append(((hi shl 4) or lo).toChar())
                        i += 3
                    }
                } else {
                    require(c == ' ' || c == '\t' || c.code in 33..126) { "bare control or 8-bit char ${c.code}" }
                    sb.append(c)
                    i++
                }
            }
            if (idx < lines.size - 1 && !soft) sb.append("\r\n")
        }
        return sb.toString()
    }

    fun encode(text: String): String {
        val sb = StringBuilder()
        var lineLen = 0
        fun emit(chunk: String) {
            if (lineLen + chunk.length > MAX_LINE - 1) { // keep room for a soft-break '='
                sb.append("=\r\n")
                lineLen = 0
            }
            sb.append(chunk)
            lineLen += chunk.length
        }
        for (c in text) {
            when {
                c == '\n' -> { sb.append("\r\n"); lineLen = 0 }
                c == '\r' -> Unit // normalized away; the matching \n emits CRLF
                c == '=' || c.code > 126 || (c.code < 32 && c != '\t') ->
                    emit("=%02X".format(c.code and 0xFF))
                else -> emit(c.toString())
            }
        }
        return sb.toString()
    }

    private fun hexUpper(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'A'..'F' -> c - 'A' + 10
        in 'a'..'f' -> throw IllegalArgumentException("lowercase escape digit '$c' refused (RFC 2045 rule 1)")
        else -> throw IllegalArgumentException("bad escape digit '$c'")
    }
}
