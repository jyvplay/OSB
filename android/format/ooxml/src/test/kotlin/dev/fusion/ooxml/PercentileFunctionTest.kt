package dev.fusion.ooxml

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R117: the percentile variants and the `A`-suffixed aggregate family a formula can now reach.
 *
 * Two independent things are asserted. First, the 776-row corpus in `/r117/percentile_corpus.tsv`, whose expected
 * column was produced from the documented definitions (ECMA-376 18.17.7 for the percentile rank, Microsoft's
 * function reference for the `A` family) and independently recomputed in `tools/test_r117_percentile.py` through
 * CPython's `statistics.quantiles` and a separate transcription of the counting rules. Agreement to [TOL] relative
 * therefore measures two implementations against each other instead of one against itself. Second, the behaviour the
 * definitions do not pin down by themselves: the exclusive rank's refusal of ranks outside the data, the `A` family's
 * counting of text and logicals, sample-versus-population variance, and error propagation.
 *
 * The corpus is bound to the product source by `tools/check_r117_percentile.py`, which fails if a registered name
 * loses its rows, if a row is duplicated or tampered with, or if the committed file drifts from the generator.
 */
class PercentileFunctionTest {

    private data class Row(val function: String, val data: String, val k: Double?, val expected: String)

    private val TOL = 1e-12

    private fun corpus(): List<Row> {
        val stream = javaClass.getResourceAsStream(CORPUS) ?: throw AssertionError("missing test resource $CORPUS")
        return stream.bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map { line ->
                val parts = line.split('\t')
                if (parts.size != 4) throw AssertionError("corpus row is not four columns: $line")
                Row(
                    parts[0],
                    parts[1],
                    if (parts[2].isEmpty()) null else parts[2].toDouble(),
                    parts[3],
                )
            }.toList()
        }
    }

    private fun envFor(data: String): Map<String, FormulaEval.CellValue> {
        val env = HashMap<String, FormulaEval.CellValue>()
        data.split(',').forEachIndexed { index, token ->
            env["A${index + 1}"] = when {
                token == "EMPTY" -> FormulaEval.CellValue.Empty
                token == "#N/A" -> FormulaEval.CellValue.Err(CellErr.Err.NA)
                token == "TRUE" -> FormulaEval.CellValue.Bool(true)
                token == "FALSE" -> FormulaEval.CellValue.Bool(false)
                token.startsWith("\"") -> FormulaEval.CellValue.Text(token.trim('"'))
                else -> FormulaEval.CellValue.Num(token.toDouble())
            }
        }
        return env
    }

    private fun corpusFormula(row: Row): String {
        val range = "A1:A${row.data.split(',').size}"
        return if (row.k == null) "=${row.function}($range)" else "=${row.function}($range, ${row.k})"
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
            val got = FormulaEval.evaluateFormulaAsGrid(formula, envFor(row.data))[0][0]
            val detail = "$formula data=${row.data} k=${row.k}"
            if (row.expected.startsWith("#")) {
                assertEquals("$detail should be ${row.expected}", row.expected, got.displayText())
            } else {
                val want = row.expected.toDouble()
                if (got !is FormulaEval.CellValue.Num) throw AssertionError("$detail returned ${got.displayText()}")
                assertTrue("$detail: expected $want, got ${got.value}", abs(got.value - want) <= TOL * maxOf(1.0, abs(want)))
            }
            checked++
        }
        assertTrue("corpus must stay substantial, saw $checked rows", checked >= 700)
    }

    @Test fun everyRegisteredNameIsReachableFromAFormula() {
        val env = envFor("1,2,3,4")
        val percentileNames = listOf("PERCENTILE", "PERCENTILE.INC", "PERCENTILE.EXC", "QUARTILE", "QUARTILE.INC", "QUARTILE.EXC")
        for (name in percentileNames) {
            val k = if (name.startsWith("QUARTILE")) "2" else "0.5"
            val formula = "=$name(A1:A4, $k)"
            val value = FormulaEval.evaluateFormulaAsGrid(formula, env)[0][0]
            assertTrue("$formula must evaluate to a number, got ${value.displayText()}", value is FormulaEval.CellValue.Num)
        }
        for (name in listOf("AVERAGEA", "MAXA", "MINA", "VARA", "VARPA", "STDEVA", "STDEVPA")) {
            val formula = "=$name(A1:A4)"
            val value = FormulaEval.evaluateFormulaAsGrid(formula, env)[0][0]
            assertTrue("$formula must evaluate to a number, got ${value.displayText()}", value is FormulaEval.CellValue.Num)
        }
    }

    @Test fun inclusiveVariantsMatchTheLegacyFunctions() {
        // PERCENTILE.INC and QUARTILE.INC are the documented renames of the legacy inclusive method.
        val env = envFor("3,1,4,1,5,9,2,6,5,3,5")
        for (k in listOf(0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 1.0)) {
            assertEquals("PERCENTILE.INC vs PERCENTILE at $k",
                FormulaEval.evaluateFormulaAsGrid("=PERCENTILE(A1:A11, $k)", env)[0][0].displayText(),
                FormulaEval.evaluateFormulaAsGrid("=PERCENTILE.INC(A1:A11, $k)", env)[0][0].displayText())
        }
        for (q in listOf(0, 1, 2, 3, 4)) {
            assertEquals("QUARTILE.INC vs QUARTILE at $q",
                FormulaEval.evaluateFormulaAsGrid("=QUARTILE(A1:A11, $q)", env)[0][0].displayText(),
                FormulaEval.evaluateFormulaAsGrid("=QUARTILE.INC(A1:A11, $q)", env)[0][0].displayText())
        }
    }

    @Test fun exclusiveUsesTheExclusiveRank() {
        val env = envFor("1,2,3,4")
        // Inclusive rank pos = k*(n-1); exclusive rank pos = k*(n+1)-1. At k=0.25 on four values: 1.75 vs 1.25.
        assertEquals(1.75, (FormulaEval.evaluateFormulaAsGrid("=PERCENTILE.INC(A1:A4, 0.25)", env)[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        assertEquals(1.25, (FormulaEval.evaluateFormulaAsGrid("=PERCENTILE.EXC(A1:A4, 0.25)", env)[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        assertEquals(1.25, (FormulaEval.evaluateFormulaAsGrid("=QUARTILE.EXC(A1:A4, 1)", env)[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        assertEquals(3.75, (FormulaEval.evaluateFormulaAsGrid("=QUARTILE.EXC(A1:A4, 3)", env)[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        // The median is the same under both methods on an even count.
        assertEquals(2.5, (FormulaEval.evaluateFormulaAsGrid("=PERCENTILE.EXC(A1:A4, 0.5)", env)[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
    }

    @Test fun exclusiveRefusesRanksOutsideTheData() {
        val env = envFor("1,2,3,4")
        assertEquals("#NUM!", FormulaEval.evaluateFormulaAsGrid("=PERCENTILE.EXC(A1:A4, 0)", env)[0][0].displayText())
        assertEquals("#NUM!", FormulaEval.evaluateFormulaAsGrid("=PERCENTILE.EXC(A1:A4, 1)", env)[0][0].displayText())
        // pos = 0.05*5-1 = -0.75, below the first value: refused, not extrapolated.
        assertEquals("#NUM!", FormulaEval.evaluateFormulaAsGrid("=PERCENTILE.EXC(A1:A4, 0.05)", env)[0][0].displayText())
        assertEquals("#NUM!", FormulaEval.evaluateFormulaAsGrid("=QUARTILE.EXC(A1:A4, 0)", env)[0][0].displayText())
        assertEquals("#NUM!", FormulaEval.evaluateFormulaAsGrid("=QUARTILE.EXC(A1:A4, 4)", env)[0][0].displayText())
        // The inclusive variants accept the endpoints.
        assertEquals(1.0, (FormulaEval.evaluateFormulaAsGrid("=PERCENTILE.INC(A1:A4, 0)", env)[0][0] as FormulaEval.CellValue.Num).value, 0.0)
        assertEquals(4.0, (FormulaEval.evaluateFormulaAsGrid("=PERCENTILE.INC(A1:A4, 1)", env)[0][0] as FormulaEval.CellValue.Num).value, 0.0)
    }

    @Test fun quartileIsPercentileAtQuarterRanks() {
        val env = envFor("10,20,30,40,50,60,70")
        for (q in listOf(0, 1, 2, 3, 4)) {
            val quartile = (FormulaEval.evaluateFormulaAsGrid("=QUARTILE.INC(A1:A7, $q)", env)[0][0] as FormulaEval.CellValue.Num).value
            val percentile = (FormulaEval.evaluateFormulaAsGrid("=PERCENTILE.INC(A1:A7, ${q * 0.25})", env)[0][0] as FormulaEval.CellValue.Num).value
            assertEquals("QUARTILE.INC($q) vs PERCENTILE.INC(${q * 0.25})", percentile, quartile, 1e-15)
        }
    }

    @Test fun aFamilyCountsTextAsZero() {
        val env = envFor("1,\"x\",3")
        assertEquals(4.0 / 3.0, (FormulaEval.evaluateFormulaAsGrid("=AVERAGEA(A1:A3)", env)[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        // Numeric-looking text is still text: AVERAGEA("5") is 0, where AVERAGE("5") is 5. The coercion applies
        // to a direct literal argument; a referenced cell holding text is ignored by AVERAGE entirely.
        val textEnv = HashMap<String, FormulaEval.CellValue>()
        textEnv["A1"] = FormulaEval.CellValue.Text("5")
        assertEquals(0.0, (FormulaEval.evaluateFormulaAsGrid("=AVERAGEA(A1)", textEnv)[0][0] as FormulaEval.CellValue.Num).value, 0.0)
        assertEquals(0.0, (FormulaEval.evaluateFormulaAsGrid("=AVERAGEA(\"5\")", textEnv)[0][0] as FormulaEval.CellValue.Num).value, 0.0)
        assertEquals(5.0, (FormulaEval.evaluateFormulaAsGrid("=AVERAGE(\"5\")", textEnv)[0][0] as FormulaEval.CellValue.Num).value, 0.0)
        assertEquals("#DIV/0!", FormulaEval.evaluateFormulaAsGrid("=AVERAGE(A1)", textEnv)[0][0].displayText())
        // MAXA over only text is 0, not an error and not the text.
        val onlyText = envFor("\"a\",\"b\"")
        assertEquals(0.0, (FormulaEval.evaluateFormulaAsGrid("=MAXA(A1:A2)", onlyText)[0][0] as FormulaEval.CellValue.Num).value, 0.0)
        assertEquals(0.0, (FormulaEval.evaluateFormulaAsGrid("=MINA(A1:A2)", onlyText)[0][0] as FormulaEval.CellValue.Num).value, 0.0)
    }

    @Test fun aFamilyCountsLogicals() {
        val env = envFor("TRUE,2,3")
        assertEquals(2.0, (FormulaEval.evaluateFormulaAsGrid("=AVERAGEA(A1:A3)", env)[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        val falses = envFor("FALSE,FALSE,5")
        assertEquals(5.0 / 3.0, (FormulaEval.evaluateFormulaAsGrid("=AVERAGEA(A1:A3)", falses)[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        assertEquals(5.0, (FormulaEval.evaluateFormulaAsGrid("=MAXA(A1:A3)", falses)[0][0] as FormulaEval.CellValue.Num).value, 0.0)
        assertEquals(0.0, (FormulaEval.evaluateFormulaAsGrid("=MINA(A1:A3)", falses)[0][0] as FormulaEval.CellValue.Num).value, 0.0)
    }

    @Test fun aFamilySkipsEmptyCells() {
        val env = envFor("1,2,3,EMPTY")
        assertEquals(2.0, (FormulaEval.evaluateFormulaAsGrid("=AVERAGEA(A1:A4)", env)[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        val onlyEmpty = envFor("EMPTY")
        assertEquals("#DIV/0!", FormulaEval.evaluateFormulaAsGrid("=AVERAGEA(A1)", onlyEmpty)[0][0].displayText())
        assertEquals(0.0, (FormulaEval.evaluateFormulaAsGrid("=MAXA(A1)", onlyEmpty)[0][0] as FormulaEval.CellValue.Num).value, 0.0)
    }

    @Test fun aFamilyPropagatesErrors() {
        val env = envFor("1,#N/A,3")
        assertEquals("#N/A", FormulaEval.evaluateFormulaAsGrid("=AVERAGEA(A1:A3)", env)[0][0].displayText())
        assertEquals("#N/A", FormulaEval.evaluateFormulaAsGrid("=MAXA(A1:A3)", env)[0][0].displayText())
        assertEquals("#N/A", FormulaEval.evaluateFormulaAsGrid("=VARA(A1:A3)", env)[0][0].displayText())
    }

    @Test fun varianceFamilySplitsSampleFromPopulation() {
        val env = envFor("1,2,3,4")
        // Sample variance divides by n-1, population variance by n.
        assertEquals(5.0 / 3.0, (FormulaEval.evaluateFormulaAsGrid("=VARA(A1:A4)", env)[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        assertEquals(1.25, (FormulaEval.evaluateFormulaAsGrid("=VARPA(A1:A4)", env)[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        assertEquals(sqrt(5.0 / 3.0), (FormulaEval.evaluateFormulaAsGrid("=STDEVA(A1:A4)", env)[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        assertEquals(sqrt(1.25), (FormulaEval.evaluateFormulaAsGrid("=STDEVPA(A1:A4)", env)[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
        // Text and logicals enter the variance too, as 0 and 1.
        val mixed = envFor("1,\"x\",3,TRUE")
        assertEquals(2.0, (FormulaEval.evaluateFormulaAsGrid("=AVERAGEA(A1:A4)", mixed)[0][0] as FormulaEval.CellValue.Num).value, 1e-15)
    }

    @Test fun varianceFamilyRefusals() {
        val single = envFor("5")
        assertEquals("#DIV/0!", FormulaEval.evaluateFormulaAsGrid("=VARA(A1)", single)[0][0].displayText())
        assertEquals("#DIV/0!", FormulaEval.evaluateFormulaAsGrid("=STDEVA(A1)", single)[0][0].displayText())
        assertEquals(0.0, (FormulaEval.evaluateFormulaAsGrid("=VARPA(A1)", single)[0][0] as FormulaEval.CellValue.Num).value, 0.0)
        assertEquals(0.0, (FormulaEval.evaluateFormulaAsGrid("=STDEVPA(A1)", single)[0][0] as FormulaEval.CellValue.Num).value, 0.0)
        val onlyEmpty = envFor("EMPTY")
        assertEquals("#DIV/0!", FormulaEval.evaluateFormulaAsGrid("=VARPA(A1)", onlyEmpty)[0][0].displayText())
        assertEquals("#DIV/0!", FormulaEval.evaluateFormulaAsGrid("=STDEVPA(A1)", onlyEmpty)[0][0].displayText())
    }

    @Test fun argumentCountsAreEnforced() {
        assertEquals("#VALUE!", shown("=PERCENTILE.EXC(A1)", 1.0))
        assertEquals("#VALUE!", shown("=PERCENTILE.INC(A1,A2,A2)", 1.0, 2.0))
        assertEquals("#VALUE!", shown("=QUARTILE.EXC(A1)", 1.0))
        assertEquals("#VALUE!", shown("=AVERAGEA()"))
        assertEquals("#VALUE!", shown("=MAXA()"))
    }

    @Test fun unknownNamesStayUnknown() {
        assertEquals("#NAME?", shown("=PERCENTILE.INCC(A1,A2)", 1.0, 2.0))
        assertEquals("#NAME?", shown("=AVERAGEAA(A1)", 1.0))
        assertEquals("#NAME?", shown("=QUARTILE.EX(A1,A2)", 1.0, 2.0))
    }

    @Test fun theSheetEvaluatorThatTheEditorUsesReachesThemToo() {
        // The formulas above go through evaluateFormulaAsGrid; a workbook cell goes through the dependency DAG.
        val sheet = FormulaEval.evaluateSheet(
            mapOf(
                "A1" to FormulaEval.CellInput.NumberLiteral(1.0),
                "A2" to FormulaEval.CellInput.NumberLiteral(2.0),
                "A3" to FormulaEval.CellInput.NumberLiteral(3.0),
                "A4" to FormulaEval.CellInput.NumberLiteral(4.0),
                "B1" to FormulaEval.CellInput.Formula("=PERCENTILE.EXC(A1:A4, 0.25)"),
                "B2" to FormulaEval.CellInput.Formula("=AVERAGEA(A1:A4)"),
                "B3" to FormulaEval.CellInput.Formula("=B1+B2"),
            ),
        )
        assertEquals(1.25, (sheet.values["B1"] as FormulaEval.CellValue.Num).value, 1e-15)
        assertEquals(2.5, (sheet.values["B2"] as FormulaEval.CellValue.Num).value, 1e-15)
        assertEquals(3.75, (sheet.values["B3"] as FormulaEval.CellValue.Num).value, 1e-15)
        assertTrue("B3 must depend on B1", sheet.dependencies["B3"]!!.contains("B1"))
    }

    private companion object {
        const val CORPUS = "/r117/percentile_corpus.tsv"
    }
}
