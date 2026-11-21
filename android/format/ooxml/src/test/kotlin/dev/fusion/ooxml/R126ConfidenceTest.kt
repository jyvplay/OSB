package dev.fusion.ooxml

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R126: `CONFIDENCE` — legacy alias for CONFIDENCE.NORM.
 * Before this round `CONFIDENCE` returned `#NAME?` from a formula even though the
 * SCOPE string listed it as supported; only `CONFIDENCE.NORM` was dispatched.
 *
 * Corpus-driven test: every row in `/r126/confidence_corpus.tsv` (expected column produced
 * by the pure-math oracle in `tools/gen_r126_confidence_corpus.py`) is evaluated through the
 * engine and compared to `TOL = 1e-12` relative. The legacy `CONFIDENCE` name is asserted to
 * produce identical results to `CONFIDENCE.NORM` for the same inputs.
 *
 * The corpus is bound to the product source by `tools/check_r126_confidence.py`, which fails if
 * `CONFIDENCE` loses its dispatch or export, if a row is duplicated or tampered with, or if the
 * committed file drifts from `tools/gen_r126_confidence_corpus.py`.
 */
class R126ConfidenceTest {

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

    @Test fun confidenceIsAliasForConfidenceNorm() {
        // CONFIDENCE must produce identical results to CONFIDENCE.NORM for the same inputs
        val cases = listOf(
            "0.05,1.0,100",
            "0.05,2.0,100",
            "0.01,1.0,100",
            "0.10,1.0,100",
            "0.05,1.5,50",
        )
        for (args in cases) {
            val legacy = number("=CONFIDENCE($args)")
            val modern = number("=CONFIDENCE.NORM($args)")
            assertTrue("CONFIDENCE($args) must equal CONFIDENCE.NORM($args), got $legacy vs $modern",
                abs(legacy - modern) <= TOL)
        }
    }

    @Test fun confidenceValues() {
        // CONFIDENCE(0.05, 1, 100) = NORM.S.INV(0.975) * 1 / 10 ≈ 0.1959963986120195
        assertTrue(abs(number("=CONFIDENCE(0.05,1.0,100)") - 0.1959963986120195) <= TOL)
        // CONFIDENCE(0.05, 2, 100) ≈ 0.391992797224039
        assertTrue(abs(number("=CONFIDENCE(0.05,2.0,100)") - 0.391992797224039) <= TOL)
        // CONFIDENCE(0.01, 1, 100) ≈ 0.25758293064439264
        assertTrue(abs(number("=CONFIDENCE(0.01,1.0,100)") - 0.25758293064439264) <= TOL)
    }

    @Test fun confidenceDifferentStdDev() {
        // Larger standard deviation -> wider interval
        val narrow = number("=CONFIDENCE(0.05,1.0,100)")
        val wide = number("=CONFIDENCE(0.05,5.0,100)")
        assertTrue(wide > narrow)
        assertTrue(abs(wide - narrow * 5.0) <= TOL * maxOf(1.0, abs(wide)))
    }

    @Test fun confidenceDifferentSize() {
        // Larger sample size -> narrower interval
        val smallN = number("=CONFIDENCE(0.05,1.0,10)")
        val largeN = number("=CONFIDENCE(0.05,1.0,100)")
        assertTrue(smallN > largeN)
    }

    @Test fun confidenceDifferentAlpha() {
        // Larger alpha -> wider interval
        val alpha10 = number("=CONFIDENCE(0.10,1.0,100)")
        val alpha05 = number("=CONFIDENCE(0.05,1.0,100)")
        assertTrue(alpha10 > alpha05)
    }

    @Test fun domainRefusals() {
        // alpha <= 0 or alpha >= 1 -> #NUM!.
        assertEquals("#NUM!", shown("=CONFIDENCE(0,1,100)"))
        assertEquals("#NUM!", shown("=CONFIDENCE(1,1,100)"))
        // standard_dev <= 0 -> #NUM!.
        assertEquals("#NUM!", shown("=CONFIDENCE(0.05,-1,100)"))
        // size < 1 -> #NUM!.
        assertEquals("#NUM!", shown("=CONFIDENCE(0.05,1,0)"))
        assertEquals("#NUM!", shown("=CONFIDENCE(0.05,1,-5)"))
    }

    @Test fun wrongArityRefusals() {
        // Only 2 args -> #VALUE!.
        assertEquals("#VALUE!", shown("=CONFIDENCE(0.05,1)"))
        // 4 args -> #VALUE!.
        assertEquals("#VALUE!", shown("=CONFIDENCE(0.05,1,100,1)"))
        // 0 args -> #VALUE!.
        assertEquals("#VALUE!", shown("=CONFIDENCE()"))
    }

    @Test fun unknownNameIsRefused() {
        assertEquals("#NAME?", shown("=NOCONFIDENCE(0.05,1,100)"))
    }

    @Test fun theSheetEvaluatorReachesItToo() {
        val sheet = FormulaEval.evaluateSheet(
            mapOf(
                "A1" to FormulaEval.CellInput.NumberLiteral(0.05),
                "B1" to FormulaEval.CellInput.Formula("=CONFIDENCE(A1,1.0,100)"),
                "B2" to FormulaEval.CellInput.Formula("=CONFIDENCE.NORM(A1,1.0,100)"),
            ),
        )
        val b1 = sheet.values["B1"] as FormulaEval.CellValue.Num
        val b2 = sheet.values["B2"] as FormulaEval.CellValue.Num
        assertTrue(abs(b1.value - 0.1959963986120195) <= TOL)
        assertTrue(abs(b1.value - b2.value) <= TOL)
    }

    companion object {
        private const val CORPUS = "/r126/confidence_corpus.tsv"
    }
}
