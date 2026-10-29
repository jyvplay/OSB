package dev.fusion.documents

/**
 * Dash and ellipsis education for the writer surface (TeX input
 * conventions: `--` is an en dash, `---` an em dash, `...` an ellipsis).
 *
 * Longest-match scanning: `---` wins over `--`; runs of four or more
 * hyphens are left untouched (they are rules/separators, not prose).
 * A single `-` is NEVER touched (hyphenated-words, list markers, minus
 * signs all survive). `....` and longer dot runs are left untouched for
 * the same reason.
 *
 * Declared narrowings: plain prose only — no code-span or URL awareness
 * (callers exclude those ranges first; the editor already tracks them),
 * and no locale spacing rules around the dashes.
 */
object DashEducate {
    const val EN = '\u2013'
    const val EM = '\u2014'
    const val ELLIPSIS = '\u2026'
    const val SCOPE = "TeX-convention -- / --- / ... education only; single hyphens and 4+ runs untouched; no code awareness."

    fun educate(text: String): String = educateOutside(text, emptyList())

    /**
     * Educates `--`, `---`, and `...` strictly outside [excludedRanges] (inclusive UTF-16
     * `IntRange`s such as code spans, fenced blocks, and URLs). Runs inside excluded slices
     * are preserved verbatim.
     */
    fun educateOutside(text: String, excludedRanges: List<IntRange>): String {
        val normalized = normalizeExcluded(excludedRanges, text.length)
        if (normalized.isEmpty()) return educateSlice(text)
        val out = StringBuilder(text.length)
        var cursor = 0
        for (ex in normalized) {
            if (cursor < ex.first) out.append(educateSlice(text.substring(cursor, ex.first)))
            out.append(text, ex.first, ex.last + 1)
            cursor = ex.last + 1
        }
        if (cursor < text.length) out.append(educateSlice(text.substring(cursor)))
        return out.toString()
    }

    private fun normalizeExcluded(ranges: List<IntRange>, len: Int): List<IntRange> {
        val nonEmpty = ArrayList<IntRange>(ranges.size)
        for (r in ranges) {
            if (r.isEmpty()) continue
            require(r.first >= 0 && r.last < len) { "excluded range $r out of bounds for length $len" }
            nonEmpty.add(r)
        }
        if (nonEmpty.size <= 1) return nonEmpty
        nonEmpty.sortWith(compareBy<IntRange> { it.first }.thenBy { it.last })
        val merged = ArrayList<IntRange>(nonEmpty.size)
        var cur = nonEmpty[0]
        for (k in 1 until nonEmpty.size) {
            val nxt = nonEmpty[k]
            cur = if (nxt.first <= cur.last + 1) cur.first..maxOf(cur.last, nxt.last) else {
                merged.add(cur); nxt
            }
        }
        merged.add(cur)
        return merged
    }

    private fun educateSlice(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '-') {
                var run = 0
                while (i + run < text.length && text[i + run] == '-') run++
                when (run) {
                    2 -> sb.append(EN)
                    3 -> sb.append(EM)
                    else -> repeat(run) { sb.append('-') }
                }
                i += run
            } else if (c == '.') {
                var run = 0
                while (i + run < text.length && text[i + run] == '.') run++
                if (run == 3) sb.append(ELLIPSIS) else repeat(run) { sb.append('.') }
                i += run
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    /** Inverse for export to plain-ASCII targets. */
    fun deEducate(text: String): String =
        text.replace(EM.toString(), "---").replace(EN.toString(), "--").replace(ELLIPSIS.toString(), "...")
}
