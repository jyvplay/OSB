// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.documents.CopyProtocol
import dev.fusion.documents.FrozenExport
import dev.fusion.editor.MarkdownOfficeBridge
import dev.fusion.ooxml.DocxPackage
import dev.fusion.ooxml.FormulaEval
import dev.fusion.ooxml.FormulaXlsx
import dev.fusion.ooxml.OfficeSheetCodec
import dev.fusion.ooxml.OfficeSheetDraft
import dev.fusion.ooxml.PlainWriterDocx
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/** Executed host contracts for the bounded authoring path, not Android or whole-Office acceptance. */
object R54Checks {
    private var assertions = 0
    private fun verify(condition: Boolean, message: String) { assertions++; check(condition) { message } }
    private inline fun refuses(message: String, operation: () -> Unit) {
        assertions++
        check(runCatching(operation).isFailure) { "Expected refusal: $message" }
    }
    private fun cell(kind: OfficeSheetDraft.Kind, text: String) = OfficeSheetDraft.Cell(kind, text)
    private fun edit(draft: OfficeSheetDraft.Draft, ref: String, kind: OfficeSheetDraft.Kind, text: String) =
        OfficeSheetDraft.replace(draft, ref, cell(kind, text)).draft
    private fun frame(draft: OfficeSheetDraft.Draft): ByteArray = ByteArrayOutputStream().also { OfficeSheetCodec.write(draft, it) }.toByteArray()
    private fun result(draft: OfficeSheetDraft.Draft, ref: String) = OfficeSheetDraft.calculate(draft).values[ref]?.displayText()

    @JvmStatic fun main(args: Array<String>) {
        val output = File(args.single()).apply { mkdirs() }
        val text = "  First\tcolumn & <quoted> \uD83D\uDE42\r\nSecond\r\n\nlast\n"
        val docx = PlainWriterDocx.write(text)
        File(output, "writer.docx").writeBytes(docx)
        val blocks = DocxPackage.read(docx).blocks
        val paragraphs = blocks.map { (it as DocxPackage.Block.Paragraph).runs.joinToString("") { run -> run.text } }
        verify(paragraphs == listOf("  First\tcolumn & <quoted> \uD83D\uDE42", "Second", "", "last", ""), "writer paragraphs and tabs")
        verify(PlainWriterDocx.write("").isNotEmpty(), "empty writer document")
        refuses("XML control") { PlainWriterDocx.write("a\u0000b") }
        refuses("unpaired surrogate") { PlainWriterDocx.write("\uD800") }
        refuses("oversized writer") { PlainWriterDocx.write("x".repeat(PlainWriterDocx.MAX_TEXT_BYTES + 1)) }
        refuses("too many paragraphs") { PlainWriterDocx.write("\n".repeat(PlainWriterDocx.MAX_PARAGRAPHS)) }
        val rich = DocxPackage.Document(blocks = listOf(
            DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("one\ttwo\nthree\r"))),
            DocxPackage.Block.Table(listOf(listOf("A\tB", "line1\nline2"))),
            DocxPackage.Block.CodeBlock(listOf("x\ty", "z")),
        ))
        val roundtrip = DocxPackage.read(DocxPackage.write(rich))
        verify(roundtrip.blocks == rich.blocks, "DOCX run/table/code tabs, breaks and CR round trip")
        // Former parser loop stopped forever on non-heading lines beginning with '#'.
        for (literal in listOf("#tag", "#", "##not-heading", "####### title")) {
            val parsed = MarkdownOfficeBridge.parseMarkdown(literal)
            verify(parsed == listOf(MarkdownOfficeBridge.Block.Paragraph(literal)), "markdown progress for $literal")
        }
        val mixed = MarkdownOfficeBridge.parseMarkdown("#tag\n# Heading\ntext")
        verify(mixed.size == 3 && mixed[1] == MarkdownOfficeBridge.Block.Heading(1, "Heading"), "markdown resumes at valid heading")

        val textKind = OfficeSheetDraft.Kind.TEXT
        val number = OfficeSheetDraft.Kind.NUMBER
        val formula = OfficeSheetDraft.Kind.FORMULA
        val bool = OfficeSheetDraft.Kind.BOOLEAN
        var sheet = OfficeSheetDraft.empty()
        sheet = edit(sheet, "A1", number, "2")
        sheet = edit(sheet, "A2", number, "3")
        sheet = edit(sheet, "B1", formula, "=SUM(A1:A2)")
        sheet = edit(sheet, "B2", formula, "=IF(B1>4,\"yes\",\"no\")")
        sheet = edit(sheet, "C1", textKind, "=1+1")
        sheet = edit(sheet, "C2", textKind, "\t spaced & <data>\r\n\uD83D\uDE42")
        sheet = edit(sheet, "D1", bool, "TRUE")
        sheet = edit(sheet, "E1", formula, "=1/0")
        verify(result(sheet, "B1") == "5", "sum dependency")
        verify(result(sheet, "B2") == "yes", "conditional")
        verify(result(sheet, "C1") == "=1+1", "formula-looking text stays literal")
        verify(result(sheet, "E1") == "#DIV/0!", "division error")
        val xlsx = OfficeSheetDraft.xlsx(sheet)
        File(output, "sheet.xlsx").writeBytes(xlsx)
        val reread = FormulaXlsx.read(xlsx)
        verify(reread.inputs["B1"] is FormulaEval.CellInput.Formula, "xlsx retains formula")
        verify((reread.inputs["C1"] as FormulaEval.CellInput.TextLiteral).value == "=1+1", "xlsx retains literal")
        verify((reread.inputs["C2"] as FormulaEval.CellInput.TextLiteral).value == "\t spaced & <data>\r\n\uD83D\uDE42", "xlsx preserves CRLF")
        val snapshot = frame(sheet)
        File(output, "sheet.fsh").writeBytes(snapshot)
        verify(OfficeSheetCodec.read(ByteArrayInputStream(snapshot)) == sheet, "recovery exact round trip")
        verify(frame(sheet).contentEquals(snapshot), "recovery deterministic")
        val corrupt = snapshot.copyOf().also { it[12] = (it[12].toInt() xor 1).toByte() }
        refuses("checksum") { OfficeSheetCodec.read(ByteArrayInputStream(corrupt)) }
        refuses("truncated") { OfficeSheetCodec.read(ByteArrayInputStream(snapshot.copyOf(snapshot.size - 1))) }
        refuses("trailing frame bytes") { OfficeSheetCodec.read(ByteArrayInputStream(snapshot + byteArrayOf(0))) }
        refuses("oversized frame") { OfficeSheetCodec.read(ByteArrayInputStream(ByteArray(OfficeSheetCodec.MAX_FRAME + 1))) }
        val before = sheet
        refuses("cycle") { OfficeSheetDraft.replace(sheet, "A1", cell(formula, "=B1")) }
        verify(sheet == before && result(sheet, "B1") == "5", "rejected edit cannot mutate live draft")
        for (ref in listOf("A0", "AA1", "Z101", "A01", "Sheet1!A1", "\$A1", "A1 x")) {
            refuses("out-of-scope address $ref") { OfficeSheetDraft.address(ref) }
        }
        for (bad in listOf("=NOW()", "=SEQUENCE(1000000)", "=A101", "=AA1", "=[Book]Sheet!A1", "=INDIRECT(\"A1\")", "=Name", "=SUM(A1", "=1+", "=1E999", "=ROUND(1.234,13)", "=CONCAT(A1,A1)", "=A1&A1")) {
            refuses("formula $bad") { OfficeSheetDraft.replace(sheet, "F1", cell(formula, bad)) }
        }
        refuses("malformed number") { OfficeSheetDraft.replace(sheet, "F1", cell(number, "NaN")) }
        refuses("boolean") { OfficeSheetDraft.replace(sheet, "F1", cell(bool, "true")) }
        refuses("invalid cell XML") { OfficeSheetDraft.replace(sheet, "F1", cell(textKind, "\u0001")) }
        refuses("oversized UTF-8") { OfficeSheetDraft.replace(sheet, "F1", cell(textKind, "\u00E9".repeat(2049))) }
        val clear = OfficeSheetDraft.replace(sheet, "C1", null).draft
        verify("C1" !in clear.cells && sheet.cells.containsKey("C1"), "clear is immutable")
        verify(OfficeSheetDraft.replace(sheet, "A1", sheet.cells["A1"]).draft === sheet, "no-op keeps revision")
        val supplied = linkedMapOf("A1" to cell(textKind, "old"))
        val owned = OfficeSheetDraft.restore(7, supplied).draft
        supplied["A1"] = cell(textKind, "changed")
        verify(owned.cells["A1"]?.input == "old", "draft owns its map")
        refuses("read-only exposed map") {
            val mutable = owned.cells as MutableMap<String, OfficeSheetDraft.Cell>
            mutable["A2"] = cell(textKind, "bad")
        }

        // Repeat updates and reopen real checkpoint bytes; compare simple arithmetic to JVM arithmetic.
        var evolving = OfficeSheetDraft.empty()
        repeat(300) { n ->
            val x = (n % 23) - 11
            val y = (n % 13) - 6
            evolving = edit(evolving, "A1", number, x.toString())
            evolving = edit(evolving, "B1", number, y.toString())
            evolving = edit(evolving, "C1", formula, "=A1+B1")
            verify(result(evolving, "C1") == (x + y).toString(), "arithmetic $n")
            verify(OfficeSheetCodec.read(ByteArrayInputStream(frame(evolving))) == evolving, "checkpoint $n")
        }

        val id = UUID.randomUUID().toString() + "-1"
        val wrong = UUID.randomUUID().toString() + "-1"
        verify(CopyProtocol.validRequestId(id), "request identity")
        val bytes = byteArrayOf(1, 2, 3)
        val sources = arrayListOf("content://provider/source")
        val attempt = FrozenExport(id, bytes, sources)
        bytes[0] = 99
        sources.clear()
        verify(attempt.claim(wrong) == null, "unrelated result not accepted")
        val claimed = requireNotNull(attempt.claim(id))
        verify(claimed.contentEquals(byteArrayOf(1, 2, 3)), "frozen export owns input")
        verify(attempt.forbiddenUris == listOf("content://provider/source"), "export owns sources")
        verify(attempt.claim(id) == null && !attempt.cancel(id), "result consumed once")
        val cancel = FrozenExport(id, byteArrayOf(1), emptyList())
        verify(cancel.cancel(id) && cancel.claim(id) == null, "canceled export cannot write")
        val race = FrozenExport(id, byteArrayOf(1), emptyList())
        val go = CountDownLatch(1)
        val successes = AtomicInteger(0)
        val threads = List(24) { Thread { go.await(); if (race.claim(id) != null) successes.incrementAndGet() }.apply { start() } }
        go.countDown(); threads.forEach { it.join() }
        verify(successes.get() == 1, "at most one concurrent claim")
        println("R54 host contracts: $assertions assertions passed; writer.docx, sheet.xlsx, sheet.fsh emitted")
    }
}
