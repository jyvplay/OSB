// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.documents.TextDocument
import dev.fusion.documents.TextEncoding
import dev.fusion.documents.WriterCopyConsent
import dev.fusion.ooxml.DocxPackage
import dev.fusion.ooxml.PlainWriterDocx
import dev.fusion.ooxml.StructuredWriterDocx
import java.io.File

/** Actual production encoder and consent object on JVM; independent reader and Android are separate. */
object R60WriterChecks {
    private var assertions = 0
    private fun verify(value: Boolean, message: String) { check(value) { message }; assertions++ }
    private fun refuses(message: String, action: () -> Unit) {
        val failure = runCatching(action).exceptionOrNull()
        verify(failure != null, "Expected refusal: $message")
    }
    private fun text(block: DocxPackage.Block): String = when (block) {
        is DocxPackage.Block.Paragraph -> block.runs.joinToString("") { it.text }
        is DocxPackage.Block.ListItem -> block.runs.joinToString("") { it.text }
        is DocxPackage.Block.Heading -> block.runs.joinToString("") { it.text }
        is DocxPackage.Block.BlockQuote -> block.runs.joinToString("") { it.text }
        else -> error("Unexpected block")
    }
    private fun source(i: Int) = listOf(
        "1. item-$i **bold** α😀", "1. second-$i\tend", "plain-$i", "1. later", "- bullet",
        "2. literal", "01. literal", "1. ", "  1. nested", "# heading", ""
    ).joinToString("\n")

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1)
        val folder = File(args.single()).also { check(it.exists() || it.mkdirs()) }
        val sample = "1. first\n1. **second**\nplain\n1. last\n2. literal\n01. literal\n1. \n 1. indented\n1) literal"
        val defaults = DocxPackage.read(StructuredWriterDocx.write(sample)).blocks
        val explicitOff = StructuredWriterDocx.write(sample, numberedLines = false)
        verify(StructuredWriterDocx.write(sample).contentEquals(explicitOff), "old entry point matches explicitly disabled option")
        verify(defaults.all { it is DocxPackage.Block.Paragraph }, "default numeric-looking lines remain paragraphs")
        verify(text(defaults.first()) == "1. first" && text(defaults[1]) == "1. second", "default keeps literal prefixes")
        val enabled = DocxPackage.read(StructuredWriterDocx.write(sample, numberedLines = true)).blocks
        verify(enabled.size == defaults.size, "paragraph count unchanged")
        for (index in listOf(0, 1, 3)) {
            val item = enabled[index] as? DocxPackage.Block.ListItem
            verify(item != null && item.ordered && item.level == 0, "explicit numbered item $index")
        }
        verify(text(enabled[0]) == "first" && text(enabled[1]) == "second", "only the exact prefix removed")
        for (index in listOf(2, 4, 5, 6, 7, 8)) {
            verify(enabled[index] is DocxPackage.Block.Paragraph && text(enabled[index]) == text(defaults[index]), "literal boundary $index")
        }
        val edge = DocxPackage.read(StructuredWriterDocx.write("1.  \n1.\tno\n1.1 nested\n1. 🙂", true)).blocks
        verify(edge[0] is DocxPackage.Block.ListItem && text(edge[0]) == " ", "one following space is a character")
        verify(edge[1] is DocxPackage.Block.Paragraph && edge[2] is DocxPackage.Block.Paragraph, "tab and decimal are literal")
        verify(edge[3] is DocxPackage.Block.ListItem && text(edge[3]) == "🙂", "supplementary character admitted")
        val ambiguous = DocxPackage.read(StructuredWriterDocx.write("1. *a **nested** b*", true)).blocks.single()
        verify(ambiguous is DocxPackage.Block.ListItem && ambiguous.runs.single().text == "*a **nested** b*", "ambiguous inline text is literal")
        verify(StructuredWriterDocx.write("1. " + "x".repeat(PlainWriterDocx.MAX_TEXT_BYTES - 3), true).isNotEmpty(), "exact UTF8 byte limit")
        refuses("over UTF8 byte limit") { StructuredWriterDocx.write("1. " + "x".repeat(PlainWriterDocx.MAX_TEXT_BYTES - 2), true) }
        verify(StructuredWriterDocx.write(("1. x\n".repeat(PlainWriterDocx.MAX_PARAGRAPHS - 1)) + "1. x", true).isNotEmpty(), "exact line limit")
        refuses("over line limit") { StructuredWriterDocx.write("1. x\n".repeat(PlainWriterDocx.MAX_PARAGRAPHS) + "1. x", true) }
        verify(StructuredWriterDocx.write("1. " + "*a* ".repeat(StructuredWriterDocx.MAX_RUNS / 2), true).isNotEmpty(), "exact run limit")
        refuses("over run limit") { StructuredWriterDocx.write("1. " + "*a* ".repeat(StructuredWriterDocx.MAX_RUNS / 2 + 1), true) }
        refuses("invalid control") { StructuredWriterDocx.write("1. \u0000", true) }
        refuses("unpaired surrogate") { StructuredWriterDocx.write("1. \uD800", true) }

        val doc = TextDocument("id", "draft.txt", "content://source/1", "1. text", "digest", TextEncoding.UTF8)
        val consent = WriterCopyConsent(doc, 7)
        verify(consent.documentId == doc.id && consent.permits(doc.copy(), 7), "same complete immutable document and revision")
        val stale = listOf(doc.copy(id = "other"), doc.copy(name = "other.txt"), doc.copy(sourceUri = "content://source/2"),
            doc.copy(content = "1. changed"), doc.copy(savedDigest = "other"), doc.copy(encoding = TextEncoding.UTF16_LE))
        for (changed in stale) verify(!consent.permits(changed, 7), "changed document property refused")
        verify(!consent.permits(doc, 8), "same text after later edit/undo remains stale")
        refuses("negative revision") { WriterCopyConsent(doc, -1) }

        for (i in 0 until 128) {
            val raw = source(i)
            val off = StructuredWriterDocx.write(raw)
            val on = StructuredWriterDocx.write(raw, numberedLines = true)
            val offBlocks = DocxPackage.read(off).blocks
            val onBlocks = DocxPackage.read(on).blocks
            verify(off.contentEquals(StructuredWriterDocx.write(raw, false)), "default remains byte-identical $i")
            verify(on.contentEquals(StructuredWriterDocx.write(raw, true)), "opt-in deterministic $i")
            verify(offBlocks[0] is DocxPackage.Block.Paragraph && text(offBlocks[0]).startsWith("1. item-$i"), "old mode $i")
            verify(onBlocks[0] is DocxPackage.Block.ListItem && text(onBlocks[0]) == "item-$i bold α😀", "new mode $i")
            verify((onBlocks[4] as? DocxPackage.Block.ListItem)?.ordered == false && text(onBlocks[5]) == "2. literal", "bullet and literal retained $i")
            File(folder, "doc-${i.toString().padStart(3, '0')}-default.docx").writeBytes(off)
            File(folder, "doc-${i.toString().padStart(3, '0')}-numbered.docx").writeBytes(on)
        }
        println("R60 Writer contracts: $assertions assertions; 256 actual DOCX files, independent reader pending")
    }
}
