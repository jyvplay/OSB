package dev.fusion.ooxml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R137: `HYPERLINK(link_location, [friendly_name])` — returns the friendly name when
 * provided, otherwise returns the link location text.
 *
 * Corpus-driven test: every row in `/r137/hyperlink_corpus.tsv` (expected column produced
 * by the pure-math oracle in `tools/gen_r137_hyperlink_corpus.py`) is evaluated through the
 * engine and compared exactly for text results, or exactly for refusal literals.
 *
 * The corpus is bound to the product source by `tools/check_r137_hyperlink.py`, which fails if
 * HYPERLINK loses its dispatch or export, if a row is duplicated or tampered with, or if the
 * committed file drifts from `tools/gen_r137_hyperlink_corpus.py`.
 */
class R137HyperlinkTest {

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

    @Test fun hyperlinkLinkOnly() {
        assertEquals("https://example.com", shown("=HYPERLINK(\"https://example.com\")"))
    }

    @Test fun hyperlinkWithFriendlyName() {
        assertEquals("Example", shown("=HYPERLINK(\"https://example.com\", \"Example\")"))
    }

    @Test fun hyperlinkEmptyFriendlyName() {
        assertEquals("", shown("=HYPERLINK(\"https://example.com\", \"\")"))
    }

    @Test fun hyperlinkWithQueryString() {
        assertEquals("Page 1", shown("=HYPERLINK(\"https://example.com/page?q=1\", \"Page 1\")"))
    }

    @Test fun hyperlinkVariousUrls() {
        assertEquals("Link A", shown("=HYPERLINK(\"https://a.com\", \"Link A\")"))
        assertEquals("Link B", shown("=HYPERLINK(\"https://b.com\", \"Link B\")"))
        assertEquals("Click here", shown("=HYPERLINK(\"https://c.com\", \"Click here\")"))
    }

    @Test fun hyperlinkErrorPropagation() {
        assertEquals("#VALUE!", shown("=HYPERLINK(#VALUE!)"))
        assertEquals("#VALUE!", shown("=HYPERLINK(\"https://example.com\", #VALUE!)"))
        assertEquals("#REF!", shown("=HYPERLINK(#REF!, \"Click\")"))
    }

    @Test fun hyperlinkArgumentCountValidation() {
        assertEquals("#VALUE!", shown("=HYPERLINK()"))
        assertEquals("#VALUE!", shown("=HYPERLINK(\"a\", \"b\", \"c\")"))
    }

    @Test fun hyperlinkEmptyLinkRefused() {
        assertEquals("#VALUE!", shown("=HYPERLINK(\"\")"))
    }

    @Test fun hyperlinkSpecialUrls() {
        assertEquals("Report", shown("=HYPERLINK(\"file:///C:/docs/report.pdf\", \"Report\")"))
        assertEquals("Email", shown("=HYPERLINK(\"mailto:someone@example.com\", \"Email\")"))
        assertEquals("Org", shown("=HYPERLINK(\"https://example.org\", \"Org\")"))
    }

    companion object {
        private const val CORPUS = "/r137/hyperlink_corpus.tsv"
    }
}
