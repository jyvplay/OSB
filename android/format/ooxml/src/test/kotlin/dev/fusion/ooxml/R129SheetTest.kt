package dev.fusion.ooxml

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R129: `SHEET` / `SHEETS` — sheet reference functions.
 *
 * `SHEET([value])` returns the sheet number of the referenced cell. In the single-sheet
 * engine this always returns 1. `SHEETS([ref])` returns the number of sheets in the
 * reference; in the single-sheet engine this always returns 1.
 *
 * Corpus-driven test: every row in `/r129/sheet_corpus.tsv` (expected column produced
 * by the pure-math oracle in `tools/gen_r129_sheet_corpus.py`) is evaluated through the
 * engine and compared to `TOL = 1e-12` relative for numeric results, or exactly for
 * refusal literals.
 *
 * The corpus is bound to the product source by `tools/check_r129_sheet.py`, which fails if
 * `SHEET` or `SHEETS` loses its dispatch or export, if a row is duplicated or tampered
 * with, or if the committed file drifts from `tools/gen_r129_sheet_corpus.py`.
 */
class R129SheetTest {

    private data class Row(val function: String, val args: String, val expected: String)

    private val TOL = 1e-12

    private fun corpus(): List<Row> {
        val stream = javaClass.getResourceAsStream(CORPUS) ?: throw AssertionError("missing test resource $CORPUS")
        return stream.bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map { line ->
                val parts = line.split('\t')
                if (parts.size != 3) throw AssertionError("corpus row is not three columns: $line")
                Row(parts[0], parts[1], parts[2])
            }.toList()
        }
    }

    private fun value(formula: String): FormulaEval.CellValue =
        FormulaEval.evaluateFormulaAsGrid(formula, emptyMap())[0][0]

    private fun number(formula: String): Double {
        val got = value(formula)
        if (got !is FormulaEval.CellValue.Num) throw AssertionError("$formula returned ${got.displayText()}")
        return got.value
    }

    private fun shown(formula: String): String = value(formula).displayText()

    @Test fun corpusAgreesWithTheDocumentedDefinitions() {
        var checked = 0
        for (row in corpus()) {
            val formula = "=${row.function}(${row.args})"
            val got = value(formula)
            val detail = "$formula"
            if (row.expected.startsWith("#")) {
                assertEquals("$detail should be ${row.expected}", row.expected, got.displayText())
            } else {
                val want = row.expected.toDouble()
                if (got !is FormulaEval.CellValue.Num) throw AssertionError("$detail returned ${got.displayText()}")
                assertTrue("$detail: expected $want, got ${got.value}",
                    abs(got.value - want) <= TOL * maxOf(1.0, abs(want)))
            }
            checked++
        }
        assertTrue("corpus must stay substantial, saw $checked rows", checked >= 10)
    }

    @Test fun sheetReturnsOneForNoArg() {
        assertTrue(abs(number("=SHEET()") - 1.0) <= TOL)
    }

    @Test fun sheetReturnsOneForCellRef() {
        assertTrue(abs(number("=SHEET(A1)") - 1.0) <= TOL)
    }

    @Test fun sheetReturnsOneForAnyArg() {
        assertTrue(abs(number("=SHEET(\"hello\")") - 1.0) <= TOL)
        assertTrue(abs(number("=SHEET(42)") - 1.0) <= TOL)
    }

    @Test fun sheetsReturnsOneForNoArg() {
        assertTrue(abs(number("=SHEETS()") - 1.0) <= TOL)
    }

    @Test fun sheetsReturnsOneForCellRef() {
        assertTrue(abs(number("=SHEETS(A1)") - 1.0) <= TOL)
    }

    @Test fun sheetsReturnsOneForRange() {
        assertTrue(abs(number("=SHEETS(A1:C5)") - 1.0) <= TOL)
    }

    @Test fun wrongArityRefusals() {
        assertEquals("#VALUE!", shown("=SHEET(A1,B2)"))
        assertEquals("#VALUE!", shown("=SHEETS(A1,B2)"))
    }

    @Test fun threeArgWrongArityRefusals() {
        assertEquals("#VALUE!", shown("=SHEET(A1,B2,C3)"))
        assertEquals("#VALUE!", shown("=SHEETS(A1,B2,C3)"))
    }

    @Test fun unknownNameIsRefused() {
        assertEquals("#NAME?", shown("=NOSHEET(A1)"))
    }

    companion object {
        private const val CORPUS = "/r129/sheet_corpus.tsv"
    }
}
