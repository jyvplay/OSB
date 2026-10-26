// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * ATX heading outline for Markdown notes.
 *
 * Sits next to [NotesStore] (persistence): nothing extracts a note's `#` heading
 * structure for a jump-to-section list. Only ATX headings (`#` … `######`
 * followed by a space) are recognized; Setext underlines are deliberately out of
 * scope and documented as such. Fenced code blocks (``` or ~~~) suppress heading
 * recognition so an `#include` inside a code sample never becomes an outline
 * entry; the fence must close with a run of the same character at least as long
 * as the opener. Optional closing `#` runs are stripped from titles. A `#`
 * without a following space is emphasis/anchor text, not a heading.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object OutlineParse {

    /** One outline entry: 1-based level, title text, and 1-based line number. */
    data class Entry(val level: Int, val title: String, val line: Int)

    /**
     * Extracts the heading outline of [markdown]. Returns entries in document
     * order; a note with no headings yields an empty list.
     */
    fun outline(markdown: String): List<Entry> {
        val out = mutableListOf<Entry>()
        var fenceChar = '\u0000'
        var fenceLen = 0
        markdown.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trimEnd()
            val stripped = line.trimStart()
            if (fenceChar == '\u0000') {
                val tick = fenceRun(stripped, '`')
                val tilde = fenceRun(stripped, '~')
                if (tick >= 3 || tilde >= 3) {
                    fenceChar = if (tick >= 3) '`' else '~'
                    fenceLen = maxOf(tick, tilde)
                    return@forEachIndexed
                }
                heading(stripped)?.let { (level, title) ->
                    out += Entry(level, title, index + 1)
                }
            } else {
                val run = fenceRun(stripped, fenceChar)
                if (run >= fenceLen && stripped.drop(run).isBlank()) {
                    fenceChar = '\u0000'
                    fenceLen = 0
                }
            }
        }
        return out
    }

    private fun fenceRun(s: String, c: Char): Int {
        var n = 0
        while (n < s.length && s[n] == c) n++
        return n
    }

    private fun heading(stripped: String): Pair<Int, String>? {
        var hashes = 0
        while (hashes < stripped.length && stripped[hashes] == '#') hashes++
        if (hashes == 0 || hashes > 6) return null
        if (hashes < stripped.length && stripped[hashes] != ' ' && stripped[hashes] != '\t') return null
        var title = stripped.drop(hashes).trim()
        title = title.replace(Regex("\\s+#+\\s*$"), "").trim()
        if (title.isEmpty()) return null
        return hashes to title
    }
}
