// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Clean-room non-overlapping literal search & replace kernel for the editor/writer suite.
 *
 * Features:
 * - Case-sensitive and case-insensitive (`Locale.ROOT` character fold) matching.
 * - Whole-word boundary check (`isLetterOrDigit() || '_'`).
 * - Case-preserving replacement (`FOO` -> `BAR`, `Foo` -> `Bar`, `foo` -> `bar`) when enabled.
 * - Protected-span exclusion (`excludedRanges`, e.g. code blocks, read-only fields, or front-matter).
 */
object FindReplace {
    const val SCOPE =
        "Non-overlapping literal find/replace with whole-word, case-preserving, and excluded-range gates; full regex backreferences out of scope."

    const val MAX_TEXT_CHARS = 524_288
    const val MAX_QUERY_CHARS = 4_096

    data class Options(
        val caseSensitive: Boolean = true,
        val wholeWord: Boolean = false,
        val preserveCase: Boolean = false,
        val excludedRanges: List<IntRange> = emptyList(),
    )

    data class Match(val start: Int, val endExclusive: Int, val matchedText: String)

    data class ReplaceResult(
        val output: String,
        val replacedCount: Int,
        val matches: List<Match>,
    )

    fun findAll(text: String, query: String, options: Options = Options()): List<Match> {
        validate(text, query, options.excludedRanges)
        val excluded = normalizeRanges(options.excludedRanges)
        val out = ArrayList<Match>()
        var i = 0
        val limit = text.length - query.length
        while (i <= limit) {
            if (text.regionMatches(i, query, 0, query.length, ignoreCase = !options.caseSensitive)) {
                val endEx = i + query.length
                val wordOk = !options.wholeWord || (isWordBoundaryLeft(text, i) && isWordBoundaryRight(text, endEx))
                val rangeOk = !overlapsExcluded(i, endEx - 1, excluded)
                if (wordOk && rangeOk) {
                    out.add(Match(i, endEx, text.substring(i, endEx)))
                    i = endEx
                    continue
                }
            }
            i++
        }
        return out
    }

    fun replaceAll(
        text: String,
        query: String,
        replacement: String,
        options: Options = Options(),
    ): ReplaceResult {
        require(replacement.length <= MAX_QUERY_CHARS) { "replacement exceeds $MAX_QUERY_CHARS chars" }
        val matches = findAll(text, query, options)
        if (matches.isEmpty()) return ReplaceResult(text, 0, emptyList())
        val sb = StringBuilder(text.length + matches.size * maxOf(0, replacement.length - query.length))
        var cursor = 0
        for (m in matches) {
            if (cursor < m.start) sb.append(text, cursor, m.start)
            val rep = if (options.preserveCase) adaptCase(m.matchedText, replacement) else replacement
            sb.append(rep)
            cursor = m.endExclusive
        }
        if (cursor < text.length) sb.append(text, cursor, text.length)
        return ReplaceResult(sb.toString(), matches.size, matches)
    }

    private fun adaptCase(source: String, replacement: String): String {
        if (replacement.isEmpty()) return ""
        val letters = source.filter { it.isLetter() }
        if (letters.isEmpty()) return replacement
        return when {
            letters.all { it.isUpperCase() } -> replacement.uppercase()
            letters.all { it.isLowerCase() } -> replacement.lowercase()
            source[0].isUpperCase() && source.drop(1).filter { it.isLetter() }.all { it.isLowerCase() } ->
                replacement.substring(0, 1).uppercase() + replacement.substring(1).lowercase()
            else -> replacement
        }
    }

    private fun validate(text: String, query: String, excluded: List<IntRange>) {
        require(text.length <= MAX_TEXT_CHARS) { "text exceeds $MAX_TEXT_CHARS chars" }
        require(query.isNotEmpty()) { "search query must not be empty" }
        require(query.length <= MAX_QUERY_CHARS) { "query exceeds $MAX_QUERY_CHARS chars" }
        for (r in excluded) {
            if (r.isEmpty()) continue
            require(r.first >= 0 && r.last < text.length) {
                "excluded range $r out of bounds for length ${text.length}"
            }
        }
    }

    private fun normalizeRanges(ranges: List<IntRange>): List<IntRange> {
        val clean = ranges.filter { !it.isEmpty() }.sortedWith(compareBy<IntRange> { it.first }.thenBy { it.last })
        if (clean.size <= 1) return clean
        val merged = ArrayList<IntRange>(clean.size)
        var cur = clean[0]
        for (k in 1 until clean.size) {
            val nxt = clean[k]
            cur = if (nxt.first <= cur.last + 1) cur.first..maxOf(cur.last, nxt.last) else {
                merged.add(cur); nxt
            }
        }
        merged.add(cur)
        return merged
    }

    private fun overlapsExcluded(start: Int, lastInclusive: Int, sortedDisjoint: List<IntRange>): Boolean {
        var lo = 0
        var hi = sortedDisjoint.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val r = sortedDisjoint[mid]
            when {
                r.last < start -> lo = mid + 1
                r.first > lastInclusive -> hi = mid - 1
                else -> return true
            }
        }
        return false
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
    private fun isWordBoundaryLeft(text: String, pos: Int): Boolean =
        pos == 0 || !isWordChar(text[pos - 1]) || !isWordChar(text[pos])
    private fun isWordBoundaryRight(text: String, endEx: Int): Boolean =
        endEx >= text.length || !isWordChar(text[endEx - 1]) || !isWordChar(text[endEx])
}
