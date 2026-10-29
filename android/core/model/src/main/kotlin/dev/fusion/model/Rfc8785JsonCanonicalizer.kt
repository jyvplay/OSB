// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

/**
 * Pure-JVM **RFC 8785 JSON Canonicalization Scheme (`JCS`) Deterministic Serializer**
 * for `:core:model`, guaranteeing byte-identical UTF-8 JSON representations for cryptographic
 * signing and content-addressed hashing across platforms.
 */
object Rfc8785JsonCanonicalizer {
    const val SCOPE =
        "IETF RFC 8785 JSON Canonicalization Scheme (JCS) deterministic lexicographical UTF-16 key sorter and string/number serializer."

    fun canonicalize(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> if (value) "true" else "false"
        is Int, is Long -> value.toString()
        is Double -> {
            require(value.isFinite()) { "RFC 8785 forbids NaN and Infinity in canonical JSON" }
            if (value == 0.0) "0"
            else if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 1e15) value.toLong().toString()
            else value.toString().lowercase()
        }
        is String -> quoteJsonString(value)
        is List<*> -> value.joinToString(",", prefix = "[", postfix = "]") { canonicalize(it) }
        is Map<*, *> -> {
            val entries = value.entries.map { it.key.toString() to it.value }
                .sortedWith { a, b -> compareUtf16CodeUnits(a.first, b.first) }
            entries.joinToString(",", prefix = "{", postfix = "}") { (k, v) ->
                "${quoteJsonString(k)}:${canonicalize(v)}"
            }
        }
        else -> quoteJsonString(value.toString())
    }

    private fun compareUtf16CodeUnits(a: String, b: String): Int {
        val len = minOf(a.length, b.length)
        for (i in 0 until len) {
            val diff = a[i].code - b[i].code
            if (diff != 0) return diff
        }
        return a.length - b.length
    }

    private fun quoteJsonString(s: String): String = buildString {
        append('"')
        for (ch in s) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }
}
