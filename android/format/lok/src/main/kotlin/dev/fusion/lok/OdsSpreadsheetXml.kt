// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Clean-room OASIS **OpenDocument 1.3 Spreadsheet (`.ods`)** writer & structural reader.
 *
 * Complements `OdtDocumentXml` (`.odt`) with native LibreOffice Calc `.ods` archives:
 * - First ZIP entry is uncompressed `mimetype` = `"application/vnd.oasis.opendocument.spreadsheet"`
 *   (`46` bytes) `STORED` at exact byte offset `38` with zero extra fields;
 * - Encodes & reads typed `float`, `string`, and `boolean` cells plus OpenFormula `of:=SUM([.A1:.A5])`
 *   <-> A1 formula conversion;
 * - Refuses XXE `<!ENTITY`, Basic/Python macros (`Basic/`, `Scripts/`), and external DDE links!
 */
object OdsSpreadsheetXml {
    const val SCOPE =
        "Clean-room OASIS ODF 1.3 .ods spreadsheet writer & reader (STORED mimetype at byte 38, typed float/string/boolean cells, OpenFormula of:= conversion, macro & XXE refusal)."

    const val MIMETYPE_ODS = "application/vnd.oasis.opendocument.spreadsheet"

    sealed interface OdsCell {
        data class Num(val value: Double, val formulaA1: String? = null) : OdsCell
        data class Text(val value: String) : OdsCell
        data class Bool(val value: Boolean) : OdsCell
        data object Blank : OdsCell
    }

    data class OdsSheet(
        val name: String,
        val rows: List<List<OdsCell>>,
    ) {
        init {
            require(name.isNotBlank()) { "ODS sheet name required" }
        }
    }

    fun toOpenFormula(a1Formula: String): String {
        val body = a1Formula.trim().removePrefix("=")
        val converted = Regex("\\b([A-Za-z]{1,3}\\d{1,7})(?::([A-Za-z]{1,3}\\d{1,7}))?\\b").replace(body) { m ->
            val c1 = m.groupValues[1].uppercase()
            val c2 = m.groupValues[2].uppercase()
            if (c2.isEmpty()) "[.$c1]" else "[.$c1:.$c2]"
        }
        return "of:=$converted"
    }

    fun fromOpenFormula(openFormula: String): String {
        val body = openFormula.trim().removePrefix("of:=").removePrefix("=")
        val restored = Regex("\\[\\.([A-Za-z]{1,3}\\d{1,7})(?::\\.([A-Za-z]{1,3}\\d{1,7}))?]").replace(body) { m ->
            val c1 = m.groupValues[1].uppercase()
            val c2 = m.groupValues[2].uppercase()
            if (c2.isEmpty()) c1 else "$c1:$c2"
        }
        return "=$restored"
    }

    fun writeOds(sheets: List<OdsSheet>): ByteArray {
        require(sheets.isNotEmpty()) { "at least 1 ODS sheet required" }
        val baos = ByteArrayOutputStream()
        val mimeBytes = MIMETYPE_ODS.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(mimeBytes) }.value
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimeBytes.size.toLong()
                    compressedSize = mimeBytes.size.toLong()
                    this.crc = crc
                    time = 315_532_800_000L
                    extra = null
                },
            )
            zos.write(mimeBytes)
            zos.closeEntry()

            putXml(zos, "META-INF/manifest.xml", MANIFEST_XML)
            putXml(zos, "content.xml", buildContentXml(sheets))
        }
        return baos.toByteArray()
    }

    fun readOds(bytes: ByteArray): List<OdsSheet> {
        require(verifyOdsMimetype(bytes)) { "invalid ODS ODF 1.3 header: mimetype must be STORED at offset 38" }
        var contentXml: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                require(!e.name.contains("..") && !e.name.startsWith("/")) { "unsafe ODS path ${e.name}" }
                require(!e.name.startsWith("Basic/") && !e.name.startsWith("Scripts/")) {
                    "embedded ODF macro directory '${e.name}' refused"
                }
                val text = zis.readBytes().toString(Charsets.UTF_8)
                require(!text.contains("<!ENTITY", ignoreCase = true) && !text.contains("dde-source", ignoreCase = true)) {
                    "XXE or DDE external link refused in ${e.name}"
                }
                if (e.name == "content.xml") contentXml = text
            }
        }
        val xml = contentXml ?: error("missing content.xml in ODS archive")
        val sheets = ArrayList<OdsSheet>()
        for (tm in Regex("""<table:table\s+table:name="([^"]+)">([\s\S]*?)</table:table>""").findAll(xml)) {
            val sName = unesc(tm.groupValues[1])
            val rows = ArrayList<List<OdsCell>>()
            for (rm in Regex("""<table:table-row>([\s\S]*?)</table:table-row>""").findAll(tm.groupValues[2])) {
                val cells = ArrayList<OdsCell>()
                for (cm in Regex("""<table:table-cell([^/>]*)(?:/>|>([\s\S]*?)</table:table-cell>)""").findAll(rm.groupValues[1])) {
                    val attrs = cm.groupValues[1]
                    val inner = cm.groupValues[2]
                    val vType = Regex("""office:value-type="([^"]+)"""").find(attrs)?.groupValues?.get(1)
                    val formulaAttr = Regex("""table:formula="([^"]+)"""").find(attrs)?.groupValues?.get(1)?.let(::unesc)
                    val pText = Regex("""<text:p>([\s\S]*?)</text:p>""").find(inner)?.groupValues?.get(1)?.let(::unesc).orEmpty()
                    val cell = when (vType) {
                        "float" -> {
                            val v = Regex("""office:value="([^"]+)"""").find(attrs)?.groupValues?.get(1)?.toDouble() ?: 0.0
                            OdsCell.Num(v, formulaAttr?.let(::fromOpenFormula))
                        }
                        "boolean" -> {
                            val b = Regex("""office:boolean-value="([^"]+)"""").find(attrs)?.groupValues?.get(1) == "true"
                            OdsCell.Bool(b)
                        }
                        "string" -> OdsCell.Text(pText)
                        else -> OdsCell.Blank
                    }
                    cells.add(cell)
                }
                rows.add(cells)
            }
            sheets.add(OdsSheet(sName, rows))
        }
        return sheets
    }

    fun verifyOdsMimetype(bytes: ByteArray): Boolean {
        if (bytes.size < 38 + MIMETYPE_ODS.length) return false
        val method = (bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8)
        val nameLen = (bytes[26].toInt() and 0xFF) or ((bytes[27].toInt() and 0xFF) shl 8)
        val extraLen = (bytes[28].toInt() and 0xFF) or ((bytes[29].toInt() and 0xFF) shl 8)
        if (method != 0 || nameLen != 8 || extraLen != 0) return false
        return String(bytes, 30, 8, Charsets.US_ASCII) == "mimetype" &&
            String(bytes, 38, MIMETYPE_ODS.length, Charsets.US_ASCII) == MIMETYPE_ODS
    }

    private fun putXml(zos: ZipOutputStream, name: String, xml: String) {
        zos.putNextEntry(ZipEntry(name).apply { time = 315_532_800_000L })
        zos.write(xml.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private const val MANIFEST_XML =
        """<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.3">
  <manifest:file-entry manifest:full-path="/" manifest:version="1.3" manifest:media-type="application/vnd.oasis.opendocument.spreadsheet"/>
  <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
</manifest:manifest>"""

    private fun buildContentXml(sheets: List<OdsSheet>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:of="urn:oasis:names:tc:opendocument:xmlns:of:1.2" office:version="1.3">""")
        append("""<office:body><office:spreadsheet>""")
        for (sheet in sheets) {
            append("""<table:table table:name="${esc(sheet.name)}">""")
            for (row in sheet.rows) {
                append("""<table:table-row>""")
                for (c in row) {
                    when (c) {
                        OdsCell.Blank -> append("""<table:table-cell/>""")
                        is OdsCell.Num -> {
                            val fAttr = if (c.formulaA1 != null) """ table:formula="${esc(toOpenFormula(c.formulaA1))}"""" else ""
                            append("""<table:table-cell$fAttr office:value-type="float" office:value="${c.value}"><text:p>${c.value}</text:p></table:table-cell>""")
                        }
                        is OdsCell.Text -> append("""<table:table-cell office:value-type="string"><text:p>${esc(c.value)}</text:p></table:table-cell>""")
                        is OdsCell.Bool -> append("""<table:table-cell office:value-type="boolean" office:boolean-value="${c.value}"><text:p>${if (c.value) "TRUE" else "FALSE"}</text:p></table:table-cell>""")
                    }
                }
                append("""</table:table-row>""")
            }
            append("""</table:table>""")
        }
        append("""</office:spreadsheet></office:body></office:document-content>""")
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun unesc(s: String): String = s
        .replace("&quot;", "\"")
        .replace("&gt;", ">")
        .replace("&lt;", "<")
        .replace("&amp;", "&")
}
