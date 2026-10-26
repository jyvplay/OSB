package dev.fusion.archive

/**
 * Debian control-file field parsing (Debian Policy §5.1: `Name: value`
 * fields; continuation lines start with a space or tab; a line holding
 * just " ." is a blank-line marker inside folded descriptions; field
 * names are case-insensitive for lookup but stored as written).
 *
 * Complements [ArHead]: that kernel walks the .deb envelope; this one
 * reads the control member's text.
 *
 * Declared narrowings: single paragraph only (inter-paragraph blank
 * lines end parsing input — multi-paragraph sources split first);
 * binary-package required fields checked by [requireBinaryFields].
 */
object DebControl {
    const val SCOPE = "Single control paragraph: fields + continuations + ' .' markers; multi-paragraph splits first."

    fun parse(text: String): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        var current: String? = null
        for (raw in text.split('\n')) {
            if (raw.isEmpty()) break
            if (raw[0] == ' ' || raw[0] == '\t') {
                val key = current ?: throw IllegalArgumentException("continuation before any field")
                val cont = raw.trim()
                out[key] = out.getValue(key) + "\n" + (if (cont == ".") "" else cont)
            } else {
                val colon = raw.indexOf(':')
                require(colon > 0) { "line without a field name: '${raw.take(30)}'" }
                val name = raw.substring(0, colon)
                require(name.none { it.isWhitespace() }) { "whitespace in field name '$name'" }
                require(out.keys.none { it.equals(name, ignoreCase = true) }) { "duplicate field '$name'" }
                out[name] = raw.substring(colon + 1).trim()
                current = name
            }
        }
        require(out.isNotEmpty()) { "empty control paragraph" }
        return out
    }

    fun get(fields: Map<String, String>, name: String): String? =
        fields.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    fun requireBinaryFields(fields: Map<String, String>): Map<String, String> {
        for (need in listOf("Package", "Version", "Architecture", "Maintainer", "Description")) {
            require(get(fields, need) != null) { "binary control lacks $need" }
        }
        return fields
    }
}
