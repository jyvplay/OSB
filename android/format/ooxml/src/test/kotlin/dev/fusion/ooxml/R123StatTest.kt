package dev.fusion.ooxml

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R123: `CHISQ.TEST` / `F.TEST` / `Z.TEST` / `T.TEST` / `SKEW.P` — statistical test functions.
 * Before this round all five names returned `#NAME?` from a formula.
 *
 * Two independent things are asserted. First, the corpus in `/r123/stat_test_corpus.tsv`,
 * whose expected column is produced here from the documented definitions (chi-square test of
 * independence, two-sample F test, one-sample Z test, Student's t test, population skewness)
 * and independently recomputed in `tools/check_r123_stat_test.py` from the same source
 * generator -- agreement to [TOL] relative measures the Kotlin dispatch against the generator
 * rather than against itself. Second, the behaviour the definitions do not pin down by
 * themselves: the default sigma for `Z.TEST`, the tails/type parameters for `T.TEST`, the
 * refusals (`#NUM!`/`#VALUE!`), and exact-equality semantics.
 *
 * The corpus is bound to the product source by `tools/check_r123_stat_test.py`, which fails if
 * a registered name loses its rows, if a row is duplicated or tampered with, or if the
 * committed file drifts from `tools/gen_r123_stat_test_corpus.py`.
 */
class R123StatTest {

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
            "CHISQ.TEST" to "=CHISQ.TEST({2,1;1,2},{1.5,1.5;1.5,1.5})",
            "F.TEST" to "=F.TEST({1,2,3,4,5},{2,4,6,8,10})",
            "Z.TEST" to "=Z.TEST({1,2,3,4,5},3)",
            "T.TEST" to "=T.TEST({1,2,3,4,5},{2,4,6,8,10},2,2)",
            "SKEW.P" to "=SKEW.P(1,2,3,4,5)",
        )
        for ((name, formula) in probes) {
            assertTrue("$name must evaluate to a number, got ${shown(formula)}",
                value(formula) is FormulaEval.CellValue.Num)
        }
    }

    @Test fun chisqTestIndependence() {
        // CHISQ.TEST({2,1;1,2},{1.5,1.5;1.5,1.5}) — chi2 = 0.6667, df = 1, p ≈ 0.4142
        assertTrue(abs(number("=CHISQ.TEST({2,1;1,2},{1.5,1.5;1.5,1.5})") - 0.41421617824252543) <= TOL)
        // CHISQ.TEST({10,5;5,10},{7.5,7.5;7.5,7.5}) — chi2 = 1.6667, df = 1, p ≈ 0.0679
        assertTrue(abs(number("=CHISQ.TEST({10,5;5,10},{7.5,7.5;7.5,7.5})") - 0.06788915486182934) <= TOL)
        // Perfectly balanced table -> high p-value
        assertTrue(number("=CHISQ.TEST({5,5;5,5},{5,5;5,5})") > 0.9)
    }

    @Test fun fTestTwoSampleVariance() {
        // F.TEST({1,2,3,4,5},{2,4,6,8,10}) — var ratio 4, two-tailed p ≈ 0.2080
        assertTrue(abs(number("=F.TEST({1,2,3,4,5},{2,4,6,8,10})") - 0.20799999999999996) <= TOL)
        // Equal variances -> p = 1
        assertTrue(abs(number("=F.TEST({1,2,3},{1,2,3})") - 1.0) <= TOL)
    }

    @Test fun zTestOneSample() {
        // Z.TEST({1,2,3,4,5},3) — mean = 3, z = 0, p = 0.5
        assertTrue(abs(number("=Z.TEST({1,2,3,4,5},3)") - 0.5) <= TOL)
        // Z.TEST({1,2,3,4,5},3,2) — known sigma = 2, same result
        assertTrue(abs(number("=Z.TEST({1,2,3,4,5},3,2)") - 0.5) <= TOL)
        // Z.TEST({10,20,30},20) — mean = 20, z = 0, p = 0.5
        assertTrue(abs(number("=Z.TEST({10,20,30},20)") - 0.5) <= TOL)
    }

    @Test fun tTestStudent() {
        // T.TEST({1,2,3,4,5},{2,4,6,8,10},2,2) — two-tailed, equal variance, p ≈ 0.0943
        assertTrue(abs(number("=T.TEST({1,2,3,4,5},{2,4,6,8,10},2,2)") - 0.09434977284243765) <= TOL)
        // T.TEST same array -> t = 0, p = 1
        assertTrue(abs(number("=T.TEST({1,2,3},{1,2,3},2,2)") - 1.0) <= TOL)
        // Paired test with identical arrays
        assertTrue(abs(number("=T.TEST({1,2,3},{1,2,3},2,1)") - 1.0) <= TOL)
    }

    @Test fun skewPPopulationSkewness() {
        // SKEW.P(1,2,3,4,5) — symmetric, skew = 0
        assertTrue(abs(number("=SKEW.P(1,2,3,4,5)") - 0.0) <= TOL)
        // SKEW.P(5,4,3,2,1) — symmetric, skew = 0
        assertTrue(abs(number("=SKEW.P(5,4,3,2,1)") - 0.0) <= TOL)
        // Positive skew
        val skew = number("=SKEW.P(1,1,1,2,3)")
        assertTrue(skew > 0.0)
    }

    @Test fun zTestDefaultsSigma() {
        // Z.TEST with one argument omits sigma, uses population SD
        assertEquals(number("=Z.TEST({1,2,3,4,5},3)"), number("=Z.TEST({1,2,3,4,5},3,null)"))
    }

    @Test fun domainRefusals() {
        // Wrong arity -> #VALUE!.
        assertEquals("#VALUE!", shown("=CHISQ.TEST(1)"))
        assertEquals("#VALUE!", shown("=CHISQ.TEST(1,2,3)"))
        assertEquals("#VALUE!", shown("=F.TEST(1)"))
        assertEquals("#VALUE!", shown("=F.TEST(1,2,3)"))
        assertEquals("#VALUE!", shown("=Z.TEST(1)"))
        assertEquals("#VALUE!", shown("=Z.TEST(1,2,3,4)"))
        assertEquals("#VALUE!", shown("=T.TEST(1)"))
        assertEquals("#VALUE!", shown("=T.TEST(1,2)"))
        assertEquals("#VALUE!", shown("=SKEW.P()"))
    }

    @Test fun numericalRefusals() {
        // Zero expected -> #NUM!.
        assertEquals("#NUM!", shown("=CHISQ.TEST({0,0;0,0},{0,0;0,0})"))
        // Zero variance -> #NUM!.
        assertEquals("#NUM!", shown("=F.TEST({1,1},{1,1})"))
        // sigma <= 0 -> #NUM!.
        assertEquals("#NUM!", shown("=Z.TEST({1,2,3},2,0)"))
        // n < 3 -> #NUM!.
        assertEquals("#NUM!", shown("=SKEW.P(1,2)"))
        // sigma = 0 -> #NUM!.
        assertEquals("#NUM!", shown("=SKEW.P(5,5,5)"))
    }

    @Test fun unknownNameIsRefused() {
        assertEquals("#NAME?", shown("=NOCHISQ(1)"))
    }

    @Test fun theSheetEvaluatorReachesThemToo() {
        val sheet = FormulaEval.evaluateSheet(
            mapOf(
                "A1" to FormulaEval.CellInput.NumberLiteral(3.0),
                "B1" to FormulaEval.CellInput.Formula("=Z.TEST({1,2,3,4,5},A1)"),
                "B2" to FormulaEval.CellInput.Formula("=SKEW.P(1,2,3,4,5)"),
            ),
        )
        val b1 = sheet.values["B1"] as FormulaEval.CellValue.Num
        val b2 = sheet.values["B2"] as FormulaEval.CellValue.Num
        assertTrue(abs(b1.value - 0.5) <= TOL)
        assertEquals(0.0, b2.value)
    }

    companion object {
        private const val CORPUS = "/r123/stat_test_corpus.tsv"
    }
}
