package dev.fusion.messages

/**
 * RFC 2231 parameter continuations and extended values (the mechanism
 * Rfc2047 refused inside encoded-words, implemented at its proper
 * layer: MIME header PARAMETERS like `filename*0*=UTF-8''...;
 * filename*1*=...`).
 *
 * Rules: segments numbered from 0 with no gaps; the `*`-suffixed form
 * is percent-encoded with `charset''`-prefix ONLY on segment 0;
 * reassembly decodes extended segments and concatenates plain ones in
 * numeric order. Charsets UTF-8 and ISO-8859-1 only (same declared
 * subset as Rfc2047).
 *
 * Declared narrowings: parameter reassembly only — full header
 * tokenization (quoted-string lexing across a whole Content-Disposition)
 * is the caller's layer; language tags after the second apostrophe are
 * accepted and DISCARDED (recorded in the result so nothing silently
 * vanishes).
 */
object Rfc2231 {
    const val SCOPE = "Continuation reassembly + extended-value decode; UTF-8/Latin-1; language tag kept visible."

    data class Param(val name: String, val value: String, val language: String?)

    private fun pct(s: String, cs: java.nio.charset.Charset): String {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%') {
                require(i + 2 < s.length) { "truncated percent escape" }
                out.write(s.substring(i + 1, i + 3).toInt(16)); i += 3
            } else { require(c.code < 0x80) { "raw non-ASCII in extended value" }; out.write(c.code); i++ }
        }
        return String(out.toByteArray(), cs)
    }

    /** Reassemble one logical parameter from its (possibly continued) segments. */
    fun reassemble(name: String, segments: Map<String, String>): Param {
        // keys are the raw parameter names as they appeared: name, name*, name*0, name*0*, name*1 ...
        segments.keys.forEach { require(it == name || it.startsWith("$name*")) { "'$it' does not belong to '$name'" } }
        segments[name]?.let { plain ->
            require(segments.size == 1) { "plain and continued forms mixed" }
            return Param(name, plain, null)
        }
        segments["$name*"]?.let { ext ->
            require(segments.size == 1) { "single-extended and continued forms mixed" }
            return decodeExtended(name, ext)
        }
        val parts = ArrayList<Triple<Int, Boolean, String>>()
        for ((k, v) in segments) {
            val suffix = k.removePrefix("$name*")
            val extended = suffix.endsWith("*")
            val numText = if (extended) suffix.dropLast(1) else suffix
            require(numText.isNotEmpty() && numText.all { it.isDigit() }) { "segment key '$k' malformed" }
            parts.add(Triple(numText.toInt(), extended, v))
        }
        parts.sortBy { it.first }
        require(parts.first().first == 0) { "continuations must start at segment 0" }
        for (i in parts.indices) require(parts[i].first == i) { "gap in continuation numbering at $i" }
        var language: String? = null
        var charset = Charsets.UTF_8
        val sb = StringBuilder()
        for ((idx, extended, raw) in parts) {
            if (extended) {
                if (idx == 0) {
                    val (d, cs) = decodeExtendedWithCharset(name, raw)
                    language = d.language
                    sb.append(d.value)
                    charset = cs
                } else sb.append(pct(raw, charset))
            } else sb.append(raw)
        }
        return Param(name, sb.toString(), language)
    }

    private fun decodeExtended(name: String, value: String): Param = decodeExtendedWithCharset(name, value).first

    private fun decodeExtendedWithCharset(name: String, value: String): Pair<Param, java.nio.charset.Charset> {
        val first = value.indexOf('\'')
        val second = if (first >= 0) value.indexOf('\'', first + 1) else -1
        require(first >= 0 && second > first) { "extended value needs charset''" }
        val csName = value.substring(0, first)
        val cs = when (csName.uppercase()) {
            "UTF-8" -> Charsets.UTF_8
            "ISO-8859-1" -> Charsets.ISO_8859_1
            else -> throw IllegalArgumentException("charset '$csName' outside the declared subset")
        }
        val lang = value.substring(first + 1, second).ifEmpty { null }
        return Param(name, pct(value.substring(second + 1), cs), lang) to cs
    }
}
