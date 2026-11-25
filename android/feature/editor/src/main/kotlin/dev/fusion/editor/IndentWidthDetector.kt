// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Bounded indentation style detector (tabs vs 2/4/8 spaces).
 */
class IndentWidthDetector private constructor() {
    companion object {
        const val MAX_LINES = 5000

        enum class IndentStyle { TABS, SPACES_2, SPACES_4, SPACES_8, UNKNOWN }

        fun detect(lines: List<String>): IndentStyle {
            require(lines.size <= MAX_LINES) { "too many lines" }
            var tabLines = 0
            var space2 = 0
            var space4 = 0
            var space8 = 0
            for (line in lines) {
                if (line.isEmpty()) continue
                if (line[0] == '\t') {
                    tabLines++
                } else if (line[0] == ' ') {
                    var spaces = 0
                    while (spaces < line.length && line[spaces] == ' ') spaces++
                    if (spaces == line.length) continue
                    when {
                        spaces % 8 == 0 -> space8++
                        spaces % 4 == 0 -> space4++
                        spaces % 2 == 0 -> space2++
                    }
                }
            }
            val max = maxOf(tabLines, space2, space4, space8)
            if (max == 0) return IndentStyle.UNKNOWN
            return when (max) {
                tabLines -> IndentStyle.TABS
                space4 -> IndentStyle.SPACES_4
                space2 -> IndentStyle.SPACES_2
                else -> IndentStyle.SPACES_8
            }
        }
    }
}
