// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * R28 EXECUTED host kernel. Bounded YAML-subset front-matter for notes:
 * a leading `---` header with flat `key: value` pairs (string/int/bool/
 * string-list). No nesting, no anchors, no duplicate keys. Returns the
 * header map plus the body; documents without a header return an empty map.
 */
class FrontMatter private constructor(val values: Map<String, Value>, val body: String) {
    sealed interface Value {
        data class Str(val s: String) : Value
        data class IntNum(val n: Long) : Value
        data class Bool(val b: Boolean) : Value
        data class ListStr(val items: List<String>) : Value
    }

    companion object {
        const val MAX_KEYS = 32
        const val MAX_HEADER_BYTES = 8192
        const val MAX_KEY = 32
        const val MAX_STRING = 512
        const val MAX_LIST_ITEMS = 32
        const val MAX_LIST_ITEM = 128
        private val KEY = Regex("[a-z0-9][a-z0-9_-]{0,31}")
        private val INT = Regex("-?\\d{1,10}")

        fun parse(document: String): FrontMatter {
            require(document.toByteArray(Charsets.UTF_8).size <= (1 shl 20)) { "Document exceeds 1 MiB" }
            val lines = document.split('\n')
            if (lines.isEmpty() || lines[0].trim() != "---") return FrontMatter(emptyMap(), document)
            val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
            require(end >= 0) { "Unterminated front-matter header" }
            val headerLines = lines.drop(1).take(end)
            require(headerLines.joinToString("\n").toByteArray(Charsets.UTF_8).size <= MAX_HEADER_BYTES) {
                "Front-matter header exceeds 8 KiB"
            }
            require(headerLines.size <= MAX_KEYS + 4) { "Too many header lines" }
            val values = linkedMapOf<String, Value>()
            for ((i, raw) in headerLines.withIndex()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                require(!raw.startsWith(" ") && !raw.startsWith("\t")) { "Header line ${i + 1}: nesting is not supported" }
                require(':' in line) { "Header line ${i + 1}: expected key: value" }
                val key = line.substringBefore(':').trim()
                require(KEY.matches(key)) { "Header line ${i + 1}: bad key" }
                require(key !in values) { "Header line ${i + 1}: duplicate key" }
                values[key] = parseValue(line.substringAfter(':').trim(), i + 1)
                require(values.size <= MAX_KEYS) { "Too many header keys" }
            }
            val body = lines.drop(end + 2).joinToString("\n")
            return FrontMatter(values.toMap(), body)
        }

        private fun parseValue(raw: String, line: Int): Value {
            if (raw.startsWith("[") && raw.endsWith("]")) {
                val inner = raw.removePrefix("[").removeSuffix("]").trim()
                if (inner.isEmpty()) return Value.ListStr(emptyList())
                val items = inner.split(',').map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                require(items.size <= MAX_LIST_ITEMS) { "Header line $line: too many list items" }
                items.forEach { require(it.length in 1..MAX_LIST_ITEM) { "Header line $line: bad list item" } }
                return Value.ListStr(items)
            }
            if (raw.equals("true", true)) return Value.Bool(true)
            if (raw.equals("false", true)) return Value.Bool(false)
            if (INT.matches(raw)) {
                val n = raw.toLong()
                require(n in -1_000_000_000L..1_000_000_000L) { "Header line $line: int out of range" }
                return Value.IntNum(n)
            }
            val s = raw.removeSurrounding("\"").removeSurrounding("'")
            require(s.length in 1..MAX_STRING) { "Header line $line: bad string value" }
            return Value.Str(s)
        }
    }
}
