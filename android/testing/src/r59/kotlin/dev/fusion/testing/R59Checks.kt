// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.FormulaEval
import dev.fusion.ooxml.OfficeSheetDraft as Sheet
import dev.fusion.ooxml.OfficeSheetTextGrid as Grid
import java.io.File
import java.util.Base64
import java.util.Random

/** Production parser tests and real artifacts; no Android or Excel claim. */
object R59Checks {
    private var assertions = 0
    private fun checkThat(ok: Boolean, label: String) { assertions++; check(ok) { label } }
    private fun text(s: String) = Sheet.Cell(Sheet.Kind.TEXT, s)
    private fun number(s: String) = Sheet.Cell(Sheet.Kind.NUMBER, s)
    private fun quote(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    @JvmStatic fun main(args: Array<String>) {
        val output = File(args.single()).apply { mkdirs() }
        val source = Sheet.restore(4, mapOf("D4" to Sheet.Cell(Sheet.Kind.FORMULA, "=A1+1"))).draft
        val raw = "0\t-7\t12.5\t\"42\"\t0012\t=1+1\n" +
            "+1\t1e3\t1.0\t-0\t0.1\t999999999999999\n" +
            "9999999999999999\t0.000000000000001\t 1\t1,2\t\"\"\tliteral"
        val preview = Grid.prepare(source, "A1", raw, Grid.Mode.DECIMALS)
        val result = preview.calculated.draft
        checkThat(preview.rows == 3 && preview.columns == 6, "rectangle")
        checkThat(preview.numericFields == 6 && preview.textFields == 11 && preview.emptyFields == 1, "type summary")
        checkThat(preview.inserted == 17 && preview.changed == 17 && result.revision == 5L, "one atomic revision")
        checkThat(source.revision == 4L && source.cells.size == 1 && result.cells["D4"] == source.cells["D4"], "source and unrelated formula")
        val expected = mapOf(
            "A1" to number("0"), "B1" to number("-7"), "C1" to number("12.5"),
            "D1" to text("42"), "E1" to text("0012"), "F1" to text("=1+1"),
            "A2" to text("+1"), "B2" to text("1e3"), "C2" to text("1.0"), "D2" to text("-0"),
            "E2" to number("0.1"), "F2" to number("999999999999999"),
            "A3" to text("9999999999999999"), "B3" to number("0.000000000000001"),
            "C3" to text(" 1"), "D3" to text("1,2"), "F3" to text("literal"),
        )
        expected.forEach { (ref, cell) -> checkThat(result.cells[ref] == cell, "typed fixture $ref") }
        checkThat(result.cells["E3"] == null, "quoted empty clears")
        checkThat(preview.isCurrent(source, "A1") && !preview.isCurrent(result, "A1"), "stale preview")
        val default = Grid.prepare(source, "A1", raw)
        checkThat(default.mode == Grid.Mode.TEXT_ONLY && default.numericFields == 0 && default.textFields == 17, "default is text")
        checkThat(default.calculated.draft.cells["A1"] == text("0"), "default never auto-converts")
        val noOp = Grid.prepare(result, "A1", raw, Grid.Mode.DECIMALS)
        checkThat(noOp.changed == 0 && noOp.calculated.draft === result, "typed no-op")
        val cases = linkedMapOf(
            "123456789012345" to Sheet.Kind.NUMBER, "1234567890123456" to Sheet.Kind.TEXT,
            "0.1" to Sheet.Kind.NUMBER, "0.5" to Sheet.Kind.NUMBER, "-0.5" to Sheet.Kind.NUMBER,
            "-0" to Sheet.Kind.TEXT, "-0.0" to Sheet.Kind.TEXT, "00" to Sheet.Kind.TEXT,
            "01.2" to Sheet.Kind.TEXT, ".5" to Sheet.Kind.TEXT, "1." to Sheet.Kind.TEXT,
            "1.00" to Sheet.Kind.TEXT, "1e3" to Sheet.Kind.TEXT, "+7" to Sheet.Kind.TEXT,
            " 1" to Sheet.Kind.TEXT, "1 " to Sheet.Kind.TEXT, "NaN" to Sheet.Kind.TEXT,
            "Infinity" to Sheet.Kind.TEXT, "=2+2" to Sheet.Kind.TEXT, "@SUM(A1)" to Sheet.Kind.TEXT,
            ("0." + "0".repeat(323) + "1") to Sheet.Kind.TEXT,
            "9".repeat(400) to Sheet.Kind.TEXT,
        )
        for ((token, kind) in cases) {
            checkThat(Grid.prepare(Sheet.empty(), "A1", token, Grid.Mode.DECIMALS).calculated.draft.cells["A1"]?.kind == kind, "classification $token")
            checkThat(Grid.prepare(Sheet.empty(), "A1", quote(token), Grid.Mode.DECIMALS).calculated.draft.cells["A1"] == text(token), "quoted remains text $token")
        }
        checkThat(runCatching { Grid.prepare(source, "A1", "1\t2\n3", Grid.Mode.DECIMALS) }.isFailure && source.cells.size == 1, "malformed grid stays atomic")
        val maxGrid = (1..16).joinToString("\n") { (1..16).joinToString("\t") { "1" } }
        val maxPreview = Grid.prepare(Sheet.empty(), "A1", maxGrid, Grid.Mode.DECIMALS)
        checkThat(maxPreview.numericFields == 256 && maxPreview.calculated.draft.cells.size == 256, "typed exact 256 fields")
        val overflow = (1..17).joinToString("\n") { (1..16).joinToString("\t") { "1" } }
        val fieldError = runCatching { Grid.prepare(Sheet.empty(), "A1", overflow, Grid.Mode.DECIMALS) }.exceptionOrNull()
        checkThat(fieldError is IllegalArgumentException && fieldError.message == "Text grid exceeds 256 fields", "typed field limit, not row/column refusal")
        val exhausted = Sheet.restore(Long.MAX_VALUE, mapOf("A1" to number("1"))).draft
        checkThat(Grid.prepare(exhausted, "A1", "1", Grid.Mode.DECIMALS).changed == 0, "typed no-op at revision limit")
        checkThat(runCatching { Grid.prepare(exhausted, "A1", "2", Grid.Mode.DECIMALS) }.isFailure, "typed revision exhaustion")
        val exactInput = ("x".repeat(4096) + "\t").repeat(15) + "y".repeat(4081)
        checkThat(exactInput.toByteArray().size == Grid.MAX_INPUT_BYTES, "typed exact byte fixture")
        checkThat(Grid.prepare(Sheet.empty(), "A1", exactInput, Grid.Mode.DECIMALS).textFields == 16, "typed exact byte budget")
        val byteError = runCatching { Grid.prepare(Sheet.empty(), "A1", exactInput + "y", Grid.Mode.DECIMALS) }.exceptionOrNull()
        checkThat(byteError is IllegalArgumentException && byteError.message == "Text grid exceeds 64 KiB", "typed byte budget")
        val dependent = Grid.prepare(source, "A1", "12.5", Grid.Mode.DECIMALS)
        checkThat(dependent.calculated.values["D4"] == FormulaEval.CellValue.Num(13.5), "existing formula recalculates from numeric paste")
        checkThat(source.cells["A1"] == null && source.cells["D4"]?.kind == Sheet.Kind.FORMULA, "dependency source remains unchanged")
        File(output, "typed-input.tsv").writeText(raw, Charsets.UTF_8)
        File(output, "typed.xlsx").writeBytes(Sheet.xlsx(result))
        File(output, "dependent.xlsx").writeBytes(Sheet.xlsx(dependent.calculated.draft))

        val random = Random(5901)
        val corpus = ArrayList<String>()
        val b64 = Base64.getEncoder()
        repeat(1500) { index ->
            val whole = random.nextInt(1_000_000).toString()
            val frac = (1 + random.nextInt(9999)).toString()
            val token = when (index % 12) {
                0 -> whole
                1 -> "-$whole"
                2 -> "$whole.$frac"
                3 -> "0$whole"
                4 -> "+$whole"
                5 -> "$whole.0"
                6 -> "${whole}e2"
                7 -> "=$whole"
                8 -> " $whole"
                9 -> whole + "1234567890123456"
                10 -> "0." + "0".repeat(random.nextInt(9)) + frac
                else -> ""
            }
            val quoted = index % 5 == 0
            val encoded = if (quoted) quote(token) else token
            // Empty raw input is refused; quote it to represent an empty field.
            val input = if (encoded.isEmpty()) quote(token) else encoded
            val actualQuoted = quoted || encoded.isEmpty()
            val cell = Grid.prepare(Sheet.empty(), "A1", input, Grid.Mode.DECIMALS).calculated.draft.cells["A1"]
            val kind = cell?.kind?.name ?: "EMPTY"
            checkThat(cell?.input == token || (cell == null && token.isEmpty()), "corpus retains field $index")
            corpus.add(b64.encodeToString(token.toByteArray(Charsets.UTF_8)) + "\t" + actualQuoted + "\t" + kind)
        }
        File(output, "classifications.tsv").writeText(corpus.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        println("R59 typed grid host contracts: $assertions assertions; ${corpus.size} classification observations and XLSX emitted, independent comparison pending")
    }
}
