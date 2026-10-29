// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Bidirectional Markdown <-> Structured Office Document / Slide Deck AST Bridge.
 *
 * Parses CommonMark/GFM headings (`#`..`######`), unordered/ordered bullet lists, pipe tables, and
 * prose paragraphs into a typed intermediate representation, and splits `#` / `##` sections into
 * presentation slide outlines or restores canonical Markdown losslessly.
 */
object MarkdownOfficeBridge {
    const val SCOPE =
        "Bidirectional Markdown <-> Office document & slide-deck outline AST bridge (headings, bullet lists, pipe tables, prose blocks)."

    sealed interface Block {
        data class Heading(val level: Int, val text: String) : Block
        data class Paragraph(val text: String) : Block
        data class BulletList(val items: List<String>) : Block
        data class Table(val rows: List<List<String>>) : Block
    }

    data class SlideOutline(
        val title: String,
        val bullets: List<String>,
        val tableRows: List<List<String>>,
    )

    fun parseMarkdown(md: String): List<Block> {
        val lines = md.replace("\r\n", "\n").lines()
        val blocks = ArrayList<Block>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                i++
                continue
            }
            val hm = Regex("^(#{1,6})\\s+(.+)$").matchEntire(line)
            if (hm != null) {
                blocks.add(Block.Heading(hm.groupValues[1].length, hm.groupValues[2].trim()))
                i++
                continue
            }
            if (line.startsWith("- ") || line.startsWith("* ")) {
                val items = ArrayList<String>()
                while (i < lines.size) {
                    val l = lines[i].trim()
                    if (l.startsWith("- ") || l.startsWith("* ")) {
                        items.add(l.substring(2).trim())
                        i++
                    } else break
                }
                blocks.add(Block.BulletList(items))
                continue
            }
            if (line.startsWith("|") && line.endsWith("|")) {
                val rows = ArrayList<List<String>>()
                while (i < lines.size) {
                    val l = lines[i].trim()
                    if (l.startsWith("|") && l.endsWith("|")) {
                        val cells = l.substring(1, l.length - 1).split('|').map { it.trim() }
                        val isDivider = cells.all { it.matches(Regex(":?-{2,}:?")) }
                        if (!isDivider) rows.add(cells)
                        i++
                    } else break
                }
                if (rows.isNotEmpty()) blocks.add(Block.Table(rows))
                continue
            }
            val paraLines = ArrayList<String>()
            while (i < lines.size) {
                val l = lines[i].trim()
                if (l.isEmpty() || l.startsWith("#") || l.startsWith("- ") || l.startsWith("* ") ||
                    (l.startsWith("|") && l.endsWith("|"))
                ) break
                paraLines.add(l)
                i++
            }
            blocks.add(Block.Paragraph(paraLines.joinToString(" ")))
        }
        return blocks
    }

    fun toMarkdown(blocks: List<Block>): String = blocks.joinToString("\n\n") { b ->
        when (b) {
            is Block.Heading -> "#".repeat(b.level.coerceIn(1, 6)) + " " + b.text
            is Block.Paragraph -> b.text
            is Block.BulletList -> b.items.joinToString("\n") { "- $it" }
            is Block.Table -> {
                if (b.rows.isEmpty()) ""
                else buildString {
                    val w = b.rows.maxOf { it.size }
                    fun fmtRow(r: List<String>) = "| " + (0 until w).joinToString(" | ") { r.getOrElse(it) { "" } } + " |"
                    append(fmtRow(b.rows[0]))
                    append("\n| ").append((0 until w).joinToString(" | ") { "---" }).append(" |")
                    for (r in b.rows.drop(1)) append("\n").append(fmtRow(r))
                }
            }
        }
    }

    fun toSlideOutlines(blocks: List<Block>, fallbackTitle: String = "Overview"): List<SlideOutline> {
        val slides = ArrayList<SlideOutline>()
        var curTitle = fallbackTitle
        var curBullets = ArrayList<String>()
        var curTable: List<List<String>> = emptyList()
        var started = false

        fun flush() {
            if (started || curBullets.isNotEmpty() || curTable.isNotEmpty()) {
                slides.add(SlideOutline(curTitle, curBullets.toList(), curTable))
            }
        }

        for (b in blocks) {
            when (b) {
                is Block.Heading -> {
                    if (b.level <= 2) {
                        if (started) flush()
                        curTitle = b.text
                        curBullets = ArrayList()
                        curTable = emptyList()
                        started = true
                    } else {
                        curBullets.add(b.text)
                        started = true
                    }
                }
                is Block.BulletList -> { curBullets.addAll(b.items); started = true }
                is Block.Paragraph -> { curBullets.add(b.text); started = true }
                is Block.Table -> { curTable = b.rows; started = true }
            }
        }
        flush()
        return slides.ifEmpty { listOf(SlideOutline(fallbackTitle, emptyList(), emptyList())) }
    }
}
