// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settings

import java.util.Locale

/**
 * Clean-room TOML 1.0 configuration subset parser & canonical deterministic serializer (`TomlConfigCodec`).
 *
 * Powers `:core:settings` human-editable privacy/workspace profiles:
 * - Standard table headers `[privacy]`, nested dotted headers `[network.tor]`, and dotted keys `a.b = 1`.
 * - Values: basic `"..."` (with `\n`, `\t`, `\"`, `\\` escapes) and literal `'...'` strings, signed 64-bit
 *   integers (with `_` digit separators, `0x` hex), finite IEEE-754 floats, booleans `true`/`false`, and
 *   nested arrays `[1, 2, 3]`.
 * - Strict TOML 1.0 duplicate-key and table-redefinition refusal.
 */
object TomlConfigCodec {
    const val SCOPE =
        "TOML 1.0 configuration parser + canonical deterministic serializer (nested [table.sub] headers, dotted keys, strings, ints/hex, floats, bools, arrays, duplicate-key refusal); RFC 3339 offset datetimes and [[array-of-tables]] out of scope."

    sealed interface TomlVal {
        data class Str(val v: String) : TomlVal
        data class Int64(val v: Long) : TomlVal
        data class Float64(val v: Double) : TomlVal
        data class Bool(val v: Boolean) : TomlVal
        data class Arr(val items: List<TomlVal>) : TomlVal
        data class Table(val entries: MutableMap<String, TomlVal> = linkedMapOf()) : TomlVal
    }

    fun parse(tomlText: String): TomlVal.Table {
        val root = TomlVal.Table()
        val definedTables = hashSetOf<List<String>>()
        var currentPath = emptyList<String>()

        for ((lineIdx, rawLine) in tomlText.lines().withIndex()) {
            val line = stripComment(rawLine).trim()
            if (line.isEmpty()) continue

            if (line.startsWith("[")) {
                require(line.endsWith("]") && !line.startsWith("[[")) {
                    "Unsupported or malformed table header on line ${lineIdx + 1}: $line"
                }
                val path = splitKeyPath(line.substring(1, line.length - 1))
                require(definedTables.add(path)) { "Duplicate TOML table [${path.joinToString(".")}] on line ${lineIdx + 1}" }
                ensureTable(root, path)
                currentPath = path
            } else {
                val eq = findTopLevelEquals(line)
                require(eq > 0) { "Missing '=' on line ${lineIdx + 1}: $line" }
                val keyPath = splitKeyPath(line.substring(0, eq))
                val rhs = line.substring(eq + 1).trim()
                val target = ensureTable(root, currentPath + keyPath.dropLast(1))
                val leaf = keyPath.last()
                require(!target.entries.containsKey(leaf)) {
                    "Duplicate TOML key '${(currentPath + keyPath).joinToString(".")}' on line ${lineIdx + 1}"
                }
                target.entries[leaf] = parseValue(rhs, lineIdx + 1)
            }
        }
        return root
    }

    fun getAt(root: TomlVal.Table, dottedPath: String): TomlVal? {
        var cur: TomlVal = root
        for (part in splitKeyPath(dottedPath)) {
            cur = (cur as? TomlVal.Table)?.entries?.get(part) ?: return null
        }
        return cur
    }

    fun formatCanonical(root: TomlVal.Table): String {
        val sb = StringBuilder()
        fun emitTable(prefix: List<String>, tbl: TomlVal.Table) {
            val scalars = tbl.entries.filterValues { it !is TomlVal.Table }.toSortedMap()
            val subTables = tbl.entries.filterValues { it is TomlVal.Table }.toSortedMap()
            if (prefix.isNotEmpty() && (scalars.isNotEmpty() || subTables.isEmpty())) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append('[').append(prefix.joinToString(".")).append("]\n")
            }
            for ((k, v) in scalars) {
                val renderedKey = if (k.matches(Regex("^[A-Za-z0-9_-]+$"))) k else "\"${escBasic(k)}\""
                sb.append(renderedKey).append(" = ").append(formatValue(v)).append('\n')
            }
            for ((k, v) in subTables) {
                emitTable(prefix + k, v as TomlVal.Table)
            }
        }
        emitTable(emptyList(), root)
        return sb.toString()
    }

    private fun formatValue(v: TomlVal): String = when (v) {
        is TomlVal.Str -> "\"${escBasic(v.v)}\""
        is TomlVal.Int64 -> v.v.toString()
        is TomlVal.Float64 -> String.format(Locale.US, "%s", v.v)
        is TomlVal.Bool -> if (v.v) "true" else "false"
        is TomlVal.Arr -> v.items.joinToString(", ", "[", "]") { formatValue(it) }
        is TomlVal.Table -> error("Nested table handled by section emitter")
    }

    private fun parseValue(raw: String, lineNo: Int): TomlVal {
        require(raw.isNotEmpty()) { "Empty TOML value on line $lineNo" }
        return when {
            raw == "true" -> TomlVal.Bool(true)
            raw == "false" -> TomlVal.Bool(false)
            raw.startsWith("\"") -> {
                require(raw.length >= 2 && raw.endsWith("\"")) { "Unterminated basic string on line $lineNo" }
                TomlVal.Str(unescBasic(raw.substring(1, raw.length - 1), lineNo))
            }
            raw.startsWith("'") -> {
                require(raw.length >= 2 && raw.endsWith("'")) { "Unterminated literal string on line $lineNo" }
                TomlVal.Str(raw.substring(1, raw.length - 1))
            }
            raw.startsWith("[") -> {
                require(raw.endsWith("]")) { "Unterminated array on line $lineNo" }
                val inner = raw.substring(1, raw.length - 1).trim()
                if (inner.isEmpty()) TomlVal.Arr(emptyList())
                else TomlVal.Arr(splitTopLevelComma(inner).filter { it.isNotEmpty() }.map { parseValue(it, lineNo) })
            }
            raw.startsWith("0x") || raw.startsWith("0X") -> {
                val cleaned = raw.substring(2).replace("_", "")
                TomlVal.Int64(cleaned.toLong(16))
            }
            raw.contains('.') || raw.contains('e') || raw.contains('E') -> {
                val d = raw.replace("_", "").toDoubleOrNull()
                    ?: throw IllegalArgumentException("Invalid float '$raw' on line $lineNo")
                require(d.isFinite()) { "Non-finite float '$raw' refused on line $lineNo" }
                TomlVal.Float64(d)
            }
            else -> {
                val n = raw.replace("_", "").toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid TOML literal '$raw' on line $lineNo")
                TomlVal.Int64(n)
            }
        }
    }

    private fun ensureTable(root: TomlVal.Table, path: List<String>): TomlVal.Table {
        var cur = root
        for (part in path) {
            val existing = cur.entries[part]
            cur = when (existing) {
                null -> TomlVal.Table().also { cur.entries[part] = it }
                is TomlVal.Table -> existing
                else -> throw IllegalArgumentException("Cannot redefine scalar '$part' as TOML table")
            }
        }
        return cur
    }

    private fun splitKeyPath(spec: String): List<String> {
        val parts = mutableListOf<String>()
        var i = 0
        val s = spec.trim()
        while (i < s.length) {
            while (i < s.length && s[i].isWhitespace()) i++
            if (i >= s.length) break
            if (s[i] == '"') {
                val end = s.indexOf('"', i + 1)
                require(end > i) { "Unterminated quoted TOML key: $spec" }
                parts.add(unescBasic(s.substring(i + 1, end), 0))
                i = end + 1
            } else {
                val start = i
                while (i < s.length && s[i] != '.' && !s[i].isWhitespace()) i++
                val bare = s.substring(start, i)
                require(bare.matches(Regex("^[A-Za-z0-9_-]+$"))) { "Invalid bare TOML key '$bare'" }
                parts.add(bare)
            }
            while (i < s.length && s[i].isWhitespace()) i++
            if (i < s.length) {
                require(s[i] == '.') { "Unexpected char '${s[i]}' in TOML key '$spec'" }
                i++
            }
        }
        require(parts.isNotEmpty()) { "Empty TOML key" }
        return parts
    }

    private fun stripComment(line: String): String {
        var quote: Char? = null
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (quote != null) {
                if (quote == '"' && ch == '\\') { i += 2; continue }
                if (ch == quote) quote = null
            } else if (ch == '"' || ch == '\'') {
                quote = ch
            } else if (ch == '#') {
                return line.substring(0, i)
            }
            i++
        }
        return line
    }

    private fun findTopLevelEquals(line: String): Int {
        var quote: Char? = null
        for (i in line.indices) {
            val ch = line[i]
            if (quote != null) {
                if (ch == quote && (quote != '"' || line.getOrNull(i - 1) != '\\')) quote = null
            } else if (ch == '"' || ch == '\'') {
                quote = ch
            } else if (ch == '=') {
                return i
            }
        }
        return -1
    }

    private fun splitTopLevelComma(inner: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var quote: Char? = null
        var start = 0
        var i = 0
        while (i < inner.length) {
            val ch = inner[i]
            if (quote != null) {
                if (quote == '"' && ch == '\\') { i += 2; continue }
                if (ch == quote) quote = null
            } else when (ch) {
                '"', '\'' -> quote = ch
                '[' -> depth++
                ']' -> depth--
                ',' -> if (depth == 0) {
                    out.add(inner.substring(start, i).trim())
                    start = i + 1
                }
            }
            i++
        }
        out.add(inner.substring(start).trim())
        return out
    }

    private fun escBasic(s: String): String = buildString(s.length) {
        for (ch in s) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun unescBasic(s: String, lineNo: Int): String = buildString(s.length) {
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\') {
                require(i + 1 < s.length) { "Trailing backslash on line $lineNo" }
                when (val e = s[i + 1]) {
                    '\\', '"' -> append(e)
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    else -> throw IllegalArgumentException("Unsupported TOML escape \\$e on line $lineNo")
                }
                i += 2
            } else {
                append(s[i++])
            }
        }
    }
}
