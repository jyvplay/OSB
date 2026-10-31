// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * An explicit, opt-in text-to-DOCX conversion, not a Markdown parser or rich editor.
 * One input line becomes one paragraph. At the beginning of a nonempty line, `# `
 * through `###### `, `- ` and `> ` produce a heading, level-zero bullet and quote.
 * Within each line, simple, nonempty, non-nested `**bold**`, `*italic*` and
 * backtick-delimited monospace spans are converted. If any inline delimiter on a
 * line is unmatched, nested, escaped or ambiguous, the ENTIRE inline text of that
 * line is kept literally; block-prefix conversion still applies. No other Markdown
 * syntax is interpreted. The source string is never modified.
 */
object StructuredWriterDocx {
    const val MAX_RUNS = 8_192

    fun write(text: String): ByteArray {
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
            val content = when {
                heading -> line.substring(hashes + 1)
                bullet || quote -> line.substring(2)
                else -> line
            }
            val runs = inlineRuns(content)
            runCount += runs.size
            require(runCount <= MAX_RUNS) { "DOCX export accepts at most $MAX_RUNS text runs" }
            when {
                heading -> DocxPackage.Block.Heading(hashes, runs)
                bullet -> DocxPackage.Block.ListItem(ordered = false, runs = runs)
                quote -> DocxPackage.Block.BlockQuote(runs)
                else -> DocxPackage.Block.Paragraph(runs)
            }
        }
        val bytes = DocxPackage.write(DocxPackage.Document(blocks = blocks))
        require(bytes.size <= PlainWriterDocx.MAX_OUTPUT_BYTES) { "DOCX copy exceeds the export budget" }
        return bytes
    }

    private fun inlineRuns(text: String): List<DocxPackage.Run> {
        val runs = ArrayList<DocxPackage.Run>()
        var plainStart = 0
        var i = 0
        fun literal() = listOf(DocxPackage.Run(text))
        while (i < text.length) {
            val marker = text[i]
            if (marker != '*' && marker != '`') { i++; continue }
            if (i > 0 && text[i - 1] == '\\') return literal()
            val width = if (marker == '*' && i + 1 < text.length && text[i + 1] == '*') 2 else 1
            val start = i + width
            var end = start
            while (end < text.length && text[end] != '*' && text[end] != '`') end++
            if (end == start || end == text.length || text[end] != marker || text[end - 1] == '\\') return literal()
            var closeWidth = 1
            while (end + closeWidth < text.length && text[end + closeWidth] == marker) closeWidth++
            if (closeWidth != width) return literal()
            if (i > plainStart) runs.add(DocxPackage.Run(text.substring(plainStart, i)))
            runs.add(DocxPackage.Run(text.substring(start, end), bold = width == 2, italic = marker == '*' && width == 1, code = marker == '`'))
            i = end + width
            plainStart = i
        }
        if (plainStart < text.length) runs.add(DocxPackage.Run(text.substring(plainStart)))
        return runs.ifEmpty { listOf(DocxPackage.Run("")) }
    }
}
