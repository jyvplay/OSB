package dev.fusion.ooxml

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R135: `CONCATENATE(text1, [text2], ...)` — legacy text concatenation alias for CONCAT.
 *
 * Corpus-driven test: every row in `/r135/concatenate_corpus.tsv` (expected column produced
 * by the pure-math oracle in `tools/gen_r135_concatenate_corpus.py`) is evaluated through the
 * engine and compared exactly for text results, or exactly for refusal literals.
 *
 * The corpus is bound to the product source by `tools/check_r135_concatenate.py`, which fails if
 * CONCATENATE loses its dispatch or export, if a row is duplicated or tampered with, or if the
 * committed file drifts from `tools/gen_r135_concatenate_corpus.py`.
 */
class R135ConcatenateTest {

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
                assertEquals("$detail: expected text ${row.expected}, got ${got.displayText()}",
                    row.expected, got.displayText())
            }
            checked++
        }
        assertTrue("corpus must stay substantial, saw $checked rows", checked >= 10)
    }

    @Test fun concatenateSimpleText() {
        assertEquals("Hello World", shown("=CONCATENATE(\"Hello\", \" \", \"World\")"))
    }

    @Test fun concatenateSingleArgument() {
        assertEquals("abc", shown("=CONCATENATE(\"abc\")"))
    }

    @Test fun concatenateEmptyStrings() {
        assertEquals("", shown("=CONCATENATE(\"\", \"\")"))
    }

    @Test fun concatenateWithNumbers() {
        assertEquals("Value: 42", shown("=CONCATENATE(\"Value: \", 42)"))
        assertEquals("Price: 19.99", shown("=CONCATENATE(\"Price: \", 19.99)"))
    }

    @Test fun concatenateWithBooleans() {
        assertEquals("TRUE", shown("=CONCATENATE(TRUE)"))
        assertEquals("FALSE", shown("=CONCATENATE(FALSE)"))
        assertEquals("Result: TRUE", shown("=CONCATENATE(\"Result: \", TRUE)"))
    }

    @Test fun concatenateErrorPropagation() {
        assertEquals("#VALUE!", shown("=CONCATENATE(\"A\", #VALUE!)"))
        assertEquals("#DIV/0!", shown("=CONCATENATE(#DIV/0!)"))
    }

    @Test fun concatenateZeroArgumentsRefused() {
        assertEquals("#VALUE!", shown("=CONCATENATE()"))
    }

    @Test fun concatenateManyArguments() {
        assertEquals("Item42 of 100TRUE", shown("=CONCATENATE(\"Item\", 42, \" of \", 100, TRUE)"))
    }

    @Test fun concatenateAliasForConcat() {
        assertEquals(shown("=CONCAT(\"Hello\", \" \", \"World\")"),
            shown("=CONCATENATE(\"Hello\", \" \", \"World\")"))
    }

    companion object {
        private const val CORPUS = "/r135/concatenate_corpus.tsv"
    }
}
