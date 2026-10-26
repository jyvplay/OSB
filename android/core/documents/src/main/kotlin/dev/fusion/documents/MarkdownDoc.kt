// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

import java.util.Collections

/**
 * Bounded CommonMark-subset block importer for notes interop: ATX headings, paragraphs,
 * fenced code blocks, bullet/ordered lists, blockquotes, thematic breaks and GFM-style
 * pipe tables. Inline markup (emphasis, links, images) is preserved as RAW TEXT — this
 * kernel reports block structure only, never rendered inline fidelity. Raw HTML tags are
 * stripped with a COUNTED disclosure (lossy import), never executed or passed through.
 */
object MarkdownDoc {
    const val MAX_BYTES = 256 * 1024
    const val MAX_LINES = 8192
    const val MAX_BLOCKS = 2048
    const val MAX_CELL = 2048

    sealed interface Block {
        data class Heading(val level: Int, val text: String) : Block {
            init { require(level in 1..6 && text.length in 1..2048) }
        }
        data class Paragraph(val text: String) : Block {
            init { require(text.length in 1..8192) }
        }
        data class Code(val info: String?, val text: String) : Block {
            init { require((info == null || info.length <= 64) && text.length <= 65536) }
        }
        data class BulletList(val items: List<String>) : Block {
            init { require(items.size in 1..1024 && items.all { it.length in 1..2048 }) }
        }
        data class OrderedList(val start: Int, val items: List<String>) : Block {
            init { require(start in 0..999999 && items.size in 1..1024 && items.all { it.length in 1..2048 }) }
        }
        data class Quote(val text: String) : Block {
            init { require(text.length in 1..8192) }
        }
        data object Rule : Block
        data class Table(val header: List<String>, val rows: List<List<String>>) : Block {
            init {
                require(header.size in 1..64 && header.all { it.length <= MAX_CELL })
                require(rows.size <= 1024 && rows.all { it.size == header.size && it.all { c -> c.length <= MAX_CELL } })
            }
        }
    }

    data class Document(val blocks: List<Block>, val strippedHtmlTags: Int)

    private val htmlTag = Regex("<[^<>\\n]{1,256}>")

    private fun stripHtml(line: String): Pair<String, Int> {
        var count = 0
        val out = htmlTag.replace(line) { count++; "" }
        return out to count
    }

    private fun splitRow(line: String): List<String>? {
        if ('|' !in line) return null
        val cells = line.trim().trim('|').split('|').map { it.trim() }
        if (cells.isEmpty() || cells.any { it.length > MAX_CELL }) return null
        return cells
    }

    private fun isDelimiter(line: String, width: Int): Boolean {
        val cells = splitRow(line) ?: return false
        if (cells.size != width) return false
        return cells.all { it.matches(Regex(":?-{1,64}:?")) }
    }

    fun parse(text: String): Document {
        require(text.length <= MAX_BYTES) { "Document exceeds $MAX_BYTES chars" }
        require('\u0000' !in text) { "NUL refused" }
        val lines = text.split('\n')
        require(lines.size <= MAX_LINES + 1) { "More than $MAX_LINES lines" }
        val body = if (lines.isNotEmpty() && lines.last().isEmpty()) lines.dropLast(1) else lines
        val blocks = mutableListOf<Block>()
        var stripped = 0
        fun push(block: Block) {
            require(blocks.size < MAX_BLOCKS) { "More than $MAX_BLOCKS blocks" }
            blocks += block
        }
        var i = 0
        val paragraph = mutableListOf<String>()
        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                val joined = paragraph.joinToString("\n").trim()
                if (joined.isNotEmpty()) push(Block.Paragraph(joined))
                paragraph.clear()
            }
        }
        while (i < body.size) {
            val raw = body[i]
            require(raw.length <= 8192) { "Line ${i + 1} too long" }
            val (line, cut) = stripHtml(raw)
            stripped += cut
            val trimmed = line.trim()
            if (trimmed.isEmpty()) { flushParagraph(); i++; continue }
            // Fenced code.
            val fence = if (trimmed.startsWith("```")) "```" else if (trimmed.startsWith("~~~")) "~~~" else null
            if (fence != null) {
                flushParagraph()
                val info = trimmed.removePrefix(fence).trim().takeIf { it.isNotEmpty() }?.take(64)
                if (fence == "```") require(info == null || '`' !in info) { "Backtick info string refused at line ${i + 1}" }
                val code = mutableListOf<String>()
                i++
                while (i < body.size && !body[i].trim().startsWith(fence)) {
                    require(body[i].length <= 8192) { "Code line too long" }
                    code += body[i].take(8192)
                    i++
                }
                require(i < body.size) { "Unclosed code fence" }
                push(Block.Code(info, code.joinToString("\n")))
                i++
                continue
            }
            // ATX heading.
            val heading = Regex("^(#{1,6})\\s+(\\S.*)$").matchEntire(trimmed)
            if (heading != null) {
                flushParagraph()
                push(Block.Heading(heading.groupValues[1].length, heading.groupValues[2].trim().take(2048)))
                i++
                continue
            }
            // Thematic break.
            if (trimmed.matches(Regex("((\\*\\s*){3,}|(-\\s*){3,}|(_\\s*){3,})"))) { flushParagraph(); push(Block.Rule); i++; continue }
            // GFM table.
            val header = splitRow(trimmed)
            if (header != null && i + 1 < body.size && isDelimiter(body[i + 1].trim(), header.size)) {
                flushParagraph()
                val rows = mutableListOf<List<String>>()
                i += 2
                while (i < body.size) {
                    val row = splitRow(body[i].trim()) ?: break
                    require(row.size == header.size) { "Ragged table row at line ${i + 1}" }
                    rows += row
                    i++
                }
                push(Block.Table(header, rows))
                continue
            }
            // Blockquote.
            if (trimmed.startsWith(">")) {
                flushParagraph()
                val quote = mutableListOf<String>()
                while (i < body.size && body[i].trim().startsWith(">")) {
                    quote += body[i].trim().removePrefix(">").trimStart()
                    i++
                }
                val joined = quote.joinToString("\n").trim()
                if (joined.isNotEmpty()) push(Block.Quote(joined.take(8192)))
                continue
            }
            // Lists.
            val bullet = Regex("^[-*+]\\s+(\\S.*)$").matchEntire(trimmed)
            val ordered = Regex("^(\\d{1,6})[.)]\\s+(\\S.*)$").matchEntire(trimmed)
            if (bullet != null || ordered != null) {
                flushParagraph()
                if (bullet != null) {
                    val items = mutableListOf<String>()
                    while (i < body.size) {
                        val m = Regex("^[-*+]\\s+(\\S.*)$").matchEntire(body[i].trim()) ?: break
                        items += m.groupValues[1].trim().take(2048)
                        i++
                    }
                    push(Block.BulletList(Collections.unmodifiableList(items)))
                } else {
                    val first = ordered!!.groupValues[1].toInt()
                    val items = mutableListOf<String>()
                    while (i < body.size) {
                        val m = Regex("^(\\d{1,6})[.)]\\s+(\\S.*)$").matchEntire(body[i].trim()) ?: break
                        items += m.groupValues[2].trim().take(2048)
                        i++
                    }
                    push(Block.OrderedList(first, Collections.unmodifiableList(items)))
                }
                continue
            }
            paragraph += trimmed.take(8192)
            i++
        }
        flushParagraph()
        require(blocks.isNotEmpty()) { "Empty document" }
        return Document(Collections.unmodifiableList(blocks), stripped)
    }
}
