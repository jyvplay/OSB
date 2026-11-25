// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Bounded tab-to-spaces expander aligned to [tabWidth] columns.
 */
class DocumentTabToSpacesExpander private constructor() {
    companion object {
        fun expandLine(line: String, tabWidth: Int = 4): String {
            require(tabWidth in 1..16) { "tabWidth out of bounds" }
            val sb = StringBuilder(line.length)
            var col = 0
            for (ch in line) {
                if (ch == '\t') {
                    val spaces = tabWidth - (col % tabWidth)
                    repeat(spaces) { sb.append(' ') }
                    col += spaces
                } else {
                    sb.append(ch)
                    col++
                }
            }
            return sb.toString()
        }
    }
}
