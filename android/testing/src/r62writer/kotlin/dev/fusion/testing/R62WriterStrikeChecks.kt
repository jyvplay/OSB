// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import dev.fusion.ooxml.PlainWriterDocx
import dev.fusion.ooxml.StructuredWriterDocx as Writer
import java.io.File

/** Actual encoder and bounded parser checks; external Word/Android are separate. */
object R62WriterStrikeChecks {
    private var assertions = 0
    private fun verify(value: Boolean, why: String) { assertions++; check(value) { why } }
    private fun refuses(why: String, f: () -> Unit) = verify(runCatching(f).exceptionOrNull() is IllegalArgumentException, why)
    private fun runs(block: DocxPackage.Block): List<DocxPackage.Run> = when (block) {
        is DocxPackage.Block.Paragraph -> block.runs
        is DocxPackage.Block.Heading -> block.runs
        is DocxPackage.Block.ListItem -> block.runs
        is DocxPackage.Block.BlockQuote -> block.runs
        else -> error("Unexpected block")
    }
    private fun one(s: String, strike: Boolean = true) = runs(DocxPackage.read(Writer.write(s, false, strike)).blocks.single())
    private fun source(i: Int) = listOf(
        "1. line-$i ~~drop~~ end", "# Heading ~~gone~~", "alpha **bold** + *ital* + `code` + ~~cross~~!",
        "- bullet ~~old~~", "left ~~bad ~~ right", "~single~ remains", "~~a **nested** b~~", "~~unclosed",
        "\\~~escaped~~", "~~~triple~~~", "> quote ~~q~~", "1. ~~🙂~~", ""
    ).joinToString("\n")

    @JvmStatic fun main(args: Array<String>) {
        val folder = File(args.single()).also { check(it.exists() || it.mkdirs()) }
        val plain = one("before ~~gone~~ after", false)
        verify(plain.size == 1 && plain.single().text == "before ~~gone~~ after" && !plain.single().strike, "off keeps markers")
        val on = one("before ~~gone~~ after")
        verify(on.map { it.text } == listOf("before ", "gone", " after") && on[1].strike && !on[1].bold, "strike only")
        val siblings = one("**bold** ~~gone~~ *italic* `code`")
        verify(siblings.any { it.bold && it.text == "bold" } && siblings.any { it.strike && it.text == "gone" } &&
            siblings.any { it.italic && it.text == "italic" } && siblings.any { it.code && it.text == "code" }, "distinct sibling styles")
        verify(one("~~a~b~~").single().strike && one("~~a~b~~").single().text == "a~b", "single inner tilde")
        for (raw in listOf("~~", "~~x", "~~~x~~~", "~~x~~~", "~~ x~~", "~~x ~~", "~~ ~~",
            "\\~~x~~", "~~x\\~~", "~~x *nested*~~", "*outer ~~inner~~*", "~~x~~ trailing ~~")) {
            val result = one(raw)
            verify(result.size == 1 && result.single().text == raw && !result.single().strike, "ambiguous line stays literal: $raw")
        }
        verify(one("~x~").single().text == "~x~" && !one("~x~").single().strike, "single tildes remain text")
        verify(Writer.write("x".repeat(PlainWriterDocx.MAX_TEXT_BYTES), false, true).isNotEmpty(), "exact text budget")
        refuses("over byte budget") { Writer.write("x".repeat(PlainWriterDocx.MAX_TEXT_BYTES + 1), false, true) }
        verify(Writer.write("x\n".repeat(PlainWriterDocx.MAX_PARAGRAPHS - 1) + "~~x~~", false, true).isNotEmpty(), "exact paragraph budget")
        refuses("over paragraph budget") { Writer.write("x\n".repeat(PlainWriterDocx.MAX_PARAGRAPHS) + "~~x~~", false, true) }
        verify(Writer.write("~~x~~ ".repeat(Writer.MAX_RUNS / 2), false, true).isNotEmpty(), "exact run budget")
        refuses("over run budget") { Writer.write("~~x~~ ".repeat(Writer.MAX_RUNS / 2 + 1), false, true) }
        refuses("unpaired surrogate") { Writer.write("~~\uD800~~", false, true) }
        refuses("invalid XML control") { Writer.write("~~\u0000~~", false, true) }
        val cases = mutableListOf("id,numbered,strike")
        var id = 0
        for (numbered in listOf(false, true)) for (strike in listOf(false, true)) repeat(64) { i ->
            val raw = source(i)
            val bytes = Writer.write(raw, numbered, strike)
            if (!strike) verify(bytes.contentEquals(Writer.write(raw, numbered)), "legacy overload $id")
            verify(bytes.contentEquals(Writer.write(raw, numbered, strike)), "determinism $id")
            val blocks = DocxPackage.read(bytes).blocks
            verify(blocks.size == 13, "line count $id")
            verify((blocks[0] is DocxPackage.Block.ListItem) == numbered, "number option $id")
            verify(runs(blocks[0]).any { it.strike } == strike, "strike option $id")
            verify(runs(blocks[6]).singleOrNull()?.text == "~~a **nested** b~~" || !strike, "nested literal $id")
            val name = id++.toString().padStart(3, '0')
            File(folder, "doc-$name.docx").writeBytes(bytes)
            cases.add("$name,$numbered,$strike")
        }
        verify(id == 256, "four option combinations")
        File(folder, "cases.csv").writeText(cases.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        println("R62 Writer strike: $assertions assertions; $id DOCX files, independent reader pending")
    }
}
