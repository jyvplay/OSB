// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.FormulaXlsx
import dev.fusion.ooxml.OfficeSheetDraft as Sheet
import dev.fusion.ooxml.OfficeSheetTextGrid as Paste
import java.io.File
import java.util.Random

/** Contracts run real production Kotlin on the host; not an Android or Excel claim. */
object R58Checks {
    private var assertions = 0
    private fun verify(ok: Boolean, label: String) { assertions++; check(ok) { label } }
    private inline fun refuses(label: String, action: () -> Unit) {
        assertions++
        check(runCatching(action).isFailure) { "Expected refusal: $label" }
    }
    private fun text(value: String) = Sheet.Cell(Sheet.Kind.TEXT, value)
    private fun quote(value: String) = "\"" + value.replace("\"", "\"\"") + "\""

    @JvmStatic fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val before = Sheet.restore(7, linkedMapOf(
            "A1" to Sheet.Cell(Sheet.Kind.NUMBER, "7"),
            "B1" to Sheet.Cell(Sheet.Kind.FORMULA, "=1+2"),
            "B2" to text("old"),
            "D3" to Sheet.Cell(Sheet.Kind.FORMULA, "=3+4"),
        )).draft
        val original = before.cells.toMap()
        val raw = "  hello  \t\"=1+1\"\t\"line 1\r\nline 2\"\r\n\"Q\"\"A\"\t\t\"🙂\tT\"\r\n"
        val preview = Paste.prepare(before, "a1", raw)
        val after = preview.calculated.draft
        verify(preview.rows == 2 && preview.columns == 3, "quoted rectangle and final CRLF")
        verify(preview.inserted == 3 && preview.replaced == 2 && preview.cleared == 1 && preview.unchanged == 0, "exact impact summary")
        verify(preview.changed == 6 && after.revision == 8L, "one revision per paste")
        verify(before.cells == original && before.revision == 7L, "source draft unchanged")
        verify(after.cells["A1"] == text("  hello  "), "spaces preserved")
        verify(after.cells["B1"] == text("=1+1"), "formula-looking value is literal text")
        verify(after.cells["C1"] == text("line 1\r\nline 2"), "quoted CRLF preserved inside field")
        verify(after.cells["A2"] == text("Q\"A"), "doubled quote")
        verify(after.cells["B2"] == null, "empty field clears target")
        verify(after.cells["C2"] == text("🙂\tT"), "supplementary Unicode and quoted tab")
        verify(after.cells["D3"] == before.cells["D3"], "unrelated formula unchanged")
        verify(preview.isCurrent(before, "A1") && !preview.isCurrent(before, "B1"), "anchor binding")
        val other = Sheet.restore(before.revision, before.cells).draft
        verify(!preview.isCurrent(other, "A1") && !preview.isCurrent(after, "A1"), "identity binding prevents stale or reconstructed draft")
        val unchanged = Paste.prepare(after, "A1", raw)
        verify(unchanged.changed == 0 && unchanged.unchanged == 6 && unchanged.calculated.draft === after, "no-op preserves revision")
        val workbook = Sheet.xlsx(after)
        File(out, "input.tsv").writeText(raw, Charsets.UTF_8)
        File(out, "pasted.xlsx").writeBytes(workbook)
        val loaded = FormulaXlsx.read(workbook)
        verify(loaded.inputs["B1"] == dev.fusion.ooxml.FormulaEval.CellInput.TextLiteral("=1+1"), "internal XLSX roundtrip preserves text typing")

        listOf("\"unclosed", "x\"y", "\"x\"tail", "\"x\"\"", "x\ty\nz", "x\n\ty", "\u0000", "\uD800", "\uDC00", "").forEachIndexed { i, bad ->
            refuses("invalid input $i") { Paste.prepare(before, "A1", bad) }
            verify(before.cells == original && before.revision == 7L, "refusal is atomic $i")
        }
        refuses("outside row") { Paste.prepare(before, "Z100", "x\ny") }
        refuses("outside column") { Paste.prepare(before, "Z100", "x\ty") }
        refuses("bad anchor") { Paste.prepare(before, "AA1", "x") }
        verify(Paste.prepare(before, "A1", "x\ty\nq\t").columns == 2, "trailing empty field makes a rectangular row")
        // A final empty field is meaningful and must clear, not disappear.
        val trailing = Paste.prepare(before, "A1", "x\t")
        verify(trailing.columns == 2 && trailing.calculated.draft.cells["B1"] == null, "trailing tab clears second cell")
        verify(Paste.prepare(Sheet.empty(), "A1", "\"a\r\nb\"\n").calculated.draft.cells["A1"] == text("a\r\nb"), "inside/outside newline distinction")
        val columns = (1..26).joinToString("\t") { "x" }
        verify(Paste.prepare(Sheet.empty(), "A1", columns).columns == 26, "exact column boundary")
        refuses("27 columns") { Paste.prepare(Sheet.empty(), "A1", "$columns\tx") }
        val rows = (1..100).joinToString("\n") { "x" }
        verify(Paste.prepare(Sheet.empty(), "A1", rows).rows == 100, "exact row boundary")
        refuses("101 rows") { Paste.prepare(Sheet.empty(), "A1", "$rows\nx") }
        val maxGrid = (1..16).joinToString("\n") { (1..16).joinToString("\t") { "x" } }
        verify(Paste.prepare(Sheet.empty(), "A1", maxGrid).calculated.draft.cells.size == 256, "exact 256 populated cells")
        refuses("257 fields") { Paste.prepare(Sheet.empty(), "A1", (1..257).joinToString("\n") { "x" }) }
        verify(Paste.prepare(Sheet.empty(), "A1", "x".repeat(4096)).calculated.draft.cells["A1"]?.input?.length == 4096, "exact cell byte boundary")
        refuses("cell over character limit") { Paste.prepare(Sheet.empty(), "A1", "x".repeat(4097)) }
        refuses("cell over UTF-8 byte limit") { Paste.prepare(Sheet.empty(), "A1", "é".repeat(2049)) }
        val exact = ("x".repeat(4096) + "\t").repeat(15) + "y".repeat(4081)
        verify(exact.toByteArray().size == Paste.MAX_INPUT_BYTES, "exact input fixture")
        verify(Paste.prepare(Sheet.empty(), "A1", exact).columns == 16, "exact input byte limit")
        refuses("one input byte over limit") { Paste.prepare(Sheet.empty(), "A1", exact + "y") }
        val full = Paste.prepare(Sheet.empty(), "A1", maxGrid).calculated.draft
        refuses("paste cannot exceed populated-cell limit") { Paste.prepare(full, "Z100", "new") }
        verify(full.cells.size == 256, "capacity refusal preserves source")
        val exhausted = Sheet.restore(Long.MAX_VALUE, mapOf("A1" to text("x"))).draft
        refuses("revision exhaustion") { Paste.prepare(exhausted, "A1", "y") }
        verify(Paste.prepare(exhausted, "A1", "x").changed == 0, "no-op permitted at revision limit")

        // Generated, quoted rows use a separate encoder and verify every resulting cell.
        val random = Random(5801)
        val alphabet = listOf("a", " ", "\"", "\t", "\n", "\r", "&", "<", "é", "🙂", "=")
        repeat(500) { case ->
            val nr = 1 + random.nextInt(6); val nc = 1 + random.nextInt(6)
            val values = List(nr) { List(nc) {
                buildString { repeat(random.nextInt(20)) { append(alphabet[random.nextInt(alphabet.size)]) } }
            } }
            val encoded = values.joinToString("\r\n") { row -> row.joinToString("\t", transform = ::quote) } + "\r\n"
            val result = Paste.prepare(Sheet.empty(), "A1", encoded)
            verify(result.rows == nr && result.columns == nc, "random rectangle $case")
            values.forEachIndexed { row, fields -> fields.forEachIndexed { column, value ->
                val ref = "${('A'.code + column).toChar()}${row + 1}"
                verify(result.calculated.draft.cells[ref] == if (value.isEmpty()) null else text(value), "random cell $case/$ref")
            } }
        }
        println("R58 text-grid host contracts: $assertions assertions passed; actual TSV and XLSX artifacts emitted (independent reader not yet run)")
    }
}
