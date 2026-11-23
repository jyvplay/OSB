package dev.fusion.ooxml

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R136: `TEXTSPLIT(text, col_delimiter, [row_delimiter], [ignore_empty], [match_mode], [pad_with])`
 * — text splitting into a dynamic array.
 *
 * Corpus-driven test: every row in `/r136/textsplit_corpus.tsv` (expected column produced
 * by the pure-math oracle in `tools/gen_r136_textsplit_corpus.py`) is evaluated through the
 * engine and compared exactly for text results, or exactly for refusal literals.
 *
 * The corpus is bound to the product source by `tools/check_r136_textsplit.py`, which fails if
 * TEXTSPLIT loses its dispatch or export, if a row is duplicated or tampered with, or if the
 * committed file drifts from `tools/gen_r136_textsplit_corpus.py`.
 */
class R136TextSplitTest {

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

    @Test fun textsplitBasicColumnSplit() {
        assertEquals("a", shown("=TEXTSPLIT(\"a,b,c\", \",\")"))
    }

    @Test fun textsplitSingleElement() {
        assertEquals("single", shown("=TEXTSPLIT(\"single\", \",\")"))
    }

    @Test fun textsplitWithEmptyBetweenDelimiters() {
        assertEquals("a", shown("=TEXTSPLIT(\"a,,b\", \",\")"))
    }

    @Test fun textsplitRowDelimiter() {
        assertEquals("a", shown("=TEXTSPLIT(\"a,b;c,d\", \",\", \";\")"))
    }

    @Test fun textsplitIgnoreEmptyTrue() {
        assertEquals("a", shown("=TEXTSPLIT(\"a,,b\", \",\", , TRUE)"))
    }

    @Test fun textsplitIgnoreEmptyFalse() {
        assertEquals("a", shown("=TEXTSPLIT(\"a,,b\", \",\", , FALSE)"))
    }

    @Test fun textsplitMatchModeCaseSensitive() {
        assertEquals("A", shown("=TEXTSPLIT(\"A,B,C\", \",\", , FALSE, 0)"))
    }

    @Test fun textsplitMatchModeCaseInsensitive() {
        assertEquals("a", shown("=TEXTSPLIT(\"a,b,c\", \",\", , FALSE, 1)"))
    }

    @Test fun textsplitPadWith() {
        assertEquals("a", shown("=TEXTSPLIT(\"a,b;c\", \",\", \";\", , , \"-\")"))
    }

    @Test fun textsplitMultiCharDelimiter() {
        assertEquals("a", shown("=TEXTSPLIT(\"a<>b<>c\", \"<>\")"))
    }

    @Test fun textsplitErrorPropagation() {
        assertEquals("#VALUE!", shown("=TEXTSPLIT(\"a,b\", \"\")"))
        assertEquals("#VALUE!", shown("=TEXTSPLIT(#VALUE!, \",\")"))
    }

    @Test fun textsplitMissingDelimiterRefused() {
        assertEquals("#VALUE!", shown("=TEXTSPLIT(\"a,b\")"))
    }

    @Test fun textsplitEmptyText() {
        assertEquals("", shown("=TEXTSPLIT(\"\", \",\")"))
    }

    companion object {
        private const val CORPUS = "/r136/textsplit_corpus.tsv"
    }
}
