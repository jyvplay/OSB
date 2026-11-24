package dev.fusion.ooxml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R139: `AREAS(reference)` — returns the number of areas in a reference.
 * In the single-sheet engine, a CellRef or RangeRef is always exactly 1 area.
 *
 * Corpus-driven test: every row in `/r139/areas_corpus.tsv` (expected column produced
 * by the pure-math oracle in `tools/gen_r139_areas_corpus.py`) is evaluated through the
 * engine and compared exactly for number results, or exactly for refusal literals.
 *
 * The corpus is bound to the product source by `tools/check_r139_areas.py`, which fails if
 * AREAS loses its dispatch, if a row is duplicated or tampered with, or if the committed
 * file drifts from `tools/gen_r139_areas_corpus.py`.
 */
class R139AreasTest {

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

    @Test fun areasSingleCell() {
        assertEquals("1", shown("=AREAS(A1)"))
        assertEquals("1", shown("=AREAS(Z100)"))
        assertEquals("1", shown("=AREAS(XFD1048576)"))
    }

    @Test fun areasRangeReference() {
        assertEquals("1", shown("=AREAS(A1:B2)"))
        assertEquals("1", shown("=AREAS(A1:A10)"))
        assertEquals("1", shown("=AREAS(C5:F20)"))
        assertEquals("1", shown("=AREAS(1:1)"))
        assertEquals("1", shown("=AREAS(A:A)"))
    }

    @Test fun areasArrayLiteral() {
        assertEquals("1", shown("=AREAS({1,2,3})"))
        assertEquals("1", shown("=AREAS({1;2;3})"))
        assertEquals("1", shown("=AREAS({1,2;3,4})"))
    }

    @Test fun areasErrorPropagation() {
        assertEquals("#VALUE!", shown("=AREAS(#VALUE!)"))
        assertEquals("#REF!", shown("=AREAS(#REF!)"))
    }

    @Test fun areasArgumentCountValidation() {
        assertEquals("#VALUE!", shown("=AREAS()"))
        assertEquals("#VALUE!", shown("=AREAS(A1, B1)"))
    }

    @Test fun areasNonReferenceExpression() {
        assertEquals("1", shown("=AREAS(42)"))
        assertEquals("1", shown("=AREAS(\"text\")"))
        assertEquals("1", shown("=AREAS(TRUE)"))
        assertEquals("1", shown("=AREAS(A1+B1)"))
    }

    @Test fun areasEntireRowColumn() {
        assertEquals("1", shown("=AREAS(5:5)"))
        assertEquals("1", shown("=AREAS(B:B)"))
    }

    @Test fun areasNestedReference() {
        assertEquals("1", shown("=AREAS(INDEX(A1:C3, 1, 1))"))
    }

    @Test fun areasErrorInNestedExpression() {
        assertEquals("#DIV/0!", shown("=AREAS(1/0)"))
    }

    companion object {
        private const val CORPUS = "/r139/areas_corpus.tsv"
    }
}
