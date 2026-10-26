// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Greedy single-paragraph word wrap at a fixed column.
 *
 * Sits next to [BracketMatch] (structure lookup) and [IndentDetect]
 * (style detection): nothing reflows a paragraph to a width for
 * fixed-column display or export. Words (runs split on single spaces —
 * callers normalize whitespace first) pack greedily left to right; a word
 * that would overflow starts the next line. A word longer than the width
 * is REFUSED, not hyphenated or hard-split: breaking inside a word needs
 * a hyphenation dictionary this kernel does not have, and silently
 * emitting over-width lines would break the caller's column contract.
 * Input is one paragraph (no newlines); multi-paragraph text is split by
 * the caller so blank-line preservation stays the caller's policy.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object WrapCol {
    const val MAX_WIDTH = 1000

    /**
     * Wraps [paragraph] to lines of at most [width] columns.
     *
     * @throws IllegalArgumentException on a bad width, embedded newlines,
     * or a word longer than the width.
     */
    fun wrap(paragraph: String, width: Int): List<String> {
        require(width in 1..MAX_WIDTH) { "bad width" }
        require('\n' !in paragraph && '\r' !in paragraph) { "multi-line input" }
        val words = paragraph.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        for (w in words) require(w.length <= width) { "word over width" }
        val lines = mutableListOf<String>()
        val cur = StringBuilder(words[0])
        for (w in words.drop(1)) {
            if (cur.length + 1 + w.length <= width) {
                cur.append(' ').append(w)
            } else {
                lines.add(cur.toString())
                cur.clear().append(w)
            }
        }
        lines.add(cur.toString())
        return lines
    }
}
