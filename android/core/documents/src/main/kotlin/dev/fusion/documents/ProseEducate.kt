// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Span-aware prose typographer composing [SmartQuote] and [DashEducate] strictly outside
 * CommonMark fenced code blocks (``` / ~~~), CommonMark §6.1 multi-backtick inline code spans,
 * and bare `http://` / `https://` / `mailto:` / `file://` URLs.
 *
 * Completes R44 §J item 1 (editor/writer span-exclusion wiring) so CLI flags (`--dry-run`),
 * markdown table dividers (`----`), code literals (`"a--b"`), and URLs (`https://a--b.example`)
 * remain byte-identical while surrounding prose receives curly quotes and en/em dashes.
 */
object ProseEducate {
    const val SCOPE =
        "Fenced blocks (```/~~~), CommonMark backtick spans, and http/https/mailto/file URLs excluded around SmartQuote + DashEducate; HTML tags and entity decoding out of scope."

    const val MAX_CHARS = 262_144

    enum class ProtectedKind { FENCED_CODE, INLINE_CODE, URL }

    data class ProtectedSpan(val range: IntRange, val kind: ProtectedKind)

    data class EducatedProse(
        val text: String,
        val protectedSpans: List<ProtectedSpan>,
        val unbalancedDoubleQuotes: Int,
    )

    private val URL_PREFIXES = listOf("https://", "http://", "mailto:", "file://")

    fun detectProtectedSpans(text: String): List<ProtectedSpan> {
        require(text.length <= MAX_CHARS) { "text exceeds $MAX_CHARS chars" }
        val spans = ArrayList<ProtectedSpan>()
        var i = 0
        val n = text.length
        while (i < n) {
            val atLineStart = i == 0 || text[i - 1] == '\n'
            if (atLineStart) {
                var indent = i
                var spaces = 0
                while (indent < n && text[indent] == ' ' && spaces < 3) {
                    indent++
                    spaces++
                }
                if (indent < n && (text[indent] == '`' || text[indent] == '~')) {
                    val fenceChar = text[indent]
                    var fenceEnd = indent
                    while (fenceEnd < n && text[fenceEnd] == fenceChar) fenceEnd++
                    val fenceLen = fenceEnd - indent
                    if (fenceLen >= 3) {
                        val lineEnd = text.indexOf('\n', fenceEnd).let { if (it < 0) n else it }
                        val info = text.substring(fenceEnd, lineEnd)
                        if (fenceChar != '`' || '`' !in info) {
                            val closeEnd = findFenceClose(text, if (lineEnd < n) lineEnd + 1 else n, fenceChar, fenceLen)
                            spans.add(ProtectedSpan(i..(closeEnd - 1), ProtectedKind.FENCED_CODE))
                            i = closeEnd
                            continue
                        }
                    }
                }
            }
            if (text[i] == '`') {
                var tickEnd = i
                while (tickEnd < n && text[tickEnd] == '`') tickEnd++
                val tickLen = tickEnd - i
                val closeEnd = findInlineBacktickClose(text, tickEnd, tickLen)
                if (closeEnd > 0) {
                    spans.add(ProtectedSpan(i..(closeEnd - 1), ProtectedKind.INLINE_CODE))
                    i = closeEnd
                    continue
                }
                i = tickEnd
                continue
            }
            val urlPrefix = URL_PREFIXES.firstOrNull { text.regionMatches(i, it, 0, it.length, ignoreCase = true) }
            if (urlPrefix != null && (i == 0 || !isUrlWordPrev(text[i - 1]))) {
                val endExclusive = scanUrlEnd(text, i + urlPrefix.length)
                if (endExclusive > i + urlPrefix.length) {
                    spans.add(ProtectedSpan(i..(endExclusive - 1), ProtectedKind.URL))
                    i = endExclusive
                    continue
                }
            }
            i++
        }
        return spans
    }

    fun educate(text: String): EducatedProse {
        require(text.length <= MAX_CHARS) { "text exceeds $MAX_CHARS chars" }
        val spans = detectProtectedSpans(text)
        if (spans.isEmpty()) {
            val dashed = DashEducate.educate(text)
            val quoted = SmartQuote.educate(dashed)
            return EducatedProse(quoted, emptyList(), SmartQuote.unbalancedDoubles(quoted))
        }
        // Replace each protected span with an equal-length neutral letter sentinel ('x' repeated,
        // preserving leading/trailing whitespace semantics cleanly because code/URLs behave as word tokens)
        // so SmartQuote sees cross-span word context (`"hello" `code` "world"`) without mutating protected chars!
        val masked = StringBuilder(text.length)
        var cursor = 0
        for (sp in spans) {
            if (cursor < sp.range.first) {
                masked.append(text, cursor, sp.range.first)
            }
            val spanLen = sp.range.last - sp.range.first + 1
            if (sp.kind == ProtectedKind.FENCED_CODE) {
                for (k in sp.range.first..sp.range.last) {
                    masked.append(if (text[k] == '\n') '\n' else 'x')
                }
            } else {
                repeat(spanLen) { masked.append('x') }
            }
            cursor = sp.range.last + 1
        }
        if (cursor < text.length) masked.append(text, cursor, text.length)

        val smartMasked = SmartQuote.educate(masked.toString())
        val unbalanced = SmartQuote.unbalancedDoubles(smartMasked)

        // Restore exact protected characters at their exact indices (SmartQuote is 1-to-1 length-preserving!).
        check(smartMasked.length == text.length) { "SmartQuote invariant violated: length changed" }
        val restored = StringBuilder(text.length)
        var rCursor = 0
        for (sp in spans) {
            if (rCursor < sp.range.first) {
                restored.append(smartMasked, rCursor, sp.range.first)
            }
            restored.append(text, sp.range.first, sp.range.last + 1)
            rCursor = sp.range.last + 1
        }
        if (rCursor < text.length) restored.append(smartMasked, rCursor, text.length)

        val finalOut = DashEducate.educateOutside(restored.toString(), spans.map { it.range })
        return EducatedProse(finalOut, spans, unbalanced)
    }

    private fun findFenceClose(text: String, startPos: Int, fenceChar: Char, minLen: Int): Int {
        var pos = startPos
        val n = text.length
        while (pos < n) {
            val lineEnd = text.indexOf('\n', pos).let { if (it < 0) n else it }
            var p = pos
            var spaces = 0
            while (p < lineEnd && text[p] == ' ' && spaces < 3) {
                p++
                spaces++
            }
            var f = p
            while (f < lineEnd && text[f] == fenceChar) f++
            if (f - p >= minLen) {
                var tail = f
                while (tail < lineEnd && (text[tail] == ' ' || text[tail] == '\t' || text[tail] == '\r')) tail++
                if (tail == lineEnd) {
                    return if (lineEnd < n) lineEnd + 1 else n
                }
            }
            pos = if (lineEnd < n) lineEnd + 1 else n
        }
        return n
    }

    private fun findInlineBacktickClose(text: String, searchFrom: Int, targetLen: Int): Int {
        var i = searchFrom
        val n = text.length
        while (i < n) {
            if (text[i] == '`') {
                var end = i
                while (end < n && text[end] == '`') end++
                if (end - i == targetLen) return end
                i = end
            } else {
                i++
            }
        }
        return -1
    }

    private fun isUrlWordPrev(c: Char): Boolean = c.isLetterOrDigit()

    private fun scanUrlEnd(text: String, startAfterScheme: Int): Int {
        var i = startAfterScheme
        val n = text.length
        var openParens = 0
        while (i < n) {
            val c = text[i]
            if (c.isWhitespace() || c in "<>\"'`[]") break
            if (c == '(') openParens++
            if (c == ')') {
                if (openParens == 0) break
                openParens--
            }
            i++
        }
        while (i > startAfterScheme && text[i - 1] in ".,;:!?") {
            i--
        }
        return i
    }
}
