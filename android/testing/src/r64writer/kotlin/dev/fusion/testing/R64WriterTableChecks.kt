// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import dev.fusion.ooxml.StructuredWriterDocx as Writer
import java.io.File

/** Real DOCX authoring. Independent readers and rendering observations are separate. */
object R64WriterTableChecks {
    private var checks = 0
    private fun ok(value: Boolean, why: String) { checks++; check(value) { why } }
    private fun refused(why: String, body: () -> Unit) {
        ok(runCatching(body).exceptionOrNull() is IllegalArgumentException, "Expected refusal: $why")
    }
    private fun source(i: Int) = listOf(
        "# Heading $i", "1. first ~~ink~~", "| Name | Value |", "| --- | --- |",
        "| α-$i | <&> |", "|  | 007 |", "After | literal", "1. second",
    ).joinToString("\n")

    @JvmStatic fun main(args: Array<String>) {
        val folder = File(args.single()).also { check(it.exists() || it.mkdirs()) }
        val basic = "Before\n| Name | Count |\n| --- | --- |\n| α<&> | 007 |\n| |  |\nAfter"
        val out = Writer.write(basic, false, false, false, true)
        val blocks = DocxPackage.read(out).blocks
        val table = blocks[1] as? DocxPackage.Block.Table
        ok(blocks.size == 3 && table != null, "table is a real block between paragraphs")
        ok(table?.rows == listOf(listOf("Name", "Count"), listOf("α<&>", "007"), listOf("", "")), "trim, escaping, blank cells")
        ok(Writer.write(basic, false, false, false).contentEquals(Writer.write(basic, false, false, false, false)), "old overload unchanged")
        val invalid = listOf(
            "| A | B |\n| --- | --- |", // no body
            "| A | B |\n| :--- | --- |\n| x | y |", // unsupported alignment
            "| A | B |\n| --- | --- |\n| x |", // ragged
            "| A | B |\n| --- | --- |\n| x | y", // missing outer pipe
            "| A\\|x | B |\n| --- | --- |\n| a | b |", // escaped pipe
            "| wrong |\n| A | B |\n| --- | --- |\n| x | y |", // no partial suffix
            " | A | B |\n | --- | --- |\n | x | y |", // indentation
            "| A | B |\n| -- | --- |\n| x | y |", // separator too short
            "| **bold** |\n| -- |\n| ~~strike~~ |", // existing inline options must not transform a rejected group
        )
        var malformedId = 0
        for (raw in invalid) for (strike in listOf(false, true)) {
            val bytes = Writer.write(raw, false, strike, false, true)
            val result = DocxPackage.read(bytes)
            ok(result.blocks.none { it is DocxPackage.Block.Table }, "unsupported group is not a table: $raw")
            ok(result.blocks.map { (it as DocxPackage.Block.Paragraph).runs } ==
                raw.lines().map { listOf(DocxPackage.Run(it)) }, "unsupported group preserves all characters and markers")
            File(folder, "malformed-${(malformedId++).toString().padStart(2, '0')}.docx").writeBytes(bytes)
        }
        ok(malformedId == 18, "malformed corpus covers strike options")
        fun row(count: Int, value: String) = List(count) { value }.joinToString("|", "|", "|")
        val header64 = row(64, "x"); val delimiter64 = row(64, "---")
        val max = listOf(header64, delimiter64, header64, header64, header64).joinToString("\n")
        val boundaryBytes = Writer.write(max, false, false, false, true)
        File(folder, "boundary-256.docx").writeBytes(boundaryBytes)
        val maxTable = DocxPackage.read(boundaryBytes).blocks.single() as DocxPackage.Block.Table
        ok(maxTable.rows.size == 4 && maxTable.rows.all { it.size == 64 }, "256-cell boundary")
        refused("257-plus cells") { Writer.write(max + "\n" + header64, false, false, false, true) }
        val row65 = row(65, "x"); val sep65 = row(65, "---")
        refused("65 columns") { Writer.write(listOf(row65, sep65, row65).joinToString("\n"), false, false, false, true) }
        refused("total run budget includes table cells") {
            Writer.write(List(33) { max }.joinToString("\n\n"), false, false, false, true)
        }
        refused("restart still requires numbered") { Writer.write(basic, false, false, true, true) }

        val rows = mutableListOf("id,numbered,strike,restart,tables,index")
        var id = 0
        for (numbered in listOf(false, true)) for (strike in listOf(false, true))
            for (restart in if (numbered) listOf(false, true) else listOf(false))
                for (tables in listOf(false, true)) repeat(16) { index ->
                    val raw = source(index)
                    val bytes = Writer.write(raw, numbered, strike, restart, tables)
                    ok(bytes.contentEquals(Writer.write(raw, numbered, strike, restart, tables)), "deterministic $id")
                    if (!tables) ok(bytes.contentEquals(Writer.write(raw, numbered, strike, restart)), "legacy overload $id")
                    val actual = DocxPackage.read(bytes).blocks
                    ok(actual.count { it is DocxPackage.Block.Table } == if (tables) 1 else 0, "table opt-in $id")
                    ok(actual.size == if (tables) 5 else 8, "block count $id")
                    if (tables) {
                        val t = actual[2] as DocxPackage.Block.Table
                        ok(t.rows == listOf(listOf("Name", "Value"), listOf("α-$index", "<&>"), listOf("", "007")), "table cells $id")
                    }
                    val name = (id++).toString().padStart(3, '0')
                    File(folder, "doc-$name.docx").writeBytes(bytes)
                    rows.add("$name,$numbered,$strike,$restart,$tables,$index")
                }
        ok(id == 192, "all table/numbering/strike/restart options exercised")
        File(folder, "cases.csv").writeText(rows.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        println("R64 Writer pipe tables: $checks assertions; $id matrix DOCX plus 18 malformed and one boundary file; independent reader pending")
    }
}
