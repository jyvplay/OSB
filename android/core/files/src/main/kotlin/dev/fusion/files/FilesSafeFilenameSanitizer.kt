// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

/**
 * Bounded filename sanitizer replacing forbidden chars with `_`.
 */
class FilesSafeFilenameSanitizer private constructor() {
    companion object {
        private val FORBIDDEN = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

        fun sanitize(name: String): String {
            val sb = StringBuilder(name.length)
            for (ch in name.trim()) {
                if (ch.code < 0x20 || ch in FORBIDDEN) sb.append('_') else sb.append(ch)
            }
            val out = sb.toString()
            require(out.isNotEmpty() && out != "." && out != "..") { "invalid filename" }
            return out
        }
    }
}
