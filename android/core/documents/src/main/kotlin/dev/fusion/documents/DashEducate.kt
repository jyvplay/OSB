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

    fun educate(text: String): String {
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
