// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

/**
 * Match-window snippet cutting for search hits.
 *
 * Sits next to [LocalIndex] (which returns whole [Doc]s with scores):
 * nothing cuts the "…context [match] context…" preview a results list
 * actually displays. Given text plus a match span, this keeps up to
 * [radius] characters on each side and marks trims with U+2026, returning
 * the match offsets rebased into the snippet so the UI can highlight
 * without re-searching. Windows never split the match itself; when the
 * text fits, it is returned whole with no ellipsis. Matching is caller
 * duty — this kernel cuts windows, it does not find hits, so it cannot
 * disagree with the index about what matched.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object SnippetCut {
    const val MAX_RADIUS = 1000

    /** A cut snippet: display text plus match span [lo, hi) inside it. */
    data class Snip(val text: String, val lo: Int, val hi: Int)

    /**
     * Cuts a [radius]-context window around text[start, end).
     *
     * @throws IllegalArgumentException on an empty text, an out-of-bounds
     * or inverted span, or an over-cap radius.
     */
    fun window(text: String, start: Int, end: Int, radius: Int = 40): Snip {
        require(text.isNotEmpty()) { "empty text" }
        require(radius in 0..MAX_RADIUS) { "bad radius" }
        require(start in 0..end && end <= text.length && start < end) { "bad span" }
        val lo = maxOf(0, start - radius)
        val hi = minOf(text.length, end + radius)
        val sb = StringBuilder()
        if (lo > 0) sb.append('…')
        sb.append(text.substring(lo, hi))
        if (hi < text.length) sb.append('…')
        val cut = sb.toString()
        val base = if (lo > 0) 1 else 0
        return Snip(cut, base + start - lo, base + end - lo)
    }
}
