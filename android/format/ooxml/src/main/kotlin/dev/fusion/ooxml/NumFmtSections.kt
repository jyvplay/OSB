package dev.fusion.ooxml

/**
 * SpreadsheetML number-format STRUCTURE parser (ECMA-376 Part 1 §18.8.31).
 *
 * A format code has up to four semicolon-separated sections
 * (positive;negative;zero;text). Semicolons inside "quoted literals",
 * inside [bracketed] parts, or after a backslash escape do not split.
 * A section may start with one [Color] from the eight named colors or
 * Color1..Color56; `General` (case-insensitive, whole section) is the
 * built-in passthrough format.
 *
 * Declared narrowings: STRUCTURE only — this kernel splits and classifies;
 * it does NOT render numbers, parse placeholders, or apply formats.
 * `LiteralXlsxImport` continues to refuse custom numFmts at import; this
 * kernel exists so a future style pass can classify what it refused.
 */
object NumFmtSections {
    const val SCOPE = "Section splitting + color/General classification only; no rendering, no placeholder semantics."
    val NAMED_COLORS = setOf("BLACK", "BLUE", "CYAN", "GREEN", "MAGENTA", "RED", "WHITE", "YELLOW")

    data class Section(val raw: String, val color: String?, val general: Boolean)

    fun split(format: String): List<String> {
        require(format.isNotEmpty()) { "empty format code" }
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inQuote = false
        var inBracket = false
        var i = 0
        while (i < format.length) {
            val c = format[i]
            when {
                c == '\\' && !inQuote -> {
                    require(i + 1 < format.length) { "dangling backslash escape" }
                    cur.append(c).append(format[i + 1]); i += 2; continue
                }
                c == '"' -> { inQuote = !inQuote; cur.append(c) }
                c == '[' && !inQuote -> { require(!inBracket) { "nested bracket" }; inBracket = true; cur.append(c) }
                c == ']' && !inQuote -> { require(inBracket) { "stray closing bracket" }; inBracket = false; cur.append(c) }
                c == ';' && !inQuote && !inBracket -> { out.add(cur.toString()); cur.setLength(0) }
                else -> cur.append(c)
            }
            i++
        }
        require(!inQuote) { "unterminated quoted literal" }
        require(!inBracket) { "unterminated bracket" }
        out.add(cur.toString())
        require(out.size <= 4) { "${out.size} sections exceed the positive;negative;zero;text maximum of 4" }
        return out
    }

    fun classify(section: String): Section {
        var color: String? = null
        var rest = section
        if (rest.startsWith("[")) {
            val end = rest.indexOf(']')
            if (end > 0) {
                val inner = rest.substring(1, end).uppercase()
                val isNamed = inner in NAMED_COLORS
                val isIndexed = inner.startsWith("COLOR") &&
                    inner.removePrefix("COLOR").toIntOrNull()?.let { it in 1..56 } == true
                if (isNamed || isIndexed) { color = inner; rest = rest.substring(end + 1) }
            }
        }
        return Section(section, color, rest.trim().equals("general", ignoreCase = true))
    }

    fun parse(format: String): List<Section> = split(format).map { classify(it) }
}
