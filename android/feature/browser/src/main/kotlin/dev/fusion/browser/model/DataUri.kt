package dev.fusion.browser.model

/**
 * data: URL parsing (RFC 2397) for the browser's request policy layer.
 *
 * Grammar: `data:[<mediatype>][;base64],<data>` — the comma is
 * mandatory; an absent mediatype defaults to `text/plain;charset=US-ASCII`
 * (the RFC's own default, surfaced explicitly). Base64 payloads decode
 * strictly (standard alphabet, correct padding); non-base64 payloads
 * percent-decode.
 *
 * Declared narrowings: decode-and-describe only — no fetching, no
 * rendering, no size cap policy here (the policy layer applies its own
 * budget to [bytes].size); nested data URLs inside the payload are
 * bytes like any other.
 */
object DataUri {
    const val SCOPE = "RFC 2397 decode-and-describe; policy/size budgeting happens in the caller."
    const val DEFAULT_MEDIATYPE = "text/plain"
    const val DEFAULT_CHARSET = "US-ASCII"

    data class Parsed(
        val mediatype: String, val parameters: Map<String, String>,
        val base64: Boolean, val bytes: ByteArray
    )

    private fun pctDecode(s: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%') {
                require(i + 2 < s.length) { "truncated percent escape" }
                val hex = s.substring(i + 1, i + 3)
                require(hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) { "bad escape %$hex" }
                out.write(hex.toInt(16)); i += 3
            } else {
                require(c.code < 0x80) { "raw non-ASCII in data URL payload" }
                out.write(c.code); i++
            }
        }
        return out.toByteArray()
    }

    fun parse(url: String): Parsed {
        require(url.startsWith("data:")) { "not a data: URL" }
        val comma = url.indexOf(',')
        require(comma >= 5) { "data: URL lacks the mandatory comma" }
        var header = url.substring(5, comma)
        val payload = url.substring(comma + 1)
        var base64 = false
        if (header.endsWith(";base64")) { base64 = true; header = header.dropLast(7) }
        val parts = header.split(';').filter { it.isNotEmpty() }
        val mediatype: String
        val params = LinkedHashMap<String, String>()
        if (parts.isEmpty()) {
            mediatype = DEFAULT_MEDIATYPE
            params["charset"] = DEFAULT_CHARSET
        } else {
            mediatype = parts[0]
            require('/' in mediatype) { "mediatype '$mediatype' lacks a subtype" }
            for (p in parts.drop(1)) {
                val eq = p.indexOf('=')
                require(eq > 0) { "parameter '$p' lacks '='" }
                val key = p.substring(0, eq).lowercase()
                require(params.put(key, p.substring(eq + 1)) == null) { "duplicate parameter '$key'" }
            }
        }
        val bytes = if (base64) {
            try {
                java.util.Base64.getDecoder().decode(payload)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("invalid base64 payload: ${e.message}")
            }
        } else pctDecode(payload)
        return Parsed(mediatype, params, base64, bytes)
    }

    fun isDataUrl(url: String): Boolean = url.startsWith("data:")
}
