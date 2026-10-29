// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Clean-room RFC 2045 §6.7 `Quoted-Printable` Body Codec & RFC 2047 MIME `=?charset?Q|B?...?=`
 * Header Encoded-Word Decoder.
 *
 * Guarantees:
 * - Every encoded output line is strictly `<= 76` characters (including soft line break `=`).
 * - Trailing spaces (`0x20`) and tabs (`0x09`) before CRLF or EOF are escaped (`=20`, `=09`) so
 *   MTA/transport whitespace stripping never corrupts signed MIME payloads!
 * - Malformed `=XX` non-hex sequences throw `IllegalArgumentException` in strict mode.
 */
object QuotedPrintable {
    const val SCOPE =
        "RFC 2045 §6.7 Quoted-Printable body encoder/decoder (<=76-col soft wraps, trailing space protection) + RFC 2047 UTF-8/ISO-8859-1 Q/B header word decoder."

    const val MAX_LINE_LEN = 76
    private val ENCODED_WORD_RE = Regex("""=\?([A-Za-z0-9._-]+)\?([QqBb])\?([^?\s]+)\?=""")

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size + bytes.size / 8)
        var col = 0

        fun emitRaw(ch: Char) {
            if (col >= MAX_LINE_LEN - 1) {
                sb.append("=\r\n")
                col = 0
            }
            sb.append(ch)
            col++
        }

        fun emitEscaped(b: Int) {
            if (col > MAX_LINE_LEN - 4) {
                sb.append("=\r\n")
                col = 0
            }
            sb.append('=')
            sb.append(HEX[(b ushr 4) and 0x0F])
            sb.append(HEX[b and 0x0F])
            col += 3
        }

        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            // Preserve canonical CRLF line breaks
            if (b == 0x0D && i + 1 < bytes.size && (bytes[i + 1].toInt() and 0xFF) == 0x0A) {
                sb.append("\r\n")
                col = 0
                i += 2
                continue
            }
            val nextIsLineEnd = (i == bytes.lastIndex) ||
                (i + 2 <= bytes.size && (bytes[i + 1].toInt() and 0xFF) == 0x0D &&
                    (i + 2 < bytes.size && (bytes[i + 2].toInt() and 0xFF) == 0x0A))
            val safeLiteral = (b in 33..60 || b in 62..126) ||
                ((b == 0x20 || b == 0x09) && !nextIsLineEnd && col < MAX_LINE_LEN - 2)
            if (safeLiteral) emitRaw(b.toChar()) else emitEscaped(b)
            i++
        }
        return sb.toString()
    }

    fun encodeUtf8(text: String): String = encode(text.toByteArray(StandardCharsets.UTF_8))

    fun decode(qp: String): ByteArray {
        val out = ByteArrayOutputStream(qp.length)
        var i = 0
        while (i < qp.length) {
            val c = qp[i]
            if (c == '=') {
                // Soft line break: =\r\n or =\n
                if (i + 1 < qp.length && qp[i + 1] == '\n') {
                    i += 2; continue
                }
                if (i + 2 < qp.length && qp[i + 1] == '\r' && qp[i + 2] == '\n') {
                    i += 3; continue
                }
                require(i + 2 < qp.length) { "truncated Quoted-Printable '=' escape at end of input" }
                val hi = hexDigit(qp[i + 1])
                val lo = hexDigit(qp[i + 2])
                out.write((hi shl 4) or lo)
                i += 3
            } else {
                out.write(c.code and 0xFF)
                i++
            }
        }
        return out.toByteArray()
    }

    fun decodeUtf8(qp: String): String = decode(qp).toString(StandardCharsets.UTF_8)

    /** Decodes RFC 2047 `=?UTF-8?B?...?=` and `=?UTF-8?Q?...?=` MIME header words (collapsing whitespace between adjacent encoded-words). */
    fun decodeHeaderWords(header: String): String {
        val collapsed = Regex("""(\?=)\s+(=\?)""").replace(header, "$1$2")
        return ENCODED_WORD_RE.replace(collapsed) { m ->
            val cs = resolveSafeCharset(m.groupValues[1])
            val enc = m.groupValues[2].uppercase()
            val payload = m.groupValues[3]
            val bytes = when (enc) {
                "B" -> Base64.getDecoder().decode(payload)
                "Q" -> decode(payload.replace('_', ' '))
                else -> throw IllegalArgumentException("unsupported RFC 2047 encoding '$enc'")
            }
            String(bytes, cs)
        }
    }

    private fun resolveSafeCharset(name: String): Charset = when (name.uppercase()) {
        "UTF-8", "UTF8" -> StandardCharsets.UTF_8
        "US-ASCII", "ASCII" -> StandardCharsets.US_ASCII
        "ISO-8859-1", "LATIN1" -> StandardCharsets.ISO_8859_1
        else -> throw IllegalArgumentException("unsupported MIME charset '$name'")
    }

    private fun hexDigit(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'A'..'F' -> c - 'A' + 10
        in 'a'..'f' -> c - 'a' + 10
        else -> throw IllegalArgumentException("invalid hex digit '$c' in Quoted-Printable")
    }

    private val HEX = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
}
