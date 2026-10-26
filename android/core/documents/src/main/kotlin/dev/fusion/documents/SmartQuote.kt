package dev.fusion.documents

/**
 * Typographic quote education for the writer surface.
 *
 * Contract (stated, not imported): straight `"` and `'` become U+201C/U+201D
 * and U+2018/U+2019 by local context. A quote OPENS when it sits at the start
 * of the text or after whitespace, an opening bracket `([{`, an en/em dash,
 * or another opening quote; otherwise it CLOSES. An apostrophe between two
 * letters, or before a digit (decade elision, '90s), is always U+2019.
 *
 * Declared narrowings: word-initial elisions ('Tis, 'twas) are NOT
 * special-cased — they educate as opening singles, which is the documented
 * wrong-but-deterministic choice; a dictionary pass is future work. Already
 * curly input passes through untouched. No locale styles (guillemets,
 * low-9 quotes) — out of scope.
 */
object SmartQuote {
    const val SCOPE = "Context-rule educator only; elision words not special-cased; no locale quote styles."
    private const val OPENERS = "([{\u2018\u201C\u2013\u2014"

    private fun opensAfter(prev: Char?): Boolean =
        prev == null || prev.isWhitespace() || prev in OPENERS

    fun educate(text: String): String {
        val sb = StringBuilder(text.length)
        for (i in text.indices) {
            val c = text[i]
            val prev = if (i == 0) null else text[i - 1]
            val next = if (i == text.length - 1) null else text[i + 1]
            when (c) {
                '"' -> sb.append(if (opensAfter(prev)) '\u201C' else '\u201D')
                '\'' -> sb.append(
                    when {
                        prev != null && prev.isLetter() && next != null && next.isLetter() -> '\u2019'
                        next != null && next.isDigit() && (prev == null || prev.isWhitespace()) -> '\u2019'
                        opensAfter(prev) -> '\u2018'
                        else -> '\u2019'
                    }
                )
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** Count of double-quote pairs that fail to balance after education. */
    fun unbalancedDoubles(text: String): Int {
        val e = educate(text)
        val open = e.count { it == '\u201C' }
        val close = e.count { it == '\u201D' }
        return kotlin.math.abs(open - close)
    }
}
