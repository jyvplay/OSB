package dev.fusion.documents

/**
 * Explicit-bidi-control hygiene for the writer (the Trojan Source
 * class: U+202A..U+202E embeddings/overrides, U+2066..U+2069 isolates,
 * U+200E/U+200F marks can make displayed text diverge from logical
 * text).
 *
 * [positions] reports every control; [strip] removes them;
 * [isBalanced] checks that every embedding/override/isolate push has
 * its matching pop (PDF/PDI) — UNBALANCED text is the suspicious
 * shape even when controls are legitimate.
 *
 * Declared narrowings: explicit controls only — no UBA implementation,
 * no rendering claim; stripping can change the DISPLAY of legitimate
 * RTL text and the caller must surface that choice to the user, never
 * apply it silently (stated here so the UI contract is pinned).
 */
object BidiStrip {
    const val SCOPE = "Explicit bidi controls: detect/strip/balance only; no UBA; stripping is a user-visible choice."
    private val PUSH = setOf('\u202A', '\u202B', '\u202D', '\u202E', '\u2066', '\u2067', '\u2068')
    private val POP = setOf('\u202C', '\u2069')
    private val MARKS = setOf('\u200E', '\u200F')

    fun isControl(c: Char): Boolean = c in PUSH || c in POP || c in MARKS

    fun positions(text: String): List<Int> = text.indices.filter { isControl(text[it]) }

    fun strip(text: String): String = text.filter { !isControl(it) }

    fun isBalanced(text: String): Boolean {
        var depth = 0
        for (c in text) {
            if (c in PUSH) depth++
            else if (c in POP) { depth--; if (depth < 0) return false }
        }
        return depth == 0
    }

    /** The audit verdict a paste gate consumes. */
    fun audit(text: String): Pair<Int, Boolean> = positions(text).size to isBalanced(text)
}
