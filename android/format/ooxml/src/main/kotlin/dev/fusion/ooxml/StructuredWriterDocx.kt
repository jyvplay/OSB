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
 * guaranteed. A separate default-off option converts only strict unescaped pipe
 * blocks with an all-hyphen separator and at least one body row into actual tables.
 * Cell-edge spaces/tabs are trimmed and cells are literal; malformed or unsupported
 * blocks stay ordinary text. It is not a GFM parser. The source is never modified.
 */
object StructuredWriterDocx {
    const val MAX_RUNS = 8_192
    /** A line containing exactly this marker becomes a page break (WordprocessingML <w:br w:type="page"/>). */
    const val PAGE_BREAK_MARKER = "[[pagebreak]]"
    private val LINK_TARGETS = listOf("http://", "https://", "mailto:")

    // Keep the original public method and its literal-numbered-line behavior unchanged.
    fun write(text: String): ByteArray = write(text, numberedLines = false)

    fun write(text: String, numberedLines: Boolean): ByteArray = write(text, numberedLines, strikeSpans = false)

    fun write(text: String, numberedLines: Boolean, strikeSpans: Boolean): ByteArray =
        write(text, numberedLines, strikeSpans, restartOrderedGroups = false)

    fun write(text: String, numberedLines: Boolean, strikeSpans: Boolean, restartOrderedGroups: Boolean): ByteArray =
        write(text, numberedLines, strikeSpans, restartOrderedGroups, pipeTables = false)

    fun write(text: String, numberedLines: Boolean, strikeSpans: Boolean, restartOrderedGroups: Boolean, pipeTables: Boolean): ByteArray =
        write(text, numberedLines, strikeSpans, restartOrderedGroups, pipeTables, images = emptyMap())

    /**
     * The same conversion with caller-supplied image bytes. A line that is exactly `![alt](key)`
     * becomes an inline picture whose bytes are `images[key]`; the writer itself never reads a file,
     * a URL or a provider, so the access model stays "pure function over data the caller already has".
     * When `images` is empty the syntax stays literal, which is what keeps legacy output byte-identical.
     */
    fun write(
        text: String,
        numberedLines: Boolean,
        strikeSpans: Boolean,
        restartOrderedGroups: Boolean,
        pipeTables: Boolean,
        images: Map<String, ByteArray>,
    ): ByteArray {
        require(!restartOrderedGroups || numberedLines) { "Numbered lines must be enabled to request list restarts" }
        // For valid XML text UTF-16 code units never outnumber UTF-8 bytes; this
        // early bound avoids a large temporary encoding allocation on rejected input.
        require(text.length <= PlainWriterDocx.MAX_TEXT_BYTES) { "DOCX export accepts at most 128 KiB of UTF-8 text" }
        OfficeSheetDraft.validateXml(text)
        require(OfficeSheetDraft.utf8(text).size <= PlainWriterDocx.MAX_TEXT_BYTES) { "DOCX export accepts at most 128 KiB of UTF-8 text" }
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        require(lines.size <= PlainWriterDocx.MAX_PARAGRAPHS) { "DOCX export accepts at most ${PlainWriterDocx.MAX_PARAGRAPHS} paragraphs" }
        var runCount = 0
        val blocks = ArrayList<DocxPackage.Block>()
        fun addLine(line: String) {
            if (line == PAGE_BREAK_MARKER) {
                blocks.add(DocxPackage.Block.PageBreak)
                return
            }
            if (images.isNotEmpty()) {
                val picture = imageLine(line, images)
                if (picture != null) {
                    blocks.add(picture)
                    return
                }
            }
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
            blocks.add(when {
                heading -> DocxPackage.Block.Heading(hashes, runs)
                bullet -> DocxPackage.Block.ListItem(ordered = false, runs = runs)
                quote -> DocxPackage.Block.BlockQuote(runs)
                numbered -> DocxPackage.Block.ListItem(ordered = true, runs = runs)
                else -> DocxPackage.Block.Paragraph(runs)
            })
        }
        var index = 0
        while (index < lines.size) {
            if (!pipeTables || !lines[index].startsWith('|')) {
                addLine(lines[index++])
                continue
            }
            // Treat a whole contiguous pipe-prefixed group as one candidate. A
            // malformed first row cannot turn a later suffix into a partial table.
            var end = index + 1
            while (end < lines.size && lines[end].startsWith('|')) end++
            val group = lines.subList(index, end)
            val rows = pipeTable(group)
            if (rows == null) {
                // The user opted into this strict table grammar, not partial Markdown
                // interpretation of a rejected group. Preserve every line literally.
                runCount += group.size
                require(runCount <= MAX_RUNS) { "DOCX export accepts at most $MAX_RUNS text runs" }
                group.forEach { blocks.add(DocxPackage.Block.Paragraph(listOf(DocxPackage.Run(it)))) }
            } else {
                runCount += rows.sumOf { it.size }
                require(runCount <= MAX_RUNS) { "DOCX export accepts at most $MAX_RUNS text runs" }
                blocks.add(DocxPackage.Block.Table(rows))
            }
            index = end
        }
        val bytes = DocxPackage.write(DocxPackage.Document(blocks = blocks), restartOrderedGroups)
        require(bytes.size <= PlainWriterDocx.MAX_OUTPUT_BYTES) { "DOCX copy exceeds the export budget" }
        return bytes
    }

    private fun pipeTable(lines: List<String>): List<List<String>>? {
        if (lines.size < 3) return null
        val parsed = ArrayList<List<String>>(lines.size)
        for (line in lines) {
            if (line.length < 2 || line.last() != '|' || line.contains("\\|")) return null
            parsed.add(line.substring(1, line.lastIndex).split('|').map { it.trim(' ', '\t') })
        }
        val columns = parsed.first().size
        if (parsed.any { it.size != columns } || parsed[1].any { !it.matches(Regex("-{3,}")) }) return null
        require(columns in 1..DocxPackage.MAX_TABLE_COLS) { "A pipe table accepts at most ${DocxPackage.MAX_TABLE_COLS} columns" }
        require((parsed.size - 1) * columns <= 256) { "A pipe table accepts at most 256 header and body cells" }
        return listOf(parsed.first()) + parsed.drop(2)
    }

    /**
     * Splits a line into literal chunks and validated external links. A `[` that is escaped,
     * unterminated, or followed by a non-http(s)/mailto target stays literal text, so the
     * Markdown-ish writer never silently drops brackets it does not understand.
     */
    private fun linkSegments(text: String): List<Pair<String, String?>> {
        val segments = ArrayList<Pair<String, String?>>()
        var plainStart = 0
        var i = 0
        while (i < text.length) {
            if (text[i] != '[' || (i > 0 && text[i - 1] == '\\')) { i++; continue }
            val labelEnd = text.indexOf(']', i + 1)
            if (labelEnd <= i + 1 || labelEnd + 1 >= text.length || text[labelEnd + 1] != '(') { i++; continue }
            val targetEnd = text.indexOf(')', labelEnd + 2)
            if (targetEnd <= labelEnd + 2) { i++; continue }
            val label = text.substring(i + 1, labelEnd)
            val target = text.substring(labelEnd + 2, targetEnd)
            if (label.contains('[') || target.any { it.isWhitespace() }) { i++; continue }
            if (LINK_TARGETS.none { target.startsWith(it, ignoreCase = true) }) { i++; continue }
            if (target.length > DocxPackage.MAX_HREF_CHARS) { i++; continue }
            if (i > plainStart) segments.add(text.substring(plainStart, i) to null)
            segments.add(label to target)
            i = targetEnd + 1
            plainStart = i
        }
        if (plainStart < text.length) segments.add(text.substring(plainStart) to null)
        return segments
    }

    /**
     * Exact grammar: `![alt](key)` with no surrounding text, alt of at most 1024 characters and no
     * brackets, key matching a caller-supplied entry. Anything else stays literal text.
     */
    private fun imageLine(line: String, images: Map<String, ByteArray>): DocxPackage.Block.Picture? {
        if (!line.startsWith("![") || !line.endsWith(")")) return null
        val split = line.indexOf("](")
        if (split < 2) return null
        val alt = line.substring(2, split)
        val key = line.substring(split + 2, line.length - 1)
        if (key.isEmpty() || key.any { it == '(' || it == ')' || it == ']' }) return null
        if (alt.any { it == '[' || it == ']' }) return null
        val bytes = images[key] ?: return null
        return DocxPackage.Block.Picture(DocxPackage.Image.of(bytes), alt)
    }

    private fun inlineRuns(text: String, strikeSpans: Boolean): List<DocxPackage.Run> {
        val segments = linkSegments(text)
        if (segments.none { it.second != null }) return inlineMarkup(text, strikeSpans)
        val runs = ArrayList<DocxPackage.Run>()
        for ((chunk, href) in segments) {
            if (href != null) {
                if (chunk.isNotEmpty()) runs.add(DocxPackage.Run(chunk, href = href))
            } else {
                runs.addAll(inlineMarkup(chunk, strikeSpans).filter { it.text.isNotEmpty() })
            }
        }
        return runs.ifEmpty { listOf(DocxPackage.Run("")) }
    }

    private fun inlineMarkup(text: String, strikeSpans: Boolean): List<DocxPackage.Run> {
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
