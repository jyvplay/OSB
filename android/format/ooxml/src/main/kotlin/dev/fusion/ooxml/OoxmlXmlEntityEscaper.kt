// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Bounded XML entity escaper (`&`, `<`, `>`, `"`, `'`).
 */
class OoxmlXmlEntityEscaper private constructor() {
    companion object {
        fun escape(text: String): String {
            val sb = StringBuilder(text.length)
            for (ch in text) {
                when (ch) {
                    '&' -> sb.append("&amp;")
                    '<' -> sb.append("&lt;")
                    '>' -> sb.append("&gt;")
                    '"' -> sb.append("&quot;")
                    '\'' -> sb.append("&apos;")
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }
    }
}
