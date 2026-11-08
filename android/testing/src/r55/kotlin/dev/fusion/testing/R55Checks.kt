// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.CellErr
import dev.fusion.ooxml.FormulaEval
import dev.fusion.ooxml.FormulaXlsx
import dev.fusion.ooxml.MultiSheetBook
import dev.fusion.ooxml.OfficeSheetCodec
import dev.fusion.ooxml.OfficeSheetDraft
import dev.fusion.ooxml.SpillSheetEngine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

/** Real JVM contracts and a bounded comparison corpus, not Android or Excel acceptance. */
object R55Checks {
    private var assertions = 0
    private fun verify(condition: Boolean, message: String) {
        assertions++
        check(condition) { message }
    }
    private fun cell(kind: OfficeSheetDraft.Kind, value: String) = OfficeSheetDraft.Cell(kind, value)
    private fun edit(draft: OfficeSheetDraft.Draft, ref: String, kind: OfficeSheetDraft.Kind, value: String) =
        OfficeSheetDraft.replace(draft, ref, cell(kind, value)).draft
    private fun result(draft: OfficeSheetDraft.Draft, ref: String) = OfficeSheetDraft.calculate(draft).values[ref]
    private fun b64(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
    private fun encodedValue(value: FormulaEval.CellValue): Pair<String, String> = when (value) {
        FormulaEval.CellValue.Empty -> "Z" to ""
        is FormulaEval.CellValue.Num -> "N" to value.displayText()
        is FormulaEval.CellValue.Text -> "T" to value.value
        is FormulaEval.CellValue.Bool -> "B" to value.displayText()
        is FormulaEval.CellValue.Err -> "E" to value.error.literal
    }

    private fun formulaCorpus(): Set<String> {
        val formulas = linkedSetOf(
            "=A5", "=A1+A2", "=A1/0", "=SUM(A1:A5)", "=SUM(A1:A10)", "=AVERAGE(A1:A5)",
            "=COUNT(A1:A10)", "=COUNTA(A1:A10)", "=IF(TRUE,1,1/0)", "=IF(FALSE,1/0,2)",
            "=IFERROR(1/0,\"ok\")", "=IFERROR(A5,4)", "=IFERROR(1/0,A5)", "=A5=0", "=A5=\"\"", "=A5=FALSE",
            "=A8=A9", "=LEN(A8)", "=LEN(\"\")", "=SUM(TRUE,2)", "=SUM(\"5\",2)", "=SUM(A3)",
            "=SUM(A3+0)", "=COUNT(A3)", "=COUNT(\"5\")", "=COUNT(TRUE)", "=COUNT(A4)", "=COUNTA(A7)",
            "=IF(A4,\"yes\",\"no\")", "=NOT(A5)", "=AND(A1:A5)", "=OR(A1:A5)", "=AND(A5)", "=OR(A5)",
            "=A3+2", "=ABS(A3)", "=MOD(-3,2)", "=MOD(3,-2)", "=INT(-1.1)", "=1e308+1e308", "=1e308/1e-308",
            "=AVERAGE(1e308,1e308)", "=1e-320*0.5", "=-2^2", "=2^3^2", "=(2^3)^2", "=2^-3", "=1/2*3",
            "=\"abc\"=\"ABC\"", "=A6+1", "=IF(FALSE,A6,99)", "=IFERROR(A6,0)", "=AVERAGE(A3:A5)",
            "=MIN(A3:A5)", "=MAX(A3:A5)",
        )
        val atoms = listOf("A1", "A2", "A3", "A4", "A5", "A7", "A8", "A9", "0", "1", "\"5\"", "\"\"", "TRUE", "FALSE")
        for (a in atoms) for (b in atoms) for (op in listOf("=", "<>", "<", ">")) formulas.add("=$a$op$b")
        val arguments = listOf("A1:A5", "A3:A5", "A5:A7", "A7:A9", "1,2", "TRUE,1", "\"5\",2", "\"x\",2", "A1,A2,A3,A4,A5", "A6", "A5")
        for (name in listOf("SUM", "AVERAGE", "MIN", "MAX", "COUNT", "COUNTA", "AND", "OR")) {
            for (args in arguments) formulas.add("=$name($args)")
        }
        return formulas
    }

    @JvmStatic fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val formula = OfficeSheetDraft.Kind.FORMULA
        val num = OfficeSheetDraft.Kind.NUMBER
        var sheet = OfficeSheetDraft.empty()
        for ((ref, expression) in linkedMapOf(
            "B1" to "=A1", "B2" to "=IFERROR(A1,7)", "B3" to "=IFERROR(1/0,A1)", "B4" to "=IFERROR(1/0,\"\")",
            "C1" to "=COUNT(A1)", "C2" to "=COUNTA(A1)", "C3" to "=AVERAGE(A1)", "C4" to "=COUNT(B1:B4)",
            "C5" to "=COUNTA(B1:B4)", "C6" to "=B1=\"\"", "C7" to "=B4=\"\"", "C8" to "=LEN(IFERROR(A1,7))",
            "C9" to "=A1=\"\"", "C10" to "=A1=0", "C11" to "=IFERROR(A1,7)+1", "C12" to "=SUM(A1,1)",
            "C13" to "=SUM(\"not numeric\",2)",
        )) sheet = edit(sheet, ref, formula, expression)
        for (ref in listOf("B1", "B2", "B3")) verify(result(sheet, ref) == FormulaEval.CellValue.Num(0.0), "$ref blank result is numeric zero")
        verify(result(sheet, "B4") == FormulaEval.CellValue.Text(""), "explicit empty text is not a blank cell or zero")
        verify("A1" !in sheet.cells && "A1" !in OfficeSheetDraft.calculate(sheet).values, "source cell is still unoccupied")
        verify(result(sheet, "C1") == FormulaEval.CellValue.Num(0.0), "COUNT ignores blank source")
        verify(result(sheet, "C2") == FormulaEval.CellValue.Num(0.0), "COUNTA ignores blank source")
        verify(result(sheet, "C3") == FormulaEval.CellValue.Err(CellErr.Err.DIV0), "AVERAGE of blank source")
        verify(result(sheet, "C4") == FormulaEval.CellValue.Num(3.0), "COUNT counts three formula zeros")
        verify(result(sheet, "C5") == FormulaEval.CellValue.Num(4.0), "COUNTA counts formula results including empty text")
        verify(result(sheet, "C6") == FormulaEval.CellValue.Bool(false), "a formula zero is not empty text")
        verify(result(sheet, "C7") == FormulaEval.CellValue.Bool(true), "explicit empty text remains text")
        verify(result(sheet, "C8") == FormulaEval.CellValue.Num(0.0), "blank remains blank inside an expression")
        verify(result(sheet, "C9") == FormulaEval.CellValue.Bool(true), "empty source compares equal to empty text")
        verify(result(sheet, "C10") == FormulaEval.CellValue.Bool(true), "empty source compares equal to zero")
        verify(result(sheet, "C11") == FormulaEval.CellValue.Num(1.0), "blank used by arithmetic")
        verify(result(sheet, "C12") == FormulaEval.CellValue.Num(1.0), "blank source ignored by aggregate")
        verify(result(sheet, "C13") == FormulaEval.CellValue.Err(CellErr.Err.VALUE), "non-numeric direct aggregate argument remains an error")

        val bytes = ByteArrayOutputStream().also { OfficeSheetCodec.write(sheet, it) }.toByteArray()
        val recovered = OfficeSheetCodec.read(ByteArrayInputStream(bytes))
        verify(recovered == sheet && result(recovered, "C4") == FormulaEval.CellValue.Num(3.0), "recovery recalculates same typed formula results")
        val workbook = OfficeSheetDraft.xlsx(sheet)
        File(out, "blank-references.xlsx").writeBytes(workbook)
        val reread = FormulaXlsx.read(workbook)
        verify(reread.cachedValues["B1"] == FormulaEval.CellValue.Num(0.0), "XLSX numeric cache for blank reference")
        verify(reread.cachedValues["B4"] == FormulaEval.CellValue.Text(""), "XLSX explicit empty text cache")
        verify(reread.cachedValues["C5"] == FormulaEval.CellValue.Num(4.0), "XLSX downstream cache")
        val before = sheet
        sheet = edit(sheet, "A1", num, "9")
        verify(result(sheet, "B1") == FormulaEval.CellValue.Num(9.0), "new source recalculates")
        verify(result(sheet, "B2") == FormulaEval.CellValue.Num(9.0), "IFERROR passes new nonerror source")
        verify(result(before, "B1") == FormulaEval.CellValue.Num(0.0), "prior immutable draft remains unchanged")
        sheet = OfficeSheetDraft.replace(sheet, "A1", null).draft
        verify(result(sheet, "B1") == FormulaEval.CellValue.Num(0.0), "cleared source recalculates to zero")
        verify(result(sheet, "C4") == FormulaEval.CellValue.Num(3.0), "dependent count recomputed after clear")
        val direct = FormulaEval.evaluateSheet(mapOf("B1" to FormulaEval.CellInput.Formula("=A1"))).values["B1"]
        verify(direct == FormulaEval.CellValue.Num(0.0), "shared evaluator also stores numeric formula result")
        // MultiSheetBook's synthetic probe is the *completed* formula, unlike the spill
        // engine's intermediate argument probe. Its result must be materialized.
        val sheets = listOf(
            MultiSheetBook.SheetDef("Source", mapOf(
                "B1" to FormulaEval.CellInput.Formula("=A1"),
                "C1" to FormulaEval.CellInput.Formula("=\"\""),
            )),
            MultiSheetBook.SheetDef("Summary", mapOf(
                "B1" to FormulaEval.CellInput.Formula("=Source!A1"),
                "B2" to FormulaEval.CellInput.Formula("=Source!B1"),
                "B3" to FormulaEval.CellInput.Formula("=COUNT(Source!B1)"),
                "B4" to FormulaEval.CellInput.Formula("=COUNT(Source!A1)"),
                "B5" to FormulaEval.CellInput.Formula("=COUNTA(Source!C1)"),
            )),
        )
        val book = MultiSheetBook.evaluate(sheets)
        verify(book.cell("Source", "A1") == FormulaEval.CellValue.Empty, "multisheet source stays empty")
        verify(book.cell("Source", "B1") == FormulaEval.CellValue.Num(0.0), "multisheet local formula zero")
        verify(book.cell("Source", "C1") == FormulaEval.CellValue.Text(""), "multisheet explicit empty text")
        verify(book.cell("Summary", "B1") == FormulaEval.CellValue.Num(0.0), "cross-sheet blank source materializes")
        verify(book.cell("Summary", "B2") == FormulaEval.CellValue.Num(0.0), "cross-sheet formula zero propagates")
        verify(book.cell("Summary", "B3") == FormulaEval.CellValue.Num(1.0), "cross-sheet COUNT sees formula zero")
        verify(book.cell("Summary", "B4") == FormulaEval.CellValue.Num(0.0), "cross-sheet COUNT ignores blank source")
        verify(book.cell("Summary", "B5") == FormulaEval.CellValue.Num(1.0), "cross-sheet COUNTA sees empty text")
        val multisheetXlsx = MultiSheetBook.write(sheets)
        File(out, "blank-multisheet.xlsx").writeBytes(multisheetXlsx)
        val rereadBook = MultiSheetBook.evaluate(MultiSheetBook.read(multisheetXlsx))
        verify(rereadBook.cell("Summary", "B3") == FormulaEval.CellValue.Num(1.0), "multisheet XLSX roundtrip recalculates")
        val seq = SpillSheetEngine.evaluate(mapOf("B1" to FormulaEval.CellInput.Formula("=SEQUENCE(1,1,A5)")))
        verify(seq.typedGrid["B1"] == FormulaEval.CellValue.Num(1.0), "spill subexpression keeps existing blank start behavior")
        val sort = SpillSheetEngine.evaluate(mapOf(
            "A1" to FormulaEval.CellInput.NumberLiteral(2.0), "A2" to FormulaEval.CellInput.NumberLiteral(1.0),
            "B1" to FormulaEval.CellInput.Formula("=SORT(A1:A2,A5)"),
        ))
        verify(sort.typedGrid["B1"] == FormulaEval.CellValue.Num(1.0), "spill sort blank index preserves prior default")
        verify(sort.typedGrid["B2"] == FormulaEval.CellValue.Num(2.0), "spill sort order preserved")
        val split = SpillSheetEngine.evaluate(mapOf("B1" to FormulaEval.CellInput.Formula("=TEXTSPLIT(A5,\":\")")))
        verify(split.typedGrid["B1"] == FormulaEval.CellValue.Text(""), "spill text subexpression remains empty")
        val filter = SpillSheetEngine.evaluate(mapOf(
            "A1" to FormulaEval.CellInput.NumberLiteral(2.0), "A2" to FormulaEval.CellInput.NumberLiteral(1.0),
            "B1" to FormulaEval.CellInput.BoolLiteral(false), "B2" to FormulaEval.CellInput.BoolLiteral(false),
            "D1" to FormulaEval.CellInput.Formula("=FILTER(A1:A2,B1:B2,C5)"),
        ))
        verify(filter.typedGrid["D1"] == FormulaEval.CellValue.Empty, "spill fallback keeps prior intermediate blank")
        val stable = sheet
        val refused = runCatching { edit(sheet, "D1", formula, "=AVERAGE(1e308,1e308)") }
        verify(refused.isFailure && sheet == stable && "D1" !in sheet.cells, "overflowing average is refused without mutating draft")

        // Exercise source present/empty transitions and readback, not just a single constructed sheet.
        for (i in -50..50) {
            sheet = edit(sheet, "A1", num, i.toString())
            verify(result(sheet, "B1") == FormulaEval.CellValue.Num(i.toDouble()), "direct source update $i")
            verify(result(sheet, "B2") == FormulaEval.CellValue.Num(i.toDouble()), "IFERROR primary $i")
            verify(result(sheet, "B3") == FormulaEval.CellValue.Num(i.toDouble()), "IFERROR fallback $i")
            verify(result(sheet, "C1") == FormulaEval.CellValue.Num(1.0), "numeric source is counted $i")
            verify(result(sheet, "C4") == FormulaEval.CellValue.Num(3.0), "formula numbers counted $i")
            verify(result(sheet, "C5") == FormulaEval.CellValue.Num(4.0), "formula empty text counted $i")
            val present = sheet
            sheet = OfficeSheetDraft.replace(sheet, "A1", null).draft
            verify(result(sheet, "B1") == FormulaEval.CellValue.Num(0.0), "clear recalculates reference $i")
            verify(result(sheet, "C1") == FormulaEval.CellValue.Num(0.0), "cleared source is not counted $i")
            verify(result(present, "B1") == FormulaEval.CellValue.Num(i.toDouble()), "earlier snapshot remains frozen $i")
            val checkpoint = ByteArrayOutputStream().also { OfficeSheetCodec.write(sheet, it) }.toByteArray()
            verify(result(OfficeSheetCodec.read(ByteArrayInputStream(checkpoint)), "C5") == FormulaEval.CellValue.Num(4.0), "clear survives recovery $i")
        }

        // Emit independent-comparison inputs/results in deterministic text; it is not itself an oracle.
        val sample = OfficeSheetDraft.restore(0, linkedMapOf(
            "A1" to cell(num, "3"), "A2" to cell(num, "-2"), "A3" to cell(OfficeSheetDraft.Kind.TEXT, "5"),
            "A4" to cell(OfficeSheetDraft.Kind.BOOLEAN, "TRUE"), "A6" to cell(formula, "=1/0"),
            "A7" to cell(formula, "=\"\""), "A8" to cell(OfficeSheetDraft.Kind.TEXT, "Hello"),
            "A9" to cell(OfficeSheetDraft.Kind.TEXT, "HELLO"), "A10" to cell(OfficeSheetDraft.Kind.TEXT, " "),
        )).draft
        val corpus = formulaCorpus()
        val results = corpus.map { expression ->
            val actual = try {
                encodedValue(OfficeSheetDraft.replace(sample, "B1", cell(formula, expression)).values.getValue("B1"))
            } catch (e: IllegalArgumentException) {
                "R" to (e.message ?: e.javaClass.simpleName)
            }
            "${b64(expression)}\t${actual.first}\t${b64(actual.second)}"
        }
        File(out, "formula-corpus.tsv").writeText(results.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        verify(corpus.size >= 900, "comparison corpus is populated")
        println("R55 host contracts: $assertions assertions passed; ${corpus.size} formula observations emitted, not yet independently compared")
    }
}
