// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Clean-room bounded WordprocessingML `.docx` OPC package writer & structural reader over [BoundedXml].
 *
 * Advances `:format:ooxml` beyond read-only plain-text scanning (`Ooxml.extractDocxText`) into
 * round-trippable `.docx` authoring and structural import:
 * - Deterministic zero-epoch (`1980-01-01T00:00:00`) stored-ZIP output.
 * - Blocks: [Block.Heading] (`1..6`), [Block.Paragraph], [Block.ListItem] (`ordered`/`bullet`,
 *   `level 0..8` wired to `word/numbering.xml`), [Block.BlockQuote], [Block.CodeBlock], [Block.Table].
 * - Inline runs: [Run] (`bold`, `italic`, `underline`, `strike`, `code`).
 * - Fail-closed reader: bounded ZIP byte caps, [BoundedXml] parsing, and explicit refusal of
 *   VBA `vbaProject.bin` macro parts (`.docm` smuggling).
 */
object DocxPackage {
    const val SCOPE =
        "Bounded WordprocessingML .docx writer + structural BoundedXml reader (headings 1..6, styled runs, 9-level bullet/ordered lists, quotes, code blocks, rectangular tables); VBA macros refused; drawings/comments/track-changes/footnotes out of scope."

    const val WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    const val MAX_BLOCKS = 4_096
    const val MAX_TABLE_COLS = 64
    const val MAX_ZIP_BYTES = 16 * 1024 * 1024
    const val MAX_PART_BYTES = 1024 * 1024
    private const val BULLET_NUM_ID = 1
    private const val ORDERED_NUM_ID = 2

    data class Run(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val code: Boolean = false,
    ) {
        init {
            validateXmlChars(text)
        }
    }

    sealed interface Block {
        data class Heading(val level: Int, val runs: List<Run>) : Block {
            init {
                require(level in 1..6) { "heading level $level outside 1..6" }
                require(runs.isNotEmpty()) { "heading runs must not be empty" }
            }
        }

        data class Paragraph(val runs: List<Run>) : Block

        data class ListItem(val ordered: Boolean, val level: Int = 0, val runs: List<Run>) : Block {
            init {
                require(level in 0..8) { "list level $level outside 0..8" }
            }
        }

        data class BlockQuote(val runs: List<Run>) : Block

        data class CodeBlock(val lines: List<String>) : Block {
            init {
                require(lines.isNotEmpty()) { "code block must contain at least 1 line" }
                lines.forEach(::validateXmlChars)
            }
        }

        data class Table(val rows: List<List<String>>) : Block {
            init {
                require(rows.isNotEmpty() && rows[0].isNotEmpty()) { "table must be non-empty" }
                val cols = rows[0].size
                require(cols <= MAX_TABLE_COLS) { "table columns $cols exceed $MAX_TABLE_COLS" }
                require(rows.all { it.size == cols }) { "table rows must be rectangular ($cols columns)" }
                rows.flatten().forEach(::validateXmlChars)
            }
        }
    }

    data class Document(val title: String = "", val blocks: List<Block>) {
        init {
            validateXmlChars(title)
            require(blocks.size <= MAX_BLOCKS) { "document blocks ${blocks.size} exceed $MAX_BLOCKS" }
        }
    }

    fun write(doc: Document): ByteArray {
        val parts = linkedMapOf(
            "[Content_Types].xml" to contentTypesXml(),
            "_rels/.rels" to rootRelsXml(),
            "word/_rels/document.xml.rels" to documentRelsXml(),
            "word/styles.xml" to stylesXml(),
            "word/numbering.xml" to defaultNumberingXml(),
            "word/document.xml" to buildDocumentXml(doc),
        )
        return storedZip(parts)
    }

    fun read(docxBytes: ByteArray): Document {
        require(docxBytes.size <= MAX_ZIP_BYTES) { "docx exceeds $MAX_ZIP_BYTES bytes" }
        val parts = readZipParts(docxBytes)
        require(parts.keys.none { it.endsWith("vbaProject.bin", ignoreCase = true) }) {
            "VBA macro part (vbaProject.bin) refused"
        }
        val docBytes = parts["word/document.xml"]
            ?: throw IllegalArgumentException("missing word/document.xml")
        val numberingDefs = parts["word/numbering.xml"]?.let(DocxNumbering::parseXmlBytes)
        return parseDocumentBlocks(docBytes, numberingDefs)
    }

    private fun buildDocumentXml(doc: Document): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:document xmlns:w="$WORD_NS"><w:body>""")
        for (b in doc.blocks) {
            when (b) {
                is Block.Heading -> {
                    append("""<w:p><w:pPr><w:pStyle w:val="Heading${b.level}"/></w:pPr>""")
                    appendRuns(b.runs)
                    append("</w:p>")
                }
                is Block.Paragraph -> {
                    append("<w:p>")
                    appendRuns(b.runs)
                    append("</w:p>")
                }
                is Block.ListItem -> {
                    val numId = if (b.ordered) ORDERED_NUM_ID else BULLET_NUM_ID
                    append("""<w:p><w:pPr><w:pStyle w:val="ListParagraph"/><w:numPr><w:ilvl w:val="${b.level}"/><w:numId w:val="$numId"/></w:numPr></w:pPr>""")
                    appendRuns(b.runs)
                    append("</w:p>")
                }
                is Block.BlockQuote -> {
                    append("""<w:p><w:pPr><w:pStyle w:val="Quote"/></w:pPr>""")
                    appendRuns(b.runs)
                    append("</w:p>")
                }
                is Block.CodeBlock -> {
                    append("""<w:p><w:pPr><w:pStyle w:val="CodeBlock"/></w:pPr><w:r><w:rPr><w:rFonts w:ascii="monospace" w:hAnsi="monospace"/></w:rPr>""")
                    for ((idx, line) in b.lines.withIndex()) {
                        if (idx > 0) append("<w:br/>")
                        append("""<w:t xml:space="preserve">${escapeXml(line)}</w:t>""")
                    }
                    append("</w:r></w:p>")
                }
                is Block.Table -> {
                    append("<w:tbl><w:tblPr/><w:tblGrid>")
                    repeat(b.rows[0].size) { append("<w:gridCol/>") }
                    append("</w:tblGrid>")
                    for (row in b.rows) {
                        append("<w:tr>")
                        for (cell in row) {
                            append("""<w:tc><w:p><w:r><w:t xml:space="preserve">${escapeXml(cell)}</w:t></w:r></w:p></w:tc>""")
                        }
                        append("</w:tr>")
                    }
                    append("</w:tbl>")
                }
            }
        }
        append("<w:sectPr/></w:body></w:document>")
    }

    private fun StringBuilder.appendRuns(runs: List<Run>) {
        for (r in runs) {
            if (r.text.isEmpty()) continue
            append("<w:r>")
            if (r.bold || r.italic || r.underline || r.strike || r.code) {
                append("<w:rPr>")
                if (r.bold) append("<w:b/>")
                if (r.italic) append("<w:i/>")
                if (r.underline) append("""<w:u w:val="single"/>""")
                if (r.strike) append("<w:strike/>")
                if (r.code) append("""<w:rFonts w:ascii="monospace" w:hAnsi="monospace"/>""")
                append("</w:rPr>")
            }
            append("""<w:t xml:space="preserve">${escapeXml(r.text)}</w:t></w:r>""")
        }
    }

    private fun parseDocumentBlocks(docBytes: ByteArray, numberingDefs: DocxNumbering.Definitions?): Document {
        val root = BoundedXml.parse(docBytes)
        require(root.name.local == "document") { "expected <w:document>, got <${root.name.local}>" }
        val body = root.children.firstOrNull { it.name.local == "body" }
            ?: throw IllegalArgumentException("missing <w:body>")
        val blocks = ArrayList<Block>()

        for (node in body.children) {
            when (node.name.local) {
                "p" -> {
                    require(blocks.size < MAX_BLOCKS) { "document exceeds $MAX_BLOCKS blocks" }
                    blocks.add(parseParagraph(node, numberingDefs))
                }
                "tbl" -> {
                    require(blocks.size < MAX_BLOCKS) { "document exceeds $MAX_BLOCKS blocks" }
                    parseTable(node)?.let(blocks::add)
                }
            }
        }
        return Document(blocks = blocks)
    }

    private fun parseParagraph(pNode: BoundedXml.Node, numberingDefs: DocxNumbering.Definitions?): Block {
        var pStyle: String? = null
        var numId: Int? = null
        var ilvl = 0
        val runs = ArrayList<Run>()

        for (child in pNode.children) {
            when (child.name.local) {
                "pPr" -> {
                    for (pr in child.children) {
                        when (pr.name.local) {
                            "pStyle" -> pStyle = wAttr(pr, "val")
                            "numPr" -> {
                                for (np in pr.children) {
                                    when (np.name.local) {
                                        "ilvl" -> ilvl = (wAttr(np, "val")?.toIntOrNull() ?: 0).coerceIn(0, 8)
                                        "numId" -> numId = wAttr(np, "val")?.toIntOrNull()
                                    }
                                }
                            }
                        }
                    }
                }
                "r" -> parseRun(child)?.let(runs::add)
            }
        }

        val style = pStyle ?: ""
        val headingLvl = style.removePrefix("Heading").toIntOrNull()
        return when {
            headingLvl != null && headingLvl in 1..6 -> Block.Heading(headingLvl, runs.ifEmpty { listOf(Run("")) })
            style == "CodeBlock" -> Block.CodeBlock(runs.joinToString("") { it.text }.split('\n'))
            style == "Quote" || style == "IntenseQuote" -> Block.BlockQuote(runs)
            numId != null && numId != 0 -> Block.ListItem(isOrderedNum(numberingDefs, numId!!, ilvl), ilvl, runs)
            else -> Block.Paragraph(runs)
        }
    }

    private fun parseRun(rNode: BoundedXml.Node): Run? {
        var bold = false
        var italic = false
        var underline = false
        var strike = false
        var code = false
        val sb = StringBuilder()

        for (child in rNode.children) {
            when (child.name.local) {
                "rPr" -> {
                    for (pr in child.children) {
                        when (pr.name.local) {
                            "b" -> bold = wBoolAttr(pr)
                            "i" -> italic = wBoolAttr(pr)
                            "u" -> underline = (wAttr(pr, "val") ?: "single") != "none"
                            "strike" -> strike = wBoolAttr(pr)
                            "rFonts" -> {
                                val ascii = wAttr(pr, "ascii")?.lowercase() ?: ""
                                if (ascii == "monospace" || "courier" in ascii || "consolas" in ascii) code = true
                            }
                        }
                    }
                }
                "t" -> sb.append(child.text)
                "br" -> sb.append('\n')
                "tab" -> sb.append('\t')
            }
        }
        return if (sb.isEmpty()) null else Run(sb.toString(), bold, italic, underline, strike, code)
    }

    private fun parseTable(tblNode: BoundedXml.Node): Block.Table? {
        val rows = ArrayList<List<String>>()
        for (tr in tblNode.children) {
            if (tr.name.local != "tr") continue
            val cells = ArrayList<String>()
            for (tc in tr.children) {
                if (tc.name.local != "tc") continue
                val pTexts = ArrayList<String>()
                for (p in tc.children) {
                    if (p.name.local != "p") continue
                    val pSb = StringBuilder()
                    for (r in p.children) {
                        if (r.name.local == "r") {
                            for (rc in r.children) {
                                when (rc.name.local) {
                                    "t" -> pSb.append(rc.text)
                                    "br" -> pSb.append('\n')
                                    "tab" -> pSb.append('\t')
                                }
                            }
                        }
                    }
                    pTexts.add(pSb.toString())
                }
                cells.add(pTexts.joinToString("\n"))
            }
            if (cells.isNotEmpty()) rows.add(cells)
        }
        if (rows.isEmpty()) return null
        val width = rows.maxOf { it.size }
        val normalized = rows.map { r -> if (r.size == width) r else r + List(width - r.size) { "" } }
        return Block.Table(normalized)
    }

    private fun wAttr(node: BoundedXml.Node, local: String): String? =
        node.attr(local, WORD_NS) ?: node.attr(local, "")

    private fun wBoolAttr(node: BoundedXml.Node): Boolean {
        val v = wAttr(node, "val") ?: return true
        return v != "0" && v != "false"
    }

    private fun isOrderedNum(defs: DocxNumbering.Definitions?, numId: Int, ilvl: Int): Boolean {
        if (defs != null && numId in defs.nums) {
            val fmt = defs.resolveLevel(numId, ilvl).numFmt
            return fmt != DocxNumbering.NumFormat.BULLET && fmt != DocxNumbering.NumFormat.NONE
        }
        return numId != BULLET_NUM_ID
    }

    private fun readZipParts(zipBytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        var totalExpanded = 0L
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entries = 0
            val buf = ByteArray(8192)
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                entries++
                require(entries <= 256) { "too many ZIP entries" }
                val name = entry.name
                require(name.isNotEmpty() && !name.startsWith("/") && '\\' !in name && ".." !in name.split('/')) {
                    "unsafe ZIP entry '$name'"
                }
                require(name !in out) { "duplicate ZIP entry '$name'" }
                if (!entry.isDirectory) {
                    val baos = ByteArrayOutputStream()
                    var partBytes = 0
                    while (true) {
                        val n = zis.read(buf)
                        if (n < 0) break
                        partBytes += n
                        totalExpanded += n
                        require(partBytes <= MAX_PART_BYTES && totalExpanded <= MAX_ZIP_BYTES) {
                            "expanded ZIP budget exceeded on '$name'"
                        }
                        baos.write(buf, 0, n)
                    }
                    out[name] = baos.toByteArray()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return out
    }

    private fun storedZip(entries: Map<String, String>): ByteArray {
        val body = ByteArrayOutputStream()
        val cd = ByteArrayOutputStream()
        var count = 0
        for ((name, content) in entries) {
            val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
            val data = content.toByteArray(StandardCharsets.UTF_8)
            val crc = CRC32().apply { update(data) }.value.toInt()
            val offset = body.size()
            body.write(leHeader(30) {
                putInt(0x04034b50); putShort(20); putShort(0x0800); putShort(0)
                putShort(0); putShort(0x0021); putInt(crc)
                putInt(data.size); putInt(data.size); putShort(nameBytes.size.toShort()); putShort(0)
            })
            body.write(nameBytes)
            body.write(data)
            cd.write(leHeader(46) {
                putInt(0x02014b50); putShort(20); putShort(20); putShort(0x0800); putShort(0)
                putShort(0); putShort(0x0021); putInt(crc)
                putInt(data.size); putInt(data.size); putShort(nameBytes.size.toShort())
                putShort(0); putShort(0); putShort(0); putShort(0); putInt(0); putInt(offset)
            })
            cd.write(nameBytes)
            count++
        }
        val cdOffset = body.size()
        val cdBytes = cd.toByteArray()
        body.write(cdBytes)
        body.write(leHeader(22) {
            putInt(0x06054b50); putShort(0); putShort(0)
            putShort(count.toShort()); putShort(count.toShort())
            putInt(cdBytes.size); putInt(cdOffset); putShort(0)
        })
        return body.toByteArray()
    }

    private fun leHeader(size: Int, fill: ByteBuffer.() -> Unit): ByteArray =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(fill).array()

    private fun contentTypesXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            """<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""" +
            """<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>""" +
            """<Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>""" +
            """</Types>"""

    private fun rootRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>""" +
            """</Relationships>"""

    private fun documentRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
            """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>""" +
            """</Relationships>"""

    private fun stylesXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:styles xmlns:w="$WORD_NS">""")
        for (lvl in 1..6) {
            append("""<w:style w:type="paragraph" w:styleId="Heading$lvl"><w:name w:val="heading $lvl"/></w:style>""")
        }
        append("""<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/></w:style>""")
        append("""<w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/></w:style>""")
        append("""<w:style w:type="paragraph" w:styleId="CodeBlock"><w:name w:val="Code Block"/></w:style>""")
        append("""</w:styles>""")
    }

    private fun defaultNumberingXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:numbering xmlns:w="$WORD_NS">""")
        append("""<w:abstractNum w:abstractNumId="0">""")
        for (lvl in 0..8) {
            append("""<w:lvl w:ilvl="$lvl"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="&#x2022;"/></w:lvl>""")
        }
        append("""</w:abstractNum>""")
        append("""<w:abstractNum w:abstractNumId="1">""")
        for (lvl in 0..8) {
            val pat = (1..(lvl + 1)).joinToString(".") { "%$it" } + "."
            append("""<w:lvl w:ilvl="$lvl"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="$pat"/><w:lvlRestart w:val="$lvl"/></w:lvl>""")
        }
        append("""</w:abstractNum>""")
        append("""<w:num w:numId="$BULLET_NUM_ID"><w:abstractNumId w:val="0"/></w:num>""")
        append("""<w:num w:numId="$ORDERED_NUM_ID"><w:abstractNumId w:val="1"/></w:num>""")
        append("""</w:numbering>""")
    }

    private fun validateXmlChars(s: String) {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isHighSurrogate()) {
                require(i + 1 < s.length && s[i + 1].isLowSurrogate()) { "unpaired high surrogate" }
                i += 2
                continue
            }
            require(!c.isLowSurrogate()) { "unpaired low surrogate" }
            val cp = c.code
            val ok = cp == 0x09 || cp == 0x0A || cp == 0x0D || cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD
            require(ok) { "XML 1.0 illegal control char U+${cp.toString(16).uppercase()}" }
            i++
        }
    }

    private fun escapeXml(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}
