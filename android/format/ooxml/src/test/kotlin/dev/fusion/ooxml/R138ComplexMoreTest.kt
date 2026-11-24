package dev.fusion.ooxml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R138: `IMCSCH(inumber)`, `IMLOG2(inumber)`, `IMLOG10(inumber)` — complex functions:
 * hyperbolic cosecant, base-2 logarithm, base-10 logarithm of complex numbers.
 *
 * Corpus-driven test: every row in `/r138/complex_more_corpus.tsv` (expected column produced
 * by the pure-math oracle in `tools/gen_r138_complex_more_corpus.py`) is evaluated through the
 * engine and compared exactly for text results, or exactly for refusal literals.
 *
 * The corpus is bound to the product source by `tools/check_r138_complex_more.py`, which fails if
 * any of IMCSCH/IMLOG2/IMLOG10 loses its dispatch or export, if a row is duplicated or tampered
 * with, or if the committed file drifts from `tools/gen_r138_complex_more_corpus.py`.
 */
class R138ComplexMoreTest {

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

    @Test fun imcschBasic() {
        assertEquals("0.8509181282393217", shown("=IMCSCH(\"1+0i\")"))
    }

    @Test fun imcschImaginary() {
        assertEquals("-1.1883951057781212i", shown("=IMCSCH(\"0+1i\")"))
    }

    @Test fun imcschComplex() {
        assertEquals("0.3039310016284264-0.6215180171704283i", shown("=IMCSCH(\"1+1i\")"))
    }

    @Test fun imcschJSuffix() {
        assertEquals("#DIV/0!", shown("=IMCSCH(\"0+0j\")"))
    }

    @Test fun imcschRealOnly() {
        assertEquals("0.8509181282393217", shown("=IMCSCH(\"1\")"))
    }

    @Test fun imlog2Basic() {
        assertEquals("1", shown("=IMLOG2(\"2+0i\")"))
    }

    @Test fun imlog2PowerOfTwo() {
        assertEquals("2", shown("=IMLOG2(\"4+0i\")"))
    }

    @Test fun imlog2Complex() {
        assertEquals("0.5000000000000001+1.1330900354567985i", shown("=IMLOG2(\"1+1i\")"))
    }

    @Test fun imlog2ZeroRefused() {
        assertEquals("#NUM!", shown("=IMLOG2(\"0+0i\")"))
    }

    @Test fun imlog2JSuffix() {
        assertEquals("0", shown("=IMLOG2(\"1+0j\")"))
    }

    @Test fun imlog10Basic() {
        assertEquals("1", shown("=IMLOG10(\"10+0i\")"))
    }

    @Test fun imlog10PowerOfTen() {
        assertEquals("2", shown("=IMLOG10(\"100+0i\")"))
    }

    @Test fun imlog10Complex() {
        assertEquals("0.1505149978319906+0.3410940884604603i", shown("=IMLOG10(\"1+1i\")"))
    }

    @Test fun imlog10ZeroRefused() {
        assertEquals("#NUM!", shown("=IMLOG10(\"0+0i\")"))
    }

    @Test fun errorPropagation() {
        assertEquals("#VALUE!", shown("=IMCSCH(#VALUE!)"))
        assertEquals("#VALUE!", shown("=IMLOG2(#VALUE!)"))
        assertEquals("#VALUE!", shown("=IMLOG10(#VALUE!)"))
    }

    companion object {
        private const val CORPUS = "/r138/complex_more_corpus.tsv"
    }
}
