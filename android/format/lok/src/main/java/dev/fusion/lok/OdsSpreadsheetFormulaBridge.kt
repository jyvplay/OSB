package dev.fusion.lok

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Clean-room OASIS OpenDocument 1.3 Spreadsheet (`.ods`) `OpenFormula` (`of:=...`) <-> OOXML `A1`
 * Formula Bridge & ODF `STORED@46` `mimetype` Archive Writer/Reader (Apache-2.0).
 */
object OdsSpreadsheetFormulaBridge {
    const val SCOPE =
        "OASIS ODF 1.3 .ods OpenFormula (of:=SUM([.A1:.B4])) <-> OOXML A1 formula translator + STORED@46 mimetype .ods writer/reader"

    const val ODS_MIME = "application/vnd.oasis.opendocument.spreadsheet" // 46 ASCII bytes

    data class OdsCell(
        val valueText: String,
        val numericValue: Double? = null,
        val openFormula: String? = null,
    )

    data class OdsSheetSummary(
        val sheetName: String,
        val mimetypeStoredUncompressedFirst: Boolean,
        val rows: List<List<OdsCell>>,
    )

    private val OF_RANGE_RE = Regex("\\[(?:([A-Za-z0-9_]+|'[^']+')?\\.)(\\$?[A-Za-z]{1,3}\\$?[1-9][0-9]*)(?::(?:[A-Za-z0-9_]+|'[^']+')?\\.(\\$?[A-Za-z]{1,3}\\$?[1-9][0-9]*))?\\]")
    private val A1_SHEET_RANGE_RE = Regex("(?:('[^']+'|[A-Za-z_][A-Za-z0-9_]*)!)?(\\$?[A-Za-z]{1,3}\\$?[1-9][0-9]*)(?::(\\$?[A-Za-z]{1,3}\\$?[1-9][0-9]*))?")

    /** Converts OOXML `=SUM(A1:B4;Sheet2!C3)` or `=SUM(A1:B4,Sheet2!C3)` into ODF 1.3 `of:=SUM([.A1:.B4];[Sheet2.C3])`. */
    fun a1ToOpenFormula(a1Formula: String): String {
        val body = a1Formula.trim().removePrefix("=")
        val sb = StringBuilder("of:=")
        var i = 0
        var inQ = false
        while (i < body.length) {
            val ch = body[i]
            if (ch == '"') { inQ = !inQ; sb.append(ch); i++; continue }
            if (!inQ) {
                if (ch == ',') { sb.append(';'); i++; continue }
                val m = A1_SHEET_RANGE_RE.matchAt(body, i)
                val prev = if (i > 0) body[i - 1] else ' '
                val after = m?.let { it.range.last + 1 } ?: -1
                val next = if (after in body.indices) body[after] else ' '
                if (m != null && !prev.isLetterOrDigit() && prev != '.' && next != '(' && !next.isLetterOrDigit()) {
                    val sheet = m.groupValues[1]
                    val start = m.groupValues[2]
                    val end = m.groupValues[3]
                    val prefix = if (sheet.isEmpty()) "." else "$sheet."
                    if (end.isEmpty()) sb.append("[$prefix$start]") else sb.append("[$prefix$start:.$end]")
                    i = after
                    continue
                }
            }
            sb.append(ch)
            i++
        }
        return sb.toString()
    }

    /** Converts ODF 1.3 `of:=SUM([.A1:.B4];[Sheet2.C3])` back into canonical `,`-separated OOXML `=SUM(A1:B4,Sheet2!C3)`. */
    fun openFormulaToA1(openFormula: String): String {
        val body = openFormula.trim().removePrefix("of:").removePrefix("=")
        val unbracketed = OF_RANGE_RE.replace(body) { m ->
            val sh = m.groupValues[1]
            val c1 = m.groupValues[2]
            val c2 = m.groupValues[3]
            val p = if (sh.isEmpty()) "" else "$sh!"
            if (c2.isEmpty()) "$p$c1" else "$p$c1:$c2"
        }
        val sb = StringBuilder("=")
        var inQ = false
        for (c in unbracketed) {
            if (c == '"') inQ = !inQ
            sb.append(if (!inQ && c == ';') ',' else c)
        }
        return sb.toString()
    }

    fun writeOds(sheetName: String, rows: List<List<OdsCell>>): ByteArray {
        require(sheetName.isNotBlank() && rows.isNotEmpty())
        val contentXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:of="urn:oasis:names:tc:opendocument:xmlns:of:1.2" office:version="1.3">""")
            append("""<office:body><office:spreadsheet><table:table table:name="${esc(sheetName)}">""")
            for (r in rows) {
                append("<table:table-row>")
                for (c in r) {
                    append("<table:table-cell")
                    if (c.numericValue != null) {
                        append(""" office:value-type="float" office:value="${c.numericValue}"""")
                    } else {
                        append(""" office:value-type="string"""")
                    }
                    if (c.openFormula != null) append(""" table:formula="${esc(c.openFormula)}"""")
                    append("><text:p>${esc(c.valueText)}</text:p></table:table-cell>")
                }
                append("</table:table-row>")
            }
            append("</table:table></office:spreadsheet></office:body></office:document-content>")
        }
        val manifestXml = """<?xml version="1.0" encoding="UTF-8"?><manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.3"><manifest:file-entry manifest:full-path="/" manifest:version="1.3" manifest:media-type="$ODS_MIME"/><manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/></manifest:manifest>"""

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos, StandardCharsets.UTF_8).use { zos ->
            val mimeBytes = ODS_MIME.toByteArray(StandardCharsets.US_ASCII)
            val mimeCrc = CRC32().apply { update(mimeBytes) }.value
            zos.putNextEntry(
                ZipEntry("mimetype").apply {
                    time = 315532800000L
                    method = ZipEntry.STORED
                    size = mimeBytes.size.toLong()
                    compressedSize = mimeBytes.size.toLong()
                    crc = mimeCrc
                },
            )
            zos.write(mimeBytes)
            zos.closeEntry()

            for ((path, xml) in listOf("META-INF/manifest.xml" to manifestXml, "content.xml" to contentXml)) {
                val raw = xml.toByteArray(StandardCharsets.UTF_8)
                zos.putNextEntry(ZipEntry(path).apply { time = 315532800000L })
                zos.write(raw)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    fun readOds(bytes: ByteArray): OdsSheetSummary {
        // ODF 1.3 Part 2 §2.2.1: local file header 'PK\x03\x04' + 26 bytes -> 'mimetype' at offset 30, payload at offset 38 (46 bytes)
        val headerStr = if (bytes.size >= 84) String(bytes, 30, 8 + ODS_MIME.length, StandardCharsets.US_ASCII) else ""
        val storedFirst = headerStr == "mimetype$ODS_MIME"
        var contentXml = ""
        ZipInputStream(ByteArrayInputStream(bytes), StandardCharsets.UTF_8).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                require(!e.name.contains("..")) { "Unsafe ODS entry: ${e.name}" }
                if (e.name == "content.xml") contentXml = String(zis.readBytes(), StandardCharsets.UTF_8)
            }
        }
        val tblName = Regex("""table:name="([^"]+)"""").find(contentXml)?.groupValues?.get(1).orEmpty()
        val parsedRows = Regex("<table:table-row>([\\s\\S]*?)</table:table-row>").findAll(contentXml).map { rm ->
            Regex("<table:table-cell([^>]*)><text:p>([\\s\\S]*?)</text:p></table:table-cell>").findAll(rm.groupValues[1]).map { cm ->
                val attrs = cm.groupValues[1]
                val num = Regex("""office:value="([^"]+)"""").find(attrs)?.groupValues?.get(1)?.toDoubleOrNull()
                val fml = Regex("""table:formula="([^"]+)"""").find(attrs)?.groupValues?.get(1)?.let(::unesc)
                OdsCell(unesc(cm.groupValues[2]), num, fml)
            }.toList()
        }.toList()
        return OdsSheetSummary(tblName, storedFirst, parsedRows)
    }

    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun unesc(s: String): String = s.replace("&quot;", "\"").replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&")
}
