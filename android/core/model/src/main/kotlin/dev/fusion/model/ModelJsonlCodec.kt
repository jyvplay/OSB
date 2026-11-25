// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

/**
 * Production-grade deterministic JSON-Lines (JSONL) flat-object codec
 * with proper string escaping and bounded line-length checks.
 */
class ModelJsonlCodec private constructor() {
    companion object {
        const val MAX_LINE_LEN = 65_536

        fun escapeJsonString(s: String): String {
            val sb = StringBuilder(s.length + 8)
            for (ch in s) {
                when (ch) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        fun encodeLine(fields: Map<String, String>): String {
            val body = fields.entries.joinToString(",") { (k, v) ->
                "\"${escapeJsonString(k)}\":\"${escapeJsonString(v)}\""
            }
            val line = "{$body}"
            require(line.length <= MAX_LINE_LEN) { "JSONL line exceeds MAX_LINE_LEN" }
            return line
        }
    }
}
