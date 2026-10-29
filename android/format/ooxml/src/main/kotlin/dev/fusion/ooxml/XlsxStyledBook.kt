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
 * Clean-room ECMA-376 SpreadsheetML styled workbook writer & reader (`XlsxStyledBook`).
 *
 * Advances `:format:ooxml` beyond unstyled [XlsxWorkbook] by emitting and reading:
 * - `xl/styles.xml` with custom `<numFmts>` (`#,##0.00`, `$#,##0.00`, `0.0%`, `yyyy-mm-dd`),
 *   `<fonts>` (`bold`, `italic`, `rgbHex`), `<fills>` (`solid` ARGB background), `<borders>`,
 *   and deduplicated `<cellXfs>` indices (`s="N"` on `<c>`).
 * - Workbook `<definedNames>` (`<definedName name="...">Sheet1!$A$1:$B$10</definedName>`).
 * - Worksheet `<sheetViews>` frozen header rows/columns (`<pane ySplit="..." xSplit="..." state="frozen"/>`).
 * - Column width specs (`<cols><col min="..." max="..." width="..." customWidth="1"/></cols>`).
 * - Typed cells (`NumberCell`, `TextCell`, `BoolCell`, `FormulaCell` with cached value) compatible with `openpyxl`.
 */
object XlsxStyledBook {
    const val SCOPE =
        "Styled ECMA-376 SpreadsheetML .xlsx writer + reader (xl/styles.xml numFmts/fonts/fills/borders, frozen panes, definedNames, column widths, formula cells); pivotCaches and VBA vbaProject.bin out of scope."

    const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"

    data class CellStyle(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val fontRgb: String? = null,
        val fillRgb: String? = null,
        val thinBorder: Boolean = false,
        val numberFormat: String? = null,
    ) {
        init {
            fontRgb?.let { require(HEX6.matches(it)) { "fontRgb must be 6-hex RGB: $it" } }
            fillRgb?.let { require(HEX6.matches(it)) { "fillRgb must be 6-hex RGB: $it" } }
        }
    }

    sealed interface StyledVal {
        data class Num(val value: Double) : StyledVal
        data class Txt(val value: String) : StyledVal
        data class Bool(val value: Boolean) : StyledVal
        data class Formula(val expr: String, val cachedNumber: Double? = null) : StyledVal
    }

    data class StyledCell(
        val ref: String,
        val value: StyledVal,
        val style: CellStyle = CellStyle(),
    )

    data class DefinedName(val name: String, val reference: String) {
        init {
            require(NAME_RE.matches(name)) { "Invalid Excel definedName: $name" }
            require(reference.isNotBlank()) { "definedName reference must not be blank" }
        }
    }

    data class StyledSheet(
        val name: String,
        val cells: List<StyledCell>,
        val freezeRows: Int = 0,
        val freezeCols: Int = 0,
        val columnWidths: Map<Int, Double> = emptyMap(),
    ) {
        init {
            require(name.isNotBlank() && name.length <= 31) { "Invalid worksheet name: $name" }
            require(freezeRows >= 0 && freezeCols >= 0) { "Negative freeze pane split" }
        }
    }

    data class WorkbookSpec(
        val sheets: List<StyledSheet>,
        val definedNames: List<DefinedName> = emptyList(),
    ) {
        init {
            require(sheets.isNotEmpty()) { "Workbook requires at least one sheet" }
        }
    }

    private val HEX6 = Regex("^[0-9A-Fa-f]{6}$")
    private val NAME_RE = Regex("^[A-Za-z_][A-Za-z0-9_.]{0,63}$")
    private val CELL_RE = Regex("^([A-Z]{1,3})([1-9][0-9]{0,6})$")

    fun write(spec: WorkbookSpec): ByteArray {
        val styleTable = StyleRegistry()
        for (sh in spec.sheets) {
            for (c in sh.cells) styleTable.intern(c.style)
        }

        val parts = linkedMapOf<String, ByteArray>()
        parts["[Content_Types].xml"] = buildContentTypes(spec.sheets.size)
        parts["_rels/.rels"] = buildRootRels()
        parts["xl/workbook.xml"] = buildWorkbookXml(spec)
        parts["xl/_rels/workbook.xml.rels"] = buildWorkbookRels(spec.sheets.size)
        parts["xl/styles.xml"] = styleTable.toXml()
        spec.sheets.forEachIndexed { idx, sheet ->
            parts["xl/worksheets/sheet${idx + 1}.xml"] = buildSheetXml(sheet, styleTable)
        }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos, StandardCharsets.UTF_8).use { zos ->
            for ((name, bytes) in parts) {
                val crc = CRC32().apply { update(bytes) }.value
                val entry = ZipEntry(name).apply {
                    time = 315532800000L
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

    fun read(bytes: ByteArray): WorkbookSpec {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes), StandardCharsets.UTF_8).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                require(!entry.name.contains("..") && !entry.name.endsWith("vbaProject.bin", ignoreCase = true)) {
                    "Refusing unsafe/macro XLSX entry: ${entry.name}"
                }
                entries[entry.name] = zis.readBytes()
            }
        }
        val styles = entries["xl/styles.xml"]?.let { parseStyles(BoundedXml.parse(it)) } ?: listOf(CellStyle())
        val wbRoot = BoundedXml.parse(entries["xl/workbook.xml"] ?: error("Missing xl/workbook.xml"))
        val defined = wbRoot.firstChild("definedNames")?.children
            ?.filter { it.localName == "definedName" }
            ?.mapNotNull { dn ->
                val nm = dn.attr("name") ?: return@mapNotNull null
                DefinedName(nm, dn.text)
            } ?: emptyList()

        val sheetElems = wbRoot.firstChild("sheets")?.children?.filter { it.localName == "sheet" }.orEmpty()
        val sheets = sheetElems.mapIndexed { idx, sEl ->
            val name = sEl.attr("name") ?: "Sheet${idx + 1}"
            val shBytes = entries["xl/worksheets/sheet${idx + 1}.xml"]
                ?: error("Missing xl/worksheets/sheet${idx + 1}.xml")
            parseSheet(name, BoundedXml.parse(shBytes), styles)
        }
        return WorkbookSpec(sheets = sheets, definedNames = defined)
    }

    private val BoundedXml.Node.localName: String get() = name.local
    private fun BoundedXml.Node.firstChild(local: String): BoundedXml.Node? =
        children.firstOrNull { it.name.local == local }

    private fun parseStyles(root: BoundedXml.Node): List<CellStyle> {
        val numFmts = mutableMapOf<Int, String>()
        root.firstChild("numFmts")?.children?.filter { it.localName == "numFmt" }?.forEach { nf ->
            val id = nf.attr("numFmtId")?.toIntOrNull()
            val code = nf.attr("formatCode")
            if (id != null && code != null) numFmts[id] = code
        }
        data class FontRec(val bold: Boolean, val italic: Boolean, val rgb: String?)
        val fonts = root.firstChild("fonts")?.children?.filter { it.localName == "font" }?.map { f ->
            val rgb = f.firstChild("color")?.attr("rgb")?.takeLast(6)
            FontRec(f.firstChild("b") != null, f.firstChild("i") != null, rgb)
        }.orEmpty()
        val fills = root.firstChild("fills")?.children?.filter { it.localName == "fill" }?.map { fl ->
            val pf = fl.firstChild("patternFill")
            if (pf?.attr("patternType") == "solid") pf.firstChild("fgColor")?.attr("rgb")?.takeLast(6) else null
        }.orEmpty()
        val borders = root.firstChild("borders")?.children?.filter { it.localName == "border" }?.map { bd ->
            bd.firstChild("left")?.attr("style") == "thin"
        }.orEmpty()

        return root.firstChild("cellXfs")?.children?.filter { it.localName == "xf" }?.map { xf ->
            val fIdx = xf.attr("fontId")?.toIntOrNull() ?: 0
            val flIdx = xf.attr("fillId")?.toIntOrNull() ?: 0
            val bIdx = xf.attr("borderId")?.toIntOrNull() ?: 0
            val nfId = xf.attr("numFmtId")?.toIntOrNull() ?: 0
            val font = fonts.getOrNull(fIdx)
            CellStyle(
                bold = font?.bold ?: false,
                italic = font?.italic ?: false,
                fontRgb = font?.rgb,
                fillRgb = fills.getOrNull(flIdx),
                thinBorder = borders.getOrNull(bIdx) ?: false,
                numberFormat = numFmts[nfId],
            )
        }.orEmpty().ifEmpty { listOf(CellStyle()) }
    }

    private fun parseSheet(name: String, root: BoundedXml.Node, styles: List<CellStyle>): StyledSheet {
        val pane = root.firstChild("sheetViews")?.firstChild("sheetView")?.firstChild("pane")
        val freezeRows = pane?.attr("ySplit")?.toIntOrNull() ?: 0
        val freezeCols = pane?.attr("xSplit")?.toIntOrNull() ?: 0
        val colWidths = linkedMapOf<Int, Double>()
        root.firstChild("cols")?.children?.filter { it.localName == "col" }?.forEach { col ->
            val min = col.attr("min")?.toIntOrNull()
            val w = col.attr("width")?.toDoubleOrNull()
            if (min != null && w != null) colWidths[min] = w
        }
        val cells = mutableListOf<StyledCell>()
        for (row in root.firstChild("sheetData")?.children?.filter { it.localName == "row" }.orEmpty()) {
            for (c in row.children.filter { it.localName == "c" }) {
                val ref = c.attr("r") ?: continue
                val sIdx = c.attr("s")?.toIntOrNull() ?: 0
                val style = styles.getOrElse(sIdx) { CellStyle() }
                val fEl = c.firstChild("f")
                val vEl = c.firstChild("v")
                val tAttr = c.attr("t") ?: ""
                val sv: StyledVal = when {
                    fEl != null -> StyledVal.Formula(fEl.text, vEl?.text?.toDoubleOrNull())
                    tAttr == "inlineStr" -> StyledVal.Txt(c.firstChild("is")?.firstChild("t")?.text ?: "")
                    tAttr == "b" -> StyledVal.Bool((vEl?.text ?: "0") == "1")
                    else -> StyledVal.Num(vEl?.text?.toDoubleOrNull() ?: 0.0)
                }
                cells.add(StyledCell(ref, sv, style))
            }
        }
        return StyledSheet(name, cells, freezeRows, freezeCols, colWidths)
    }

    private class StyleRegistry {
        private val numFmtIds = linkedMapOf<String, Int>()
        private val fonts = mutableListOf(Triple(false, false, null as String?))
        private val fills = mutableListOf<String?>(null, "__GRAY125__")
        private val borders = mutableListOf(false)
        private val xfs = mutableListOf(Quad(0, 0, 0, 0))
        private val styleToXf = linkedMapOf(CellStyle() to 0)

        private data class Quad(val numFmtId: Int, val fontId: Int, val fillId: Int, val borderId: Int)

        fun intern(style: CellStyle): Int = styleToXf.getOrPut(style) {
            val nfId = if (style.numberFormat == null) 0 else {
                numFmtIds.getOrPut(style.numberFormat) { 164 + numFmtIds.size }
            }
            val fontKey = Triple(style.bold, style.italic, style.fontRgb?.uppercase())
            var fontId = fonts.indexOf(fontKey)
            if (fontId < 0) {
                fontId = fonts.size
                fonts.add(fontKey)
            }
            val fillId = if (style.fillRgb == null) 0 else {
                val up = style.fillRgb.uppercase()
                var idx = fills.indexOf(up)
                if (idx < 0) {
                    idx = fills.size
                    fills.add(up)
                }
                idx
            }
            var borderId = borders.indexOf(style.thinBorder)
            if (borderId < 0) {
                borderId = borders.size
                borders.add(style.thinBorder)
            }
            val xfIdx = xfs.size
            xfs.add(Quad(nfId, fontId, fillId, borderId))
            xfIdx
        }

        fun toXml(): ByteArray = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<styleSheet xmlns="$MAIN_NS">""")
            if (numFmtIds.isNotEmpty()) {
                append("""<numFmts count="${numFmtIds.size}">""")
                for ((code, id) in numFmtIds) {
                    append("""<numFmt numFmtId="$id" formatCode="${esc(code)}"/>""")
                }
                append("</numFmts>")
            }
            append("""<fonts count="${fonts.size}">""")
            for ((b, i, rgb) in fonts) {
                append("<font>")
                if (b) append("<b/>")
                if (i) append("<i/>")
                append("""<sz val="11"/><name val="Calibri"/>""")
                if (rgb != null) append("""<color rgb="FF$rgb"/>""")
                append("</font>")
            }
            append("</fonts>")
            append("""<fills count="${fills.size}">""")
            for (fl in fills) {
                when (fl) {
                    null -> append("""<fill><patternFill patternType="none"/></fill>""")
                    "__GRAY125__" -> append("""<fill><patternFill patternType="gray125"/></fill>""")
                    else -> append("""<fill><patternFill patternType="solid"><fgColor rgb="FF$fl"/></patternFill></fill>""")
                }
            }
            append("</fills>")
            append("""<borders count="${borders.size}">""")
            for (thin in borders) {
                if (!thin) {
                    append("<border><left/><right/><top/><bottom/><diagonal/></border>")
                } else {
                    append("""<border><left style="thin"/><right style="thin"/><top style="thin"/><bottom style="thin"/><diagonal/></border>""")
                }
            }
            append("</borders>")
            append("""<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""")
            append("""<cellXfs count="${xfs.size}">""")
            for (q in xfs) {
                append("""<xf numFmtId="${q.numFmtId}" fontId="${q.fontId}" fillId="${q.fillId}" borderId="${q.borderId}" xfId="0"""")
                if (q.numFmtId != 0) append(""" applyNumberFormat="1"""")
                if (q.fontId != 0) append(""" applyFont="1"""")
                if (q.fillId != 0) append(""" applyFill="1"""")
                if (q.borderId != 0) append(""" applyBorder="1"""")
                append("/>")
            }
            append("</cellXfs></styleSheet>")
        }.toByteArray(StandardCharsets.UTF_8)
    }

    private fun buildContentTypes(sheetCount: Int): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }.toByteArray(StandardCharsets.UTF_8)

    private fun buildRootRels(): ByteArray =
        ("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="$PKG_REL_NS">""" +
            """<Relationship Id="rId1" Type="$REL_NS/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>""").toByteArray(StandardCharsets.UTF_8)

    private fun buildWorkbookXml(spec: WorkbookSpec): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="$MAIN_NS" xmlns:r="$REL_NS"><sheets>""")
        spec.sheets.forEachIndexed { idx, s ->
            append("""<sheet name="${esc(s.name)}" sheetId="${idx + 1}" r:id="rSheet${idx + 1}"/>""")
        }
        append("</sheets>")
        if (spec.definedNames.isNotEmpty()) {
            append("<definedNames>")
            for (dn in spec.definedNames) {
                append("""<definedName name="${esc(dn.name)}">${esc(dn.reference)}</definedName>""")
            }
            append("</definedNames>")
        }
        append("</workbook>")
    }.toByteArray(StandardCharsets.UTF_8)

    private fun buildWorkbookRels(sheetCount: Int): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="$PKG_REL_NS">""")
        append("""<Relationship Id="rStyles" Type="$REL_NS/styles" Target="styles.xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rSheet$i" Type="$REL_NS/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("</Relationships>")
    }.toByteArray(StandardCharsets.UTF_8)

    private fun buildSheetXml(sheet: StyledSheet, styles: StyleRegistry): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="$MAIN_NS">""")
        if (sheet.freezeRows > 0 || sheet.freezeCols > 0) {
            val topLeftCol = colLabel(sheet.freezeCols + 1)
            val topLeftRow = sheet.freezeRows + 1
            append("""<sheetViews><sheetView workbookViewId="0"><pane""")
            if (sheet.freezeCols > 0) append(""" xSplit="${sheet.freezeCols}"""")
            if (sheet.freezeRows > 0) append(""" ySplit="${sheet.freezeRows}"""")
            append(""" topLeftCell="$topLeftCol$topLeftRow" activePane="bottomLeft" state="frozen"/>""")
            append("</sheetView></sheetViews>")
        }
        if (sheet.columnWidths.isNotEmpty()) {
            append("<cols>")
            for ((colIdx, width) in sheet.columnWidths.toSortedMap()) {
                append("""<col min="$colIdx" max="$colIdx" width="$width" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        val byRow = sheet.cells.groupBy { parseRef(it.ref).second }.toSortedMap()
        for ((rowNum, rowCells) in byRow) {
            append("""<row r="$rowNum">""")
            for (cell in rowCells.sortedBy { parseRef(it.ref).first }) {
                val xfIdx = styles.intern(cell.style)
                val sAttr = if (xfIdx > 0) """ s="$xfIdx"""" else ""
                when (val v = cell.value) {
                    is StyledVal.Num -> append("""<c r="${cell.ref}"$sAttr><v>${v.value}</v></c>""")
                    is StyledVal.Txt -> append("""<c r="${cell.ref}"$sAttr t="inlineStr"><is><t xml:space="preserve">${esc(v.value)}</t></is></c>""")
                    is StyledVal.Bool -> append("""<c r="${cell.ref}"$sAttr t="b"><v>${if (v.value) 1 else 0}</v></c>""")
                    is StyledVal.Formula -> {
                        val expr = v.expr.removePrefix("=")
                        append("""<c r="${cell.ref}"$sAttr><f>${esc(expr)}</f>""")
                        if (v.cachedNumber != null) append("""<v>${v.cachedNumber}</v>""")
                        append("</c>")
                    }
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }.toByteArray(StandardCharsets.UTF_8)

    private fun parseRef(ref: String): Pair<Int, Int> {
        val m = CELL_RE.matchEntire(ref.uppercase()) ?: error("Invalid cell reference: $ref")
        var col = 0
        for (ch in m.groupValues[1]) col = col * 26 + (ch - 'A' + 1)
        return col to m.groupValues[2].toInt()
    }

    private fun colLabel(oneBased: Int): String {
        var n = oneBased
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

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
}
