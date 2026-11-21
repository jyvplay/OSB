package dev.fusion.ooxml

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R125: `XNPV` / `XIRR` / `ACCRINT` / `ACCRINTM` — financial functions for irregular cash flows.
 * Before this round all four names returned `#NAME?` from a formula.
 *
 * Corpus-driven test: every row in `/r125/stat_financial_corpus.tsv` (expected column produced by the
 * pure-math oracle in `tools/gen_r125_financial_corpus.py`) is evaluated through the engine and compared
 * to `TOL = 1e-12` relative. Behaviour the definitions don't pin down — the day-count basis for
 * `ACCRINT`/`ACCRINTM`, the initial guess for `XIRR`, and the refusals (`#NUM!`/`#VALUE!`) — are asserted
 * explicitly.
 *
 * The corpus is bound to the product source by `tools/check_r125_financial.py`, which fails if a
 * registered name loses its rows, if a row is duplicated or tampered with, or if the committed
 * file drifts from `tools/gen_r125_financial_corpus.py`.
 */
class R125FinancialTest {

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
        assertTrue("corpus must stay substantial, saw $checked rows", checked >= 15)
    }

    @Test fun everyRegisteredNameIsReachableFromAFormula() {
        val probes = mapOf(
            "XNPV" to "=XNPV(0.1,{-1000,200,300,400,500},{45292,45352,45413,45474,45536})",
            "XIRR" to "=XIRR({-1000,200,300,400,500},{45292,45352,45413,45474,45536},0.1)",
            "ACCRINT" to "=ACCRINT(45292,45352,45458,0.05,1000,2,0)",
            "ACCRINTM" to "=ACCRINTM(45292,45657,0.05,10000,0)",
        )
        for ((name, formula) in probes) {
            assertTrue("$name must evaluate to a number, got ${shown(formula)}",
                value(formula) is FormulaEval.CellValue.Num)
        }
    }

    @Test fun xnpvValues() {
        // XNPV(0.1, {-1000,200,300,400,500}, dates from 2024-01-01)
        assertTrue(abs(number("=XNPV(0.1,{-1000,200,300,400,500},{45292,45352,45413,45474,45536})") - 338.13171368427186) <= TOL)
        // XNPV(0.05, same cash flows)
        assertTrue(abs(number("=XNPV(0.05,{-1000,200,300,400,500},{45292,45352,45413,45474,45536})") - 367.93035936669384) <= TOL)
        // Rate = 0 -> sum of undiscounted values
        assertTrue(abs(number("=XNPV(0,{-1000,200,300,400,500},{45292,45352,45413,45474,45536})") - 0.0) <= TOL)
    }

    @Test fun xirrValues() {
        // XIRR with known cash flows
        val irr = number("=XIRR({-1000,200,300,400,500},{45292,45352,45413,45474,45536},0.1)")
        assertTrue(irr > 0.0)
        // XNPV at IRR should be ~0
        val npvAtIrr = number("=XNPV($irr,{-1000,200,300,400,500},{45292,45352,45413,45474,45536})")
        assertTrue(abs(npvAtIrr) <= 1e-6)
    }

    @Test fun accrintValues() {
        // ACCRINT with different bases
        val acc0 = number("=ACCRINT(45292,45352,45458,0.05,1000,2,0)")
        assertTrue(abs(acc0 - 44.93150684931507) <= TOL)
        val acc1 = number("=ACCRINT(45292,45352,45458,0.05,1000,2,1)")
        assertTrue(abs(acc1 - 0.12451595360655583) <= TOL)
        val acc4 = number("=ACCRINT(45292,45352,45458,0.05,1000,2,4)")
        assertTrue(abs(acc4 - 44.93150684931507) <= TOL)
    }

    @Test fun accrintmValues() {
        // ACCRINTM with different bases
        val acc0 = number("=ACCRINTM(45292,45657,0.05,10000,0)")
        assertTrue(abs(acc0 - 493.1506849315069) <= TOL)
        val acc3 = number("=ACCRINTM(45292,45657,0.05,10000,3)")
        assertTrue(abs(acc3 - 1.36986301369863) <= TOL)
        // Default basis = 0
        val accDefault = number("=ACCRINTM(45292,45657,0.05,10000)")
        assertTrue(abs(accDefault - acc0) <= TOL)
    }

    @Test fun domainRefusals() {
        // Wrong arity -> #VALUE!.
        assertEquals("#VALUE!", shown("=XNPV(0.1)"))
        assertEquals("#VALUE!", shown("=XIRR({-1000,200})"))
        assertEquals("#VALUE!", shown("=ACCRINT(45292,45352,45458,0.05,1000)"))
        assertEquals("#VALUE!", shown("=ACCRINTM(45292,45657,0.05,10000,0,1)"))
    }

    @Test fun numericalRefusals() {
        // XNPV rate <= -1 -> #NUM!.
        assertEquals("#NUM!", shown("=XNPV(-1.1,{-1000,200},{45292,45352})"))
        // XIRR all positive -> #NUM!.
        assertEquals("#NUM!", shown("=XIRR({100,200},{45292,45352})"))
        // XIRR all negative -> #NUM!.
        assertEquals("#NUM!", shown("=XIRR({-100,-200},{45292,45352})"))
        // ACCRINT negative rate -> #NUM!.
        assertEquals("#NUM!", shown("=ACCRINT(45292,45352,45458,-0.05,1000,2,0)"))
        // ACCRINT issue after settlement -> #NUM!.
        assertEquals("#NUM!", shown("=ACCRINT(45458,45352,45292,0.05,1000,2,0)"))
        // ACCRINTM negative rate -> #NUM!.
        assertEquals("#NUM!", shown("=ACCRINTM(45292,45657,-0.05,10000,0)"))
        // ACCRINTM issue after settlement -> #NUM!.
        assertEquals("#NUM!", shown("=ACCRINTM(45657,45292,0.05,10000,0)"))
    }

    @Test fun unknownNameIsRefused() {
        assertEquals("#NAME?", shown("=NOXNPV(0.1)"))
    }

    @Test fun theSheetEvaluatorReachesThemToo() {
        val sheet = FormulaEval.evaluateSheet(
            mapOf(
                "A1" to FormulaEval.CellInput.NumberLiteral(0.1),
                "B1" to FormulaEval.CellInput.Formula("=XNPV(A1,{-1000,200},{45292,45352})"),
                "B2" to FormulaEval.CellInput.Formula("=ACCRINTM(45292,45657,0.05,10000,0)"),
            ),
        )
        val b1 = sheet.values["B1"] as FormulaEval.CellValue.Num
        val b2 = sheet.values["B2"] as FormulaEval.CellValue.Num
        // XNPV(0.1, {-1000, 200}, {2024-01-01, 2024-03-01})
        assertTrue(abs(b1.value - (-1000.0 + 200.0 / math.pow(1.1, 59.0 / 365.0))) <= 1e-9)
        assertTrue(abs(b2.value - 493.1506849315069) <= TOL)
    }

    companion object {
        private const val CORPUS = "/r125/stat_financial_corpus.tsv"
    }
}
