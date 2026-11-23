package dev.fusion.ooxml

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R130: `DAVERAGE` / `DCOUNT` / `DCOUNTA` / `DGET` / `DMAX` / `DMIN` / `DPRODUCT` /
 * `DSTDEV` / `DSTDEVP` / `DSUM` / `DVAR` / `DVARP` — database aggregation functions.
 *
 * Each function takes (database, field, criteria): the database range's first row is
 * headers; `field` resolves by header name or 1-based column index; the criteria range's
 * first row names database columns, each criteria row is an AND of its cells, and multiple
 * criteria rows are OR'd together. Blank criteria cells match anything.
 *
 * Corpus-driven test: every row in `/r130/database_corpus.tsv` (expected column produced
 * by the pure-math oracle in `tools/gen_r130_database_corpus.py`) is evaluated through the
 * engine and compared to `TOL = 1e-12` relative for numeric results, or exactly for
 * refusal literals.
 *
 * The corpus is bound to the product source by `tools/check_r130_database.py`, which fails if
 * any database function loses its dispatch or export, if a row is duplicated or tampered
 * with, or if the committed file drifts from `tools/gen_r130_database_corpus.py`.
 */
class R130DatabaseTest {

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

    @Test fun DaverageAveragesMatchingPrices() {
        assertTrue(abs(number("=DAVERAGE(A1:D5, 2, G1:H2)") - 1.75) <= TOL)
    }

    @Test fun DcountCountsMatchingNumeric() {
        assertTrue(abs(number("=DCOUNT(A1:D5, 2, G1:H2)") - 2.0) <= TOL)
    }

    @Test fun DgetReturnsSingleMatch() {
        assertTrue(abs(number("=DGET(A1:D5, 2, G1:H2)") - 1.0) <= TOL)
    }

    @Test fun DgetRefusesMultipleMatches() {
        assertEquals("#NA!", shown("=DGET(A1:D5, 2, G1:H3)"))
    }

    @Test fun DsumSumsMatchingTotals() {
        assertTrue(abs(number("=DSUM(A1:D5, 4, G1:H3)") - 10.0) <= TOL)
    }

    @Test fun DmaxFindsMaxMatching() {
        assertTrue(abs(number("=DMAX(A1:D5, 3, G1:H3)") - 10.0) <= TOL)
    }

    @Test fun DminFindsMinMatching() {
        assertTrue(abs(number("=DMIN(A1:D5, 3, G1:H3)") - 5.0) <= TOL)
    }

    @Test fun DstdevSampleDeviation() {
        assertTrue(abs(number("=DSTDEV(A1:D5, 2, G1:H2)") - 0.3535533905932738) <= TOL)
    }

    @Test fun DstdevpPopulationDeviation() {
        assertTrue(abs(number("=DSTDEVP(A1:D5, 2, G1:H2)") - 0.25) <= TOL)
    }

    @Test fun DvarSampleVariance() {
        assertTrue(abs(number("=DVAR(A1:D5, 2, G1:H2)") - 0.125) <= TOL)
    }

    @Test fun DvarpPopulationVariance() {
        assertTrue(abs(number("=DVARP(A1:D5, 2, G1:H2)") - 0.0625) <= TOL)
    }

    @Test fun WrongArityRefused() {
        assertEquals("#VALUE!", shown("=DAVERAGE(A1:D5)"))
        assertEquals("#VALUE!", shown("=DCOUNT(A1:D5, 2)"))
    }

    @Test fun InvalidFieldRefused() {
        assertEquals("#VALUE!", shown("=DAVERAGE(A1:D5, 99, G1:H2)"))
    }

    @Test fun NoMatchingRecords() {
        assertEquals("#DIV/0!", shown("=DAVERAGE(A1:D5, 2, G1:H4)"))
    }

    companion object {
        private const val CORPUS = "/r130/database_corpus.tsv"
    }
}
