// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import dev.fusion.ooxml.PlainWriterDocx
import dev.fusion.ooxml.StructuredWriterDocx
import java.io.File
import java.util.Random

/** Executed contracts for one bounded opt-in Writer conversion, not arbitrary DOCX fidelity. */
object R57Checks {
    private var assertions = 0
    private fun verify(ok: Boolean, label: String) { assertions++; check(ok) { label } }
    private inline fun refuses(label: String, block: () -> Unit) {
        assertions++
        check(runCatching(block).isFailure) { "Expected refusal: $label" }
    }
    private fun blocks(text: String) = DocxPackage.read(StructuredWriterDocx.write(text)).blocks
    private fun content(block: DocxPackage.Block): String = when (block) {
        is DocxPackage.Block.Heading -> block.runs.joinToString("") { it.text }
        is DocxPackage.Block.Paragraph -> block.runs.joinToString("") { it.text }
        is DocxPackage.Block.ListItem -> block.runs.joinToString("") { it.text }
        is DocxPackage.Block.BlockQuote -> block.runs.joinToString("") { it.text }
        else -> error("Unexpected block $block")
    }

    @JvmStatic fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val source = "# Heading & <title>\r\n## Second\n- item **bold** and *italic*\n> quote `code`\n  plain\t\uD83D\uDE42  \n\nlast\n"
        val encoded = StructuredWriterDocx.write(source)
        File(out, "structured.docx").writeBytes(encoded)
        verify(encoded.contentEquals(StructuredWriterDocx.write(source)), "deterministic structured package")
        val items = DocxPackage.read(encoded).blocks
        verify(items.size == 8, "line and trailing blank count")
        verify(items[0] is DocxPackage.Block.Heading && (items[0] as DocxPackage.Block.Heading).level == 1, "heading level 1")
        verify(items[1] is DocxPackage.Block.Heading && (items[1] as DocxPackage.Block.Heading).level == 2, "heading level 2")
        verify(content(items[0]) == "Heading & <title>", "heading text and XML escaping")
        val bullet = items[2] as DocxPackage.Block.ListItem
        verify(!bullet.ordered && bullet.level == 0 && content(bullet) == "item bold and italic", "bullet text")
        verify(bullet.runs.any { it.text == "bold" && it.bold && !it.italic }, "bold run")
        verify(bullet.runs.any { it.text == "italic" && it.italic && !it.bold }, "italic run")
        val quote = items[3] as DocxPackage.Block.BlockQuote
        verify(content(quote) == "quote code" && quote.runs.any { it.text == "code" && it.code }, "quote and monospace")
        verify(content(items[4]) == "  plain\t\uD83D\uDE42  ", "tabs, whitespace and supplementary Unicode")
        verify(content(items[5]).isEmpty() && content(items[6]) == "last" && content(items[7]).isEmpty(), "empty and trailing paragraphs")

        val literalLines = listOf("#tag", "####### not a heading", "# ", "- ", "> ", "| a | b |", "[link](https://example.invalid)",
            "**unmatched", "*one **nested** end*", "***ambiguous***", "\\*escaped*", "`code **nested**`", "x *not closed")
        val literal = literalLines.joinToString("\n")
        File(out, "literal.docx").writeBytes(StructuredWriterDocx.write(literal))
        val literalBlocks = blocks(literal)
        verify(literalBlocks.size == literalLines.size, "literal count")
        literalLines.forEachIndexed { index, line ->
            verify(literalBlocks[index] is DocxPackage.Block.Paragraph && content(literalBlocks[index]) == line, "literal fallback $index")
        }
        val fallback = blocks("# heading *not closed").single() as DocxPackage.Block.Heading
        verify(content(fallback) == "heading *not closed", "block prefix survives inline fallback")
        val adjacent = blocks("A **bold** B *italic* C `code` D").single() as DocxPackage.Block.Paragraph
        verify(adjacent.runs.map { it.text } == listOf("A ", "bold", " B ", "italic", " C ", "code", " D"), "mixed text/run boundaries")
        verify(adjacent.runs[1].bold && adjacent.runs[3].italic && adjacent.runs[5].code, "mixed formatting flags")
        for (level in 1..6) {
            val result = blocks("#".repeat(level) + " title").single() as DocxPackage.Block.Heading
            verify(result.level == level && content(result) == "title", "all six heading levels")
        }
        val plain = PlainWriterDocx.write(source)
        File(out, "plain.docx").writeBytes(plain)
        verify(content(DocxPackage.read(plain).blocks[0]) == "# Heading & <title>", "plain export remains literal")
        verify(StructuredWriterDocx.write("").isNotEmpty(), "empty document")
        refuses("invalid XML control") { StructuredWriterDocx.write("a\u0000b") }
        refuses("unpaired high surrogate") { StructuredWriterDocx.write("\uD800") }
        refuses("unpaired low surrogate") { StructuredWriterDocx.write("\uDC00") }
        verify(StructuredWriterDocx.write("x".repeat(PlainWriterDocx.MAX_TEXT_BYTES)).isNotEmpty(), "exact ASCII byte limit")
        refuses("over ASCII byte limit") { StructuredWriterDocx.write("x".repeat(PlainWriterDocx.MAX_TEXT_BYTES + 1)) }
        verify(StructuredWriterDocx.write("\u00E9".repeat(PlainWriterDocx.MAX_TEXT_BYTES / 2)).isNotEmpty(), "exact multibyte limit")
        refuses("over multibyte limit") { StructuredWriterDocx.write("\u00E9".repeat(PlainWriterDocx.MAX_TEXT_BYTES / 2 + 1)) }
        verify(StructuredWriterDocx.write("\n".repeat(PlainWriterDocx.MAX_PARAGRAPHS - 1)).isNotEmpty(), "exact line limit")
        refuses("over line limit") { StructuredWriterDocx.write("\n".repeat(PlainWriterDocx.MAX_PARAGRAPHS)) }
        verify(StructuredWriterDocx.write("*a* ".repeat(StructuredWriterDocx.MAX_RUNS / 2)).isNotEmpty(), "exact run limit")
        refuses("over run limit") { StructuredWriterDocx.write("*a* ".repeat(StructuredWriterDocx.MAX_RUNS / 2 + 1)) }
        // Fuzz literal-only text including controls allowed in XML, supplementary codepoints and punctuation.
        val random = Random(5701)
        val alphabet = listOf("a", "Z", " ", "\t", "&", "<", ">", "#", "-", "é", "\uD83D\uDE42")
        repeat(500) { n ->
            val text = buildString { append('x'); repeat(random.nextInt(100)) { append(alphabet[random.nextInt(alphabet.size)]) } }
            verify(content(blocks(text).single()) == text, "literal fuzz $n")
        }
        println("R57 Writer host contracts: $assertions assertions passed; structured/plain DOCX artifacts emitted (not independently verified yet)")
    }
}
