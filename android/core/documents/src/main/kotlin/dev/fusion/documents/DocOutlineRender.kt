// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Bidirectional structured Writer Document <-> Markdown & HTML5 interchange renderer.
 *
 * Connects [PieceTable] / [ProseEducate] / Markdown editing to clean semantic HTML5 and canonical
 * Markdown export without requiring a browser DOM or WebView. Escapes all raw HTML characters inside
 * runs so embedded `<script>` or `<img onerror=...>` payloads are neutralized by construction.
 */
object DocOutlineRender {
    const val SCOPE =
        "Bidirectional CommonMark-subset <-> structured Writer AST + XSS-escaped semantic HTML5 renderer; raw inline HTML passthrough intentionally escaped."

    const val MAX_CHARS = 524_288

    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
    )

    sealed interface Node {
        data class Heading(val level: Int, val spans: List<Span>) : Node {
            init {
                require(level in 1..6) { "heading level $level outside 1..6" }
            }
        }

        data class Paragraph(val spans: List<Span>) : Node
        data class CodeBlock(val language: String, val code: String) : Node
        data class ListBlock(val ordered: Boolean, val items: List<List<Span>>) : Node
        data class TableBlock(val headers: List<String>, val rows: List<List<String>>) : Node
    }

    fun parseMarkdown(markdown: String): List<Node> {
        require(markdown.length <= MAX_CHARS) { "markdown exceeds $MAX_CHARS chars" }
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = ArrayList<Node>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()
            if (trimmed.isEmpty()) {
                i++
                continue
            }
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim()
                val buf = ArrayList<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    buf.add(lines[i])
                    i++
                }
                if (i < lines.size) i++
                out.add(Node.CodeBlock(lang, buf.joinToString("\n")))
                continue
            }
            val hMatch = Regex("""^(#{1,6})\s+(.*)$""").find(trimmed)
            if (hMatch != null) {
                val lvl = hMatch.groupValues[1].length
                val body = hMatch.groupValues[2].trimEnd('#', ' ')
                out.add(Node.Heading(lvl, parseInlineSpans(body)))
                i++
                continue
            }
            if (trimmed.startsWith("|") && i + 1 < lines.size && Regex("""^\s*\|?[\s:-]+\|[\s|:-]*$""").matches(lines[i + 1])) {
                val headers = splitPipeRow(trimmed)
                i += 2
                val rows = ArrayList<List<String>>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    val r = splitPipeRow(lines[i].trimStart())
                    val padded = List(headers.size) { idx -> r.getOrElse(idx) { "" } }
                    rows.add(padded)
                    i++
                }
                out.add(Node.TableBlock(headers, rows))
                continue
            }
            val ulMatch = Regex("""^[-*+]\s+(.*)$""").find(trimmed)
            val olMatch = Regex("""^\d+\.\s+(.*)$""").find(trimmed)
            if (ulMatch != null || olMatch != null) {
                val ordered = olMatch != null
                val items = ArrayList<List<Span>>()
                while (i < lines.size) {
                    val cur = lines[i].trimStart()
                    val m = if (ordered) Regex("""^\d+\.\s+(.*)$""").find(cur) else Regex("""^[-*+]\s+(.*)$""").find(cur)
                    if (m == null) break
                    items.add(parseInlineSpans(m.groupValues[1]))
                    i++
                }
                out.add(Node.ListBlock(ordered, items))
                continue
            }
            val paraLines = ArrayList<String>()
            while (i < lines.size) {
                val cur = lines[i].trimStart()
                if (cur.isEmpty() || cur.startsWith("#") || cur.startsWith("```") || cur.startsWith("|") ||
                    Regex("""^([-*+]|\d+\.)\s+""").containsMatchIn(cur)
                ) break
                paraLines.add(cur)
                i++
            }
            out.add(Node.Paragraph(parseInlineSpans(paraLines.joinToString(" "))))
        }
        return out
    }

    fun toHtml(nodes: List<Node>): String = buildString {
        append("<article class=\"fusion-doc\">")
        for (n in nodes) {
            when (n) {
                is Node.Heading -> append("<h${n.level}>${spansToHtml(n.spans)}</h${n.level}>")
                is Node.Paragraph -> append("<p>${spansToHtml(n.spans)}</p>")
                is Node.CodeBlock -> {
                    val cls = if (n.language.isNotEmpty()) " class=\"language-${escHtml(n.language)}\"" else ""
                    append("<pre><code$cls>${escHtml(n.code)}</code></pre>")
                }
                is Node.ListBlock -> {
                    val tag = if (n.ordered) "ol" else "ul"
                    append("<$tag>")
                    for (it in n.items) append("<li>${spansToHtml(it)}</li>")
                    append("</$tag>")
                }
                is Node.TableBlock -> {
                    append("<table><thead><tr>")
                    for (h in n.headers) append("<th>${escHtml(h)}</th>")
                    append("</tr></thead><tbody>")
                    for (r in n.rows) {
                        append("<tr>")
                        for (c in r) append("<td>${escHtml(c)}</td>")
                        append("</tr>")
                    }
                    append("</tbody></table>")
                }
            }
        }
        append("</article>")
    }

    fun toMarkdown(nodes: List<Node>): String = nodes.joinToString("\n\n") { n ->
        when (n) {
            is Node.Heading -> "${"#".repeat(n.level)} ${spansToMd(n.spans)}"
            is Node.Paragraph -> spansToMd(n.spans)
            is Node.CodeBlock -> "```${n.language}\n${n.code}\n```"
            is Node.ListBlock -> n.items.mapIndexed { idx, sp ->
                val pfx = if (n.ordered) "${idx + 1}. " else "- "
                pfx + spansToMd(sp)
            }.joinToString("\n")
            is Node.TableBlock -> buildString {
                append("| ").append(n.headers.joinToString(" | ")).append(" |\n")
                append("| ").append(n.headers.joinToString(" | ") { "---" }).append(" |")
                for (r in n.rows) {
                    append("\n| ").append(r.joinToString(" | ")).append(" |")
                }
            }
        }
    }

    fun parseInlineSpans(text: String): List<Span> {
        val out = ArrayList<Span>()
        val buf = StringBuilder()
        var i = 0
        fun flushPlain() {
            if (buf.isNotEmpty()) {
                out.add(Span(buf.toString()))
                buf.setLength(0)
            }
        }
        while (i < text.length) {
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    flushPlain()
                    out.add(Span(text.substring(i + 1, end), code = true))
                    i = end + 1
                    continue
                }
            }
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end > i + 1) {
                    flushPlain()
                    out.add(Span(text.substring(i + 2, end), bold = true))
                    i = end + 2
                    continue
                }
            }
            if (text[i] == '*') {
                val end = text.indexOf('*', i + 1)
                if (end > i) {
                    flushPlain()
                    out.add(Span(text.substring(i + 1, end), italic = true))
                    i = end + 1
                    continue
                }
            }
            buf.append(text[i])
            i++
        }
        flushPlain()
        return out
    }

    private fun spansToHtml(spans: List<Span>): String = spans.joinToString("") { s ->
        val e = escHtml(s.text)
        when {
            s.code -> "<code>$e</code>"
            s.bold && s.italic -> "<strong><em>$e</em></strong>"
            s.bold -> "<strong>$e</strong>"
            s.italic -> "<em>$e</em>"
            else -> e
        }
    }

    private fun spansToMd(spans: List<Span>): String = spans.joinToString("") { s ->
        when {
            s.code -> "`${s.text}`"
            s.bold && s.italic -> "***${s.text}***"
            s.bold -> "**${s.text}**"
            s.italic -> "*${s.text}*"
            else -> s.text
        }
    }

    private fun splitPipeRow(row: String): List<String> =
        row.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    private fun escHtml(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}
