package dev.fusion.ooxml

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R134: `FORECAST.ETS` / `FORECAST.ETS.CONFINT` / `FORECAST.ETS.SEASONALITY` / `FORECAST.ETS.STAT`
 * — exponential triple smoothing (Holt-Winters) forecasting.
 *
 * Corpus-driven test: every row in `/r134/forecast_ets_corpus.tsv` (expected column produced
 * by the pure-math oracle in `tools/gen_r134_forecast_ets_corpus.py`) is evaluated through the
 * engine and compared to `TOL = 1e-12` relative for numeric results, or exactly for
 * refusal literals.
 *
 * The corpus is bound to the product source by `tools/check_r134_forecast_ets.py`, which fails if
 * any FORECAST.ETS function loses its dispatch or export, if a row is duplicated or tampered
 * with, or if the committed file drifts from `tools/gen_r134_forecast_ets_corpus.py`.
 */
class R134ForecastEtsTest {

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

    @Test fun forecastEtsSeasonality() {
        assertTrue(abs(number("=FORECAST.ETS.SEASONALITY(A1:A12, B1:B12)") - 4.0) <= TOL)
    }

    @Test fun forecastEtsPointForecast() {
        assertTrue(abs(number("=FORECAST.ETS(13, A1:A12, B1:B12)") - 12.304638542843296) <= TOL)
    }

    @Test fun forecastEtsPointForecast14() {
        assertTrue(abs(number("=FORECAST.ETS(14, A1:A12, B1:B12)") - 17.43860970830992) <= TOL)
    }

    @Test fun forecastEtsExplicitSeasonality() {
        assertTrue(abs(number("=FORECAST.ETS(13, A1:A12, B1:B12, 4)") - 12.304638542843296) <= TOL)
    }

    @Test fun forecastEtsLinearFallback() {
        assertTrue(abs(number("=FORECAST.ETS(13, A1:A3, B1:B3)") - 23.333333333333336) <= TOL)
    }

    @Test fun forecastEtsConfint() {
        assertTrue(abs(number("=FORECAST.ETS.CONFINT(13, A1:A12, B1:B12)") - 10.457451436943337) <= TOL)
    }

    @Test fun forecastEtsConfint90() {
        assertTrue(abs(number("=FORECAST.ETS.CONFINT(13, A1:A12, B1:B12, 0.90)") - 8.776169882470995) <= TOL)
    }

    @Test fun forecastEtsConfintZero() {
        assertTrue(abs(number("=FORECAST.ETS.CONFINT(12, A1:A12, B1:B12)") - 0.0) <= TOL)
    }

    @Test fun forecastEtsStatAlpha() {
        assertTrue(abs(number("=FORECAST.ETS.STAT(A1:A12, B1:B12, 4, 1, 1, 1)") - 0.3) <= TOL)
    }

    @Test fun forecastEtsStatBeta() {
        assertTrue(abs(number("=FORECAST.ETS.STAT(A1:A12, B1:B12, 4, 1, 1, 2)") - 0.1) <= TOL)
    }

    @Test fun forecastEtsStatGamma() {
        assertTrue(abs(number("=FORECAST.ETS.STAT(A1:A12, B1:B12, 4, 1, 1, 3)") - 0.1) <= TOL)
    }

    @Test fun forecastEtsStatMase() {
        assertTrue(abs(number("=FORECAST.ETS.STAT(A1:A12, B1:B12, 4, 1, 1, 4)") - 1.03125) <= TOL)
    }

    @Test fun forecastEtsStatSmse() {
        assertTrue(abs(number("=FORECAST.ETS.STAT(A1:A12, B1:B12, 4, 1, 1, 5)") - 26.278067502075437) <= TOL)
    }

    @Test fun forecastEtsStatSmpe() {
        assertTrue(abs(number("=FORECAST.ETS.STAT(A1:A12, B1:B12, 4, 1, 1, 6)") - 24.182604574589732) <= TOL)
    }

    @Test fun wrongArityRefused() {
        assertEquals("#VALUE!", shown("=FORECAST.ETS(13)"))
        assertEquals("#VALUE!", shown("=FORECAST.ETS.SEASONALITY(A1:A3)"))
        assertEquals("#VALUE!", shown("=FORECAST.ETS.CONFINT(13)"))
        assertEquals("#VALUE!", shown("=FORECAST.ETS.STAT(A1:A12, B1:B12, 4, 1, 1)"))
    }

    @Test fun invalidInputRefused() {
        assertEquals("#NUM!", shown("=FORECAST.ETS.CONFINT(13, A1:A12, B1:B12, 1.5)"))
        assertEquals("#NUM!", shown("=FORECAST.ETS.STAT(A1:A12, B1:B12, 4, 1, 1, 7)"))
        assertEquals("#NUM!", shown("=FORECAST.ETS.SEASONALITY(A1:A12, B1:B12, 0)"))
    }

    companion object {
        private const val CORPUS = "/r134/forecast_ets_corpus.tsv"
    }
}
