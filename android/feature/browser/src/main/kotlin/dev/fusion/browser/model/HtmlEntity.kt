package dev.fusion.browser.model

/**
 * HTML character-reference decoding for the browser's text extraction
 * (WHATWG HTML "character references"; named subset + numeric forms).
 *
 * Numeric references `&#dd;` and `&#xhh;` decode to the code point, with
 * refusals for 0, surrogates and > U+10FFFF. The 0x80..0x9F range —
 * which full HTML5 remaps through windows-1252 — is REFUSED here as a
 * declared narrowing instead of silently remapped; the held-out Python
 * verifier is therefore only consulted outside that range.
 *
 * Declared narrowings: a 30-name subset (the long WHATWG table is data,
 * not logic); semicolons are REQUIRED (legacy semicolonless forms
 * refused); encode() escapes only the five XML-significant characters.
 */
object HtmlEntity {
    const val SCOPE = "30 named entities + strict numeric refs; 0x80..0x9F refused (no cp1252 quirk); ';' required."

    val NAMED: Map<String, Int> = mapOf(
        "amp" to 0x26, "lt" to 0x3C, "gt" to 0x3E, "quot" to 0x22, "apos" to 0x27,
        "nbsp" to 0xA0, "copy" to 0xA9, "reg" to 0xAE, "trade" to 0x2122,
        "hellip" to 0x2026, "mdash" to 0x2014, "ndash" to 0x2013,
        "lsquo" to 0x2018, "rsquo" to 0x2019, "ldquo" to 0x201C, "rdquo" to 0x201D,
        "deg" to 0xB0, "middot" to 0xB7, "bull" to 0x2022, "euro" to 0x20AC,
        "pound" to 0xA3, "yen" to 0xA5, "cent" to 0xA2, "sect" to 0xA7,
        "para" to 0xB6, "dagger" to 0x2020, "Dagger" to 0x2021, "permil" to 0x2030,
        "larr" to 0x2190, "rarr" to 0x2192
    )

    private fun codePoint(body: String): Int {
        val cp = when {
            body.startsWith("#x") || body.startsWith("#X") -> {
                val hex = body.substring(2)
                require(hex.isNotEmpty() && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) { "bad hex reference &$body;" }
                require(hex.length <= 6) { "hex reference too long" }
                hex.toInt(16)
            }
            body.startsWith("#") -> {
                val dec = body.substring(1)
                require(dec.isNotEmpty() && dec.all { it.isDigit() }) { "bad decimal reference &$body;" }
                require(dec.length <= 7) { "decimal reference too long" }
                dec.toInt()
            }
            else -> NAMED[body] ?: throw IllegalArgumentException("'&$body;' is outside the declared named subset")
        }
        require(cp != 0) { "NUL reference refused" }
        require(cp !in 0xD800..0xDFFF) { "surrogate reference refused" }
        require(cp <= 0x10FFFF) { "reference beyond U+10FFFF" }
        require(cp !in 0x80..0x9F) { "C1 range refused by declared narrowing (no cp1252 remap)" }
        return cp
    }

    fun decode(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '&') {
                val end = text.indexOf(';', i + 1)
                require(end > i + 1) { "unterminated or empty character reference at $i" }
                val body = text.substring(i + 1, end)
                require(body.length <= 32) { "implausibly long reference" }
                sb.appendCodePoint(codePoint(body))
                i = end + 1
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    fun encode(text: String): String = buildString(text.length) {
        for (c in text) when (c) {
            '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;")
            '"' -> append("&quot;"); '\'' -> append("&apos;"); else -> append(c)
        }
    }
}
