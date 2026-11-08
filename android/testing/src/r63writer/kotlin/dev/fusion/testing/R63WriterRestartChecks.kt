// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import dev.fusion.ooxml.StructuredWriterDocx as Writer
import java.io.File

/** Actual DOCX construction; external ZIP/XML/python-docx verification is separate. */
object R63WriterRestartChecks {
    private var assertions = 0
    private fun verify(condition: Boolean, message: String) { assertions++; check(condition) { message } }
    private fun refuses(message: String, body: () -> Unit) {
        verify(runCatching(body).exceptionOrNull() is IllegalArgumentException, "Expected refusal: $message")
    }
    private fun source(i: Int): String = listOf(
        "1. first-$i **bold**", "1. second", "plain-$i", "1. third ~~strike~~", "1. fourth",
        "- bullet", "1. fifth", "", "1. sixth", "2. literal", " 1. indented", "1. seventh", "last-$i",
    ).joinToString("\n")

    @JvmStatic fun main(args: Array<String>) {
        val folder = File(args.single()).also { check(it.exists() || it.mkdirs()) }
        refuses("restart requires numbered option") { Writer.write("1. x", false, false, true) }
        val nested = DocxPackage.Document(blocks = listOf(DocxPackage.Block.ListItem(true, 1, listOf(DocxPackage.Run("x")))))
        refuses("restart mode is level-zero only") { DocxPackage.write(nested, restartOrderedGroups = true) }
        verify(DocxPackage.write(nested).isNotEmpty(), "existing multi-level output is still supported")
        val maxLines = (0 until 2048).joinToString("\n") { if (it % 2 == 0) "1. x" else "" }
        val max = Writer.write(maxLines, true, false, true)
        verify(max.size <= 2 * 1024 * 1024 && DocxPackage.read(max).blocks.size == 2048, "1024 separate groups at paragraph bound")
        refuses("paragraph budget") { Writer.write(maxLines + "\n1. overflow", true, false, true) }
        val rows = mutableListOf("id,numbered,strike,restart")
        var id = 0
        for (numbered in listOf(false, true)) for (strike in listOf(false, true)) {
            for (restart in if (numbered) listOf(false, true) else listOf(false)) repeat(32) { index ->
                val raw = source(index)
                val bytes = Writer.write(raw, numbered, strike, restart)
                if (!restart) verify(bytes.contentEquals(Writer.write(raw, numbered, strike)), "legacy overload $id")
                verify(bytes.contentEquals(Writer.write(raw, numbered, strike, restart)), "deterministic $id")
                val blocks = DocxPackage.read(bytes).blocks
                verify(blocks.size == 13, "line count $id")
                for (line in listOf(0, 1, 3, 4, 6, 8, 11)) {
                    verify((blocks[line] is DocxPackage.Block.ListItem) == numbered, "numbered option $id/$line")
                }
                verify((blocks[5] as? DocxPackage.Block.ListItem)?.ordered == false, "bullet retained $id")
                verify(blocks[9] is DocxPackage.Block.Paragraph && blocks[10] is DocxPackage.Block.Paragraph, "unsupported numbering remains text $id")
                val name = (id++).toString().padStart(3, '0')
                File(folder, "doc-$name.docx").writeBytes(bytes)
                rows.add("$name,$numbered,$strike,$restart")
            }
        }
        verify(id == 192, "six option combinations")
        File(folder, "cases.csv").writeText(rows.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        println("R63 Writer list instances: $assertions assertions; $id DOCX files; independent reader and Word rendering pending")
    }
}
