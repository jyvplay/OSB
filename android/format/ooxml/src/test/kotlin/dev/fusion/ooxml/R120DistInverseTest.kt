package dev.fusion.ooxml

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R120: the distribution functions and inverses the engine was missing — `T.DIST`, `T.DIST.2T`, `T.DIST.RT`,
 * `F.DIST`, `T.INV`, `T.INV.2T`, `CHISQ.INV`, `CHISQ.INV.RT`, `F.INV`, `F.INV.RT`, `BETA.INV`, `GAMMA.INV`
 * and `GAMMALN.PRECISE`. Before this round every one of those names returned `#NAME?` from a formula.
 *
 * Two independent things are asserted. First, the 612-row corpus in `/r120/dist_inverse_corpus.tsv`, whose
 * expected column was produced here from the documented definitions (Microsoft's function reference for each
 * name; the regularized incomplete beta and gamma functions for the CDFs) and independently recomputed in
 * `tools/test_r120_dist_inverse.py` through numerical integration of the probability density functions
 * (Simpson's rule) plus bisection — a genuinely different algorithm from the continued fractions used to
 * generate the corpus. Agreement to [TOL] relative therefore measures two implementations against each other
 * instead of one against itself. Second, the behaviour the definitions do not pin down by themselves: the
 * tail rules (`T.DIST` tails 1 vs 2, `T.INV`/`T.INV.2T` two-tailed), the right- versus left-tailed inverses,
 * the `0 < p < 1` domain, floored degrees of freedom, and the refusal and error-propagation rules.
 *
 * The corpus is bound to the product source by `tools/check_r120_dist_inverse.py`, which fails if a
 * registered name loses its rows, if a row is duplicated or tampered with, or if the committed file drifts
 * from the generator.
 */
class R120DistInverseTest {

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
        assertTrue("corpus must stay substantial, saw $checked rows", checked >= 150)
    }

    @Test fun everyRegisteredNameIsReachableFromAFormula() {
        val probes = mapOf(
            "GAMMALN.PRECISE" to "=GAMMALN.PRECISE(2)",
            "T.DIST" to "=T.DIST(1, 10, 2)",
            "T.DIST.2T" to "=T.DIST.2T(1, 10)",
            "T.DIST.RT" to "=T.DIST.RT(1, 10)",
            "F.DIST" to "=F.DIST(1, 5, 10)",
            "T.INV" to "=T.INV(0.05, 10)",
            "T.INV.2T" to "=T.INV.2T(0.05, 10)",
            "CHISQ.INV" to "=CHISQ.INV(0.05, 10)",
            "CHISQ.INV.RT" to "=CHISQ.INV.RT(0.05, 10)",
            "F.INV" to "=F.INV(0.05, 5, 10)",
            "F.INV.RT" to "=F.INV.RT(0.05, 5, 10)",
            "BETA.INV" to "=BETA.INV(0.05, 2, 3)",
            "GAMMA.INV" to "=GAMMA.INV(0.05, 2, 3)",
        )
        for ((name, formula) in probes) {
            assertTrue("$name must evaluate to a number, got ${shown(formula)}",
                value(formula) is FormulaEval.CellValue.Num)
        }
    }

    @Test fun inversesRoundTripThroughTheirCdfs() {
        // Each inverse must land back on its probability through the CDF kernel it inverts.
        assertTrue(abs(number("=TDIST.2T(T.INV(0.05, 10), 10)") - 0.05) <= TOL)
        assertTrue(abs(number("=TDIST.2T(T.INV.2T(0.95, 10), 10)") - 0.95) <= TOL)
        assertTrue(abs(number("=CHISQ.DIST(CHISQ.INV(0.05, 10), 10, TRUE)") - 0.05) <= TOL)
        assertTrue(abs(number("=CHISQ.DIST(CHISQ.INV.RT(0.05, 10), 10, TRUE)") - 0.95) <= TOL)
        // `F.DIST(x, d1, d2)` (three arguments) is the right tail, so the left-tailed inverse lands on 0.95
        // and the right-tailed inverse lands on 0.05.
        assertTrue(abs(number("=F.DIST(F.INV(0.05, 5, 10), 5, 10)") - 0.95) <= TOL)
        assertTrue(abs(number("=F.DIST(F.INV.RT(0.05, 5, 10), 5, 10)") - 0.05) <= TOL)
        assertTrue(abs(number("=BETA.DIST(BETA.INV(0.05, 2, 3), 2, 3, TRUE)") - 0.05) <= TOL)
        assertTrue(abs(number("=GAMMA.DIST(GAMMA.INV(0.05, 2, 3), 2, 3, TRUE)") - 0.05) <= TOL)
    }

    @Test fun tDistTailRules() {
        // One-tailed mirrors about zero: rt(-x) = 1 - rt(x), so rt(x) + rt(-x) = 1.
        // Two-tailed (tails=2) is twice one-tailed for x >= 0.
        val rt2 = number("=T.DIST(2, 10, 1)")
        val rtNeg2 = number("=T.DIST(-2, 10, 1)")
        assertTrue(abs(rt2 + rtNeg2 - 1.0) <= TOL)
        assertTrue(abs(2.0 * rt2 - number("=T.DIST(2, 10, 2)")) <= 1e-12)
        assertTrue(abs((1.0 - rt2) - rtNeg2) <= TOL)
    }

    @Test fun gammalnPreciseHoldsAtExactPoints() {
        // ln(Gamma(1)) = ln(1) = 0; ln(Gamma(2)) = ln(1) = 0; ln(Gamma(3)) = ln(2).
        // ln(Gamma(1/2)) = ln(sqrt(pi)). Lanczos is accurate to ~1e-16, not exact.
        assertTrue(abs(number("=GAMMALN.PRECISE(1)") - 0.0) <= TOL)
        assertTrue(abs(number("=GAMMALN.PRECISE(2)") - 0.0) <= TOL)
        assertTrue(abs(number("=GAMMALN.PRECISE(3)") - 0.6931471805599453) <= 1e-12)
        assertTrue(abs(number("=GAMMALN.PRECISE(0.5)") - 0.5723649429247001) <= 1e-12)
    }

    @Test fun inversesRefuseOutOfDomain() {
        // Probabilities must live strictly inside (0, 1); degrees of freedom floor to at least 1.
        for (formula in listOf(
            "=T.INV(0, 10)", "=T.INV(1, 10)", "=T.INV(0.5, 0)", "=T.INV(-0.1, 10)", "=T.INV(1.1, 10)",
            "=T.INV.2T(0, 10)", "=T.INV.2T(1, 10)", "=T.INV.2T(0.5, 0)",
            "=CHISQ.INV(0, 10)", "=CHISQ.INV(1, 10)", "=CHISQ.INV(0.5, 0)",
            "=CHISQ.INV.RT(0, 10)", "=CHISQ.INV.RT(1, 10)", "=CHISQ.INV.RT(0.5, 0)",
            "=F.INV(0, 5, 10)", "=F.INV(1, 5, 10)", "=F.INV(0.5, 0, 10)", "=F.INV(0.5, 5, 0)",
            "=F.INV.RT(0, 5, 10)", "=F.INV.RT(1, 5, 10)", "=F.INV.RT(0.5, 0, 10)", "=F.INV.RT(0.5, 5, 0)",
            "=BETA.INV(0, 2, 3)", "=BETA.INV(1, 2, 3)", "=BETA.INV(0.5, 0, 3)", "=BETA.INV(0.5, 2, 0)",
            "=GAMMA.INV(0, 2, 3)", "=GAMMA.INV(1, 2, 3)", "=GAMMA.INV(0.5, 0, 3)", "=GAMMA.INV(0.5, 2, 0)",
        )) {
            assertEquals("$formula must be #NUM!", "#NUM!", shown(formula))
        }
        assertEquals("#NUM!", shown("=GAMMALN.PRECISE(0)"))
        assertEquals("#NUM!", shown("=GAMMALN.PRECISE(-1)"))
    }

    @Test fun tDistTwoTailRefusesNegativeX() {
        // The two-tailed tail is defined only for x >= 0.
        assertEquals("#NUM!", shown("=T.DIST(-1, 10, 2)"))
        assertEquals("#NUM!", shown("=T.DIST(-0.5, 30, 2)"))
    }

    @Test fun gammalnPreciseRefusesNonPositive() {
        assertEquals("#NUM!", shown("=GAMMALN.PRECISE(0)"))
        assertEquals("#NUM!", shown("=GAMMALN.PRECISE(-1)"))
        assertEquals("#NUM!", shown("=GAMMALN.PRECISE(-0.5)"))
    }

    @Test fun tDistAcceptsFlooredDegreesOfFreedom() {
        // Degrees of freedom are floored: 10.7 behaves like 10.
        assertTrue(abs(number("=T.DIST(1, 10, 2)") - number("=T.DIST(1, 10.7, 2)")) <= TOL)
        assertTrue(abs(number("=CHISQ.INV(0.05, 10)") - number("=CHISQ.INV(0.05, 10.9)")) <= TOL)
    }

    @Test fun betaInverseAcceptsSingularShapes() {
        // alpha and beta below 1 give singular densities at the edges; the quantile still exists.
        assertTrue(value("=BETA.INV(0.05, 0.5, 0.5)") is FormulaEval.CellValue.Num)
        assertTrue(value("=BETA.INV(0.95, 0.5, 0.5)") is FormulaEval.CellValue.Num)
        val x = number("=BETA.INV(0.5, 0.5, 0.5)")
        assertTrue(x > 0.0 && x < 1.0)
    }

    @Test fun gammaInverseAcceptsSmallShape() {
        assertTrue(value("=GAMMA.INV(0.5, 0.5, 2)") is FormulaEval.CellValue.Num)
        val x = number("=GAMMA.INV(0.5, 0.5, 2)")
        assertTrue(x > 0.0)
    }

    @Test fun fInverseAcceptsUnitDegreesOfFreedom() {
        assertTrue(value("=F.INV(0.5, 1, 1)") is FormulaEval.CellValue.Num)
        assertTrue(value("=F.INV.RT(0.5, 1, 1)") is FormulaEval.CellValue.Num)
    }

    @Test fun tDistRefusesBadTailsAndDegrees() {
        assertEquals("#NUM!", shown("=T.DIST(1, 10, 3)"))
        assertEquals("#NUM!", shown("=T.DIST(1, 10, 0)"))
        assertEquals("#NUM!", shown("=T.DIST(1, 0, 1)"))
        assertEquals("#NUM!", shown("=T.DIST(-1, 10, 2)"))
        assertEquals("#NUM!", shown("=T.DIST.2T(-1, 10)"))
        assertEquals("#NUM!", shown("=T.DIST.RT(1, 0)"))
        assertEquals("#NUM!", shown("=F.DIST(-1, 5, 10)"))
        assertEquals("#NUM!", shown("=F.DIST(1, 0, 10)"))
        assertEquals("#NUM!", shown("=F.DIST(1, 5, 0)"))
    }

    @Test fun wrongArityAndUnknownNamesAreRefused() {
        assertEquals("#VALUE!", shown("=T.INV(0.5)"))
        assertEquals("#VALUE!", shown("=T.INV(0.5, 10, 3)"))
        assertEquals("#VALUE!", shown("=GAMMALN.PRECISE()"))
        assertEquals("#VALUE!", shown("=BETA.INV(0.5, 2)"))
        assertEquals("#NAME?", shown("=NOSUCHINV(1)"))
        assertEquals("#NAME?", shown("=T.INVS(0.5, 10)"))
    }

    @Test fun theSheetEvaluatorThatTheEditorUsesReachesThemToo() {
        val sheet = FormulaEval.evaluateSheet(
            mapOf(
                "A1" to FormulaEval.CellInput.NumberLiteral(0.05),
                "B1" to FormulaEval.CellInput.Formula("=T.INV(A1, 10)"),
                "B2" to FormulaEval.CellInput.Formula("=CHISQ.INV(A1, 10)"),
                "B3" to FormulaEval.CellInput.Formula("=GAMMALN.PRECISE(2)"),
                "B4" to FormulaEval.CellInput.Formula("=T.DIST.2T(B1, 10)"),
            ),
        )
        assertTrue(abs((sheet.values["B4"] as FormulaEval.CellValue.Num).value - 0.05) <= TOL)
        assertTrue(abs((sheet.values["B3"] as FormulaEval.CellValue.Num).value - 0.0) <= TOL)
        assertTrue("B1 must depend on A1", sheet.dependencies["B1"]!!.contains("A1"))
    }

    companion object {
        private const val CORPUS = "/r120/dist_inverse_corpus.tsv"
    }
}
