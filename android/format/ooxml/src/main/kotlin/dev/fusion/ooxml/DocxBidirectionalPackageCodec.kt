// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Clean-room ECMA-376 5th Edition WordprocessingML (`.docx`) bidirectional package codec.
 *
 * Writes and reparses a real ZIP `OPC` package containing `[Content_Types].xml`, `_rels/.rels`,
 * `word/document.xml`, `word/styles.xml`, `word/numbering.xml`, `word/footnotes.xml`,
 * `word/comments.xml`, `word/header1.xml`, `word/footer1.xml`, and `word/_rels/document.xml.rels`,
 * with correct `Transitional` namespaces, `w:pStyle`/`w:numPr` list numbering, `w:rPr` run
 * formatting, `w:tbl`/`w:tr`/`w:tc` tables, `w:footnoteReference`, and `w:commentRangeStart/End`.
 *
 * Deterministic: every ZIP entry uses a fixed 1980-01-01 timestamp so byte output is reproducible.
 */
object DocxBidirectionalPackageCodec {
    const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
    const val CT_NS = "http://schemas.openxmlformats.org/package/2006/content-types"
    const val DOCX_MAIN_CT = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"

    enum class Align { LEFT, CENTER, RIGHT, JUSTIFY }

    data class RunProps(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val code: Boolean = false,
        val sizeHalfPoints: Int = 22,
        val colorHex: String? = null,
    )

    sealed interface Block {
        data class Paragraph(
            val text: String,
            val style: String = "Normal",
            val align: Align = Align.LEFT,
            val listLevel: Int? = null,
            val props: RunProps = RunProps(),
            val commentId: Int? = null,
            val footnoteId: Int? = null,
        ) : Block

        data class Table(
            val header: List<String>,
            val rows: List<List<String>>,
            val gridColWidthsTwips: List<Int>,
        ) : Block

        data class PageBreak(val kind: String = "nextPage") : Block
    }

    data class WriterDocument(
        val title: String,
        val author: String,
        val blocks: List<Block>,
        val footnotes: Map<Int, String> = emptyMap(),
        val comments: Map<Int, String> = emptyMap(),
    )

    data class ParsedDocument(
        val title: String,
        val paragraphs: List<String>,
        val styles: List<String>,
        val numberingCount: Int,
        val footnoteCount: Int,
        val commentCount: Int,
        val tableCells: List<List<String>>,
        val entries: List<String>,
    )

    private fun esc(s: String): String = buildString {
        for (ch in s) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(ch)
        }
    }

    private fun rPr(p: RunProps): String {
        val sb = StringBuilder("<w:rPr>")
        if (p.bold) sb.append("<w:b/>")
        if (p.italic) sb.append("<w:i/>")
        if (p.underline) sb.append("<w:u w:val=\"single\"/>")
        if (p.strike) sb.append("<w:strike/>")
        if (p.code) sb.append("<w:rStyle w:val=\"CodeChar\"/>")
        p.colorHex?.let { sb.append("<w:color w:val=\"${esc(it)}\"/>") }
        sb.append("<w:sz w:val=\"${p.sizeHalfPoints}\"/><w:szCs w:val=\"${p.sizeHalfPoints}\"/>")
        sb.append("</w:rPr>")
        return sb.toString()
    }

    fun encodeDocument(doc: WriterDocument): ByteArray {
        val body = StringBuilder()
        for (b in doc.blocks) when (b) {
            is Block.Paragraph -> {
                body.append("<w:p>")
                body.append("<w:pPr>")
                body.append("<w:pStyle w:val=\"${esc(b.style)}\"/>")
                if (b.align != Align.LEFT) body.append("<w:jc w:val=\"${b.align.name.lowercase()}\"/>")
                b.listLevel?.let {
                    body.append("<w:numPr><w:ilvl w:val=\"$it\"/><w:numId w:val=\"1\"/></w:numPr>")
                }
                body.append("</w:pPr>")
                b.commentId?.let { body.append("<w:commentRangeStart w:id=\"$it\"/>") }
                body.append("<w:r>").append(rPr(b.props))
                    .append("<w:t xml:space=\"preserve\">").append(esc(b.text)).append("</w:t>")
                b.footnoteId?.let { body.append("<w:footnoteReference w:id=\"$it\"/>") }
                body.append("</w:r>")
                b.commentId?.let { body.append("<w:commentRangeEnd w:id=\"$it\"/><w:r><w:commentReference w:id=\"$it\"/></w:r>") }
                body.append("</w:p>")
            }
            is Block.Table -> {
                body.append("<w:tbl><w:tblPr><w:tblStyle w:val=\"TableGrid\"/><w:tblW w:w=\"0\" w:type=\"auto\"/>")
                body.append("<w:tblBorders>")
                for (edge in listOf("top", "left", "bottom", "right", "insideH", "insideV")) {
                    body.append("<w:$edge w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/>")
                }
                body.append("</w:tblBorders></w:tblPr>")
                body.append("<w:tblGrid>")
                b.gridColWidthsTwips.forEach { body.append("<w:gridCol w:w=\"$it\"/>") }
                body.append("</w:tblGrid>")
                body.append("<w:tr><w:trPr><w:tblHeader/></w:trPr>")
                b.header.forEach { cell ->
                    body.append("<w:tc><w:tcPr><w:tcW w:w=\"0\" w:type=\"auto\"/><w:shd w:val=\"clear\" w:fill=\"E8EDF5\"/></w:tcPr>")
                    body.append("<w:p><w:r>").append(rPr(RunProps(bold = true)))
                        .append("<w:t xml:space=\"preserve\">").append(esc(cell)).append("</w:t></w:r></w:p></w:tc>")
                }
                body.append("</w:tr>")
                for (row in b.rows) {
                    body.append("<w:tr>")
                    for (cIdx in b.header.indices) {
                        body.append("<w:tc><w:tcPr><w:tcW w:w=\"0\" w:type=\"auto\"/></w:tcPr>")
                        body.append("<w:p><w:r><w:t xml:space=\"preserve\">")
                            .append(esc(row.getOrElse(cIdx) { "" }))
                            .append("</w:t></w:r></w:p></w:tc>")
                    }
                    body.append("</w:tr>")
                }
                body.append("</w:tbl>")
            }
            is Block.PageBreak -> body.append("<w:p><w:r><w:br w:type=\"${esc(b.kind)}\"/></w:r></w:p>")
        }
        body.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" w:header=\"709\" w:footer=\"709\" w:gutter=\"0\"/></w:sectPr>")

        val document = xml("w:document") { "<w:body>$body</w:body>" }
        val styles = xml("w:styles") {
            "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/>" +
                "<w:sz w:val=\"22\"/></w:rPr></w:rPrDefault></w:docDefaults>" +
                "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>" +
                "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/>" +
                "<w:pPr><w:outlineLvl w:val=\"0\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"32\"/></w:rPr></w:style>" +
                "<w:style w:type=\"paragraph\" w:styleId=\"Heading2\"><w:name w:val=\"heading 2\"/>" +
                "<w:pPr><w:outlineLvl w:val=\"1\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"26\"/></w:rPr></w:style>" +
                "<w:style w:type=\"character\" w:styleId=\"CodeChar\"><w:name w:val=\"Code Char\"/>" +
                "<w:rPr><w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/><w:sz w:val=\"20\"/></w:rPr></w:style>" +
                "<w:style w:type=\"table\" w:styleId=\"TableGrid\"><w:name w:val=\"Table Grid\"/></w:style>"
        }
        val numbering = xml("w:numbering") {
            "<w:abstractNum w:abstractNumId=\"0\"><w:multiLevelType w:val=\"hybridMultilevel\"/>" +
                (0..8).joinToString("") { lvl ->
                    "<w:lvl w:ilvl=\"$lvl\"><w:start w:val=\"1\"/><w:numFmt w:val=\"bullet\"/>" +
                        "<w:lvlText w:val=\"${if (lvl % 2 == 0) "\u2022" else "\u25E6"}\"/><w:lvlJc w:val=\"left\"/></w:lvl>"
                } + "</w:abstractNum><w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num>"
        }
        val footnotes = xml("w:footnotes") {
            "<w:footnote w:type=\"separator\" w:id=\"-1\"><w:p><w:r><w:separator/></w:r></w:p></w:footnote>" +
                doc.footnotes.entries.joinToString("") { (id, text) ->
                    "<w:footnote w:id=\"$id\"><w:p><w:r><w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r></w:p></w:footnote>"
                }
        }
        val comments = xml("w:comments") {
            doc.comments.entries.joinToString("") { (id, text) ->
                "<w:comment w:id=\"$id\" w:author=\"${esc(doc.author)}\" w:date=\"2026-10-29T00:00:00Z\">" +
                    "<w:p><w:r><w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r></w:p></w:comment>"
            }
        }
        val header = xml("w:hdr") { "<w:p><w:r><w:t xml:space=\"preserve\">${esc(doc.title)}</w:t></w:r></w:p>" }
        val footer = xml("w:ftr") { "<w:p><w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText xml:space=\"preserve\"> PAGE </w:instrText></w:r><w:r><w:fldChar w:fldCharType=\"end\"/></w:r></w:p>" }

        val entries = ArrayList<Pair<String, ByteArray>>()
        entries += "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="$CT_NS"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="$DOCX_MAIN_CT"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/><Override PartName="/word/footnotes.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml"/><Override PartName="/word/comments.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml"/><Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/><Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/></Types>""".toByteArray()
        entries += "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="$PKG_REL_NS"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/></Relationships>""".toByteArray()
        entries += "word/_rels/document.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="$PKG_REL_NS"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footnotes" Target="footnotes.xml"/><Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments" Target="comments.xml"/><Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/><Relationship Id="rId6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/></Relationships>""".toByteArray()
        entries += "docProps/core.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/"><dc:title>${esc(doc.title)}</dc:title><dc:creator>${esc(doc.author)}</dc:creator></cp:coreProperties>""".toByteArray()
        entries += "word/document.xml" to document.toByteArray()
        entries += "word/styles.xml" to styles.toByteArray()
        entries += "word/numbering.xml" to numbering.toByteArray()
        entries += "word/footnotes.xml" to footnotes.toByteArray()
        entries += "word/comments.xml" to comments.toByteArray()
        entries += "word/header1.xml" to header.toByteArray()
        entries += "word/footer1.xml" to footer.toByteArray()

        return zip(entries)
    }

    private fun xml(root: String, body: () -> String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<$root xmlns:w=\"$W_NS\" xmlns:r=\"$R_NS\">${body()}</$root>"

    fun zip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for ((name, data) in entries) {
                val e = ZipEntry(name)
                e.time = 315_532_800_000L // 1980-01-01T00:00:00Z deterministic
                e.method = ZipEntry.DEFLATED
                zos.putNextEntry(e)
                zos.write(data)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    /** Specialised OASIS ODF 1.3 Part 2 §2.2.1 writer: `mimetype` FIRST, STORED, uncompressed, no extra. */
    fun zipWithStoredMimetypeFirst(entries: List<Pair<String, ByteArray>>, mimetype: String): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            val mime = mimetype.toByteArray(Charsets.US_ASCII)
            val mimeEntry = ZipEntry("mimetype")
            mimeEntry.time = 315_532_800_000L
            mimeEntry.method = ZipEntry.STORED
            mimeEntry.size = mime.size.toLong()
            mimeEntry.compressedSize = mime.size.toLong()
            val crc = CRC32().apply { update(mime) }
            mimeEntry.crc = crc.value
            zos.putNextEntry(mimeEntry)
            zos.write(mime)
            zos.closeEntry()
            for ((name, data) in entries) {
                val e = ZipEntry(name)
                e.time = 315_532_800_000L
                e.method = ZipEntry.DEFLATED
                zos.putNextEntry(e)
                zos.write(data)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    data class RawEntry(val name: String, val method: Int, val size: Int, val compressedSize: Int, val bytes: ByteArray)

    fun readEntries(docx: ByteArray): List<RawEntry> {
        val out = ArrayList<RawEntry>()
        ZipInputStream(docx.inputStream()).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                val bytes = zis.readBytes()
                out.add(RawEntry(e.name, e.method, bytes.size, e.compressedSize.toInt(), bytes))
            }
        }
        return out
    }

    private fun stripTags(xml: String): String {
        val sb = StringBuilder()
        var i = 0
        var inTag = false
        while (i < xml.length) {
            val c = xml[i]
            when {
                c == '<' -> inTag = true
                c == '>' -> inTag = false
                !inTag -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun unesc(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")

    fun parseDocument(bytes: ByteArray): ParsedDocument {
        val entries = readEntries(bytes)
        val byName = entries.associateBy { it.name }
        val documentXml = byName["word/document.xml"]?.bytes?.toString(Charsets.UTF_8) ?: error("missing word/document.xml")
        val core = byName["docProps/core.xml"]?.bytes?.toString(Charsets.UTF_8).orEmpty()
        val title = Regex("<dc:title>(.*?)</dc:title>").find(core)?.groupValues?.get(1)?.let(::unesc).orEmpty()

        val paragraphs = ArrayList<String>()
        for (pMatch in Regex("<w:p[ >].*?</w:p>", RegexOption.DOT_MATCHES_ALL).findAll(documentXml)) {
            val pXml = pMatch.value
            if (pXml.contains("<w:tbl>")) continue
            val text = Regex("<w:t(?:\\s[^>]*)?>(.*?)</w:t>").findAll(pXml)
                .joinToString("") { unesc(it.groupValues[1]) }
            if (text.isNotEmpty()) paragraphs.add(text)
            else if (pXml.contains("<w:br ")) paragraphs.add("\u0000PAGEBREAK")
        }
        val tableCells = ArrayList<List<String>>()
        for (tbl in Regex("<w:tbl>.*?</w:tbl>", RegexOption.DOT_MATCHES_ALL).findAll(documentXml)) {
            for (tr in Regex("<w:tr[ >].*?</w:tr>", RegexOption.DOT_MATCHES_ALL).findAll(tbl.value)) {
                val cells = Regex("<w:tc>.*?</w:tc>", RegexOption.DOT_MATCHES_ALL).findAll(tr.value).map { tc ->
                    Regex("<w:t(?:\\s[^>]*)?>(.*?)</w:t>").findAll(tc.value).joinToString("") { unesc(it.groupValues[1]) }
                }.toList()
                if (cells.isNotEmpty()) tableCells.add(cells)
            }
        }
        val styles = Regex("<w:style [^>]*w:styleId=\"([^\"]+)\"").findAll(
            byName["word/styles.xml"]?.bytes?.toString(Charsets.UTF_8).orEmpty(),
        ).map { it.groupValues[1] }.toList()
        val numberingXml = byName["word/numbering.xml"]?.bytes?.toString(Charsets.UTF_8).orEmpty()
        val footnotesXml = byName["word/footnotes.xml"]?.bytes?.toString(Charsets.UTF_8).orEmpty()
        val commentsXml = byName["word/comments.xml"]?.bytes?.toString(Charsets.UTF_8).orEmpty()
        return ParsedDocument(
            title = title,
            paragraphs = paragraphs,
            styles = styles,
            numberingCount = Regex("<w:num w:numId=").findAll(numberingXml).count(),
            footnoteCount = Regex("<w:footnote\\b[^>]*w:id=\"-?\\d+\"").findAll(footnotesXml).count(),
            commentCount = Regex("<w:comment\\b[^>]*w:id=").findAll(commentsXml).count(),
            tableCells = tableCells,
            entries = entries.map { it.name }.sorted(),
        )
    }

    /**
     * RTF 1.9.1 bidirectional codec. Backslash handling is expressed through `BS`/`BS_S` constants rather
     * than nested escaping, so the wire format is auditable byte-for-byte.
     */
    object Rtf {
        private val BS: Char = 92.toChar()
        private val BS_S: String = 92.toChar().toString()

        private fun cw(sb: StringBuilder, word: String) {
            sb.append(BS).append(word)
        }

        fun encode(paragraphs: List<String>): ByteArray {
            val sb = StringBuilder()
            sb.append('{')
            cw(sb, "rtf1"); cw(sb, "ansi"); cw(sb, "deff0")
            sb.append('{'); cw(sb, "fonttbl"); sb.append('{'); cw(sb, "f0 Calibri;"); sb.append('}'); sb.append('}')
            cw(sb, "uc1 ")
            for (p in paragraphs) {
                for (ch in p) {
                    when {
                        ch == BS || ch == '{' || ch == '}' -> { sb.append(BS); sb.append(ch) }
                        ch.code in 32..126 -> sb.append(ch)
                        ch.code <= 0xFFFF -> { cw(sb, "u"); sb.append(ch.code.toShort().toInt()); sb.append('?') }
                        else -> {
                            for (cu in Character.toChars(ch.code)) {
                                cw(sb, "u"); sb.append(cu.code.toShort().toInt()); sb.append('?')
                            }
                        }
                    }
                }
                cw(sb, "par")
                sb.append('\n')
            }
            sb.append('}')
            return sb.toString().toByteArray(Charsets.US_ASCII)
        }

        fun decode(rtf: ByteArray): List<String> {
            val src = rtf.toString(Charsets.US_ASCII)
            val paragraphs = ArrayList<String>()
            val cur = StringBuilder()
            val pending = ArrayList<Int>()
            val headerGroups = listOf("fonttbl", "colortbl", "stylesheet", "info", "*")

            fun flushPending() {
                var k = 0
                while (k < pending.size) {
                    val u = pending[k]
                    if (u in 0xD800..0xDBFF && k + 1 < pending.size && pending[k + 1] in 0xDC00..0xDFFF) {
                        cur.append(Character.toChars(Character.toCodePoint(u.toChar(), pending[k + 1].toChar())))
                        k += 2
                    } else {
                        cur.append(u.toChar())
                        k++
                    }
                }
                pending.clear()
            }

            fun appendLiteral(c: Char) {
                flushPending()
                cur.append(c)
            }

            var i = 0
            while (i < src.length) {
                val c = src[i]
                when {
                    c == '{' -> {
                        val lookahead = src.substring(i + 1, minOf(src.length, i + 24))
                        val skip = headerGroups.any { lookahead.startsWith(it) || lookahead.startsWith(BS_S + it) }
                        if (skip) {
                            var depth = 0
                            var j = i
                            while (j < src.length) {
                                if (src[j] == '{') depth++
                                if (src[j] == '}') {
                                    depth--
                                    if (depth == 0) { j++; break }
                                }
                                j++
                            }
                            i = j
                        } else {
                            i++
                        }
                    }
                    c == '}' -> i++
                    c == BS && i + 1 < src.length -> {
                        val nxt = src[i + 1]
                        when {
                            nxt == BS || nxt == '{' || nxt == '}' -> { appendLiteral(nxt); i += 2 }
                            nxt == '\'' -> {
                                if (i + 3 < src.length) {
                                    appendLiteral((src.substring(i + 2, i + 4).toIntOrNull(16) ?: 0x3F).toChar())
                                }
                                i += 4
                            }
                            nxt == 'u' && i + 2 < src.length && (src[i + 2].isDigit() || src[i + 2] == '-') -> {
                                var j = i + 2
                                val num = StringBuilder()
                                if (j < src.length && src[j] == '-') { num.append('-'); j++ }
                                while (j < src.length && src[j].isDigit()) { num.append(src[j]); j++ }
                                val code = num.toString().toIntOrNull() ?: 0
                                pending.add(if (code < 0) code + 65536 else code)
                                // Exactly one substitution character follows under `uc1`; drop it.
                                if (j < src.length && src[j] != BS && src[j] != '{' && src[j] != '}') j++
                                i = j
                            }
                            nxt.isLetter() -> {
                                var j = i + 1
                                val word = StringBuilder()
                                while (j < src.length && src[j].isLetter()) { word.append(src[j]); j++ }
                                while (j < src.length && (src[j] == '-' || src[j].isDigit())) j++
                                if (j < src.length && src[j] == ' ') j++
                                val w = word.toString()
                                if (w == "par" || w == "line") {
                                    flushPending()
                                    paragraphs.add(cur.toString())
                                    cur.setLength(0)
                                }
                                i = j
                            }
                            else -> i += 2
                        }
                    }
                    c == '\n' || c == '\r' -> i++
                    else -> { appendLiteral(c); i++ }
                }
            }
            flushPending()
            if (cur.isNotEmpty()) paragraphs.add(cur.toString())
            return paragraphs.filter { it.isNotEmpty() }
        }
    }
}
