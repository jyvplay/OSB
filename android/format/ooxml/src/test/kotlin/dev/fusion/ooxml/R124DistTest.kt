package dev.fusion.ooxml

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R124: `NORM.S.DIST` / `NORM.S.INV` / `BINOM.DIST` / `POISSON.DIST` / `EXPON.DIST` / `CONFIDENCE.NORM`.
 * Before this round all six names returned `#NAME?` from a formula.
 *
 * Corpus-driven test: every row in `/r124/stat_dist_corpus.tsv` (expected column produced by the
 * pure-math oracle in `tools/gen_r124_dist_corpus.py`) is evaluated through the engine and compared
 * to `TOL = 1e-12` relative. Behaviour the definitions don't pin down — the cumulative flag for
 * `NORM.S.DIST`, the inverse-tail semantics for `NORM.S.INV`, the CDF vs PMF choice for
 * `BINOM.DIST`/`POISSON.DIST`/`EXPON.DIST`, the two-sided alpha for `CONFIDENCE.NORM`, and the
 * refusals (`#NUM!`/`#VALUE!`) — are asserted explicitly.
 *
 * The corpus is bound to the product source by `tools/check_r124_dist.py`, which fails if a
 * registered name loses its rows, if a row is duplicated or tampered with, or if the committed
 * file drifts from `tools/gen_r124_dist_corpus.py`.
 */
class R124DistTest {

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
        assertTrue("corpus must stay substantial, saw $checked rows", checked >= 50)
    }

    @Test fun everyRegisteredNameIsReachableFromAFormula() {
        val probes = mapOf(
            "NORM.S.DIST" to "=NORM.S.DIST(1,TRUE)",
            "NORM.S.INV" to "=NORM.S.INV(0.5)",
            "BINOM.DIST" to "=BINOM.DIST(5,10,0.5,TRUE)",
            "POISSON.DIST" to "=POISSON.DIST(2,1,TRUE)",
            "EXPON.DIST" to "=EXPON.DIST(1,1,TRUE)",
            "CONFIDENCE.NORM" to "=CONFIDENCE.NORM(0.05,1,100)",
        )
        for ((name, formula) in probes) {
            assertTrue("$name must evaluate to a number, got ${shown(formula)}",
                value(formula) is FormulaEval.CellValue.Num)
        }
    }

    @Test fun normSDistCdfAndPdf() {
        // NORM.S.DIST(0, TRUE) = 0.5
        assertTrue(abs(number("=NORM.S.DIST(0,TRUE)") - 0.5) <= TOL)
        // NORM.S.DIST(1, TRUE) ≈ 0.8413447361676363
        assertTrue(abs(number("=NORM.S.DIST(1,TRUE)") - 0.8413447361676363) <= TOL)
        // NORM.S.DIST(-1, TRUE) ≈ 0.15865526383236372
        assertTrue(abs(number("=NORM.S.DIST(-1,TRUE)") - 0.15865526383236372) <= TOL)
        // NORM.S.DIST(0, FALSE) = phi(0) ≈ 0.3989422804014327
        assertTrue(abs(number("=NORM.S.DIST(0,FALSE)") - 0.3989422804014327) <= TOL)
        // Symmetry: NORM.S.DIST(-x, TRUE) = 1 - NORM.S.DIST(x, TRUE)
        val pos = number("=NORM.S.DIST(1.5,TRUE)")
        val neg = number("=NORM.S.DIST(-1.5,TRUE)")
        assertTrue(abs(pos + neg - 1.0) <= TOL)
    }

    @Test fun normSInvInverse() {
        // NORM.S.INV(0.5) = 0
        assertTrue(abs(number("=NORM.S.INV(0.5)") - 0.0) <= TOL)
        // NORM.S.INV(NORM.S.DIST(x)) ≈ x for x in (-3, 3)
        for (x in listOf(-2.0, -1.0, 0.0, 1.0, 2.0)) {
            val p = number("=NORM.S.DIST($x,TRUE)")
            val back = number("=NORM.S.INV($p)")
            assertTrue("NORM.S.INV(NORM.S.DIST($x)) should round-trip, got $back", abs(back - x) <= 1e-9)
        }
    }

    @Test fun binomDistPmfAndCdf() {
        // BINOM.DIST(0, 10, 0.5, FALSE) = 0.5^10 = 0.0009765625
        assertTrue(abs(number("=BINOM.DIST(0,10,0.5,FALSE)") - 0.0009765625) <= TOL)
        // BINOM.DIST(5, 10, 0.5, FALSE) = C(10,5) * 0.5^10 ≈ 0.24609375
        assertTrue(abs(number("=BINOM.DIST(5,10,0.5,FALSE)") - 0.24609375) <= TOL)
        // BINOM.DIST(5, 10, 0.5, TRUE) ≈ 0.623046875
        assertTrue(abs(number("=BINOM.DIST(5,10,0.5,TRUE)") - 0.623046875) <= TOL)
        // BINOM.DIST(10, 10, 0.5, TRUE) = 1
        assertTrue(abs(number("=BINOM.DIST(10,10,0.5,TRUE)") - 1.0) <= TOL)
    }

    @Test fun poissonDistPmfAndCdf() {
        // POISSON.DIST(0, 1, FALSE) = e^-1 ≈ 0.36787944117144233
        assertTrue(abs(number("=POISSON.DIST(0,1,FALSE)") - 0.36787944117144233) <= TOL)
        // POISSON.DIST(2, 1, TRUE) = e^-1 * (1 + 1 + 0.5) ≈ 0.9196986029286058
        assertTrue(abs(number("=POISSON.DIST(2,1,TRUE)") - 0.9196986029286058) <= TOL)
        // POISSON.DIST(0, 5, TRUE) = e^-5 ≈ 0.006737947
        assertTrue(abs(number("=POISSON.DIST(0,5,TRUE)") - 0.006737947) <= 1e-8)
    }

    @Test fun exponDistPdfAndCdf() {
        // EXPON.DIST(0, 1, FALSE) = 1 * e^0 = 1
        assertTrue(abs(number("=EXPON.DIST(0,1,FALSE)") - 1.0) <= TOL)
        // EXPON.DIST(1, 1, TRUE) = 1 - e^-1 ≈ 0.6321205588285577
        assertTrue(abs(number("=EXPON.DIST(1,1,TRUE)") - 0.6321205588285577) <= TOL)
        // EXPON.DIST(0, 1, TRUE) = 0
        assertTrue(abs(number("=EXPON.DIST(0,1,TRUE)") - 0.0) <= TOL)
    }

    @Test fun confidenceNorm() {
        // CONFIDENCE.NORM(0.05, 1, 100) = NORM.S.INV(0.975) * 1 / 10 ≈ 0.1959963986120195
        assertTrue(abs(number("=CONFIDENCE.NORM(0.05,1,100)") - 0.1959963986120195) <= TOL)
        // Larger std dev -> wider interval
        val narrow = number("=CONFIDENCE.NORM(0.05,1,100)")
        val wide = number("=CONFIDENCE.NORM(0.05,2,100)")
        assertTrue(wide > narrow)
    }

    @Test fun domainRefusals() {
        // Wrong arity -> #VALUE!.
        assertEquals("#VALUE!", shown("=NORM.S.DIST(0)"))
        assertEquals("#VALUE!", shown("=NORM.S.DIST(0,TRUE,1)"))
        assertEquals("#VALUE!", shown("=NORM.S.INV()"))
        assertEquals("#VALUE!", shown("=NORM.S.INV(0.5,0.3)"))
        assertEquals("#VALUE!", shown("=BINOM.DIST(5,10,0.5)"))
        assertEquals("#VALUE!", shown("=BINOM.DIST(5,10,0.5,TRUE,1)"))
        assertEquals("#VALUE!", shown("=POISSON.DIST(0,1)"))
        assertEquals("#VALUE!", shown("=POISSON.DIST(0,1,TRUE,1)"))
        assertEquals("#VALUE!", shown("=EXPON.DIST(1,1)"))
        assertEquals("#VALUE!", shown("=EXPON.DIST(1,1,TRUE,1)"))
        assertEquals("#VALUE!", shown("=CONFIDENCE.NORM(0.05,1)"))
        assertEquals("#VALUE!", shown("=CONFIDENCE.NORM(0.05,1,100,1)"))
    }

    @Test fun numericalRefusals() {
        // NORM.S.INV domain.
        assertEquals("#NUM!", shown("=NORM.S.INV(0)"))
        assertEquals("#NUM!", shown("=NORM.S.INV(1)"))
        assertEquals("#NUM!", shown("=NORM.S.INV(-0.5)"))
        // BINOM.DIST domain.
        assertEquals("#NUM!", shown("=BINOM.DIST(-1,10,0.5,TRUE)"))
        assertEquals("#NUM!", shown("=BINOM.DIST(11,10,0.5,TRUE)"))
        assertEquals("#NUM!", shown("=BINOM.DIST(5,10,1.5,TRUE)"))
        // POISSON.DIST domain.
        assertEquals("#NUM!", shown("=POISSON.DIST(-1,5,TRUE)"))
        assertEquals("#NUM!", shown("=POISSON.DIST(5,-1,TRUE)"))
        // EXPON.DIST domain.
        assertEquals("#NUM!", shown("=EXPON.DIST(-1,1,TRUE)"))
        assertEquals("#NUM!", shown("=EXPON.DIST(1,0,TRUE)"))
        assertEquals("#NUM!", shown("=EXPON.DIST(1,-0.5,TRUE)"))
        // CONFIDENCE.NORM domain.
        assertEquals("#NUM!", shown("=CONFIDENCE.NORM(0,1,100)"))
        assertEquals("#NUM!", shown("=CONFIDENCE.NORM(1,1,100)"))
        assertEquals("#NUM!", shown("=CONFIDENCE.NORM(0.05,-1,100)"))
        assertEquals("#NUM!", shown("=CONFIDENCE.NORM(0.05,1,0)"))
    }

    @Test fun unknownNameIsRefused() {
        assertEquals("#NAME?", shown("=NONORMDIST(0.5)"))
    }

    @Test fun theSheetEvaluatorReachesThemToo() {
        val sheet = FormulaEval.evaluateSheet(
            mapOf(
                "A1" to FormulaEval.CellInput.NumberLiteral(0.5),
                "B1" to FormulaEval.CellInput.Formula("=NORM.S.INV(A1)"),
                "B2" to FormulaEval.CellInput.Formula("=EXPON.DIST(1,1,TRUE)"),
            ),
        )
        val b1 = sheet.values["B1"] as FormulaEval.CellValue.Num
        val b2 = sheet.values["B2"] as FormulaEval.CellValue.Num
        assertTrue(abs(b1.value - 0.0) <= TOL)
        assertTrue(abs(b2.value - 0.6321205588285577) <= TOL)
    }

    companion object {
        private const val CORPUS = "/r124/stat_dist_corpus.tsv"
    }
}
