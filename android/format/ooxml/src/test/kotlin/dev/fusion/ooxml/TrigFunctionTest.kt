package dev.fusion.ooxml

import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R116: the trigonometry a formula can now reach.
 *
 * Two independent things are asserted. First, the 539-row corpus in `/r116/trig_corpus.tsv`, whose expected column was
 * produced by CPython's `math` module — the platform C library, whereas `kotlin.math.sin` resolves to the JVM's own
 * fdlibm port. Agreement to [TOL] relative therefore measures two implementations against each other instead of one
 * against itself. Second, the behaviour `libm` has no opinion about: argument counts, coercion, error propagation,
 * domain refusals, and the argument order of `ATAN2`, the one place a spreadsheet and a mathematics library disagree
 * on purpose.
 *
 * The corpus is bound to the product source by `tools/check_r116_trig.py`, which fails if a registered name loses its
 * rows, if a row is duplicated or tampered with, or if the committed file drifts from the generator.
 */
class TrigFunctionTest {

    private data class Row(val function: String, val x: Double?, val y: Double?, val expected: String)

    private val TOL = 1e-12

    private fun corpus(): List<Row> {
        val stream = javaClass.getResourceAsStream(CORPUS) ?: throw AssertionError("missing test resource $CORPUS")
        return stream.bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map { line ->
                val parts = line.split('\t')
                if (parts.size != 4) throw AssertionError("corpus row is not four columns: $line")
                Row(
                    parts[0],
                    if (parts[1].isEmpty()) null else parts[1].toDouble(),
                    if (parts[2].isEmpty()) null else parts[2].toDouble(),
                    parts[3],
                )
            }.toList()
        }
    }

    private fun cell(formula: String, x: Double? = null, y: Double? = null): FormulaEval.CellValue {
        val env = HashMap<String, FormulaEval.CellValue>()
        if (x != null) env["A1"] = FormulaEval.CellValue.Num(x)
        if (y != null) env["A2"] = FormulaEval.CellValue.Num(y)
        return FormulaEval.evaluateFormulaAsGrid(formula, env)[0][0]
    }

    private fun number(formula: String, x: Double? = null, y: Double? = null): Double {
        val value = cell(formula, x, y)
        if (value !is FormulaEval.CellValue.Num) throw AssertionError("$formula returned ${value.displayText()}")
        return value.value
    }

    private fun shown(formula: String, x: Double? = null, y: Double? = null): String =
        cell(formula, x, y).displayText()

    private fun corpusFormula(row: Row): String = when (row.function) {
        "PI" -> "=PI()"
        "ATAN2" -> "=ATAN2(A1,A2)"
        else -> "=${row.function}(A1)"
    }

    @Test fun corpusAgreesWithThePlatformLibmOracle() {
        var checked = 0
        for (row in corpus()) {
            val formula = corpusFormula(row)
            val got = cell(formula, row.x, row.y)
            val detail = "$formula x=${row.x} y=${row.y}"
            if (row.expected.startsWith("#")) {
                assertEquals("$detail should be ${row.expected}", row.expected, got.displayText())
            } else {
                val want = row.expected.toDouble()
                if (got !is FormulaEval.CellValue.Num) throw AssertionError("$detail returned ${got.displayText()}")
                assertTrue("$detail: expected $want, got ${got.value}", abs(got.value - want) <= TOL * maxOf(1.0, abs(want)))
            }
            checked++
        }
        assertTrue("corpus must stay substantial, saw $checked rows", checked >= 500)
    }

    @Test fun everyRegisteredNameIsReachableFromAFormula() {
        val probes = mapOf(
            "SIN" to 0.5, "COS" to 0.5, "TAN" to 0.5, "ASIN" to 0.5, "ACOS" to 0.5, "ATAN" to 0.5,
            "ATAN2" to 0.5, "SINH" to 0.5, "COSH" to 0.5, "TANH" to 0.5, "ASINH" to 0.5, "ACOSH" to 1.5,
            "ATANH" to 0.5,
        )
        for ((name, x) in probes) {
            val formula = if (name == "ATAN2") "=ATAN2(A1,A1)" else "=$name(A1)"
            assertTrue("$formula must evaluate to a number, got ${shown(formula, x, x)}",
                cell(formula, x, x) is FormulaEval.CellValue.Num)
        }
        assertEquals(Math.PI, number("=PI()"), 0.0)
    }

    @Test fun piIsTheIeeeDoubleNearestToPi() {
        assertEquals(Math.PI, number("=PI()"), 0.0)
        assertEquals(Math.PI, 4.0 * number("=ATAN(A1)", 1.0), 1e-16)
        assertEquals(180.0, number("=DEGREES(PI())"), 1e-12)
        assertEquals(Math.PI, number("=RADIANS(180)"), 1e-15)
        assertEquals("#VALUE!", shown("=PI(A1)", 1.0))
    }

    @Test fun pythagoreanAndComplementIdentitiesHoldOnTheNewPrimitives() {
        var x = -3.0
        while (x <= 3.0) {
            val s = number("=SIN(A1)", x)
            val c = number("=COS(A1)", x)
            assertEquals("sin^2+cos^2 at $x", 1.0, s * s + c * c, 1e-14)
            x += 0.25
        }
        var y = -1.0
        while (y <= 1.0) {
            assertEquals("asin+acos at $y", Math.PI / 2.0, number("=ASIN(A1)", y) + number("=ACOS(A1)", y), 1e-14)
            y += 0.125
        }
    }

    @Test fun hyperbolicIdentityHoldsWhereItIsNotCancellationBound() {
        var x = -4.0
        while (x <= 4.0) {
            val ch = number("=COSH(A1)", x)
            val sh = number("=SINH(A1)", x)
            assertEquals("cosh^2-sinh^2 at $x", 1.0, ch * ch - sh * sh, 1e-10)
            x += 0.5
        }
    }

    @Test fun inverseRoundTripsHoldInsideTheirDomains() {
        var x = -5.0
        while (x <= 5.0) {
            assertEquals("atanh(tanh) at $x", x, number("=ATANH(A1)", number("=TANH(A1)", x)), 1e-9)
            x += 1.0
        }
        var y = 0.0
        while (y <= 20.0) {
            assertEquals("acosh(cosh) at $y", y, number("=ACOSH(A1)", number("=COSH(A1)", y)), 1e-9)
            assertEquals("asinh(sinh) at $y", y, number("=ASINH(A1)", number("=SINH(A1)", y)), 1e-9)
            y += 2.5
        }
        assertEquals(1.0, number("=TANH(A1)", 710.0), 0.0)
        assertEquals(-1.0, number("=TANH(A1)", -710.0), 0.0)
    }

    @Test fun atan2KeepsTheSpreadsheetArgumentOrder() {
        assertEquals(0.0, number("=ATAN2(A1,A2)", 1.0, 0.0), 0.0)
        assertEquals(Math.PI / 2.0, number("=ATAN2(A1,A2)", 0.0, 1.0), 1e-16)
        assertEquals(Math.PI, number("=ATAN2(A1,A2)", -1.0, 0.0), 1e-16)
        assertEquals(-Math.PI / 2.0, number("=ATAN2(A1,A2)", 0.0, -1.0), 1e-16)
        assertEquals(Math.PI / 4.0, number("=ATAN2(A1,A2)", 1.0, 1.0), 1e-16)
        assertEquals("#DIV/0!", shown("=ATAN2(A1,A2)", 0.0, 0.0))
        assertEquals("#VALUE!", shown("=ATAN2(A1)", 1.0))
    }

    @Test fun domainsAreRefusedWithNumRatherThanSilence() {
        assertEquals("#NUM!", shown("=ASIN(A1)", 1.0000001))
        assertEquals("#NUM!", shown("=ASIN(A1)", -1.5))
        assertEquals("#NUM!", shown("=ACOS(A1)", 2.0))
        assertEquals("#NUM!", shown("=ACOSH(A1)", 0.5))
        assertEquals("#NUM!", shown("=ATANH(A1)", 1.0))
        assertEquals("#NUM!", shown("=ATANH(A1)", -1.0))
        assertEquals("#NUM!", shown("=COSH(A1)", 1000.0))
        assertEquals("#NUM!", shown("=SINH(A1)", 1000.0))
        assertEquals("#NUM!", shown("=SINH(A1)", -1000.0))
    }

    @Test fun argumentCountsAreEnforced() {
        assertEquals("#VALUE!", shown("=SIN()"))
        assertEquals("#VALUE!", shown("=SIN(A1,A2)", 1.0, 2.0))
        assertEquals("#VALUE!", shown("=TAN(A1,A2)", 1.0, 2.0))
        assertEquals("#VALUE!", shown("=ATAN2(A1,A2,A2)", 1.0, 2.0, 3.0))
        assertEquals("#VALUE!", shown("=SINH()"))
        assertEquals("#VALUE!", shown("=ATANH()"))
    }

    @Test fun coercionFollowsTheEngineNotKotlin() {
        val env = HashMap<String, FormulaEval.CellValue>()
        env["A1"] = FormulaEval.CellValue.Text("0.5")
        env["A2"] = FormulaEval.CellValue.Text("not a number")
        env["A3"] = FormulaEval.CellValue.Bool(true)
        env["A4"] = FormulaEval.CellValue.Empty
        val asinText = FormulaEval.evaluateFormulaAsGrid("=ASIN(A1)", env)[0][0]
        if (asinText !is FormulaEval.CellValue.Num) throw AssertionError("numeric text must coerce, got ${asinText.displayText()}")
        assertEquals(0.5235987755982989, asinText.value, 1e-15)
        assertEquals("#VALUE!", FormulaEval.evaluateFormulaAsGrid("=ASIN(A2)", env)[0][0].displayText())
        val bool = FormulaEval.evaluateFormulaAsGrid("=SIN(A3)", env)[0][0]
        if (bool !is FormulaEval.CellValue.Num) throw AssertionError("TRUE must coerce to 1, got ${bool.displayText()}")
        assertEquals(sin(1.0), bool.value, 1e-15)
        val empty = FormulaEval.evaluateFormulaAsGrid("=SIN(A4)", env)[0][0]
        if (empty !is FormulaEval.CellValue.Num) throw AssertionError("empty must coerce to 0, got ${empty.displayText()}")
        assertEquals(0.0, empty.value, 0.0)
    }

    @Test fun errorsPropagateInsteadOfBecomingZero() {
        val env = HashMap<String, FormulaEval.CellValue>()
        env["A1"] = FormulaEval.CellValue.Err(CellErr.Err.DIV0)
        env["A2"] = FormulaEval.CellValue.Err(CellErr.Err.NA)
        assertEquals("#DIV/0!", FormulaEval.evaluateFormulaAsGrid("=SIN(A1)", env)[0][0].displayText())
        assertEquals("#N/A", FormulaEval.evaluateFormulaAsGrid("=COS(A2)", env)[0][0].displayText())
        assertEquals("#DIV/0!", shown("=TAN(1/0)"))
    }

    @Test fun unknownNamesStayUnknown() {
        assertEquals("#NAME?", shown("=SNI(A1)", 1.0))
        assertEquals("#NAME?", shown("=COSS(A1)", 1.0))
        assertEquals("#NAME?", shown("=PII()"))
    }

    @Test fun reciprocityWithThePreExistingReciprocalKernelsAgrees() {
        var x = 0.3
        while (x <= 2.5) {
            val c = number("=COS(A1)", x)
            val s = number("=SIN(A1)", x)
            val t = number("=TAN(A1)", x)
            assertEquals("sec(x)=1/cos(x) at $x", 1.0 / c, number("=SEC(A1)", x), 1e-12)
            assertEquals("csc(x)=1/sin(x) at $x", 1.0 / s, number("=CSC(A1)", x), 1e-12)
            assertEquals("cot(x)=1/tan(x) at $x", 1.0 / t, number("=COT(A1)", x), 1e-12)
            x += 0.4
        }
        var y = -4.0
        while (y <= 4.0) {
            assertEquals("acot(x)+atan(x) at $y", Math.PI / 2.0,
                number("=ACOT(A1)", y) + number("=ATAN(A1)", y), 1e-15)
            y += 0.5
        }
    }

    @Test fun overflowingResultsAreErrorsNotInfinity() {
        val cases = listOf("=TAN(A1)" to 1.5707963267948966, "=COSH(A1)" to 709.0, "=SINH(A1)" to -709.0)
        for ((formula, x) in cases) {
            val value = cell(formula, x)
            assertTrue("$formula($x) must be a finite number or #NUM!, got ${value.displayText()}",
                value is FormulaEval.CellValue.Num && value.value.isFinite() || value.displayText() == "#NUM!")
        }
    }

    @Test fun theSheetEvaluatorThatTheEditorUsesReachesThemToo() {
        // The formulas above go through evaluateFormulaAsGrid; a workbook cell goes through the dependency DAG.
        val sheet = FormulaEval.evaluateSheet(
            mapOf(
                "A1" to FormulaEval.CellInput.NumberLiteral(1.0),
                "B1" to FormulaEval.CellInput.Formula("=ATAN2(A1, A1)"),
                "B2" to FormulaEval.CellInput.Formula("=SIN(PI()/2)"),
                "B3" to FormulaEval.CellInput.Formula("=ACOSH(0.5)"),
                "B4" to FormulaEval.CellInput.Formula("=B2*2"),
            ),
        )
        assertEquals(Math.PI / 4.0, (sheet.values["B1"] as FormulaEval.CellValue.Num).value, 1e-16)
        assertEquals(1.0, (sheet.values["B2"] as FormulaEval.CellValue.Num).value, 1e-16)
        assertEquals("#NUM!", sheet.values["B3"]!!.displayText())
        assertEquals(2.0, (sheet.values["B4"] as FormulaEval.CellValue.Num).value, 1e-16)
        assertTrue("B1 must depend on A1", sheet.dependencies["B1"]!!.contains("A1"))
    }

    private companion object {
        const val CORPUS = "/r116/trig_corpus.tsv"
    }
}
