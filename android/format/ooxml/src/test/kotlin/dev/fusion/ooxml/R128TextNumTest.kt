package dev.fusion.ooxml

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R128: `T` and `N` — value conversion functions.
 *
 * `T(value)` returns the text if the value is text, otherwise empty string.
 * `N(value)` returns the number if the value is a number, 1 for TRUE, 0 for
 * FALSE/empty/unparseable text.
 *
 * Corpus-driven test: every row in `/r128/textnum_corpus.tsv` (expected column produced
 * by the pure-math oracle in `tools/gen_r128_textnum_corpus.py`) is evaluated through the
 * engine and compared to `TOL = 1e-12` relative for numeric results, or exactly for text
 * results and refusal literals.
 *
 * The corpus is bound to the product source by `tools/check_r128_textnum.py`, which fails if
 * `T` or `N` loses its dispatch or export, if a row is duplicated or tampered with, or if
 * the committed file drifts from `tools/gen_r128_textnum_corpus.py`.
 */
class R128TextNumTest {

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
            } else if (row.function == "T") {
                // Text output: exact string match
                assertEquals("$detail: expected ${row.expected}, got ${got.displayText()}",
                    row.expected, got.displayText())
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

    @Test fun tReturnsTextForText() {
        assertEquals("hello", shown("=T(\"hello\")"))
        assertEquals("42", shown("=T(\"42\")"))
        assertEquals("", shown("=T(\"\")"))
    }

    @Test fun tReturnsEmptyForNonText() {
        assertEquals("", shown("=T(123)"))
        assertEquals("", shown("=T(TRUE)"))
        assertEquals("", shown("=T(FALSE)"))
        assertEquals("", shown("=T(3.14)"))
    }

    @Test fun nReturnsNumberForNumber() {
        assertTrue(abs(number("=N(42)") - 42.0) <= TOL)
        assertTrue(abs(number("=N(3.14)") - 3.14) <= TOL)
        assertTrue(abs(number("=N(0)") - 0.0) <= TOL)
        assertTrue(abs(number("=N(-7.5)") - (-7.5)) <= TOL)
    }

    @Test fun nReturnsOneForTrueZeroForFalse() {
        assertTrue(abs(number("=N(TRUE)") - 1.0) <= TOL)
        assertTrue(abs(number("=N(FALSE)") - 0.0) <= TOL)
    }

    @Test fun nParsesNumericText() {
        assertTrue(abs(number("=N(\"123\")") - 123.0) <= TOL)
        assertTrue(abs(number("=N(\"3.14\")") - 3.14) <= TOL)
    }

    @Test fun nReturnsZeroForNonNumeric() {
        assertTrue(abs(number("=N(\"not a number\")") - 0.0) <= TOL)
        assertTrue(abs(number("=N(\"\")") - 0.0) <= TOL)
        assertTrue(abs(number("=N(TRUE)") - 1.0) <= TOL)
    }

    @Test fun nPropagatesErrors() {
        assertEquals("#DIV/0!", shown("=N(1/0)"))
    }

    @Test fun wrongArityRefusals() {
        assertEquals("#VALUE!", shown("=T()"))
        assertEquals("#VALUE!", shown("=T(1,2)"))
        assertEquals("#VALUE!", shown("=N()"))
        assertEquals("#VALUE!", shown("=N(1,2)"))
    }

    @Test fun unknownNameIsRefused() {
        assertEquals("#NAME?", shown("=NOPE(42)"))
    }

    companion object {
        private const val CORPUS = "/r128/textnum_corpus.tsv"
    }
}
