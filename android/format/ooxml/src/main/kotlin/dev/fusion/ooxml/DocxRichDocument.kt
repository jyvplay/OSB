// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Clean-room ECMA-376 WordprocessingML rich document package (`DocxRichDocument`).
 *
 * Advances `:format:ooxml` beyond [DocxPackage] by closing the scholarly/editorial `.docx` subset:
 * - Headers (`word/header1.xml`) and footers (`word/footer1.xml`) bound via `<w:sectPr>` relationships.
 * - Footnotes (`word/footnotes.xml` + `<w:footnoteReference w:id="..."/>`).
 * - Comments (`word/comments.xml` + `<w:commentReference w:id="..."/>`).
 * - Track-changes revisions (`<w:ins>` / `<w:del>` with `w:id` and `w:author`, privacy-scrubbable).
 * - Auto-generated `<w:sdt>` Table of Contents outline from Heading 1..6 blocks.
 * - Security & privacy invariants: zero-epoch (`1980-01-01T00:00:00`) ZIP entries, no `docProps/core.xml`
 *   author leak unless explicitly opted in, VBA `vbaProject.bin` macro refusal, and `TargetMode="External"`
 *   remote template-injection refusal (`CVE-2017-0199` / `Follina`-class external `.rels` defense).
 */
object DocxRichDocument {
    const val SCOPE =
        "Scholarly WordprocessingML .docx writer + reader (headers/footers, footnotes.xml, comments.xml, w:ins/w:del track-changes, auto-TOC, External-rels & VBA refusal); OLE ActiveX and DrawingML 3D scenes out of scope."

    const val WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
    const val MAX_ZIP_BYTES = 16 * 1024 * 1024
    const val MAX_PART_BYTES = 2 * 1024 * 1024

    enum class RevisionKind { INSERT, DELETE }

    data class Footnote(val id: Int, val text: String) {
        init {
            require(id >= 1) { "Footnote id must be >= 1" }
            require(text.isNotBlank()) { "Footnote text must not be blank" }
        }
    }

    data class Comment(val id: Int, val author: String, val text: String) {
        init {
            require(id >= 1) { "Comment id must be >= 1" }
            require(author.isNotBlank()) { "Comment author must not be blank" }
        }
    }

    sealed interface RichBlock {
        data class Heading(val level: Int, val text: String) : RichBlock {
            init {
                require(level in 1..6) { "Heading level must be in 1..6: $level" }
            }
        }

        data class Paragraph(
            val text: String,
            val footnoteId: Int? = null,
            val commentId: Int? = null,
            val bold: Boolean = false,
            val italic: Boolean = false,
        ) : RichBlock

        data class Revision(
            val id: Int,
            val author: String,
            val kind: RevisionKind,
            val text: String,
        ) : RichBlock

        data class Table(
            val headers: List<String>,
            val rows: List<List<String>>,
        ) : RichBlock {
            init {
                require(headers.isNotEmpty()) { "Table headers must not be empty" }
                rows.forEach { require(it.size == headers.size) { "Row width mismatch" } }
            }
        }

        data class TableOfContents(val title: String = "Contents", val maxLevel: Int = 3) : RichBlock {
            init {
                require(maxLevel in 1..6) { "maxLevel must be in 1..6" }
            }
        }
    }

    data class RichDoc(
        val blocks: List<RichBlock>,
        val headerText: String? = null,
        val footerText: String? = null,
        val footnotes: List<Footnote> = emptyList(),
        val comments: List<Comment> = emptyList(),
    ) {
        fun acceptAllRevisions(): RichDoc {
            val cleaned = blocks.mapNotNull { blk ->
                when (blk) {
                    is RichBlock.Revision ->
                        if (blk.kind == RevisionKind.INSERT) RichBlock.Paragraph(blk.text) else null
                    else -> blk
                }
            }
            return copy(blocks = cleaned)
        }

        fun stripEditorialMetadata(): RichDoc =
            acceptAllRevisions().copy(comments = emptyList())

        fun tocEntries(maxLevel: Int = 3): List<Pair<Int, String>> =
            blocks.filterIsInstance<RichBlock.Heading>()
                .filter { it.level <= maxLevel }
                .map { it.level to it.text }
    }

    fun write(doc: RichDoc): ByteArray {
        val parts = linkedMapOf<String, ByteArray>()
        val hasHeader = !doc.headerText.isNullOrBlank()
        val hasFooter = !doc.footerText.isNullOrBlank()
        val hasFootnotes = doc.footnotes.isNotEmpty()
        val hasComments = doc.comments.isNotEmpty()

        parts["[Content_Types].xml"] = buildContentTypes(hasHeader, hasFooter, hasFootnotes, hasComments)
        parts["_rels/.rels"] = buildRootRels()
        parts["word/_rels/document.xml.rels"] = buildDocRels(hasHeader, hasFooter, hasFootnotes, hasComments)
        parts["word/styles.xml"] = buildStyles()
        if (hasHeader) parts["word/header1.xml"] = buildHdrFtr("hdr", doc.headerText ?: "")
        if (hasFooter) parts["word/footer1.xml"] = buildHdrFtr("ftr", doc.footerText ?: "")
        if (hasFootnotes) parts["word/footnotes.xml"] = buildFootnotes(doc.footnotes)
        if (hasComments) parts["word/comments.xml"] = buildComments(doc.comments)
        parts["word/document.xml"] = buildDocumentXml(doc, hasHeader, hasFooter)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos, StandardCharsets.UTF_8).use { zos ->
            for ((name, bytes) in parts) {
                val crc = CRC32().apply { update(bytes) }.value
                val entry = ZipEntry(name).apply {
                    time = 315532800000L // 1980-01-01T00:00:00Z zero-epoch privacy pin
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    this.crc = crc
                }
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    fun read(bytes: ByteArray): RichDoc {
        require(bytes.size <= MAX_ZIP_BYTES) { "DOCX exceeds MAX_ZIP_BYTES ($MAX_ZIP_BYTES)" }
        val entries = linkedMapOf<String, ByteArray>()
        var totalOut = 0
        ZipInputStream(ByteArrayInputStream(bytes), StandardCharsets.UTF_8).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name
                require(!name.contains("..") && !name.startsWith("/")) { "Unsafe DOCX entry path: $name" }
                require(!name.endsWith("vbaProject.bin", ignoreCase = true)) {
                    "Refusing macro-enabled Word package entry: $name"
                }
                val buf = ByteArrayOutputStream()
                val tmp = ByteArray(4096)
                while (true) {
                    val n = zis.read(tmp)
                    if (n < 0) break
                    totalOut += n
                    require(buf.size() + n <= MAX_PART_BYTES && totalOut <= MAX_ZIP_BYTES) {
                        "DOCX XML decompression exceeds bounded cap"
                    }
                    buf.write(tmp, 0, n)
                }
                entries[name] = buf.toByteArray()
            }
        }

        // Security gate: inspect every .rels part and refuse TargetMode="External" template/OLE fetches.
        for ((name, data) in entries) {
            if (name.endsWith(".rels")) {
                val relRoot = BoundedXml.parse(data)
                for (rel in relRoot.children.filter { it.localName == "Relationship" }) {
                    val mode = rel.attr("TargetMode") ?: ""
                    val type = rel.attr("Type") ?: ""
                    if (mode.equals("External", ignoreCase = true) &&
                        !type.endsWith("/hyperlink")
                    ) {
                        throw IllegalArgumentException("Refusing external non-hyperlink relationship in $name: $type")
                    }
                }
            }
        }

        val docBytes = entries["word/document.xml"]
            ?: throw IllegalArgumentException("Missing word/document.xml")
        val docRoot = BoundedXml.parse(docBytes)
        val body = docRoot.firstChild("body")
            ?: throw IllegalArgumentException("Missing w:body in word/document.xml")

        val blocks = mutableListOf<RichBlock>()
        for (child in body.children) {
            when (child.localName) {
                "sdt" -> {
                    val content = child.firstChild("sdtContent")
                    val firstP = content?.firstChild("p")
                    val title = if (firstP != null) extractPlainText(firstP) else "Contents"
                    blocks.add(RichBlock.TableOfContents(title = title.ifBlank { "Contents" }))
                }
                "tbl" -> {
                    val trs = child.children.filter { it.localName == "tr" }
                    if (trs.isNotEmpty()) {
                        val parsedRows = trs.map { tr ->
                            tr.children.filter { it.localName == "tc" }.map { tc ->
                                tc.children.filter { it.localName == "p" }
                                    .joinToString("\n") { extractPlainText(it) }
                            }
                        }
                        blocks.add(RichBlock.Table(parsedRows.first(), parsedRows.drop(1)))
                    }
                }
                "p" -> parseParagraphOrRevision(child)?.let { blocks.add(it) }
            }
        }

        val headerText = entries["word/header1.xml"]?.let { extractHdrFtrText(BoundedXml.parse(it)) }
        val footerText = entries["word/footer1.xml"]?.let { extractHdrFtrText(BoundedXml.parse(it)) }
        val footnotes = entries["word/footnotes.xml"]?.let { parseFootnotes(BoundedXml.parse(it)) } ?: emptyList()
        val comments = entries["word/comments.xml"]?.let { parseComments(BoundedXml.parse(it)) } ?: emptyList()

        return RichDoc(
            blocks = blocks,
            headerText = headerText,
            footerText = footerText,
            footnotes = footnotes,
            comments = comments,
        )
    }

    private val BoundedXml.Node.localName: String get() = name.local
    private fun BoundedXml.Node.firstChild(local: String): BoundedXml.Node? =
        children.firstOrNull { it.name.local == local }
    private fun BoundedXml.Node.anyAttr(local: String): String? =
        attributes.entries.firstOrNull { it.key.local == local }?.value

    private fun parseParagraphOrRevision(p: BoundedXml.Node): RichBlock? {
        val pPr = p.firstChild("pPr")
        val pStyle = pPr?.firstChild("pStyle")?.anyAttr("val") ?: ""
        if (pStyle.startsWith("Heading") && pStyle.length == 8 && pStyle[7].isDigit()) {
            val lvl = pStyle[7].digitToInt()
            if (lvl in 1..6) return RichBlock.Heading(lvl, extractPlainText(p))
        }
        val ins = p.firstChild("ins")
        if (ins != null) {
            val id = ins.anyAttr("id")?.toIntOrNull() ?: 1
            val author = ins.anyAttr("author") ?: "Unknown"
            return RichBlock.Revision(id, author, RevisionKind.INSERT, extractPlainText(ins))
        }
        val del = p.firstChild("del")
        if (del != null) {
            val id = del.anyAttr("id")?.toIntOrNull() ?: 1
            val author = del.anyAttr("author") ?: "Unknown"
            val delText = del.children.filter { it.localName == "r" }.joinToString("") { r ->
                r.children.filter { it.localName == "delText" || it.localName == "t" }
                    .joinToString("") { it.text }
            }
            return RichBlock.Revision(id, author, RevisionKind.DELETE, delText)
        }
        var fnId: Int? = null
        var cmId: Int? = null
        var bold = false
        var italic = false
        val sb = StringBuilder()
        for (r in p.children.filter { it.localName == "r" }) {
            val rPr = r.firstChild("rPr")
            if (rPr?.firstChild("b") != null) bold = true
            if (rPr?.firstChild("i") != null) italic = true
            for (rc in r.children) {
                when (rc.localName) {
                    "t" -> sb.append(rc.text)
                    "footnoteReference" -> fnId = rc.anyAttr("id")?.toIntOrNull()
                    "commentReference" -> cmId = rc.anyAttr("id")?.toIntOrNull()
                }
            }
        }
        return RichBlock.Paragraph(sb.toString(), footnoteId = fnId, commentId = cmId, bold = bold, italic = italic)
    }

    private fun extractPlainText(elem: BoundedXml.Node): String {
        val sb = StringBuilder()
        fun walk(e: BoundedXml.Node) {
            if (e.localName == "t" || e.localName == "delText") sb.append(e.text)
            for (c in e.children) walk(c)
        }
        walk(elem)
        return sb.toString()
    }

    private fun extractHdrFtrText(root: BoundedXml.Node): String =
        root.children.filter { it.localName == "p" }.joinToString("\n") { extractPlainText(it) }

    private fun parseFootnotes(root: BoundedXml.Node): List<Footnote> =
        root.children.filter { it.localName == "footnote" }.mapNotNull { fn ->
            val id = fn.anyAttr("id")?.toIntOrNull() ?: return@mapNotNull null
            if (id < 1) return@mapNotNull null
            val txt = extractPlainText(fn)
            if (txt.isBlank()) null else Footnote(id, txt)
        }

    private fun parseComments(root: BoundedXml.Node): List<Comment> =
        root.children.filter { it.localName == "comment" }.mapNotNull { cm ->
            val id = cm.anyAttr("id")?.toIntOrNull() ?: return@mapNotNull null
            val author = cm.anyAttr("author") ?: "Reviewer"
            Comment(id, author, extractPlainText(cm))
        }

    private fun buildContentTypes(
        hasHeader: Boolean,
        hasFooter: Boolean,
        hasFootnotes: Boolean,
        hasComments: Boolean,
    ): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""")
        append("""<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>""")
        if (hasHeader) append("""<Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/>""")
        if (hasFooter) append("""<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>""")
        if (hasFootnotes) append("""<Override PartName="/word/footnotes.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml"/>""")
        if (hasComments) append("""<Override PartName="/word/comments.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml"/>""")
        append("</Types>")
    }.toByteArray(StandardCharsets.UTF_8)

    private fun buildRootRels(): ByteArray =
        ("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="$PKG_REL_NS">""" +
            """<Relationship Id="rId1" Type="$REL_NS/officeDocument" Target="word/document.xml"/>""" +
            """</Relationships>""").toByteArray(StandardCharsets.UTF_8)

    private fun buildDocRels(
        hasHeader: Boolean,
        hasFooter: Boolean,
        hasFootnotes: Boolean,
        hasComments: Boolean,
    ): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="$PKG_REL_NS">""")
        append("""<Relationship Id="rStyles" Type="$REL_NS/styles" Target="styles.xml"/>""")
        if (hasHeader) append("""<Relationship Id="rHdr1" Type="$REL_NS/header" Target="header1.xml"/>""")
        if (hasFooter) append("""<Relationship Id="rFtr1" Type="$REL_NS/footer" Target="footer1.xml"/>""")
        if (hasFootnotes) append("""<Relationship Id="rFootnotes" Type="$REL_NS/footnotes" Target="footnotes.xml"/>""")
        if (hasComments) append("""<Relationship Id="rComments" Type="$REL_NS/comments" Target="comments.xml"/>""")
        append("</Relationships>")
    }.toByteArray(StandardCharsets.UTF_8)

    private fun buildStyles(): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:styles xmlns:w="$WORD_NS">""")
        append("""<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>""")
        for (lvl in 1..6) {
            val sz = (36 - (lvl - 1) * 4).coerceAtLeast(20)
            append("""<w:style w:type="paragraph" w:styleId="Heading$lvl"><w:name w:val="heading $lvl"/>""")
            append("""<w:pPr><w:outlineLvl w:val="${lvl - 1}"/></w:pPr>""")
            append("""<w:rPr><w:b/><w:sz w:val="$sz"/></w:rPr></w:style>""")
        }
        append("</w:styles>")
    }.toByteArray(StandardCharsets.UTF_8)

    private fun buildHdrFtr(tag: String, text: String): ByteArray =
        ("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<w:$tag xmlns:w="$WORD_NS"><w:p><w:r><w:t xml:space="preserve">${esc(text)}</w:t></w:r></w:p></w:$tag>""")
            .toByteArray(StandardCharsets.UTF_8)

    private fun buildFootnotes(footnotes: List<Footnote>): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:footnotes xmlns:w="$WORD_NS">""")
        for (fn in footnotes) {
            append("""<w:footnote w:id="${fn.id}"><w:p><w:r><w:t xml:space="preserve">${esc(fn.text)}</w:t></w:r></w:p></w:footnote>""")
        }
        append("</w:footnotes>")
    }.toByteArray(StandardCharsets.UTF_8)

    private fun buildComments(comments: List<Comment>): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:comments xmlns:w="$WORD_NS">""")
        for (cm in comments) {
            append("""<w:comment w:id="${cm.id}" w:author="${esc(cm.author)}"><w:p><w:r><w:t xml:space="preserve">${esc(cm.text)}</w:t></w:r></w:p></w:comment>""")
        }
        append("</w:comments>")
    }.toByteArray(StandardCharsets.UTF_8)

    private fun buildDocumentXml(doc: RichDoc, hasHeader: Boolean, hasFooter: Boolean): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:document xmlns:w="$WORD_NS" xmlns:r="$REL_NS"><w:body>""")
        for (blk in doc.blocks) {
            when (blk) {
                is RichBlock.Heading -> {
                    append("""<w:p><w:pPr><w:pStyle w:val="Heading${blk.level}"/></w:pPr>""")
                    append("""<w:r><w:t xml:space="preserve">${esc(blk.text)}</w:t></w:r></w:p>""")
                }
                is RichBlock.Paragraph -> {
                    append("<w:p><w:r>")
                    if (blk.bold || blk.italic) {
                        append("<w:rPr>")
                        if (blk.bold) append("<w:b/>")
                        if (blk.italic) append("<w:i/>")
                        append("</w:rPr>")
                    }
                    append("""<w:t xml:space="preserve">${esc(blk.text)}</w:t>""")
                    if (blk.footnoteId != null) append("""<w:footnoteReference w:id="${blk.footnoteId}"/>""")
                    if (blk.commentId != null) append("""<w:commentReference w:id="${blk.commentId}"/>""")
                    append("</w:r></w:p>")
                }
                is RichBlock.Revision -> {
                    append("<w:p>")
                    if (blk.kind == RevisionKind.INSERT) {
                        append("""<w:ins w:id="${blk.id}" w:author="${esc(blk.author)}"><w:r><w:t xml:space="preserve">${esc(blk.text)}</w:t></w:r></w:ins>""")
                    } else {
                        append("""<w:del w:id="${blk.id}" w:author="${esc(blk.author)}"><w:r><w:delText xml:space="preserve">${esc(blk.text)}</w:delText></w:r></w:del>""")
                    }
                    append("</w:p>")
                }
                is RichBlock.Table -> {
                    append("<w:tbl>")
                    for (row in listOf(blk.headers) + blk.rows) {
                        append("<w:tr>")
                        for (cell in row) {
                            append("""<w:tc><w:p><w:r><w:t xml:space="preserve">${esc(cell)}</w:t></w:r></w:p></w:tc>""")
                        }
                        append("</w:tr>")
                    }
                    append("</w:tbl>")
                }
                is RichBlock.TableOfContents -> {
                    append("<w:sdt><w:sdtContent>")
                    append("""<w:p><w:r><w:rPr><w:b/></w:rPr><w:t xml:space="preserve">${esc(blk.title)}</w:t></w:r></w:p>""")
                    for ((lvl, title) in doc.tocEntries(blk.maxLevel)) {
                        val indent = "  ".repeat((lvl - 1).coerceAtLeast(0))
                        append("""<w:p><w:r><w:t xml:space="preserve">${esc("$indent$title")}</w:t></w:r></w:p>""")
                    }
                    append("</w:sdtContent></w:sdt>")
                }
            }
        }
        append("<w:sectPr>")
        if (hasHeader) append("""<w:headerReference w:type="default" r:id="rHdr1"/>""")
        if (hasFooter) append("""<w:footerReference w:type="default" r:id="rFtr1"/>""")
        append("""<w:pgSz w:w="12240" w:h="15840"/>""")
        append("</w:sectPr></w:body></w:document>")
    }.toByteArray(StandardCharsets.UTF_8)

    private fun esc(s: String): String = buildString(s.length) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    // --- R51 Multi-Section Page Setup, Colspan/Shaded Tables & Bookmark Cross-References ---

    enum class PageOrientation { PORTRAIT, LANDSCAPE }

    data class SectionSetup(
        val orientation: PageOrientation = PageOrientation.PORTRAIT,
        val widthTwips: Int = if (orientation == PageOrientation.LANDSCAPE) 15_840 else 12_240,
        val heightTwips: Int = if (orientation == PageOrientation.LANDSCAPE) 12_240 else 15_840,
        val marginTwips: Int = 1_440,
        val columns: Int = 1,
    ) {
        init {
            require(widthTwips in 2_880..31_680 && heightTwips in 2_880..31_680) { "page twips out of range" }
            require(marginTwips in 360..4_320) { "margin twips out of range" }
            require(columns in 1..4) { "columns must be in 1..4: $columns" }
        }
    }

    data class SpannedCell(
        val text: String,
        val gridSpan: Int = 1,
        val fillHex: String? = null,
        val bold: Boolean = false,
    ) {
        init {
            require(gridSpan in 1..12) { "gridSpan must be in 1..12" }
            require(fillHex == null || Regex("^[0-9A-Fa-f]{6}$").matches(fillHex)) { "invalid 6-hex fill: $fillHex" }
        }
    }

    data class SectionPart(
        val setup: SectionSetup,
        val blocks: List<RichBlock>,
        val spannedTables: List<List<List<SpannedCell>>> = emptyList(),
        val bookmarks: List<Pair<String, String>> = emptyList(), // (bookmarkName, headingOrCaptionText)
        val crossRefs: List<Pair<String, String>> = emptyList(), // (targetBookmarkName, visibleLabel)
    ) {
        init {
            for ((bm, _) in bookmarks) {
                require(Regex("^[A-Za-z_][A-Za-z0-9_]{0,39}$").matches(bm)) { "invalid bookmark identifier '$bm'" }
            }
        }
    }

    data class MultiSectionInspection(
        val sectionCount: Int,
        val orientations: List<PageOrientation>,
        val columnCounts: List<Int>,
        val bookmarkNames: List<String>,
        val crossRefAnchors: List<String>,
        val maxGridSpan: Int,
        val shadedCellFills: List<String>,
    )

    fun writeMultiSection(
        sections: List<SectionPart>,
        headerText: String? = null,
        footerText: String? = null,
    ): ByteArray {
        require(sections.isNotEmpty()) { "writeMultiSection requires >= 1 section" }
        val declaredBookmarks = sections.flatMap { s -> s.bookmarks.map { it.first } }.toSet()
        for (sec in sections) {
            for ((target, _) in sec.crossRefs) {
                require(target in declaredBookmarks) { "dangling cross-reference to undeclared bookmark '$target'" }
            }
        }
        val hasHeader = !headerText.isNullOrBlank()
        val hasFooter = !footerText.isNullOrBlank()
        var bmIdSeq = 1
        val docXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<w:document xmlns:w="$WORD_NS" xmlns:r="$REL_NS"><w:body>""")
            for ((sIdx, sec) in sections.withIndex()) {
                for ((bmName, bmTitle) in sec.bookmarks) {
                    val bid = bmIdSeq++
                    append("""<w:p><w:bookmarkStart w:id="$bid" w:name="${esc(bmName)}"/>""")
                    append("""<w:r><w:rPr><w:b/></w:rPr><w:t xml:space="preserve">${esc(bmTitle)}</w:t></w:r>""")
                    append("""<w:bookmarkEnd w:id="$bid"/></w:p>""")
                }
                for (blk in sec.blocks) {
                    when (blk) {
                        is RichBlock.Heading -> append("""<w:p><w:pPr><w:pStyle w:val="Heading${blk.level}"/></w:pPr><w:r><w:t xml:space="preserve">${esc(blk.text)}</w:t></w:r></w:p>""")
                        is RichBlock.Paragraph -> append("""<w:p><w:r><w:t xml:space="preserve">${esc(blk.text)}</w:t></w:r></w:p>""")
                        is RichBlock.Revision -> append("""<w:p><w:ins w:id="${blk.id}" w:author="${esc(blk.author)}"><w:r><w:t xml:space="preserve">${esc(blk.text)}</w:t></w:r></w:ins></w:p>""")
                        is RichBlock.Table -> {
                            append("<w:tbl>")
                            for (row in listOf(blk.headers) + blk.rows) {
                                append("<w:tr>")
                                for (c in row) append("""<w:tc><w:p><w:r><w:t xml:space="preserve">${esc(c)}</w:t></w:r></w:p></w:tc>""")
                                append("</w:tr>")
                            }
                            append("</w:tbl>")
                        }
                        is RichBlock.TableOfContents -> append("""<w:sdt><w:sdtContent><w:p><w:r><w:t>${esc(blk.title)}</w:t></w:r></w:p></w:sdtContent></w:sdt>""")
                    }
                }
                for (tbl in sec.spannedTables) {
                    append("<w:tbl>")
                    for (row in tbl) {
                        append("<w:tr>")
                        for (cell in row) {
                            append("<w:tc><w:tcPr>")
                            if (cell.gridSpan > 1) append("""<w:gridSpan w:val="${cell.gridSpan}"/>""")
                            if (cell.fillHex != null) append("""<w:shd w:val="clear" w:color="auto" w:fill="${cell.fillHex.uppercase()}"/>""")
                            append("</w:tcPr><w:p><w:r>")
                            if (cell.bold) append("<w:rPr><w:b/></w:rPr>")
                            append("""<w:t xml:space="preserve">${esc(cell.text)}</w:t></w:r></w:p></w:tc>""")
                        }
                        append("</w:tr>")
                    }
                    append("</w:tbl>")
                }
                for ((target, label) in sec.crossRefs) {
                    append("""<w:p><w:hyperlink w:anchor="${esc(target)}"><w:r><w:t xml:space="preserve">${esc(label)}</w:t></w:r></w:hyperlink></w:p>""")
                }
                val sectXml = buildSectPrXml(sec.setup, isFinal = (sIdx == sections.lastIndex), hasHeader = hasHeader, hasFooter = hasFooter)
                if (sIdx < sections.lastIndex) {
                    append("<w:p><w:pPr>$sectXml</w:pPr></w:p>")
                } else {
                    append(sectXml)
                }
            }
            append("</w:body></w:document>")
        }.toByteArray(StandardCharsets.UTF_8)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos, StandardCharsets.UTF_8).use { zos ->
            fun putPart(name: String, payload: ByteArray) {
                val e = ZipEntry(name).apply { time = 315532800000L } // 1980-01-01 deterministic ZIP epoch
                zos.putNextEntry(e); zos.write(payload); zos.closeEntry()
            }
            putPart("[Content_Types].xml", buildContentTypes(hasHeader, hasFooter, hasFootnotes = false, hasComments = false))
            putPart("_rels/.rels", buildRootRels())
            putPart("word/_rels/document.xml.rels", buildDocRels(hasHeader, hasFooter, hasFootnotes = false, hasComments = false))
            putPart("word/styles.xml", buildStyles())
            putPart("word/document.xml", docXml)
            if (hasHeader) putPart("word/header1.xml", buildHdrFtr("hdr", headerText ?: ""))
            if (hasFooter) putPart("word/footer1.xml", buildHdrFtr("ftr", footerText ?: ""))
        }
        return baos.toByteArray()
    }

    private fun buildSectPrXml(setup: SectionSetup, isFinal: Boolean, hasHeader: Boolean, hasFooter: Boolean): String = buildString {
        append("<w:sectPr>")
        if (isFinal && hasHeader) append("""<w:headerReference w:type="default" r:id="rHdr1"/>""")
        if (isFinal && hasFooter) append("""<w:footerReference w:type="default" r:id="rFtr1"/>""")
        val orientAttr = if (setup.orientation == PageOrientation.LANDSCAPE) """ w:orient="landscape"""" else ""
        append("""<w:pgSz w:w="${setup.widthTwips}" w:h="${setup.heightTwips}"$orientAttr/>""")
        append("""<w:pgMar w:top="${setup.marginTwips}" w:right="${setup.marginTwips}" w:bottom="${setup.marginTwips}" w:left="${setup.marginTwips}"/>""")
        if (setup.columns > 1) append("""<w:cols w:num="${setup.columns}" w:space="720"/>""")
        append("</w:sectPr>")
    }

    fun inspectSectionsAndBookmarks(docxBytes: ByteArray): MultiSectionInspection {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(docxBytes), StandardCharsets.UTF_8).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                entries[e.name] = zis.readBytes()
                e = zis.nextEntry
            }
        }
        val docRoot = BoundedXml.parse(entries["word/document.xml"] ?: error("Missing word/document.xml"))
        val body = docRoot.firstChild("body") ?: error("Missing w:body")
        val sectNodes = ArrayList<BoundedXml.Node>()
        val bookmarks = ArrayList<String>()
        val anchors = ArrayList<String>()
        var maxSpan = 1
        val fills = ArrayList<String>()

        fun walk(n: BoundedXml.Node) {
            when (n.localName) {
                "sectPr" -> sectNodes.add(n)
                "bookmarkStart" -> n.anyAttr("name")?.let { bookmarks.add(it) }
                "hyperlink" -> n.anyAttr("anchor")?.let { anchors.add(it) }
                "gridSpan" -> n.anyAttr("val")?.toIntOrNull()?.let { if (it > maxSpan) maxSpan = it }
                "shd" -> n.anyAttr("fill")?.let { fills.add(it) }
            }
            for (c in n.children) walk(c)
        }
        walk(body)
        val orientations = sectNodes.map { s ->
            val o = s.firstChild("pgSz")?.anyAttr("orient") ?: "portrait"
            if (o.equals("landscape", ignoreCase = true)) PageOrientation.LANDSCAPE else PageOrientation.PORTRAIT
        }
        val cols = sectNodes.map { s -> s.firstChild("cols")?.anyAttr("num")?.toIntOrNull() ?: 1 }
        return MultiSectionInspection(
            sectionCount = sectNodes.size,
            orientations = orientations,
            columnCounts = cols,
            bookmarkNames = bookmarks,
            crossRefAnchors = anchors,
            maxGridSpan = maxSpan,
            shadedCellFills = fills,
        )
    }
}
