// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Indentation-style detection over source lines.
 *
 * Sits next to [BracketMatch] (structure lookup): nothing answers "tabs or
 * spaces, and how many" for a file the editor just opened. Tab-led and
 * space-led lines are counted separately; blank lines and lines starting
 * with any other character carry no indent vote. The space width is the GCD
 * of the observed leading-space runs (2/4/6-space nesting votes width 2),
 * or the single observed run when only one space-led line exists. A file
 * with only one style wins outright; a file mixing tabs AND spaces, or with
 * no indented lines at all, yields null from [detect] rather than a guess
 * — the caller then asks the user instead of reformatting behind their
 * back. Widths over 8 refuse the GCD reading as noise (a 40-space run is
 * alignment, not indent).
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object IndentDetect {

    /** Vote tally: tab-led lines, space-led lines, GCD space width. */
    data class Tally(val tabs: Int, val spaces: Int, val width: Int?)

    /** Detected style: hard tabs or N spaces. */
    sealed interface Style {
        data object Tabs : Style
        data class Spaces(val width: Int) : Style
    }

    /**
     * Tallies indent votes in [lines]. Blank and unindented lines abstain.
     */
    fun analyze(lines: List<String>): Tally {
        var tabs = 0
        val runs = mutableListOf<Int>()
        for (line in lines) {
            if (line.isBlank()) continue
            when {
                line.startsWith('\t') -> tabs++
                line.startsWith(' ') -> runs.add(line.takeWhile { it == ' ' }.length)
            }
        }
        val width = if (runs.isEmpty()) null else runs.reduce(::gcd).takeIf { it in 1..8 }
        return Tally(tabs, runs.size, if (runs.isEmpty()) null else width)
    }

    /**
     * Detects the style, or null when the file mixes tabs and spaces, has
     * no indented lines, or the space width reads as noise.
     */
    fun detect(lines: List<String>): Style? {
        val t = analyze(lines)
        if (t.tabs > 0 && t.spaces > 0) return null
        if (t.tabs > 0) return Style.Tabs
        val w = t.width ?: return null
        return Style.Spaces(w)
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val r = x % y
            x = y
            y = r
        }
        return x
    }
}
