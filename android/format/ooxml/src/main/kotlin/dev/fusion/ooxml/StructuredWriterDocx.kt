// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * An explicit, opt-in text-to-DOCX conversion, not a Markdown parser or rich editor.
 * One input line becomes one paragraph. At the beginning of a nonempty line, `# `
 * through `###### `, `- ` and `> ` produce a heading, level-zero bullet and quote.
 * Within each line, simple, nonempty, non-nested `**bold**`, `*italic*` and
 * backtick-delimited monospace spans are converted. If any inline delimiter on a
 * line is unmatched, nested, escaped or ambiguous, the ENTIRE inline text of that
 * line is kept literally; block-prefix conversion still applies. An additional,
 * separately opted-in mode recognizes only `1. ` followed by at least one code
 * unit as a level-zero ordered item. By default such items share one DOCX numbering
 * instance, including separated groups; other numbered or nested lines stay text.
 * A separate, default-off option recognizes nonempty, nonnested `~~strike~~` spans
 * whose first and last characters are not whitespace. Single tildes stay literal;
 * ambiguous, escaped, nested, unmatched or three-plus delimiters keep the entire
 * line's inline text literal. A separate opt-in can request a new numbering instance
 * with a start override after each non-numbered line; actual reader rendering is not
 * guaranteed. This is not a GFM parser. The source is never modified.
 */
object StructuredWriterDocx {
    const val MAX_RUNS = 8_192

    // Keep the original public method and its literal-numbered-line behavior unchanged.
    fun write(text: String): ByteArray = write(text, numberedLines = false)

    fun write(text: String, numberedLines: Boolean): ByteArray = write(text, numberedLines, strikeSpans = false)

    fun write(text: String, numberedLines: Boolean, strikeSpans: Boolean): ByteArray =
        write(text, numberedLines, strikeSpans, restartOrderedGroups = false)

    fun write(text: String, numberedLines: Boolean, strikeSpans: Boolean, restartOrderedGroups: Boolean): ByteArray {
        require(!restartOrderedGroups || numberedLines) { "Numbered lines must be enabled to request list restarts" }
        // For valid XML text UTF-16 code units never outnumber UTF-8 bytes; this
        // early bound avoids a large temporary encoding allocation on rejected input.
        require(text.length <= PlainWriterDocx.MAX_TEXT_BYTES) { "DOCX export accepts at most 128 KiB of UTF-8 text" }
        OfficeSheetDraft.validateXml(text)
        require(OfficeSheetDraft.utf8(text).size <= PlainWriterDocx.MAX_TEXT_BYTES) { "DOCX export accepts at most 128 KiB of UTF-8 text" }
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        require(lines.size <= PlainWriterDocx.MAX_PARAGRAPHS) { "DOCX export accepts at most ${PlainWriterDocx.MAX_PARAGRAPHS} paragraphs" }
        var runCount = 0
        val blocks = lines.map { line ->
            val hashes = line.takeWhile { it == '#' }.length
            val heading = hashes in 1..6 && line.length > hashes + 1 && line[hashes] == ' '
            val bullet = line.startsWith("- ") && line.length > 2
            val quote = line.startsWith("> ") && line.length > 2
            val numbered = numberedLines && line.startsWith("1. ") && line.length > 3
            val content = when {
                heading -> line.substring(hashes + 1)
                bullet || quote -> line.substring(2)
                numbered -> line.substring(3)
                else -> line
            }
            val runs = inlineRuns(content, strikeSpans)
            runCount += runs.size
            require(runCount <= MAX_RUNS) { "DOCX export accepts at most $MAX_RUNS text runs" }
            when {
                heading -> DocxPackage.Block.Heading(hashes, runs)
                bullet -> DocxPackage.Block.ListItem(ordered = false, runs = runs)
                quote -> DocxPackage.Block.BlockQuote(runs)
                numbered -> DocxPackage.Block.ListItem(ordered = true, runs = runs)
                else -> DocxPackage.Block.Paragraph(runs)
            }
        }
        val bytes = DocxPackage.write(DocxPackage.Document(blocks = blocks), restartOrderedGroups)
        require(bytes.size <= PlainWriterDocx.MAX_OUTPUT_BYTES) { "DOCX copy exceeds the export budget" }
        return bytes
    }

    private fun inlineRuns(text: String, strikeSpans: Boolean): List<DocxPackage.Run> {
        val runs = ArrayList<DocxPackage.Run>()
        var plainStart = 0
        var i = 0
        fun literal() = listOf(DocxPackage.Run(text))
        fun markerAt(index: Int): Boolean = text[index] == '*' || text[index] == '`' ||
            (strikeSpans && text[index] == '~' && index + 1 < text.length && text[index + 1] == '~')
        while (i < text.length) {
            if (!markerAt(i)) { i++; continue }
            val marker = text[i]
            if (i > 0 && text[i - 1] == '\\') return literal()
            val width = if (marker == '~' || (marker == '*' && i + 1 < text.length && text[i + 1] == '*')) 2 else 1
            if (marker == '~' && i + 2 < text.length && text[i + 2] == '~') return literal()
            val start = i + width
            var end = start
            while (end < text.length && !markerAt(end)) end++
            if (end == start || end == text.length || text[end] != marker || text[end - 1] == '\\') return literal()
            var closeWidth = 1
            while (end + closeWidth < text.length && text[end + closeWidth] == marker) closeWidth++
            if (closeWidth != width) return literal()
            if (marker == '~' && (text[start].isWhitespace() || text[end - 1].isWhitespace())) return literal()
            if (i > plainStart) runs.add(DocxPackage.Run(text.substring(plainStart, i)))
            runs.add(DocxPackage.Run(text.substring(start, end), bold = marker == '*' && width == 2,
                italic = marker == '*' && width == 1, strike = marker == '~', code = marker == '`'))
            i = end + width
            plainStart = i
        }
        if (plainStart < text.length) runs.add(DocxPackage.Run(text.substring(plainStart)))
        return runs.ifEmpty { listOf(DocxPackage.Run("")) }
    }
}
