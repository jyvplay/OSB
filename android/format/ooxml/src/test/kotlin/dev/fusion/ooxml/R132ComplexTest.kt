package dev.fusion.ooxml

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R132: `IMCSC` / `IMSEC` / `IMSECH` / `IMSINH` / `IMTAN` — complex trigonometric/hyperbolic functions.
 *
 * Each function takes a single complex number in x+yi or x+yj format and returns a complex number.
 * IMCSC = 1/sin(z), IMSEC = 1/cos(z), IMSECH = 1/cosh(z), IMSINH = sinh(z), IMTAN = tan(z).
 *
 * Corpus-driven test: every row in `/r132/complex_corpus.tsv` (expected column produced by the
 * pure-math oracle in `tools/gen_r132_complex_corpus.py`) is evaluated through the engine and
 * compared to `TOL = 1e-12` relative for numeric results, or exactly for refusal literals.
 *
 * The corpus is bound to the product source by `tools/check_r132_complex.py`, which fails if
 * any complex function loses its dispatch or export, if a row is duplicated or tampered with,
 * or if the committed file drifts from `tools/gen_r132_complex_corpus.py`.
 */
class R132ComplexTest {

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
                // Complex result: compare display text exactly (formatter matches Kotlin's formatComplex)
                assertEquals("$detail: expected ${row.expected}, got ${got.displayText()}",
                    row.expected, got.displayText())
            }
            checked++
        }
        assertTrue("corpus must stay substantial, saw $checked rows", checked >= 10)
    }

    @Test fun imcscReciprocalOfSine() {
        assertEquals("0.22837506559968654-0.1413630216124078i", shown("=IMCSC(1+2i)"))
    }

    @Test fun imsecReciprocalOfCosine() {
        assertEquals("0.1511762982655772+0.22697367539372157i", shown("=IMSEC(1+2i)"))
    }

    @Test fun imsechReciprocalOfCosh() {
        assertEquals("-0.41314934426694006-0.687527438655479i", shown("=IMSECH(1+2i)"))
    }

    @Test fun imsinhHyperbolicSine() {
        assertEquals("-0.4890562590412937+1.4031192506220405i", shown("=IMSINH(1+2i)"))
    }

    @Test fun imtanTangent() {
        assertEquals("0.0338128260798967+1.0147936161466335i", shown("=IMTAN(1+2i)"))
    }

    @Test fun wrongArityRefused() {
        assertEquals("#VALUE!", shown("=IMCSC()"))
        assertEquals("#VALUE!", shown("=IMSEC(1+2i,3-4i)"))
        assertEquals("#VALUE!", shown("=IMTAN()"))
    }

    @Test fun invalidComplexRefused() {
        assertEquals("#NUM!", shown("=IMSECH(notcomplex)"))
        assertEquals("#NUM!", shown("=IMSINH(abc)"))
    }

    @Test fun zeroSinRefused() {
        assertEquals("#DIV/0!", shown("=IMCSC(0+0i)"))
    }

    @Test fun imsecZeroReturnsOne() {
        assertEquals("1", shown("=IMSEC(0+0i)"))
    }

    companion object {
        private const val CORPUS = "/r132/complex_corpus.tsv"
    }
}
