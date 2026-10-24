package dev.fusion.messages

/**
 * RFC 2047 encoded-word decoding for message headers.
 *
 * Grammar: `=?charset?encoding?encoded-text?=` with encoding B (base64)
 * or Q (quoted-printable variant: `_` is SPACE, `=XX` uppercase hex).
 * An encoded word may not exceed 75 characters (§2). Linear whitespace
 * BETWEEN two adjacent encoded words is deleted (§6.2) — between an
 * encoded word and ordinary text it is kept.
 *
 * Declared narrowings: charsets UTF-8, ISO-8859-1 and US-ASCII only
 * (anything else refuses loudly rather than mojibake); language tags
 * (RFC 2231 `*lang`) refused; encoding of outgoing headers is out of
 * scope this round.
 */
object Rfc2047 {
    const val SCOPE = "Decode-only; charsets UTF-8/ISO-8859-1/US-ASCII; others refused; language tags refused."
    private val WORD = Regex("=\\?([^?\\s]+)\\?([bBqQ])\\?([^? ]*)\\?=")

    fun decodeWord(charset: String, enc: String, payload: String): String {
        val cs = when (charset.uppercase()) {
            "UTF-8" -> Charsets.UTF_8
            "ISO-8859-1" -> Charsets.ISO_8859_1
            "US-ASCII" -> Charsets.US_ASCII
            else -> throw IllegalArgumentException("charset '$charset' outside the declared subset")
        }
        val bytes = when (enc.uppercase()) {
            "B" -> {
                require(payload.length % 4 == 0) { "B payload length not a multiple of 4" }
                java.util.Base64.getDecoder().decode(payload)
            }
            else -> {
                val out = java.io.ByteArrayOutputStream()
                var i = 0
                while (i < payload.length) {
                    when (val c = payload[i]) {
                        '_' -> { out.write(0x20); i++ }
                        '=' -> {
                            require(i + 2 < payload.length) { "truncated =XX escape" }
                            val hex = payload.substring(i + 1, i + 3)
                            require(hex.all { it.isDigit() || it in 'A'..'F' }) { "Q escape '=$hex' must be uppercase hex" }
                            out.write(hex.toInt(16)); i += 3
                        }
                        else -> {
                            require(c.code in 0x21..0x7E && c != '?') { "raw '$c' invalid in Q encoding" }
                            out.write(c.code); i++
                        }
                    }
                }
                out.toByteArray()
            }
        }
        return String(bytes, cs)
    }

    fun decode(header: String): String {
        val sb = StringBuilder()
        var pos = 0
        var lastWasWord = false
        while (pos < header.length) {
            val m = WORD.find(header, pos) ?: break
            val between = header.substring(pos, m.range.first)
            require(m.value.length <= 75) { "encoded word exceeds the 75-character cap" }
            if (lastWasWord && between.isNotEmpty() && between.all { it == ' ' || it == '\t' }) {
                // §6.2: whitespace between adjacent encoded words is deleted
            } else {
                sb.append(between)
            }
            require(!m.groupValues[1].contains('*')) { "RFC 2231 language tags refused" }
            sb.append(decodeWord(m.groupValues[1], m.groupValues[2], m.groupValues[3]))
            pos = m.range.last + 1
            lastWasWord = true
        }
        sb.append(header.substring(pos))
        return sb.toString()
    }

    fun containsEncodedWord(header: String): Boolean = WORD.containsMatchIn(header)
}
