package dev.fusion.ooxml

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R133: `IMCOSH` / `IMCOT` / `IMCOTH` / `VDB` / `ODDFPRICE` / `ODDFYIELD` /
 * `ODDLPRICE` / `ODDLYIELD` — remaining complex hyperbolic and financial functions.
 *
 * IMCOSH/IMCOT/IMCOTH operate on complex numbers in x+yi or x+yj format:
 *   IMCOSH = cosh(z), IMCOT = cot(z) = 1/tan(z), IMCOTH = coth(z) = 1/tanh(z).
 *
 * VDB(cost, salvage, life, start, end, [factor], [no_switch]) =
 *   variable declining balance depreciation between start and end periods.
 *
 * ODDFPRICE/ODDFYIELD/ODDLPRICE/ODDLYIELD = odd-coupon-period bond price/yield.
 *
 * Corpus-driven test: every row in `/r133/remaining_corpus.tsv` (expected column produced
 * by the pure-math oracle in `tools/gen_r133_remaining_corpus.py`) is evaluated through
 * the engine and compared to `TOL = 1e-12` relative for numeric results, or exactly for
 * refusal literals.
 *
 * The corpus is bound to the product source by `tools/check_r133_remaining.py`, which fails
 * if any function loses its dispatch or export, if a row is duplicated or tampered with,
 * or if the committed file drifts from `tools/gen_r133_remaining_corpus.py`.
 */
class R133RemainingTest {

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

    @Test fun imcoshHyperbolicCosine() {
        assertEquals("-0.64214812471552+1.0686074213827783i", shown("=IMCOSH(1+2i)"))
    }

    @Test fun imcotCotangent() {
        assertEquals("0.0327977555337526-0.984329226458191i", shown("=IMCOT(1+2i)"))
    }

    @Test fun imcothHyperbolicCotangent() {
        assertEquals("0.8213297974938517+0.17138361290918508i", shown("=IMCOTH(1+2i)"))
    }

    @Test fun imcotZeroRefused() {
        assertEquals("#DIV/0!", shown("=IMCOT(0+0i)"))
    }

    @Test fun vdbFirstPeriod() {
        assertTrue(abs(number("=VDB(1000, 100, 5, 1, 1, 2)") - 400.0) <= TOL)
    }

    @Test fun vdbTwoPeriods() {
        assertTrue(abs(number("=VDB(1000, 100, 5, 1, 2, 2)") - 640.0) <= TOL)
    }

    @Test fun vdbPeriod2Only() {
        assertTrue(abs(number("=VDB(1000, 100, 5, 2, 2, 2)") - 240.0) <= TOL)
    }

    @Test fun vdbFullDepreciation() {
        assertTrue(abs(number("=VDB(1000, 100, 5, 1, 5, 2)") - 900.0) <= TOL)
    }

    @Test fun odffpriceParBond() {
        assertTrue(abs(number("=ODDFPRICE(39500, 39813, 39448, 39600, 0.05, 0.05, 100, 2, 0)") - 100.0) <= TOL)
    }

    @Test fun odffyieldParBond() {
        assertTrue(abs(number("=ODDFYIELD(39500, 39813, 39448, 39600, 0.05, 100, 100, 2, 0)") - 0.05) <= TOL)
    }

    @Test fun oddlpriceParBond() {
        assertTrue(abs(number("=ODDLPRICE(39500, 39813, 39700, 0.05, 0.05, 100, 2, 0)") - 100.0) <= TOL)
    }

    @Test fun oddlyieldParBond() {
        assertTrue(abs(number("=ODDLYIELD(39500, 39813, 39700, 0.05, 100, 100, 2, 0)") - 0.05) <= TOL)
    }

    @Test fun wrongArityRefused() {
        assertEquals("#VALUE!", shown("=IMCOSH()"))
        assertEquals("#VALUE!", shown("=VDB(1000, 100, 5)"))
        assertEquals("#VALUE!", shown("=ODDFPRICE(39500, 39813)"))
    }

    @Test fun invalidInputRefused() {
        assertEquals("#NUM!", shown("=IMCOT(notcomplex)"))
        assertEquals("#NUM!", shown("=VDB(1000, 100, 0, 1, 1, 2)"))
        assertEquals("#NUM!", shown("=ODDFYIELD(39813, 39500, 39448, 39600, 0.05, 95, 100, 2, 0)"))
    }

    companion object {
        private const val CORPUS = "/r133/remaining_corpus.tsv"
    }
}
