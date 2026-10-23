// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Re-indentation for pasted blocks (the paste-into-scope operation).
 *
 * Sits next to [IndentDetect] (which detects a file's style) and
 * [TextOffsets] (offset math): neither re-indents pasted text.
 * [reindent] takes lines whose leading whitespace is an exact stack of
 * [unit] copies, and re-stacks each onto [baseDepth] — pasting a
 * 1-deep block at depth 2 yields depth 3, preserving relative shape.
 * Blank lines collapse to the empty string (no trailing whitespace is
 * ever emitted); lines whose indent is not a whole number of units are
 * refused rather than guessed. The unit is one tab or 1..8 spaces.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object PasteWrap {
    const val MAX_LINES = 10_000
    const val MAX_DEPTH = 64

    /**
     * Re-indents [lines] from unit stacks onto [baseDepth].
     *
     * @throws IllegalArgumentException on a bad unit, bad depth, an
     * over-long list, embedded newlines, or a line whose indent is not
     * a whole number of [unit] copies.
     */
    fun reindent(lines: List<String>, unit: String, baseDepth: Int): List<String> {
        require(unit == "\t" || (unit.isNotEmpty() && unit.length <= 8 && unit.all { it == ' ' })) {
            "bad unit"
        }
        require(baseDepth in 0..MAX_DEPTH) { "bad depth" }
        require(lines.size <= MAX_LINES) { "too many lines" }
        return lines.map { line ->
            require('\n' !in line && '\r' !in line) { "embedded newline" }
            if (line.isBlank()) return@map ""
            var depth = 0
            var rest = line
            while (rest.startsWith(unit)) {
                depth++
                rest = rest.substring(unit.length)
            }
            require(!rest.startsWith(" ") && !rest.startsWith("\t")) { "ragged indent" }
            require(baseDepth + depth <= MAX_DEPTH) { "depth over cap" }
            unit.repeat(baseDepth + depth) + rest
        }
    }
}
