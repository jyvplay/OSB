package dev.fusion.ooxml

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R131: `AMORDEGRC` / `AMORLINC` / `DB` / `ISPMT` / `PRICE` / `PRICEMAT` / `YIELD` / `YIELDMAT`
 * — financial functions.
 *
 * French amortization (AMORDEGRC/AMORLINC), fixed-declining depreciation (DB), interest
 * payment (ISPMT), bond price (PRICE/PRICEMAT), bond yield (YIELD/YIELDMAT).
 *
 * Corpus-driven test: every row in `/r131/financial_corpus.tsv` (expected column produced by
 * the pure-math oracle in `tools/gen_r131_financial_corpus.py`) is evaluated through the
 * engine and compared to `TOL = 1e-12` relative for numeric results, or exactly for
 * refusal literals.
 *
 * The corpus is bound to the product source by `tools/check_r131_financial.py`, which fails if
 * any financial function loses its dispatch or export, if a row is duplicated or tampered
 * with, or if the committed file drifts from `tools/gen_r131_financial_corpus.py`.
 */
class R131FinancialTest {

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

    @Test fun ismtInterestPaymentFirstPeriod() {
        assertTrue(abs(number("=ISPMT(0.1, 1, 4, 1000)") - 100.0) <= TOL)
    }

    @Test fun ismtInterestPaymentSecondPeriod() {
        assertTrue(abs(number("=ISPMT(0.1, 2, 4, 1000)") - 75.0) <= TOL)
    }

    @Test fun dbFirstPeriodDepreciation() {
        assertTrue(abs(number("=DB(1000, 100, 5, 1, 12)") - 369.0426555198067) <= TOL)
    }

    @Test fun dbPeriodBeyondLifeReturnsZero() {
        assertTrue(abs(number("=DB(1000, 100, 5, 6, 12)") - 0.0) <= TOL)
    }

    @Test fun amorlincFullPeriod() {
        assertTrue(abs(number("=AMORLINC(1000, 39448, 39813, 100, 1, 0.5, 0)") - 500.0) <= TOL)
    }

    @Test fun amordegrcFullPeriod() {
        assertTrue(abs(number("=AMORDEGRC(1000, 39448, 39813, 100, 1, 0.5, 0)") - 1250.0) <= TOL)
    }

    @Test fun priceParBond() {
        assertTrue(abs(number("=PRICE(39448, 39813, 0.05, 0.05, 100, 2, 0)") - 100.0) <= TOL)
    }

    @Test fun pricematSimple() {
        assertTrue(abs(number("=PRICEMAT(39448.0, 39813.0, 39448.0, 0.05, 0.05, 0)") - 95.17514871116987) <= TOL)
    }

    @Test fun yieldParBond() {
        assertTrue(abs(number("=YIELD(39448, 39813, 0.05, 100, 100, 2, 0)") - 0.05) <= TOL)
    }

    @Test fun wrongArityRefused() {
        assertEquals("#VALUE!", shown("=ISPMT(0.1, 1, 4)"))
        assertEquals("#VALUE!", shown("=DB(1000, 100, 5)"))
        assertEquals("#VALUE!", shown("=AMORLINC(1000, 39448)"))
    }

    @Test fun invalidInputRefused() {
        assertEquals("#NUM!", shown("=ISPMT(0.1, 0, 4, 1000)"))
        assertEquals("#NUM!", shown("=DB(1000, 100, 0, 1, 12)"))
        assertEquals("#NUM!", shown("=PRICE(39813, 39448, 0.05, 0.05, 100, 2, 0)"))
    }

    companion object {
        private const val CORPUS = "/r131/financial_corpus.tsv"
    }
}
