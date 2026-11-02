// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.OfficeSheetDraft as Sheet
import dev.fusion.ooxml.OfficeSheetTextGrid as Grid
import java.io.File
import java.util.Random

/** Actual transactional grid parser/transpose on JVM; independent reader and Android remain separate. */
object R60SheetChecks {
    private var assertions = 0
    private fun verify(value: Boolean, message: String) { assertions++; check(value) { message } }
    private fun refuses(message: String, action: () -> Unit) {
        val failure = runCatching(action).exceptionOrNull()
        verify(failure != null, "Expected refusal: $message")
    }
    private fun text(value: String) = Sheet.Cell(Sheet.Kind.TEXT, value)
    private fun quoted(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
    private fun ref(row: Int, col: Int) = "${('A'.code + col).toChar()}${row + 1}"

    @JvmStatic fun main(args: Array<String>) {
        val folder = File(args.single()).also { check(it.exists() || it.mkdirs()) }
        val before = Sheet.restore(7, linkedMapOf("B2" to text("old"), "C4" to text("remove"),
            "D10" to Sheet.Cell(Sheet.Kind.FORMULA, "=1+2"))).draft
        val source = "12.5\t\"42\"\t=1+1\n0012\t\"line\r\nnext\"\t"
        val plan = Grid.prepare(before, "B2", source, Grid.Mode.DECIMALS, true)
        val after = plan.calculated.draft
        verify(plan.transposed && plan.rows == 3 && plan.columns == 2, "2x3 becomes 3x2")
        verify(plan.inserted == 4 && plan.replaced == 1 && plan.cleared == 1 && plan.unchanged == 0, "exact changes")
        verify(plan.numericFields == 1 && plan.textFields == 4 && plan.emptyFields == 1, "classification follows original fields")
        verify(after.cells["B2"] == Sheet.Cell(Sheet.Kind.NUMBER, "12.5"), "numeric value")
        verify(after.cells["C2"] == text("0012") && after.cells["B3"] == text("42"), "leading zero and quoted number stay text")
        verify(after.cells["C3"] == text("line\r\nnext") && after.cells["B4"] == text("=1+1"), "quoted newline and formula-like literal")
        verify(after.cells["C4"] == null && after.cells["D10"] == before.cells["D10"], "empty clears only intended destination")
        verify(before.cells["B2"] == text("old") && before.cells["C4"] == text("remove") && before.revision == 7L, "immutable original")
        verify(after.revision == 8L && plan.isCurrent(before, "B2") && !plan.isCurrent(before, "A1"), "one revision and anchor binding")
        verify(!plan.isCurrent(Sheet.restore(before.revision, before.cells).draft, "B2"), "different identity is stale")
        val repeated = Grid.prepare(after, "B2", source, Grid.Mode.DECIMALS, true)
        verify(repeated.changed == 0 && repeated.calculated.draft === after && repeated.unchanged == 6, "transpose no-op preserves identity")
        val default = Grid.prepare(Sheet.empty(), "A1", "a\tb\tc\nd\te\tf")
        val explicit = Grid.prepare(Sheet.empty(), "A1", "a\tb\tc\nd\te\tf", Grid.Mode.TEXT_ONLY, false)
        verify(!default.transposed && default.rows == 2 && default.columns == 3 && default.calculated.draft.cells == explicit.calculated.draft.cells,
            "default old entry point remains untransposed")
        val reverse = Grid.prepare(Sheet.empty(), "A1", "a\td\nb\te\nc\tf", Grid.Mode.TEXT_ONLY, true)
        verify(reverse.calculated.draft.cells == default.calculated.draft.cells, "double transpose restores values")
        val row = (1..26).joinToString("\t") { "x" }
        val col = (1..26).joinToString("\n") { "x" }
        verify(Grid.prepare(Sheet.empty(), "A1", row, Grid.Mode.TEXT_ONLY, true).rows == 26, "26 source columns transpose to rows")
        verify(Grid.prepare(Sheet.empty(), "A1", col, Grid.Mode.TEXT_ONLY, true).columns == 26, "26 source rows transpose to columns")
        refuses("27 transposed columns") { Grid.prepare(before, "A1", "$col\nx", Grid.Mode.TEXT_ONLY, true) }
        refuses("27 original columns even when output would fit") { Grid.prepare(before, "A1", "$row\tx", Grid.Mode.TEXT_ONLY, true) }
        val hundredRows = (1..100).joinToString("\n") { "x" }
        verify(Grid.prepare(Sheet.empty(), "A1", hundredRows).rows == 100, "original exact source-row boundary")
        refuses("100 rows cannot transpose beyond 26 destination columns") { Grid.prepare(before, "A1", hundredRows, Grid.Mode.TEXT_ONLY, true) }
        val rowError = runCatching { Grid.prepare(before, "A1", "$hundredRows\nx", Grid.Mode.TEXT_ONLY, true) }.exceptionOrNull()
        verify(rowError is IllegalArgumentException && rowError.message == "Too many rows", "101 source rows refused before transpose")
        verify(Grid.prepare(Sheet.empty(), "Z100", "x", Grid.Mode.TEXT_ONLY, true).calculated.draft.cells["Z100"] == text("x"), "last anchor")
        refuses("transposed row crosses last row") { Grid.prepare(before, "Z100", "x\ty", Grid.Mode.TEXT_ONLY, true) }
        refuses("transposed column crosses last column") { Grid.prepare(before, "Z100", "x\ny", Grid.Mode.TEXT_ONLY, true) }
        val max = (1..16).joinToString("\n") { (1..16).joinToString("\t") { "x" } }
        verify(Grid.prepare(Sheet.empty(), "A1", max, Grid.Mode.TEXT_ONLY, true).calculated.draft.cells.size == 256, "exact field limit")
        val over = (1..17).joinToString("\n") { (1..16).joinToString("\t") { "x" } }
        val error = runCatching { Grid.prepare(before, "A1", over, Grid.Mode.TEXT_ONLY, true) }.exceptionOrNull()
        verify(error is IllegalArgumentException && error.message == "Text grid exceeds 256 fields", "field limit is real refusal")
        verify(Grid.prepare(Sheet.empty(), "A1", "x".repeat(4096), Grid.Mode.TEXT_ONLY, true).changed == 1, "exact cell bytes")
        refuses("over cell bytes") { Grid.prepare(before, "A1", "x".repeat(4097), Grid.Mode.TEXT_ONLY, true) }
        verify(Grid.prepare(Sheet.empty(), "A1", "é".repeat(2048), Grid.Mode.TEXT_ONLY, true).changed == 1, "exact multibyte cell")
        refuses("over multibyte cell") { Grid.prepare(before, "A1", "é".repeat(2049), Grid.Mode.TEXT_ONLY, true) }
        val exact = ("x".repeat(4096) + "\t").repeat(15) + "y".repeat(4081)
        verify(exact.toByteArray().size == Grid.MAX_INPUT_BYTES && Grid.prepare(Sheet.empty(), "A1", exact, Grid.Mode.TEXT_ONLY, true).rows == 16,
            "exact input byte budget")
        refuses("over input bytes") { Grid.prepare(before, "A1", exact + "y", Grid.Mode.TEXT_ONLY, true) }
        for (bad in listOf("\"unclosed", "x\"y", "x\ty\nz", "\u0000", "\uD800", ""))
            refuses("malformed source") { Grid.prepare(before, "A1", bad, Grid.Mode.TEXT_ONLY, true) }
        verify(before.revision == 7L && before.cells["B2"] == text("old"), "all refusals leave original untouched")
        val exhausted = Sheet.restore(Long.MAX_VALUE, mapOf("A1" to text("x"))).draft
        refuses("revision exhausted") { Grid.prepare(exhausted, "A1", "y", Grid.Mode.TEXT_ONLY, true) }
        verify(Grid.prepare(exhausted, "A1", "x", Grid.Mode.TEXT_ONLY, true).changed == 0, "exhausted revision allows no-op")

        val random = Random(60061)
        val alphabet = listOf("a", " ", "\"", "\t", "\n", "\r", "&", "<", "é", "🙂", "=")
        repeat(500) { case ->
            val rows = 1 + random.nextInt(6); val cols = 1 + random.nextInt(6)
            val values = List(rows) { List(cols) { buildString { repeat(random.nextInt(12)) { append(alphabet[random.nextInt(alphabet.size)]) } } } }
            val raw = values.joinToString("\r\n") { fields -> fields.joinToString("\t", transform = ::quoted) } + "\r\n"
            val result = Grid.prepare(Sheet.empty(), "A1", raw, Grid.Mode.DECIMALS, true)
            verify(result.rows == cols && result.columns == rows, "random dimensions $case")
            values.forEachIndexed { r, fields -> fields.forEachIndexed { c, value ->
                verify(result.calculated.draft.cells[ref(c, r)] == if (value.isEmpty()) null else text(value), "transposed quoted cell $case/$r/$c")
            } }
        }
        for (i in 0 until 64) {
            val raw = "12.5\t\"42\"\t=1+1\n0012\t\"line\r\n$i α🙂\"\t"
            val result = Grid.prepare(Sheet.empty(), "B2", raw, Grid.Mode.DECIMALS, true)
            val name = i.toString().padStart(3, '0')
            File(folder, "input-$name.tsv").writeText(raw, Charsets.UTF_8)
            File(folder, "transposed-$name.xlsx").writeBytes(Sheet.xlsx(result.calculated.draft))
        }
        println("R60 sheet transpose contracts: $assertions assertions; 64 actual XLSX files, independent reader pending")
    }
}
