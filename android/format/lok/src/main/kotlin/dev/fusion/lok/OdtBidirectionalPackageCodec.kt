// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Clean-room OASIS OpenDocument 1.3 (`.odt`) bidirectional writer/reader.
 *
 * ODF 1.3 Part 2 §2.2.1 invariants enforced:
 * 1. `mimetype` is the **first** ZIP entry (`index 0`);
 * 2. `mimetype` is **STORED** (`compression method 0`) with `compressedSize == size` and an explicit CRC-32;
 * 3. `META-INF/manifest.xml`, `styles.xml`, `content.xml`, and `meta.xml` follow as DEFLATE entries.
 */
object OdtBidirectionalPackageCodec {
    const val ODT_MIMETYPE = "application/vnd.oasis.opendocument.text"
    const val OFFICE_NS = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    const val TEXT_NS = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    const val TABLE_NS = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    const val STYLE_NS = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
    const val FO_NS = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
    const val MANIFEST_NS = "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0"

    data class Paragraph(
        val text: String,
        val headingLevel: Int = 0,
        val styleName: String = "Standard",
        val listLevel: Int? = null,
        val bold: Boolean = false,
        val italic: Boolean = false,
    )

    data class Table(val header: List<String>, val rows: List<List<String>>)

    data class OdtDocument(
        val title: String,
        val author: String,
        val paragraphs: List<Paragraph>,
        val tables: List<Table> = emptyList(),
    )

    data class ParsedOdt(
        val mimetypeFirst: Boolean,
        val mimetypeStored: Boolean,
        val mimetypeSize: Int,
        val mimetype: String,
        val paragraphs: List<String>,
        val headings: List<String>,
        val tableCellTexts: List<String>,
        val entries: List<String>,
    )

    private fun esc(s: String): String = buildString {
        for (ch in s) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(ch)
        }
    }

    private fun unesc(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")

    fun write(doc: OdtDocument): ByteArray {
        val contentBody = StringBuilder()
        for (p in doc.paragraphs) {
            if (p.headingLevel in 1..6) {
                contentBody.append("<text:h text:outline-level=\"${p.headingLevel}\" text:style-name=\"Heading_20_${p.headingLevel}\">")
                    .append(esc(p.text)).append("</text:h>")
            } else {
                val style = if (p.bold || p.italic) {
                    (if (p.bold) "Bold" else "") + (if (p.italic && p.bold) "_" else if (p.italic) "Italic" else "")
                } else p.styleName
                contentBody.append("<text:p text:style-name=\"${esc(style)}\">")
                if (p.listLevel != null) {
                    contentBody.append("<text:list text:style-name=\"Bullet\"><text:list-item>")
                        .append("<text:p text:style-name=\"P1\">").append(esc(p.text)).append("</text:p>")
                        .append("</text:list-item></text:list>")
                } else {
                    contentBody.append(esc(p.text))
                }
                contentBody.append("</text:p>")
            }
        }
        for (t in doc.tables) {
            contentBody.append("<table:table table:name=\"Table${doc.tables.indexOf(t) + 1}\">")
            contentBody.append("<table:table-column table:number-columns-repeated=\"${t.header.size}\"/>")
            contentBody.append("<table:table-header-rows><table:table-row>")
            for (h in t.header) {
                contentBody.append("<table:table-cell office:value-type=\"string\"><text:p>").append(esc(h)).append("</text:p></table:table-cell>")
            }
            contentBody.append("</table:table-row></table:table-header-rows>")
            for (row in t.rows) {
                contentBody.append("<table:table-row>")
                for (c in 0 until t.header.size) {
                    contentBody.append("<table:table-cell office:value-type=\"string\"><text:p>")
                        .append(esc(row.getOrElse(c) { "" }))
                        .append("</text:p></table:table-cell>")
                }
                contentBody.append("</table:table-row>")
            }
            contentBody.append("</table:table>")
        }

        val content = """<?xml version="1.0" encoding="UTF-8"?>
<office:document-content xmlns:office="$OFFICE_NS" xmlns:text="$TEXT_NS" xmlns:table="$TABLE_NS" xmlns:style="$STYLE_NS" xmlns:fo="$FO_NS" office:version="1.3"><office:body><office:text>$contentBody</office:text></office:body></office:document-content>"""

        val styles = """<?xml version="1.0" encoding="UTF-8"?>
<office:document-styles xmlns:office="$OFFICE_NS" xmlns:style="$STYLE_NS" xmlns:fo="$FO_NS" office:version="1.3"><office:styles><style:style style:name="Standard" style:family="paragraph"/><style:style style:name="Heading_20_1" style:family="paragraph"><style:text-properties fo:font-size="18pt" fo:font-weight="bold"/></style:style><style:style style:name="Heading_20_2" style:family="paragraph"><style:text-properties fo:font-size="14pt" fo:font-weight="bold"/></style:style><style:style style:name="Bold" style:family="paragraph"><style:text-properties fo:font-weight="bold"/></style:style><style:style style:name="Italic" style:family="paragraph"><style:text-properties fo:font-style="italic"/></style:style><style:style style:name="Bold_Italic" style:family="paragraph"><style:text-properties fo:font-weight="bold" fo:font-style="italic"/></style:style><style:style style:name="P1" style:family="paragraph"/></office:styles></office:document-styles>"""

        val meta = """<?xml version="1.0" encoding="UTF-8"?>
<office:document-meta xmlns:office="$OFFICE_NS" xmlns:dc="http://purl.org/dc/elements/1.1/" office:version="1.3"><office:meta><dc:title>${esc(doc.title)}</dc:title><dc:creator>${esc(doc.author)}</dc:creator></office:meta></office:document-meta>"""

        val manifest = """<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest xmlns:manifest="$MANIFEST_NS" manifest:version="1.3"><manifest:file-entry manifest:full-path="/" manifest:media-type="$ODT_MIMETYPE" manifest:version="1.3"/><manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/><manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/><manifest:file-entry manifest:full-path="meta.xml" manifest:media-type="text/xml"/></manifest:manifest>"""

        val entries = listOf(
            "content.xml" to content.toByteArray(Charsets.UTF_8),
            "styles.xml" to styles.toByteArray(Charsets.UTF_8),
            "meta.xml" to meta.toByteArray(Charsets.UTF_8),
            "META-INF/manifest.xml" to manifest.toByteArray(Charsets.UTF_8),
        )
        return writeMimetypeFirst(entries)
    }

    fun writeMimetypeFirst(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            val mime = ODT_MIMETYPE.toByteArray(Charsets.US_ASCII)
            val me = ZipEntry("mimetype")
            me.time = 315_532_800_000L
            me.method = ZipEntry.STORED
            me.size = mime.size.toLong()
            me.compressedSize = mime.size.toLong()
            me.crc = CRC32().apply { update(mime) }.value
            zos.putNextEntry(me)
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
        return out.toByteArray()
    }

    data class EntryMeta(val name: String, val method: Int, val size: Int, val compressedSize: Int, val bytes: ByteArray)

    fun readEntries(odt: ByteArray): List<EntryMeta> {
        val list = ArrayList<EntryMeta>()
        ZipInputStream(odt.inputStream()).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                val bytes = zis.readBytes()
                list.add(EntryMeta(e.name, e.method, bytes.size, e.compressedSize.toInt(), bytes))
            }
        }
        return list
    }

    fun parse(odt: ByteArray): ParsedOdt {
        val entries = readEntries(odt)
        val first = entries.firstOrNull()
        val mimeEntry = entries.firstOrNull { it.name == "mimetype" }
        val content = entries.firstOrNull { it.name == "content.xml" }?.bytes?.toString(Charsets.UTF_8).orEmpty()
        val paragraphs = Regex("<text:p[^>]*>(.*?)</text:p>", RegexOption.DOT_MATCHES_ALL).findAll(content)
            .map { unesc(it.groupValues[1].replace(Regex("<[^>]+>"), "")) }.toList()
        val headings = Regex("<text:h[^>]*>(.*?)</text:h>", RegexOption.DOT_MATCHES_ALL).findAll(content)
            .map { unesc(it.groupValues[1].replace(Regex("<[^>]+>"), "")) }.toList()
        val cells = Regex("<table:table-cell[^>]*>(.*?)</table:table-cell>", RegexOption.DOT_MATCHES_ALL).findAll(content)
            .map { unesc(it.groupValues[1].replace(Regex("<[^>]+>"), "")) }.toList()
        return ParsedOdt(
            mimetypeFirst = first?.name == "mimetype",
            mimetypeStored = mimeEntry?.method == 0,
            mimetypeSize = mimeEntry?.size ?: -1,
            mimetype = mimeEntry?.bytes?.toString(Charsets.US_ASCII).orEmpty(),
            paragraphs = paragraphs,
            headings = headings,
            tableCellTexts = cells,
            entries = entries.map { it.name },
        )
    }
}
