package dev.fusion.ooxml

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R122: `PHI` / `DELTA` / `GESTEP` — standard normal probability density and engineering
 * comparison functions. Before this round all three names returned `#NAME?` from a formula.
 *
 * Two independent things are asserted. First, the corpus in `/r122/phi_corpus.tsv`, whose
 * expected column is produced here from the documented definitions (standard normal PDF
 * for `PHI`, equality test for `DELTA`, threshold test for `GESTEP`) and independently
 * recomputed in `tools/check_r122_phi.py` from the same source generator -- agreement to
 * [TOL] relative measures the Kotlin dispatch against the generator rather than against
 * itself. Second, the behaviour the definitions do not pin down by themselves: the default
 * step argument for `GESTEP`, the refusals (`#NUM!`/`#VALUE!`), and exact-equality semantics
 * for `DELTA`.
 *
 * The corpus is bound to the product source by `tools/check_r122_phi.py`, which fails if a
 * registered name loses its rows, if a row is duplicated or tampered with, or if the committed
 * file drifts from `tools/gen_r122_phi_corpus.py`.
 */
class R122PhiTest {

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
        assertTrue("corpus must stay substantial, saw $checked rows", checked >= 80)
    }

    @Test fun everyRegisteredNameIsReachableFromAFormula() {
        val probes = mapOf(
            "PHI" to "=PHI(1)",
            "DELTA" to "=DELTA(1, 1)",
            "GESTEP" to "=GESTEP(1)",
        )
        for ((name, formula) in probes) {
            assertTrue("$name must evaluate to a number, got ${shown(formula)}",
                value(formula) is FormulaEval.CellValue.Num)
        }
    }

    @Test fun phiMatchesStandardNormalPDF() {
        // PHI(x) = exp(-x^2/2) / sqrt(2*pi).
        // References: PHI(0) = 0.3989422804014327, PHI(1) = 0.24197072451914337,
        // PHI(-1) = 0.24197072451914337, PHI(2) = 0.05399096651318806.
        assertTrue(abs(number("=PHI(0)") - 0.3989422804014327) <= TOL)
        assertTrue(abs(number("=PHI(1)") - 0.24197072451914337) <= TOL)
        assertTrue(abs(number("=PHI(-1)") - 0.24197072451914337) <= TOL)
        assertTrue(abs(number("=PHI(2)") - 0.05399096651318806) <= TOL)
        // PHI is even: PHI(x) = PHI(-x).
        assertTrue(abs(number("=PHI(1.5)") - number("=PHI(-1.5)")) <= TOL)
    }

    @Test fun phiIsPositiveAndFinite() {
        for (x in listOf(-10.0, -5.0, 0.0, 5.0, 10.0)) {
            val v = number("=PHI($x)")
            assertTrue("PHI($x) must be positive and finite, got $v", v > 0.0 && v.isFinite())
        }
        // PHI(10) is about 7.69e-23, still positive.
        assertTrue(number("=PHI(10)") > 0.0)
    }

    @Test fun deltaEqualitySemantics() {
        // DELTA(a, b) = 1 if a == b else 0.
        assertEquals("1.0", shown("=DELTA(1, 1)"))
        assertEquals("1.0", shown("=DELTA(0, 0)"))
        assertEquals("1.0", shown("=DELTA(-2.5, -2.5)"))
        assertEquals("0.0", shown("=DELTA(1, 2)"))
        assertEquals("0.0", shown("=DELTA(0, 1)"))
        assertEquals("0.0", shown("=DELTA(-1, 1)"))
        // 0 and -0 are equal in IEEE 754.
        assertEquals("1.0", shown("=DELTA(0, -0)"))
    }

    @Test fun gestepThresholdSemantics() {
        // GESTEP(x, [step]) = 1 if x >= step else 0; step defaults to 0.
        assertEquals("1.0", shown("=GESTEP(1)"))
        assertEquals("0.0", shown("=GESTEP(-1)"))
        assertEquals("1.0", shown("=GESTEP(0)"))
        assertEquals("1.0", shown("=GESTEP(1, 1)"))
        assertEquals("0.0", shown("=GESTEP(0.999, 1)"))
        assertEquals("1.0", shown("=GESTEP(1, 0)"))
        assertEquals("0.0", shown("=GESTEP(-5, -4)"))
        assertEquals("1.0", shown("=GESTEP(-4, -4)"))
    }

    @Test fun gestepDefaultsStepToZero() {
        // With one argument, GESTEP compares against 0.
        assertEquals(number("=GESTEP(2)"), number("=GESTEP(2, 0)"))
        assertEquals(number("=GESTEP(-2)"), number("=GESTEP(-2, 0)"))
        assertEquals(number("=GESTEP(0)"), number("=GESTEP(0, 0)"))
    }

    @Test fun domainRefusals() {
        // Wrong arity -> #VALUE!.
        assertEquals("#VALUE!", shown("=PHI()"))
        assertEquals("#VALUE!", shown("=PHI(1, 2)"))
        assertEquals("#VALUE!", shown("=DELTA(1)"))
        assertEquals("#VALUE!", shown("=DELTA(1, 2, 3)"))
        assertEquals("#VALUE!", shown("=GESTEP()"))
        assertEquals("#VALUE!", shown("=GESTEP(1, 2, 3)"))
        // Non-numeric argument -> #VALUE!.
        assertEquals("#VALUE!", shown("=PHI(abc)"))
        assertEquals("#VALUE!", shown("=DELTA(abc, 2)"))
        assertEquals("#VALUE!", shown("=DELTA(1, abc)"))
        assertEquals("#VALUE!", shown("=GESTEP(abc)"))
        assertEquals("#VALUE!", shown("=GESTEP(1, abc)"))
    }

    @Test fun unknownNameIsRefused() {
        assertEquals("#NAME?", shown("=NOPHI(1)"))
    }

    @Test fun theSheetEvaluatorReachesThemToo() {
        val sheet = FormulaEval.evaluateSheet(
            mapOf(
                "A1" to FormulaEval.CellInput.NumberLiteral(1.0),
                "B1" to FormulaEval.CellInput.Formula("=PHI(A1)"),
                "B2" to FormulaEval.CellInput.Formula("=DELTA(A1, 1)"),
                "B3" to FormulaEval.CellInput.Formula("=GESTEP(A1, 0.5)"),
            ),
        )
        val b1 = sheet.values["B1"] as FormulaEval.CellValue.Num
        val b2 = sheet.values["B2"] as FormulaEval.CellValue.Num
        val b3 = sheet.values["B3"] as FormulaEval.CellValue.Num
        assertTrue(abs(b1.value - 0.24197072451914337) <= TOL)
        assertEquals(1.0, b2.value)
        assertEquals(1.0, b3.value)
    }

    companion object {
        private const val CORPUS = "/r122/phi_corpus.tsv"
    }
}
