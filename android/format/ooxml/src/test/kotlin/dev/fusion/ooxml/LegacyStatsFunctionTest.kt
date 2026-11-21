package dev.fusion.ooxml

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R119: the legacy statistical aliases a formula can now reach — `VAR`, `VARP`, `STDEVP`, `MODE`,
 * `COVARIANCE` — and `FORECAST` / `FORECAST.LINEAR`.
 *
 * Two independent things are asserted. First, the 212-row corpus in `/r119/legacy_stats_corpus.tsv`, whose
 * expected column was produced from the documented definitions (Microsoft's function reference for each
 * name) and independently recomputed in `tools/test_r119_legacy_stats.py` through CPython's `statistics`
 * module (`variance`, `pvariance`, `pstdev`, `linear_regression`) and a separate transcription of the
 * pairing rules. Agreement to [TOL] relative therefore measures two implementations against each other
 * instead of one against itself. Second, the behaviour the definitions do not pin down by themselves: the
 * sample-versus-population split, the alias identities, `FORECAST` as the least-squares line, and the
 * refusal and error-propagation rules.
 *
 * The corpus is bound to the product source by `tools/check_r119_legacy_stats.py`, which fails if a
 * registered name loses its rows, if a row is duplicated or tampered with, or if the committed file drifts
 * from the generator.
 */
class LegacyStatsFunctionTest {

    private data class Row(val function: String, val a: String, val b: String, val c: String, val expected: String)

    private val TOL = 1e-12

    private fun corpus(): List<Row> {
        val stream = javaClass.getResourceAsStream(CORPUS) ?: throw AssertionError("missing test resource $CORPUS")
        return stream.bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map { line ->
                val parts = line.split('\t')
                if (parts.size != 5) throw AssertionError("corpus row is not five columns: $line")
                Row(parts[0], parts[1], parts[2], parts[3], parts[4])
            }.toList()
        }
    }

    private fun envFor(a: String, b: String): Map<String, FormulaEval.CellValue> {
        val env = HashMap<String, FormulaEval.CellValue>()
        a.split(',').forEachIndexed { index, token -> env["A${index + 1}"] = tokenValue(token) }
        if (b.isNotEmpty()) {
            b.split(',').forEachIndexed { index, token -> env["B${index + 1}"] = tokenValue(token) }
        }
        return env
    }

    private fun tokenValue(token: String): FormulaEval.CellValue = when {
        token == "EMPTY" -> FormulaEval.CellValue.Empty
        token == "#N/A" -> FormulaEval.CellValue.Err(CellErr.Err.NA)
        token == "TRUE" -> FormulaEval.CellValue.Bool(true)
        token == "FALSE" -> FormulaEval.CellValue.Bool(false)
        token.startsWith("\"") -> FormulaEval.CellValue.Text(token.trim('"'))
        else -> FormulaEval.CellValue.Num(token.toDouble())
    }

    private fun corpusFormula(row: Row): String {
        val n = row.a.split(',').size
        val m = if (row.b.isEmpty()) n else row.b.split(',').size
        return when {
            row.function == "COVARIANCE" -> "=${row.function}(A1:A$n, B1:B$m)"
            row.function == "FORECAST" || row.function == "FORECAST.LINEAR" ->
                "=${row.function}(${row.c}, A1:A$n, B1:B$m)"
            else -> "=${row.function}(A1:A$n)"
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

    @Test fun corpusAgreesWithTheDocumentedDefinitions() {
        var checked = 0
        for (row in corpus()) {
            val formula = corpusFormula(row)
            val got = FormulaEval.evaluateFormulaAsGrid(formula, envFor(row.a, row.b))[0][0]
            val detail = "$formula a=${row.a} b=${row.b} c=${row.c}"
            if (row.expected.startsWith("#")) {
                assertEquals("$detail should be ${row.expected}", row.expected, got.displayText())
            } else {
                val want = row.expected.toDouble()
                if (got !is FormulaEval.CellValue.Num) throw AssertionError("$detail returned ${got.displayText()}")
                assertTrue("$detail: expected $want, got ${got.value}", abs(got.value - want) <= TOL * maxOf(1.0, abs(want)))
            }
            checked++
        }
        assertTrue("corpus must stay substantial, saw $checked rows", checked >= 150)
    }

    @Test fun everyRegisteredNameIsReachableFromAFormula() {
        val env = envFor("1,2,3,4", "2,4,6,8")
        for (name in listOf("VAR", "VARP", "STDEVP")) {
            val formula = "=$name(A1:A4)"
            val value = FormulaEval.evaluateFormulaAsGrid(formula, env)[0][0]
            assertTrue("$formula must evaluate to a number, got ${value.displayText()}", value is FormulaEval.CellValue.Num)
        }
        // MODE needs a repeat to return a number; on 1,2,3,4 it is #N/A (asserted in modeRefusesWhenNothingRepeats).
        val mode = FormulaEval.evaluateFormulaAsGrid("=MODE(A1:A4)", envFor("1,1,2,3", ""))[0][0]
        assertTrue("MODE must evaluate to a number, got ${mode.displayText()}", mode is FormulaEval.CellValue.Num)
        val covariance = FormulaEval.evaluateFormulaAsGrid("=COVARIANCE(A1:A4, B1:B4)", env)[0][0]
        assertTrue("COVARIANCE must evaluate to a number, got ${covariance.displayText()}", covariance is FormulaEval.CellValue.Num)
        for (name in listOf("FORECAST", "FORECAST.LINEAR")) {
            val formula = "=$name(2, A1:A4, B1:B4)"
            val value = FormulaEval.evaluateFormulaAsGrid(formula, env)[0][0]
            assertTrue("$formula must evaluate to a number, got ${value.displayText()}", value is FormulaEval.CellValue.Num)
        }
    }

    @Test fun legacyAliasesMatchTheirModernKernels() {
        // VAR is the documented rename of VAR.S; VARP of VAR.P; STDEVP of STDEV.P; MODE of MODE.SNGL;
        // COVARIANCE of COVARIANCE.P; FORECAST.LINEAR of FORECAST.
        val env = envFor("3,1,4,1,5,9,2,6,5,3,5", "1,2,3,4,5,6,7,8,9,10,11")
        val pairs = listOf(
            "VAR(A1:A11)" to "VAR.S(A1:A11)",
            "VARP(A1:A11)" to "VAR.P(A1:A11)",
            "STDEVP(A1:A11)" to "STDEV.P(A1:A11)",
            "MODE(A1:A11)" to "MODE.SNGL(A1:A11)",
            "COVARIANCE(A1:A11, B1:B11)" to "COVARIANCE.P(A1:A11, B1:B11)",
            "FORECAST(2, A1:A11, B1:B11)" to "FORECAST.LINEAR(2, A1:A11, B1:B11)",
        )
        for ((legacy, modern) in pairs) {
            assertEquals(
                "$legacy must equal $modern",
                FormulaEval.evaluateFormulaAsGrid("=$modern", env)[0][0].displayText(),
                FormulaEval.evaluateFormulaAsGrid("=$legacy", env)[0][0].displayText(),
            )
        }
    }

    @Test fun sampleVersusPopulationSplit() {
        // On 1,2,3,4 the sample variance divides by n-1 and the population variance by n.
        val env = envFor("1,2,3,4", "")
        assertEquals(1.6666666666666667, (FormulaEval.evaluateFormulaAsGrid("=VAR(A1:A4)", env)[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        assertEquals(1.25, (FormulaEval.evaluateFormulaAsGrid("=VARP(A1:A4)", env)[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        assertEquals(1.118033988749895, (FormulaEval.evaluateFormulaAsGrid("=STDEVP(A1:A4)", env)[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        // STDEVP is the square root of VARP.
        val varp = (FormulaEval.evaluateFormulaAsGrid("=VARP(A1:A4)", env)[0][0] as FormulaEval.CellValue.Num).value
        val stdevp = (FormulaEval.evaluateFormulaAsGrid("=STDEVP(A1:A4)", env)[0][0] as FormulaEval.CellValue.Num).value
        assertEquals(sqrt(varp), stdevp, 1e-15)
    }

    @Test fun modeReturnsTheMostFrequentValue() {
        // 5 occurs three times, more than any other value.
        assertEquals(5.0, (FormulaEval.evaluateFormulaAsGrid("=MODE(A1:A11)", envFor("3,1,4,1,5,9,2,6,5,3,5", ""))[0][0] as FormulaEval.CellValue.Num).value, 0.0)
        // A tie between 2 and 3 resolves to the first value in the data.
        assertEquals(2.0, (FormulaEval.evaluateFormulaAsGrid("=MODE(A1:A5)", envFor("2,2,3,3,4", ""))[0][0] as FormulaEval.CellValue.Num).value, 0.0)
    }

    @Test fun modeRefusesWhenNothingRepeats() {
        assertEquals("#N/A", FormulaEval.evaluateFormulaAsGrid("=MODE(A1:A4)", envFor("1,2,3,4", ""))[0][0].displayText())
        assertEquals("#N/A", FormulaEval.evaluateFormulaAsGrid("=MODE(A1:A1)", envFor("5", ""))[0][0].displayText())
    }

    @Test fun forecastFollowsTheLeastSquaresLine() {
        // y = 2x exactly: slope 2, intercept 0.
        assertEquals(5.0, (FormulaEval.evaluateFormulaAsGrid("=FORECAST(2.5, A1:A4, B1:B4)", envFor("2,4,6,8", "1,2,3,4"))[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        // y = 2x + 1 exactly: slope 2, intercept 1.
        assertEquals(9.0, (FormulaEval.evaluateFormulaAsGrid("=FORECAST(4, A1:A3, B1:B3)", envFor("3,5,7", "1,2,3"))[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        // The rename agrees.
        assertEquals(5.0, (FormulaEval.evaluateFormulaAsGrid("=FORECAST.LINEAR(2.5, A1:A4, B1:B4)", envFor("2,4,6,8", "1,2,3,4"))[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
    }

    @Test fun forecastMatchesSlopeAndIntercept() {
        // FORECAST(x, y, x) is INTERCEPT(y, x) + SLOPE(y, x) * x by definition.
        val env = envFor("10,20,30,40,50,60,70", "1,2,3,4,5,6,7")
        val slope = (FormulaEval.evaluateFormulaAsGrid("=SLOPE(A1:A7, B1:B7)", env)[0][0] as FormulaEval.CellValue.Num).value
        val intercept = (FormulaEval.evaluateFormulaAsGrid("=INTERCEPT(A1:A7, B1:B7)", env)[0][0] as FormulaEval.CellValue.Num).value
        val forecast = (FormulaEval.evaluateFormulaAsGrid("=FORECAST(3.5, A1:A7, B1:B7)", env)[0][0] as FormulaEval.CellValue.Num).value
        assertEquals(intercept + slope * 3.5, forecast, 1e-12)
    }

    @Test fun forecastRefusesConstantKnownX() {
        // known_x constant (B column 5,5,5): the slope is undefined.
        assertEquals("#DIV/0!", FormulaEval.evaluateFormulaAsGrid("=FORECAST(2, A1:A3, B1:B3)", envFor("1,2,3", "5,5,5"))[0][0].displayText())
        assertEquals("#DIV/0!", FormulaEval.evaluateFormulaAsGrid("=FORECAST(4, A1:A1, B1:B1)", envFor("9", "4"))[0][0].displayText())
    }

    @Test fun forecastRefusesMismatchedSizes() {
        assertEquals("#N/A", FormulaEval.evaluateFormulaAsGrid("=FORECAST(2, A1:A3, B1:B2)", envFor("1,2,3", "1,2"))[0][0].displayText())
        assertEquals("#N/A", FormulaEval.evaluateFormulaAsGrid("=COVARIANCE(A1:A3, B1:B2)", envFor("1,2,3", "1,2"))[0][0].displayText())
    }

    @Test fun varianceRefusesTooFewValues() {
        // Sample variance needs at least two values; population variance needs one.
        assertEquals("#DIV/0!", FormulaEval.evaluateFormulaAsGrid("=VAR(A1:A1)", envFor("5", ""))[0][0].displayText())
        assertEquals(0.0, (FormulaEval.evaluateFormulaAsGrid("=VARP(A1:A1)", envFor("5", ""))[0][0] as FormulaEval.CellValue.Num).value, 0.0)
        assertEquals(0.0, (FormulaEval.evaluateFormulaAsGrid("=STDEVP(A1:A1)", envFor("5", ""))[0][0] as FormulaEval.CellValue.Num).value, 0.0)
        assertEquals("#DIV/0!", FormulaEval.evaluateFormulaAsGrid("=VARP(A1:A1)", envFor("EMPTY", ""))[0][0].displayText())
    }

    @Test fun covarianceIsPopulationNotSample() {
        // Population covariance divides by n; the sample variant divides by n-1.
        assertEquals(2.5, (FormulaEval.evaluateFormulaAsGrid("=COVARIANCE(A1:A4, B1:B4)", envFor("1,2,3,4", "2,4,6,8"))[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        assertEquals(3.3333333333333335, (FormulaEval.evaluateFormulaAsGrid("=COVARIANCE.S(A1:A4, B1:B4)", envFor("1,2,3,4", "2,4,6,8"))[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
    }

    @Test fun errorCellsPropagate() {
        assertEquals("#N/A", FormulaEval.evaluateFormulaAsGrid("=VAR(A1:A3)", envFor("1,#N/A,3", ""))[0][0].displayText())
        assertEquals("#N/A", FormulaEval.evaluateFormulaAsGrid("=COVARIANCE(A1:A3, B1:B3)", envFor("1,2,3", "1,#N/A,3"))[0][0].displayText())
        assertEquals("#N/A", FormulaEval.evaluateFormulaAsGrid("=FORECAST(2, A1:A3, B1:B3)", envFor("1,2,3", "1,#N/A,3"))[0][0].displayText())
    }

    @Test fun textAndLogicalsAreIgnoredInRanges() {
        // In a range, the aggregate kernels ignore text and logicals: VAR(1,"x",3) is VAR(1,3) = 2.
        assertEquals(2.0, (FormulaEval.evaluateFormulaAsGrid("=VAR(A1:A3)", envFor("1,\"x\",3", ""))[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        assertEquals(0.5, (FormulaEval.evaluateFormulaAsGrid("=VAR(A1:A3)", envFor("TRUE,2,3", ""))[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        // MODE likewise sees only numbers.
        assertEquals(2.0, (FormulaEval.evaluateFormulaAsGrid("=MODE(A1:A3)", envFor("2,2,\"x\"", ""))[0][0] as FormulaEval.CellValue.Num).value, 0.0)
    }

    @Test fun wrongArityAndUnknownNamesAreRefused() {
        // FORECAST takes exactly three arguments; an unknown name still falls through to #NAME?.
        assertEquals("#VALUE!", FormulaEval.evaluateFormulaAsGrid("=FORECAST(2, A1:A4)", envFor("1,2,3,4", ""))[0][0].displayText())
        assertEquals("#VALUE!", FormulaEval.evaluateFormulaAsGrid("=FORECAST(2, A1:A4, B1:B4, 9)", envFor("1,2,3,4", "2,4,6,8"))[0][0].displayText())
        assertEquals("#NAME?", FormulaEval.evaluateFormulaAsGrid("=NOSUCHFN(1)", envFor("1", ""))[0][0].displayText())
    }

    @Test fun directLiteralsCoerceWhereRangesIgnore() {
        // A direct numeric-text literal is coerced (VAR("5",2,3) sees 5,2,3); the same text in a range is ignored.
        assertEquals(2.3333333333333335, number("=VAR(\"5\", 2, 3)"), 1e-15)
        assertEquals(0.5, (FormulaEval.evaluateFormulaAsGrid("=VAR(A1:A3)", envFor("\"5\",2,3", ""))[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        assertEquals("#N/A", shown("=MODE(1, 2, 3, 4)"))
    }

    @Test fun theSheetEvaluatorThatTheEditorUsesReachesThemToo() {
        val sheet = FormulaEval.evaluateSheet(
            mapOf(
                "A1" to FormulaEval.CellInput.NumberLiteral(1.0),
                "A2" to FormulaEval.CellInput.NumberLiteral(2.0),
                "A3" to FormulaEval.CellInput.NumberLiteral(3.0),
                "A4" to FormulaEval.CellInput.NumberLiteral(4.0),
                "B1" to FormulaEval.CellInput.Formula("=VAR(A1:A4)"),
                "B2" to FormulaEval.CellInput.Formula("=VARP(A1:A4)"),
                "B3" to FormulaEval.CellInput.Formula("=STDEVP(A1:A4)"),
                "B4" to FormulaEval.CellInput.Formula("=FORECAST(2, A1:A4, A1:A4)"),
            ),
        )
        assertEquals(1.6666666666666667, (sheet.values["B1"] as FormulaEval.CellValue.Num).value, 1e-15)
        assertEquals(1.25, (sheet.values["B2"] as FormulaEval.CellValue.Num).value, 1e-15)
        assertEquals(1.118033988749895, (sheet.values["B3"] as FormulaEval.CellValue.Num).value, 1e-15)
        assertEquals(2.0, (sheet.values["B4"] as FormulaEval.CellValue.Num).value, 1e-15)
        assertTrue("B1 must depend on A1", sheet.dependencies["B1"]!!.contains("A1"))
    }

    companion object {
        private const val CORPUS = "/r119/legacy_stats_corpus.tsv"
    }
}
