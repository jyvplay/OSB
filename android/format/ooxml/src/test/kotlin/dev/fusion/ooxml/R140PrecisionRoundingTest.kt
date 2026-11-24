package dev.fusion.ooxml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R140: `CEILING.PRECISE(number, [significance])`, `FLOOR.PRECISE(number, [significance])`,
 * `ISO.CEILING(number, [significance])`, `MODE.MULT(number1, [number2], ...)`.
 *
 * Corpus-driven test: every row in `/r140/precision_rounding_corpus.tsv` (expected column produced
 * by the pure-math oracle in `tools/gen_r140_precision_rounding_corpus.py`) is evaluated through
 * the engine and compared exactly for number results, or exactly for refusal literals.
 *
 * The corpus is bound to the product source by `tools/check_r140_precision_rounding.py`, which
 * fails if any of these functions loses its dispatch, if a row is duplicated or tampered with,
 * or if the committed file drifts from `tools/gen_r140_precision_rounding_corpus.py`.
 */
class R140PrecisionRoundingTest {

    private data class Row(val function: String, val args: String, val expected: String)

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
                assertEquals("$detail: expected ${row.expected}, got ${got.displayText()}",
                    row.expected, got.displayText())
            }
            checked++
        }
        assertTrue("corpus must stay substantial, saw $checked rows", checked >= 10)
    }

    @Test fun ceilingPreciseBasic() {
        assertEquals("5", shown("=CEILING.PRECISE(4.3)"))
        assertEquals("6", shown("=CEILING.PRECISE(4.3, 2)"))
        assertEquals("5", shown("=CEILING.PRECISE(5)"))
    }

    @Test fun ceilingPreciseNegative() {
        assertEquals("-4", shown("=CEILING.PRECISE(-4.3)"))
        assertEquals("-4", shown("=CEILING.PRECISE(-4.3, 2)"))
    }

    @Test fun ceilingPreciseNegativeSignificance() {
        assertEquals("6", shown("=CEILING.PRECISE(4.3, -2)"))
    }

    @Test fun ceilingPreciseZero() {
        assertEquals("0", shown("=CEILING.PRECISE(0)"))
    }

    @Test fun floorPreciseBasic() {
        assertEquals("4", shown("=FLOOR.PRECISE(4.3)"))
        assertEquals("4", shown("=FLOOR.PRECISE(4.3, 2)"))
        assertEquals("5", shown("=FLOOR.PRECISE(5)"))
    }

    @Test fun floorPreciseNegative() {
        assertEquals("-5", shown("=FLOOR.PRECISE(-4.3)"))
        assertEquals("-6", shown("=FLOOR.PRECISE(-4.3, 2)"))
    }

    @Test fun floorPreciseNegativeSignificance() {
        assertEquals("4", shown("=FLOOR.PRECISE(4.3, -2)"))
    }

    @Test fun floorPreciseZero() {
        assertEquals("0", shown("=FLOOR.PRECISE(0)"))
    }

    @Test fun isoCeilingMatchesCeilingPrecise() {
        assertEquals(shown("=CEILING.PRECISE(4.3)"), shown("=ISO.CEILING(4.3)"))
        assertEquals(shown("=CEILING.PRECISE(-4.3)"), shown("=ISO.CEILING(-4.3)"))
        assertEquals(shown("=CEILING.PRECISE(4.3, 2)"), shown("=ISO.CEILING(4.3, 2)"))
    }

    @Test fun modeMultSingleMode() {
        assertEquals("2", shown("=MODE.MULT(1, 2, 2, 3)"))
        assertEquals("5", shown("=MODE.MULT(5, 5, 5, 3, 3, 1)"))
        assertEquals("1", shown("=MODE.MULT(1)"))
    }

    @Test fun modeMultMultiModeFirstValue() {
        assertEquals("1", shown("=MODE.MULT(1, 1, 2, 2, 3)"))
    }

    @Test fun modeMultNoMode() {
        assertEquals("#N/A", shown("=MODE.MULT(1, 2, 3)"))
        assertEquals("#N/A", shown("=MODE.MULT()"))
    }

    @Test fun errorPropagation() {
        assertEquals("#VALUE!", shown("=CEILING.PRECISE(#VALUE!)"))
        assertEquals("#VALUE!", shown("=FLOOR.PRECISE(#VALUE!)"))
        assertEquals("#VALUE!", shown("=ISO.CEILING(#VALUE!)"))
        assertEquals("#VALUE!", shown("=MODE.MULT(#VALUE!)"))
    }

    @Test fun argumentCountValidation() {
        assertEquals("#VALUE!", shown("=CEILING.PRECISE()"))
        assertEquals("#VALUE!", shown("=FLOOR.PRECISE()"))
        assertEquals("#VALUE!", shown("=ISO.CEILING()"))
    }

    @Test fun significanceZeroRefused() {
        assertEquals("#DIV/0!", shown("=CEILING.PRECISE(4.3, 0)"))
        assertEquals("#DIV/0!", shown("=FLOOR.PRECISE(4.3, 0)"))
        assertEquals("#DIV/0!", shown("=ISO.CEILING(4.3, 0)"))
    }

    companion object {
        private const val CORPUS = "/r140/precision_rounding_corpus.tsv"
    }
}
