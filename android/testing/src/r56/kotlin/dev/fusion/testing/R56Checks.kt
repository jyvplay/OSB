// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.CellErr
import dev.fusion.ooxml.FormulaEval
import dev.fusion.ooxml.FormulaXlsx
import dev.fusion.ooxml.OfficeSheetCodec
import dev.fusion.ooxml.OfficeSheetDraft
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

/** Finite real JVM contracts and comparison corpus, not Excel or Android acceptance. */
object R56Checks {
    private var assertions = 0
    private fun verify(condition: Boolean, message: String) {
        assertions++
        check(condition) { message }
    }
    private fun eval(formula: String): FormulaEval.CellValue =
        FormulaEval.evaluateSheet(mapOf("A1" to FormulaEval.CellInput.Formula(formula))).values.getValue("A1")
    private fun number(formula: String, expected: Double) {
        verify(eval(formula) == FormulaEval.CellValue.Num(expected), "$formula should be $expected")
    }
    private fun put(draft: OfficeSheetDraft.Draft, ref: String, kind: OfficeSheetDraft.Kind, value: String) =
        OfficeSheetDraft.replace(draft, ref, OfficeSheetDraft.Cell(kind, value)).draft

    @JvmStatic fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val cases = listOf(
            "=ROUND(1.005,2)" to 1.01,
            "=ROUND(-1.005,2)" to -1.01,
            "=ROUND(0.00000000000015,13)" to 2e-13,
            "=ROUND(15000000000000,-13)" to 2e13,
            "=ROUND(-15000000000000,-13)" to -2e13,
            "=ROUND(1e308,2)" to 1e308,
            "=ROUND(1e308,-308)" to 1e308,
            "=ROUND(1e308,-309)" to 0.0,
            "=ROUND(1e-320,320)" to 1e-320,
            "=ROUND(1e-320,319)" to 0.0,
            "=ROUND(1e-320,1e100)" to 1e-320,
            "=ROUND(4.9e-324,323)" to 0.0,
            "=ROUND(4.9e-324,324)" to Double.MIN_VALUE,
            "=ROUND(4.9e-324,325)" to Double.MIN_VALUE,
            "=ROUND(-4.9e-324,324)" to -Double.MIN_VALUE,
            "=ROUND(1.7e308,399)" to 1.7e308,
            "=ROUND(1.7e308,400)" to 1.7e308,
            "=ROUND(1.7e308,-399)" to 0.0,
            "=ROUND(1.7e308,-400)" to 0.0,
            "=ROUND(1.7976931348623157e308,399)" to Double.MAX_VALUE,
            "=ROUND(1.7976931348623157e308,400)" to Double.MAX_VALUE,
            "=ROUND(1.7976931348623157e308,-309)" to 0.0,
            "=ROUND(1e308,-1e100)" to 0.0,
            "=ROUND(2.5,0)" to 3.0,
            "=ROUND(-2.5,0)" to -3.0,
            "=ROUND(1.2345,1.9)" to 1.2,
            "=ROUND(15.5,-1.9)" to 20.0,
            "=ROUND(2.15,1)" to 2.2,
            "=ROUND(-1.475,2)" to -1.48,
            "=ROUND(626.3,-3)" to 1000.0,
            "=IFERROR(ROUND(1.7e308,-308),42)" to 42.0,
        )
        for ((formula, expected) in cases) number(formula, expected)
        verify(eval("=ROUND(1.7e308,-308)") == FormulaEval.CellValue.Err(CellErr.Err.NUM), "finite overflow becomes NUM")
        verify(eval("=ROUND(\"bad\",2)") == FormulaEval.CellValue.Err(CellErr.Err.VALUE), "invalid direct text")
        verify(eval("=ROUND(1/0,2)") == FormulaEval.CellValue.Err(CellErr.Err.DIV0), "first argument error")
        verify(eval("=ROUND(2,1/0)") == FormulaEval.CellValue.Err(CellErr.Err.DIV0), "second argument error")
        verify(eval("=ROUND(1)") == FormulaEval.CellValue.Err(CellErr.Err.VALUE), "wrong arity")
        verify(eval("=ROUND(1,2,3)") == FormulaEval.CellValue.Err(CellErr.Err.VALUE), "extra argument")
        val edgeRows = cases.map { (formula, _) ->
            val value = (eval(formula) as FormulaEval.CellValue.Num).value
            "$formula\tN\t$value"
        } + "=ROUND(1.7e308,-308)\tE\t#NUM!"
        File(out, "round-edges.tsv").writeText(edgeRows.joinToString("\n", postfix = "\n"), Charsets.UTF_8)

        var sheet = OfficeSheetDraft.empty()
        sheet = put(sheet, "A1", OfficeSheetDraft.Kind.NUMBER, "1.005")
        sheet = put(sheet, "B1", OfficeSheetDraft.Kind.FORMULA, "=ROUND(A1,2)")
        sheet = put(sheet, "C1", OfficeSheetDraft.Kind.FORMULA, "=B1*100")
        verify(OfficeSheetDraft.calculate(sheet).values["B1"] == FormulaEval.CellValue.Num(1.01), "authoring admits correct ROUND")
        verify(OfficeSheetDraft.calculate(sheet).values["C1"] == FormulaEval.CellValue.Num(101.0), "dependent recalculation")
        val workbook = OfficeSheetDraft.xlsx(sheet)
        File(out, "round.xlsx").writeBytes(workbook)
        val decoded = FormulaXlsx.read(workbook)
        verify((decoded.inputs["B1"] as? FormulaEval.CellInput.Formula)?.body == "ROUND(A1,2)", "XLSX formula")
        verify(decoded.cachedValues["B1"] == FormulaEval.CellValue.Num(1.01), "XLSX typed cache")
        val checkpoint = ByteArrayOutputStream().also { OfficeSheetCodec.write(sheet, it) }.toByteArray()
        verify(OfficeSheetCodec.read(ByteArrayInputStream(checkpoint)) == sheet, "ROUND survives checkpoint")
        val previous = sheet
        sheet = put(sheet, "A1", OfficeSheetDraft.Kind.NUMBER, "-1.005")
        verify(OfficeSheetDraft.calculate(sheet).values["B1"] == FormulaEval.CellValue.Num(-1.01), "changed source recalculates")
        verify(OfficeSheetDraft.calculate(previous).values["B1"] == FormulaEval.CellValue.Num(1.01), "prior immutable draft unchanged")
        verify(runCatching { put(sheet, "D1", OfficeSheetDraft.Kind.FORMULA, "=CONCAT(A1,A1)") }.isFailure, "unbounded text still refused")

        // Decimal inputs have at most three places; the independent checker uses Python
        // Decimal and separately formualizer. The records are observations, not an oracle.
        val scales = listOf(-14, -13, -3, -2, -1, 0, 1, 2, 3, 4, 12, 13, 14, 20)
        val rows = ArrayList<String>()
        for (i in -200..200) {
            val magnitude = kotlin.math.abs(i * 5)
            val literal = (if (i < 0) "-" else "") + String.format(Locale.ROOT, "%d.%03d", magnitude / 1000, magnitude % 1000)
            for (scale in scales) {
                val formula = "=ROUND($literal,$scale)"
                val value = eval(formula)
                verify(value is FormulaEval.CellValue.Num, "finite corpus result $formula")
                val numeric = (value as FormulaEval.CellValue.Num).value
                if (scale >= 3) verify(numeric == literal.toDouble(), "no-op scale $formula")
                rows.add("$literal\t$scale\t$numeric")
            }
        }
        File(out, "round-corpus.tsv").writeText(rows.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        verify(rows.size == 401 * scales.size, "complete deterministic corpus")
        println("R56 host contracts: $assertions assertions passed; ${rows.size} ROUND observations emitted, not yet independently compared")
    }
}
