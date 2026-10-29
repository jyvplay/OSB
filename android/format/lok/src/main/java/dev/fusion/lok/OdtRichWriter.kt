// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader

/**
 * Clean-room OASIS OpenDocument 1.3 `.odt` writer & structural reader (`OdtRichWriter`).
 *
 * Advances `:format:lok` beyond read-only [OdfText] extraction by authoring standards-compliant
 * OpenDocument Text packages:
 * - ODF 1.3 Part 2 §2.2.1 packaging rule: `mimetype` (`application/vnd.oasis.opendocument.text`) MUST
 *   be the very first ZIP entry, `STORED` uncompressed, with zero extra-field bytes!
 * - Emits `META-INF/manifest.xml`, `styles.xml`, and `content.xml` (`office:document-content`) with
 *   automatic text styles (`T_B`, `T_I`, `T_BI`, `T_CODE`), headings (`text:h text:outline-level="1..6"`),
 *   paragraphs (`text:p`), rectangular tables (`table:table`), and footnotes (`text:note`).
 * - Privacy-clean: emits zero `meta.xml` author/hostname/generator timestamps.
 */
object OdtRichWriter {
    const val SCOPE =
        "OASIS ODF 1.3 .odt writer + structural reader (uncompressed-first mimetype, manifest.xml, headings 1..6, styled spans, tables, text:note footnotes); StarBasic macros and ODF encryption out of scope."

    const val ODT_MIMETYPE = "application/vnd.oasis.opendocument.text"
    const val MAX_ZIP_BYTES = 16 * 1024 * 1024

    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val monospace: Boolean = false,
    )

    sealed interface OdtBlock {
        data class Heading(val level: Int, val text: String) : OdtBlock {
            init {
                require(level in 1..6) { "ODT heading level must be in 1..6: $level" }
            }
        }

        data class Paragraph(val spans: List<Span>, val footnote: String? = null) : OdtBlock {
            constructor(text: String, footnote: String? = null) : this(listOf(Span(text)), footnote)
        }

        data class Table(val headers: List<String>, val rows: List<List<String>>) : OdtBlock {
            init {
                require(headers.isNotEmpty()) { "ODT table headers must not be empty" }
                rows.forEach { require(it.size == headers.size) { "ODT row width mismatch" } }
            }
        }
    }

    fun write(blocks: List<OdtBlock>): ByteArray {
        val mimeBytes = ODT_MIMETYPE.toByteArray(StandardCharsets.US_ASCII)
        val stylesBytes = buildStylesXml()
        val contentBytes = buildContentXml(blocks)
        val manifestBytes = buildManifestXml()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos, StandardCharsets.UTF_8).use { zos ->
            fun putStored(name: String, data: ByteArray) {
                val crc = CRC32().apply { update(data) }.value
                val e = ZipEntry(name).apply {
                    time = 315532800000L
                    method = ZipEntry.STORED
                    size = data.size.toLong()
                    compressedSize = data.size.toLong()
                    this.crc = crc
                    extra = null
                }
                zos.putNextEntry(e)
                zos.write(data)
                zos.closeEntry()
            }
            // ODF 1.3 Part 2 §2.2.1: mimetype MUST be entry #0, STORED, no extra field.
            putStored("mimetype", mimeBytes)
            putStored("META-INF/manifest.xml", manifestBytes)
            putStored("styles.xml", stylesBytes)
            putStored("content.xml", contentBytes)
        }
        return baos.toByteArray()
    }

    fun verifyMimetypeMagic(odtBytes: ByteArray): Boolean {
        // Local file header: 30 bytes + filename ("mimetype" = 8 bytes) = offset 38 must start with ODT_MIMETYPE!
        val expected = ODT_MIMETYPE.toByteArray(StandardCharsets.US_ASCII)
        if (odtBytes.size < 38 + expected.size) return false
        if (odtBytes[0] != 0x50.toByte() || odtBytes[1] != 0x4B.toByte()) return false
        // Compression method at offset 8..9 must be 0 (STORED), extra field length at 28..29 must be 0.
        val method = (odtBytes[8].toInt() and 0xFF) or ((odtBytes[9].toInt() and 0xFF) shl 8)
        val extraLen = (odtBytes[28].toInt() and 0xFF) or ((odtBytes[29].toInt() and 0xFF) shl 8)
        if (method != 0 || extraLen != 0) return false
        val nameStr = String(odtBytes, 30, 8, StandardCharsets.US_ASCII)
        if (nameStr != "mimetype") return false
        val payloadStr = String(odtBytes, 38, expected.size, StandardCharsets.US_ASCII)
        return payloadStr == ODT_MIMETYPE
    }

    fun read(odtBytes: ByteArray): List<OdtBlock> {
        require(odtBytes.size <= MAX_ZIP_BYTES) { "ODT exceeds MAX_ZIP_BYTES" }
        var mime: String? = null
        var contentXml: String? = null
        ZipInputStream(ByteArrayInputStream(odtBytes), StandardCharsets.UTF_8).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                require(!entry.name.contains("..")) { "Unsafe ODT entry: ${entry.name}" }
                require(!entry.name.startsWith("Basic/", ignoreCase = true)) {
                    "Refusing StarBasic macro stream in ODT: ${entry.name}"
                }
                val data = zis.readBytes()
                when (entry.name) {
                    "mimetype" -> mime = String(data, StandardCharsets.US_ASCII).trim()
                    "content.xml" -> contentXml = String(data, StandardCharsets.UTF_8)
                }
            }
        }
        require(mime == ODT_MIMETYPE) { "Unexpected ODT mimetype: $mime" }
        val xml = contentXml ?: throw IllegalArgumentException("Missing content.xml in ODT")
        return parseContentBlocks(xml)
    }

    private fun parseContentBlocks(xml: String): List<OdtBlock> {
        val dbf = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isExpandEntityReferences = false
        }
        val doc = try {
            val db = dbf.newDocumentBuilder()
            db.setErrorHandler(object : org.xml.sax.ErrorHandler {
                override fun warning(e: org.xml.sax.SAXParseException) {}
                override fun error(e: org.xml.sax.SAXParseException) { throw e }
                override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
            })
            db.parse(InputSource(StringReader(xml)))
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed or forbidden ODT content.xml: ${e.message}", e)
        }
        val textList = doc.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:office:1.0", "text")
        if (textList.length == 0) return emptyList()
        val bodyText = textList.item(0) as Element
        val out = mutableListOf<OdtBlock>()
        val children = bodyText.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val el = child as Element
            when (el.localName) {
                "h" -> {
                    val lvl = el.getAttributeNS("urn:oasis:names:tc:opendocument:xmlns:text:1.0", "outline-level")
                        .ifEmpty { el.getAttribute("text:outline-level") }
                        .toIntOrNull()?.coerceIn(1, 6) ?: 1
                    out.add(OdtBlock.Heading(lvl, el.textContent ?: ""))
                }
                "table" -> {
                    val rows = mutableListOf<List<String>>()
                    val rNodes = el.childNodes
                    for (r in 0 until rNodes.length) {
                        val rn = rNodes.item(r)
                        if (rn.nodeType == Node.ELEMENT_NODE && rn.localName == "table-row") {
                            val cells = mutableListOf<String>()
                            val cNodes = rn.childNodes
                            for (c in 0 until cNodes.length) {
                                val cn = cNodes.item(c)
                                if (cn.nodeType == Node.ELEMENT_NODE && cn.localName == "table-cell") {
                                    cells.add(cn.textContent ?: "")
                                }
                            }
                            if (cells.isNotEmpty()) rows.add(cells)
                        }
                    }
                    if (rows.isNotEmpty()) out.add(OdtBlock.Table(rows.first(), rows.drop(1)))
                }
                "p" -> {
                    var noteText: String? = null
                    val spans = mutableListOf<Span>()
                    val pChildren = el.childNodes
                    for (k in 0 until pChildren.length) {
                        val pn = pChildren.item(k)
                        when {
                            pn.nodeType == Node.ELEMENT_NODE && pn.localName == "note" -> {
                                val bodies = (pn as Element).getElementsByTagNameNS(
                                    "urn:oasis:names:tc:opendocument:xmlns:text:1.0",
                                    "note-body",
                                )
                                if (bodies.length > 0) {
                                    noteText = bodies.item(0).textContent
                                }
                            }
                            pn.nodeType == Node.ELEMENT_NODE && pn.localName == "span" -> {
                                val spEl = pn as Element
                                val st = spEl.getAttributeNS("urn:oasis:names:tc:opendocument:xmlns:text:1.0", "style-name")
                                    .ifEmpty { spEl.getAttribute("text:style-name") }
                                spans.add(
                                    Span(
                                        text = spEl.textContent ?: "",
                                        bold = st == "T_B" || st == "T_BI",
                                        italic = st == "T_I" || st == "T_BI",
                                        monospace = st == "T_CODE",
                                    ),
                                )
                            }
                            pn.nodeType == Node.TEXT_NODE -> {
                                val raw = pn.nodeValue ?: ""
                                if (raw.isNotEmpty()) spans.add(Span(raw))
                            }
                        }
                    }
                    out.add(
                        if (spans.isNotEmpty()) OdtBlock.Paragraph(spans, noteText)
                        else OdtBlock.Paragraph("", noteText),
                    )
                }
            }
        }
        return out
    }

    private fun buildManifestXml(): ByteArray =
        ("""<?xml version="1.0" encoding="UTF-8"?>""" +
            """<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.3">""" +
            """<manifest:file-entry manifest:full-path="/" manifest:version="1.3" manifest:media-type="$ODT_MIMETYPE"/>""" +
            """<manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>""" +
            """<manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>""" +
            """</manifest:manifest>""").toByteArray(StandardCharsets.UTF_8)

    private fun buildStylesXml(): ByteArray =
        ("""<?xml version="1.0" encoding="UTF-8"?>""" +
            """<office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" """ +
            """xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" """ +
            """xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" office:version="1.3">""" +
            """<office:styles><style:default-style style:family="paragraph">""" +
            """<style:text-properties fo:font-size="11pt"/></style:default-style></office:styles>""" +
            """</office:document-styles>""").toByteArray(StandardCharsets.UTF_8)

    private fun buildContentXml(blocks: List<OdtBlock>): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<office:document-content """)
        append("""xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" """)
        append("""xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" """)
        append("""xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" """)
        append("""xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" """)
        append("""xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" office:version="1.3">""")
        append("<office:automatic-styles>")
        append("""<style:style style:name="T_B" style:family="text"><style:text-properties fo:font-weight="bold"/></style:style>""")
        append("""<style:style style:name="T_I" style:family="text"><style:text-properties fo:font-style="italic"/></style:style>""")
        append("""<style:style style:name="T_BI" style:family="text"><style:text-properties fo:font-weight="bold" fo:font-style="italic"/></style:style>""")
        append("""<style:style style:name="T_CODE" style:family="text"><style:text-properties style:font-name="Monospace"/></style:style>""")
        append("</office:automatic-styles><office:body><office:text>")
        var noteSeq = 0
        for (blk in blocks) {
            when (blk) {
                is OdtBlock.Heading -> append("""<text:h text:outline-level="${blk.level}">${esc(blk.text)}</text:h>""")
                is OdtBlock.Paragraph -> {
                    append("<text:p>")
                    for (sp in blk.spans) {
                        val st = when {
                            sp.monospace -> "T_CODE"
                            sp.bold && sp.italic -> "T_BI"
                            sp.bold -> "T_B"
                            sp.italic -> "T_I"
                            else -> null
                        }
                        if (st != null) append("""<text:span text:style-name="$st">${esc(sp.text)}</text:span>""")
                        else append("""<text:span>${esc(sp.text)}</text:span>""")
                    }
                    if (!blk.footnote.isNullOrBlank()) {
                        noteSeq++
                        append("""<text:note text:id="ftn$noteSeq" text:note-class="footnote">""")
                        append("""<text:note-citation>$noteSeq</text:note-citation>""")
                        append("""<text:note-body><text:p>${esc(blk.footnote)}</text:p></text:note-body></text:note>""")
                    }
                    append("</text:p>")
                }
                is OdtBlock.Table -> {
                    append("<table:table>")
                    for (row in listOf(blk.headers) + blk.rows) {
                        append("<table:table-row>")
                        for (cell in row) {
                            append("""<table:table-cell><text:p>${esc(cell)}</text:p></table:table-cell>""")
                        }
                        append("</table:table-row>")
                    }
                    append("</table:table>")
                }
            }
        }
        append("</office:text></office:body></office:document-content>")
    }.toByteArray(StandardCharsets.UTF_8)

    private fun esc(s: String): String = buildString(s.length) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(ch)
            }
        }
    }

    // --- R51 Master-Page Header/Footer, Spanned Table & Endnote ODF 1.3 Upgrade ---

    data class MasterPageLayout(
        val name: String = "Standard",
        val pageWidthMm: Int = 210,
        val pageHeightMm: Int = 297,
        val headerText: String? = null,
        val footerText: String? = null,
    ) {
        init {
            require(pageWidthMm in 100..600 && pageHeightMm in 100..600) { "page mm out of bounds" }
        }
    }

    data class Endnote(val id: String, val citation: String, val bodyText: String) {
        init {
            require(id.isNotBlank() && bodyText.isNotBlank()) { "endnote id/body required" }
        }
    }

    data class SpannedOdtCell(val text: String, val colSpan: Int = 1) {
        init {
            require(colSpan in 1..12) { "colSpan must be in 1..12" }
        }
    }

    data class ScholarlyOdtInspection(
        val mimetypeStoredAt38: Boolean,
        val masterPageName: String?,
        val headerText: String?,
        val footerText: String?,
        val endnoteCount: Int,
        val maxColSpan: Int,
    )

    fun writeScholarlyOdt(
        blocks: List<OdtBlock>,
        masterPage: MasterPageLayout = MasterPageLayout(),
        spannedRows: List<List<SpannedOdtCell>> = emptyList(),
        endnotes: List<Endnote> = emptyList(),
    ): ByteArray {
        val mimeBytes = ODT_MIMETYPE.toByteArray(StandardCharsets.US_ASCII)
        val stylesBytes = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" """)
            append("""xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" """)
            append("""xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" """)
            append("""xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" office:version="1.3">""")
            append("""<office:automatic-styles><style:page-layout style:name="pm1">""")
            append("""<style:page-layout-properties fo:page-width="${masterPage.pageWidthMm}mm" fo:page-height="${masterPage.pageHeightMm}mm"/>""")
            append("""</style:page-layout></office:automatic-styles>""")
            append("""<office:master-styles><style:master-page style:name="${esc(masterPage.name)}" style:page-layout-name="pm1">""")
            if (!masterPage.headerText.isNullOrBlank()) {
                append("""<style:header><text:p>${esc(masterPage.headerText)}</text:p></style:header>""")
            }
            if (!masterPage.footerText.isNullOrBlank()) {
                append("""<style:footer><text:p>${esc(masterPage.footerText)}</text:p></style:footer>""")
            }
            append("""</style:master-page></office:master-styles></office:document-styles>""")
        }.toByteArray(StandardCharsets.UTF_8)

        val baseContentXml = String(buildContentXml(blocks), StandardCharsets.UTF_8)
        val extraXml = buildString {
            if (spannedRows.isNotEmpty()) {
                append("""<table:table table:name="ScholarlyGrid">""")
                for (row in spannedRows) {
                    append("<table:table-row>")
                    for (cell in row) {
                        val spanAttr = if (cell.colSpan > 1) """ table:number-columns-spanned="${cell.colSpan}"""" else ""
                        append("""<table:table-cell$spanAttr><text:p>${esc(cell.text)}</text:p></table:table-cell>""")
                        repeat(cell.colSpan - 1) { append("<table:covered-table-cell/>") }
                    }
                    append("</table:table-row>")
                }
                append("</table:table>")
            }
            for (en in endnotes) {
                append("""<text:p><text:note text:id="${esc(en.id)}" text:note-class="endnote">""")
                append("""<text:note-citation>${esc(en.citation)}</text:note-citation>""")
                append("""<text:note-body><text:p>${esc(en.bodyText)}</text:p></text:note-body>""")
                append("""</text:note></text:p>""")
            }
        }
        val mergedContent = baseContentXml.replace(
            "</office:text></office:body></office:document-content>",
            "$extraXml</office:text></office:body></office:document-content>",
        ).toByteArray(StandardCharsets.UTF_8)
        val manifestBytes = buildManifestXml()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos, StandardCharsets.UTF_8).use { zos ->
            val crc = CRC32().apply { update(mimeBytes) }.value
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                this.crc = crc
                time = 315532800000L
            }
            zos.putNextEntry(mimeEntry); zos.write(mimeBytes); zos.closeEntry()
            for ((name, bytes) in listOf(
                "META-INF/manifest.xml" to manifestBytes,
                "styles.xml" to stylesBytes,
                "content.xml" to mergedContent,
            )) {
                zos.putNextEntry(ZipEntry(name).apply { time = 315532800000L })
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    fun inspectScholarlyOdt(odtBytes: ByteArray): ScholarlyOdtInspection {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(odtBytes), StandardCharsets.UTF_8).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                entries[e.name] = zis.readBytes()
                e = zis.nextEntry
            }
        }
        val dbf = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val stylesDoc = dbf.newDocumentBuilder().parse(InputSource(StringReader(String(entries["styles.xml"] ?: ByteArray(0), StandardCharsets.UTF_8))))
        val mpNodes = stylesDoc.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:style:1.0", "master-page")
        val mpEl = if (mpNodes.length > 0) mpNodes.item(0) as Element else null
        val hdrNodes = stylesDoc.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:style:1.0", "header")
        val ftrNodes = stylesDoc.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:style:1.0", "footer")

        val contentDoc = dbf.newDocumentBuilder().parse(InputSource(StringReader(String(entries["content.xml"] ?: ByteArray(0), StandardCharsets.UTF_8))))
        val notes = contentDoc.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:text:1.0", "note")
        var endnotes = 0
        for (i in 0 until notes.length) {
            val n = notes.item(i) as Element
            val cls = n.getAttributeNS("urn:oasis:names:tc:opendocument:xmlns:text:1.0", "note-class").ifEmpty { n.getAttribute("text:note-class") }
            if (cls == "endnote") endnotes++
        }
        val cells = contentDoc.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:table:1.0", "table-cell")
        var maxSpan = 1
        for (i in 0 until cells.length) {
            val c = cells.item(i) as Element
            val sp = c.getAttributeNS("urn:oasis:names:tc:opendocument:xmlns:table:1.0", "number-columns-spanned")
                .ifEmpty { c.getAttribute("table:number-columns-spanned") }
                .toIntOrNull() ?: 1
            if (sp > maxSpan) maxSpan = sp
        }
        return ScholarlyOdtInspection(
            mimetypeStoredAt38 = verifyMimetypeMagic(odtBytes),
            masterPageName = mpEl?.getAttributeNS("urn:oasis:names:tc:opendocument:xmlns:style:1.0", "name")?.ifEmpty { mpEl.getAttribute("style:name") },
            headerText = if (hdrNodes.length > 0) hdrNodes.item(0).textContent else null,
            footerText = if (ftrNodes.length > 0) ftrNodes.item(0).textContent else null,
            endnoteCount = endnotes,
            maxColSpan = maxSpan,
        )
    }
}
