package dev.fusion.ooxml

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R127: `DOLLAR` / `FIXED` / `TIMEVALUE` / `COLUMN` / `ROW` / `COLUMNS` / `ROWS`.
 *
 * Corpus-driven test: every row in `/r127/textref_corpus.tsv` (expected column produced
 * by the pure-math oracle in `tools/gen_r127_textref_corpus.py`) is evaluated through the
 * engine and compared to `TOL = 1e-12` relative for numeric results, or exactly for text
 * results (`$`-prefixed DOLLAR output and plain FIXED output) and refusal literals.
 *
 * The corpus is bound to the product source by `tools/check_r127_textref.py`, which fails if
 * any R127 name loses its dispatch or export, if a row is duplicated or tampered with, or if
 * the committed file drifts from `tools/gen_r127_textref_corpus.py`.
 */
class R127TextRefTest {

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
            } else if (row.function in listOf("DOLLAR", "FIXED")) {
                // Text output: exact string match
                assertEquals("$detail: expected ${row.expected}, got ${got.displayText()}",
                    row.expected, got.displayText())
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

    @Test fun dollarFormatsCurrency() {
        assertEquals("$1,234.50", shown("=DOLLAR(1234.5,2)"))
        assertEquals("$1,235", shown("=DOLLAR(1234.5,0)"))
        assertEquals("$0.00", shown("=DOLLAR(0,2)"))
        assertEquals("-$1,234.50", shown("=DOLLAR(-1234.5,2)"))
    }

    @Test fun dollarDefaultsToTwoDecimals() {
        assertEquals("$1,234.50", shown("=DOLLAR(1234.5)"))
    }

    @Test fun fixedFormatsWithCommas() {
        assertEquals("1,234.50", shown("=FIXED(1234.5,2)"))
        assertEquals("1,235", shown("=FIXED(1234.5,0)"))
    }

    @Test fun fixedSuppressesCommas() {
        assertEquals("1234.50", shown("=FIXED(1234.5,2,1)"))
    }

    @Test fun fixedDefaultsToTwoDecimals() {
        assertEquals("1,234.50", shown("=FIXED(1234.5)"))
    }

    @Test fun timevalueParsesHms() {
        assertTrue(abs(number("=TIMEVALUE(\"12:00:00\")") - 0.5) <= TOL)
        assertTrue(abs(number("=TIMEVALUE(\"00:00:00\")") - 0.0) <= TOL)
        assertTrue(abs(number("=TIMEVALUE(\"01:30:00\")") - 0.0625) <= TOL)
    }

    @Test fun timevalueParsesHm() {
        assertTrue(abs(number("=TIMEVALUE(\"12:30\")") - 0.5208333333333334) <= TOL)
    }

    @Test fun columnReturnsIndex() {
        assertTrue(abs(number("=COLUMN(A1)") - 1.0) <= TOL)
        assertTrue(abs(number("=COLUMN(Z1)") - 26.0) <= TOL)
        assertTrue(abs(number("=COLUMN(AA1)") - 27.0) <= TOL)
        assertTrue(abs(number("=COLUMN($C$5)") - 3.0) <= TOL)
    }

    @Test fun rowReturnsIndex() {
        assertTrue(abs(number("=ROW(A1)") - 1.0) <= TOL)
        assertTrue(abs(number("=ROW(A10)") - 10.0) <= TOL)
        assertTrue(abs(number("=ROW($C$5)") - 5.0) <= TOL)
    }

    @Test fun columnsCountsColumns() {
        assertTrue(abs(number("=COLUMNS(A1:C5)") - 3.0) <= TOL)
        assertTrue(abs(number("=COLUMNS(A1:A5)") - 1.0) <= TOL)
    }

    @Test fun rowsCountsRows() {
        assertTrue(abs(number("=ROWS(A1:C5)") - 5.0) <= TOL)
        assertTrue(abs(number("=ROWS(A1:A5)") - 5.0) <= TOL)
    }

    @Test fun domainRefusals() {
        assertEquals("#VALUE!", shown("=DOLLAR(abc)"))
        assertEquals("#VALUE!", shown("=FIXED(abc)"))
        assertEquals("#VALUE!", shown("=TIMEVALUE(not-a-time)"))
        assertEquals("#NUM!", shown("=TIMEVALUE(25:00:00)"))
        assertEquals("#VALUE!", shown("=COLUMN("))
        assertEquals("#VALUE!", shown("=ROW("))
        assertEquals("#VALUE!", shown("=COLUMNS("))
        assertEquals("#VALUE!", shown("=ROWS("))
    }

    @Test fun wrongArityRefusals() {
        assertEquals("#VALUE!", shown("=COLUMN(A1,B2)"))
        assertEquals("#VALUE!", shown("=ROW(A1,B2)"))
        assertEquals("#VALUE!", shown("=COLUMNS(A1,B2)"))
        assertEquals("#VALUE!", shown("=ROWS(A1,B2)"))
    }

    @Test fun unknownNameIsRefused() {
        assertEquals("#NAME?", shown("=NODOLLAR(1.5)"))
    }

    companion object {
        private const val CORPUS = "/r127/textref_corpus.tsv"
    }
}
