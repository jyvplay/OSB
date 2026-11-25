// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settings

/**
 * Bounded JSON string value escaper.
 */
class SettingsJsonKeyEscaper private constructor() {
    companion object {
        fun escape(s: String): String {
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
    }
}
