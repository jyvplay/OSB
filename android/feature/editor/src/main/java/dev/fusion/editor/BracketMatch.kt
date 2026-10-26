// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Bracket-mate lookup for the code editor.
 *
 * Sits next to [EditorTools] (file operations): nothing answers "where is the
 * bracket matching the one under the cursor". Given text and the offset of an
 * opening or closing bracket, this scans outward with a depth counter and
 * returns the mate's offset, or null when the bracket is unbalanced. Matching is
 * purely lexical over `()`, `[]`, `{}` — string literals and comments are NOT
 * skipped, so callers highlighting code with brackets inside strings must
 * pre-filter; that limitation is documented rather than half-solved.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object BracketMatch {

    private val OPEN = mapOf('(' to ')', '[' to ']', '{' to '}')
    private val CLOSE = mapOf(')' to '(', ']' to '[', '}' to '{')

    /**
     * Returns the offset of the bracket matching the one at [offset], or null
     * when it has no mate.
     *
     * @throws IllegalArgumentException when [offset] is out of bounds or does
     * not point at a bracket character.
     */
    fun mate(text: String, offset: Int): Int? {
        require(offset in text.indices) { "offset out of bounds" }
        val c = text[offset]
        return when {
            OPEN.containsKey(c) -> scanForward(text, offset, c, OPEN.getValue(c))
            CLOSE.containsKey(c) -> scanBackward(text, offset, CLOSE.getValue(c), c)
            else -> throw IllegalArgumentException("not a bracket")
        }
    }

    private fun scanForward(text: String, from: Int, open: Char, close: Char): Int? {
        var depth = 0
        for (i in from until text.length) {
            if (text[i] == open) depth++
            else if (text[i] == close) {
                depth--
                if (depth == 0) return i
            }
        }
        return null
    }

    private fun scanBackward(text: String, from: Int, open: Char, close: Char): Int? {
        var depth = 0
        for (i in from downTo 0) {
            if (text[i] == close) depth++
            else if (text[i] == open) {
                depth--
                if (depth == 0) return i
            }
        }
        return null
    }
}
