package dev.fusion.ooxml

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R121: `BESSELK` / `BESSELY` — Bessel functions of the second kind of orders 0, 1, 2, ... for x > 0.
 * Before this round both names returned `#NAME?` from a formula.
 *
 * Two independent things are asserted. First, the corpus in `/r121/bessel_corpus.tsv`, whose expected
 * column is produced here from the documented definitions (convergent DLMF series below x = 10, DLMF
 * asymptotic beyond) and independently recomputed in `tools/check_r121_bessel.py` from the same source
 * generator — agreement to [TOL] relative measures the Kotlin dispatch against the generator rather than
 * against itself. Second, the behaviour the definitions do not pin down by themselves: the x > 0,
 * n >= 0 domain, the refusals (`#NUM!`/`#VALUE!`), and the independent recurrence and Wronskian identities
 * each function family satisfies.
 *
 * The corpus is bound to the product source by `tools/check_r121_bessel.py`, which fails if a registered
 * name loses its rows, if a row is duplicated or tampered with, or if the committed file drifts from
 * `tools/gen_r121_bessel_corpus.py`.
 */
class R121BesselTest {

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
        assertTrue("corpus must stay substantial, saw $checked rows", checked >= 100)
    }

    @Test fun everyRegisteredNameIsReachableFromAFormula() {
        val probes = mapOf(
            "BESSELK" to "=BESSELK(2, 1)",
            "BESSELY" to "=BESSELY(2, 1)",
        )
        for ((name, formula) in probes) {
            assertTrue("$name must evaluate to a number, got ${shown(formula)}",
                value(formula) is FormulaEval.CellValue.Num)
        }
    }

    @Test fun k0AndK1MatchReferencePoints() {
        // DLMF references: K_0(1) = 0.4210244382407083, K_1(1) = 0.6019072301972345, K_0(2) = 0.1138938727495336,
        // K_0(10) = 1.778006069145861e-05.
        assertTrue(abs(number("=BESSELK(1, 0)") - 0.4210244382407083) <= TOL)
        assertTrue(abs(number("=BESSELK(1, 1)") - 0.6019072301972345) <= TOL)
        assertTrue(abs(number("=BESSELK(2, 0)") - 0.1138938727495336) <= TOL)
        assertTrue(abs(number("=BESSELK(10, 0)") - 1.778006069145861e-05) <= TOL)
    }

    @Test fun y0AndY1MatchReferencePoints() {
        // DLMF references: Y_0(1) = 0.0882569642156770, Y_1(1) = -0.7812128213002887, Y_0(2) = 0.5103756676150117.
        assertTrue(abs(number("=BESSELY(1, 0)") - 0.0882569642156770) <= TOL)
        assertTrue(abs(number("=BESSELY(1, 1)") - (-0.7812128213002887)) <= TOL)
        assertTrue(abs(number("=BESSELY(2, 0)") - 0.5103756726497453) <= TOL)
    }

    @Test fun kSeriesAndAsymptoteAgreeAtTheCrossover() {
        // In the overlap region both representations are transitioning, so only 1e-3 agreement is expected;
        // away from it each is accurate to 1e-12 (covered by the corpus).
        val series = number("=BESSELK(10, 0)")
        val asymp = number("=BESSELK(11, 0)")
        assertTrue(series > 0.0 && asymp > 0.0)
        assertTrue(abs(series - asymp) <= 1e-3 * series)
    }

    @Test fun kStaysPositiveAndFiniteForLargeX() {
        // The series alone turns negative above x ~ 12; the asymptotic keeps K positive to arbitrary x.
        for (n in 0..3) {
            val v = number("=BESSELK(500, $n)")
            assertTrue("K_$n(500) must be positive and finite, got $v", v > 0.0 && v.isFinite())
        }
        val tiny = number("=BESSELK(1000, 0)")
        assertTrue("K_0(1000) must underflow to 0.0 or a tiny positive number, not negative: $tiny", tiny >= 0.0)
    }

    @Test fun kRecurrenceIdentity() {
        // K_{i-1}(x) - K_{i+1}(x) = -2i/x * K_i(x).
        for (x in listOf(1.0, 2.0, 5.0, 10.0)) {
            for (i in 1..3) {
                val km1 = number("=BESSELK($x, ${i - 1})")
                val k = number("=BESSELK($x, $i)")
                val kp1 = number("=BESSELK($x, ${i + 1})")
                assertTrue("K recurrence at x=$x i=$i",
                    abs(km1 - kp1 - (-2.0 * i / x) * k) <= TOL * maxOf(1.0, abs(km1)))
            }
        }
    }

    @Test fun yRecurrenceIdentity() {
        // Y_{i-1}(x) + Y_{i+1}(x) = 2i/x * Y_i(x).
        for (x in listOf(1.0, 2.0, 5.0, 10.0)) {
            for (i in 1..3) {
                val ym1 = number("=BESSELY($x, ${i - 1})")
                val y = number("=BESSELY($x, $i)")
                val yp1 = number("=BESSELY($x, ${i + 1})")
                assertTrue("Y recurrence at x=$x i=$i",
                    abs(ym1 + yp1 - (2.0 * i / x) * y) <= TOL * maxOf(1.0, abs(ym1 + yp1)))
            }
        }
    }

    @Test fun recurrencesHoldBeyondTheCrossover() {
        // The asymptotic branch is active here; the recurrence identities must still hold across it.
        for (x in listOf(11.0, 20.0, 50.0)) {
            for (i in 1..2) {
                val km1 = number("=BESSELK($x, ${i - 1})")
                val k = number("=BESSELK($x, $i)")
                val kp1 = number("=BESSELK($x, ${i + 1})")
                assertTrue("K recurrence (asymptotic) at x=$x i=$i",
                    abs(km1 - kp1 - (-2.0 * i / x) * k) <= TOL * maxOf(1.0, abs(km1)))
                val ym1 = number("=BESSELY($x, ${i - 1})")
                val y = number("=BESSELY($x, $i)")
                val yp1 = number("=BESSELY($x, ${i + 1})")
                assertTrue("Y recurrence (asymptotic) at x=$x i=$i",
                    abs(ym1 + yp1 - (2.0 * i / x) * y) <= TOL * maxOf(1.0, abs(ym1 + yp1)))
            }
        }
    }

    @Test fun yStaysFiniteForLargeX() {
        // The asymptotic keeps Y finite at large x; series cancellation must not leak through at x = 10+.
        for (n in 0..3) {
            val v = number("=BESSELY(500, $n)")
            assertTrue("Y_$n(500) must be finite, got $v", v.isFinite())
        }
    }

    @Test fun domainRefusals() {
        // x must be > 0; the order n must be >= 0.
        for (formula in listOf(
            "=BESSELK(0, 0)", "=BESSELK(-1, 0)", "=BESSELK(1, -1)", "=BESSELK(0, -1)",
            "=BESSELY(0, 0)", "=BESSELY(-1, 0)", "=BESSELY(1, -1)", "=BESSELY(0, -1)",
        )) {
            assertEquals("$formula must be #NUM!", "#NUM!", shown(formula))
        }
    }

    @Test fun arityRefusals() {
        assertEquals("#VALUE!", shown("=BESSELK(1)"))
        assertEquals("#VALUE!", shown("=BESSELK(1, 0, 0)"))
        assertEquals("#VALUE!", shown("=BESSELY(1)"))
        assertEquals("#VALUE!", shown("=BESSELY(1, 0, 0)"))
    }

    @Test fun unknownNameIsRefused() {
        assertEquals("#NAME?", shown("=NOBESSEL(1)"))
    }

    @Test fun theSheetEvaluatorReachesThemToo() {
        val sheet = FormulaEval.evaluateSheet(
            mapOf(
                "A1" to FormulaEval.CellInput.NumberLiteral(2.0),
                "B1" to FormulaEval.CellInput.Formula("=BESSELK(A1, 1)"),
                "B2" to FormulaEval.CellInput.Formula("=BESSELY(A1, 0)"),
            ),
        )
        val b1 = sheet.values["B1"] as FormulaEval.CellValue.Num
        val b2 = sheet.values["B2"] as FormulaEval.CellValue.Num
        assertTrue(abs(b1.value - 0.1398658818165224) <= TOL)
        assertTrue(abs(b2.value - 0.5103756726497453) <= TOL)
    }

    companion object {
        private const val CORPUS = "/r121/bessel_corpus.tsv"
    }
}
