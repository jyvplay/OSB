// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader

/**
 * Clean-room OASIS OpenDocument Spreadsheet 1.3 (`.ods`) bounded archive & XML table extractor.
 *
 * Complements [OdtExtract] (`.odt` prose) with typed `.ods` spreadsheet extraction (`float`,
 * `percentage`, `currency`, `boolean`, `date`, `string`, and `table:formula="of:=..."` OpenFormula
 * strings) while enforcing strict decompression, XML entity, and cell/row repeat-bomb caps.
 * Does NOT claim native LibreOfficeKit `libmergedlo.so` execution.
 */
object OdsSheetExtract {
    const val SCOPE =
        "Pure-JVM OASIS ODF 1.3 .ods spreadsheet reader (multi-sheet typed cells, OpenFormula strings, repeat-bomb caps); does NOT evaluate OpenFormula or run native LibreOfficeKit."

    const val MAX_ZIP_BYTES = 16 * 1024 * 1024
    const val MAX_XML_BYTES = 2 * 1024 * 1024
    const val MAX_SHEETS = 32
    const val MAX_COL_REPEAT = 256
    const val MAX_ROW_REPEAT = 64
    const val MAX_CELLS = 32_768

    private const val ODS_MIME = "application/vnd.oasis.opendocument.spreadsheet"

    sealed interface CellData {
        data object Blank : CellData
        data class Num(val value: Double, val currency: String? = null, val isPercent: Boolean = false) : CellData
        data class Bool(val value: Boolean) : CellData
        data class Text(val value: String) : CellData
    }

    data class OdsCell(
        val value: CellData,
        val openFormula: String? = null,
    )

    data class OdsSheet(
        val name: String,
        val rows: List<List<OdsCell>>,
    )

    fun extractArchive(odsBytes: ByteArray): List<OdsSheet> {
        require(odsBytes.size <= MAX_ZIP_BYTES) { "ODS archive exceeds $MAX_ZIP_BYTES bytes" }
        val parts = LinkedHashMap<String, ByteArray>()
        var expanded = 0L
        ZipInputStream(ByteArrayInputStream(odsBytes)).use { zis ->
            val buf = ByteArray(8192)
            var entries = 0
            var e: ZipEntry? = zis.nextEntry
            while (e != null) {
                entries++
                require(entries <= 128) { "ODS entry count exceeds 128" }
                require(!e.name.contains("..") && !e.name.startsWith("/")) { "unsafe ODS entry '${e.name}'" }
                require(!e.name.startsWith("Basic/", ignoreCase = true) && !e.name.startsWith("Scripts/", ignoreCase = true)) {
                    "ODS Basic/Scripts macro directory refused: '${e.name}'"
                }
                if (!e.isDirectory) {
                    val baos = ByteArrayOutputStream()
                    while (true) {
                        val n = zis.read(buf)
                        if (n < 0) break
                        expanded += n
                        require(expanded <= MAX_ZIP_BYTES) { "ODS expanded size exceeds $MAX_ZIP_BYTES bytes" }
                        baos.write(buf, 0, n)
                    }
                    parts[e.name] = baos.toByteArray()
                }
                e = zis.nextEntry
            }
        }
        val mime = parts["mimetype"]?.toString(StandardCharsets.UTF_8)?.trim()
        if (mime != null) {
            require(mime == ODS_MIME) { "unexpected ODF mimetype '$mime' (expected $ODS_MIME)" }
        }
        val contentBytes = parts["content.xml"] ?: throw IllegalArgumentException("ODS missing content.xml")
        require(contentBytes.size <= MAX_XML_BYTES) { "ODS content.xml exceeds $MAX_XML_BYTES bytes" }
        return extractContentXml(contentBytes.toString(StandardCharsets.UTF_8))
    }

    fun extractContentXml(xml: String): List<OdsSheet> {
        require(xml.length <= MAX_XML_BYTES) { "content.xml exceeds $MAX_XML_BYTES chars" }
        require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true)) {
            "DOCTYPE/XXE declarations refused in ODS content.xml"
        }
        val dbf = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val doc = dbf.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val tables = doc.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:table:1.0", "table")
        require(tables.length in 1..MAX_SHEETS) { "ODS table count ${tables.length} outside 1..$MAX_SHEETS" }

        var totalCells = 0
        val sheets = ArrayList<OdsSheet>(tables.length)
        for (tIdx in 0 until tables.length) {
            val tbl = tables.item(tIdx) as Element
            val name = tbl.getAttributeNS("urn:oasis:names:tc:opendocument:xmlns:table:1.0", "name")
                .ifBlank { "Sheet${tIdx + 1}" }
            val sheetRows = ArrayList<List<OdsCell>>()
            val rowNodes = tbl.childNodes
            for (rIdx in 0 until rowNodes.length) {
                val rn = rowNodes.item(rIdx)
                if (rn.nodeType != Node.ELEMENT_NODE || rn.localName != "table-row") continue
                val rowEl = rn as Element
                val rowRepeat = rowEl.getAttributeNS("urn:oasis:names:tc:opendocument:xmlns:table:1.0", "number-rows-repeated")
                    .ifBlank { "1" }.toInt()
                require(rowRepeat in 1..MAX_ROW_REPEAT) {
                    "table:number-rows-repeated=$rowRepeat outside 1..$MAX_ROW_REPEAT"
                }
                val parsedRow = parseRowCells(rowEl)
                // Trim trailing blank repeated padding only if completely blank? Keep exact cells within cap:
                repeat(rowRepeat) {
                    totalCells += parsedRow.size
                    require(totalCells <= MAX_CELLS) { "ODS cell budget $MAX_CELLS exceeded" }
                    sheetRows.add(parsedRow)
                }
            }
            sheets.add(OdsSheet(name, sheetRows))
        }
        return sheets
    }

    private fun parseRowCells(rowEl: Element): List<OdsCell> {
        val out = ArrayList<OdsCell>()
        val children = rowEl.childNodes
        for (i in 0 until children.length) {
            val cn = children.item(i)
            if (cn.nodeType != Node.ELEMENT_NODE) continue
            if (cn.localName != "table-cell" && cn.localName != "covered-table-cell") continue
            val cellEl = cn as Element
            val colRepeat = cellEl.getAttributeNS("urn:oasis:names:tc:opendocument:xmlns:table:1.0", "number-columns-repeated")
                .ifBlank { "1" }.toInt()
            require(colRepeat in 1..MAX_COL_REPEAT) {
                "table:number-columns-repeated=$colRepeat outside 1..$MAX_COL_REPEAT"
            }
            val parsed = if (cn.localName == "covered-table-cell") {
                OdsCell(CellData.Blank)
            } else {
                parseSingleCell(cellEl)
            }
            repeat(colRepeat) { out.add(parsed) }
        }
        return out
    }

    private fun parseSingleCell(cellEl: Element): OdsCell {
        val officeNs = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
        val tableNs = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
        val vType = cellEl.getAttributeNS(officeNs, "value-type").lowercase()
        val rawFormula = cellEl.getAttributeNS(tableNs, "formula").ifBlank { null }
            ?.let { if (it.startsWith("of:")) it.removePrefix("of:") else it }
        val textContent = collectParagraphText(cellEl)

        val data: CellData = when (vType) {
            "float" -> CellData.Num(cellEl.getAttributeNS(officeNs, "value").toDouble())
            "percentage" -> CellData.Num(cellEl.getAttributeNS(officeNs, "value").toDouble(), isPercent = true)
            "currency" -> CellData.Num(
                cellEl.getAttributeNS(officeNs, "value").toDouble(),
                currency = cellEl.getAttributeNS(officeNs, "currency").ifBlank { null },
            )
            "boolean" -> CellData.Bool(cellEl.getAttributeNS(officeNs, "boolean-value").equals("true", ignoreCase = true))
            "string", "date" -> {
                val sv = cellEl.getAttributeNS(officeNs, "string-value").ifBlank {
                    cellEl.getAttributeNS(officeNs, "date-value").ifBlank { textContent }
                }
                CellData.Text(sv)
            }
            else -> if (textContent.isEmpty()) CellData.Blank else CellData.Text(textContent)
        }
        return OdsCell(data, rawFormula)
    }

    private fun collectParagraphText(cellEl: Element): String {
        val ps = ArrayList<String>()
        val children = cellEl.childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && n.localName == "p") {
                ps.add(n.textContent ?: "")
            }
        }
        return ps.joinToString("\n")
    }
}
