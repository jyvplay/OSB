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

    const val MAX_ARCHIVE_BYTES = 16 * 1024 * 1024
    const val MAX_PART_BYTES = 8 * 1024 * 1024
    const val MAX_EXPANDED_BYTES = 32L * 1024 * 1024
    const val MAX_ENTRIES = 2_048
    const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    /** Excel's own row-height ceiling in points; `ht` beyond it is not a legal measure. */
    const val MAX_ROW_HEIGHT_POINTS = 409.0
    /** Calibri 11 pt is the workbook default this writer has always emitted when no size is declared. */
    private const val DEFAULT_FONT_POINTS = 11.0
    const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"

    data class CellStyle(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val fontRgb: String? = null,
        val fillRgb: String? = null,
        val thinBorder: Boolean = false,
        val numberFormat: String? = null,
        /** CT_Font `<sz val>` in points, 1.0..409.0; `null` keeps the workbook default of 11 pt. */
        val fontSize: Double? = null,
        val horizontal: HAlign? = null,
        val vertical: VAlign? = null,
    ) {
        /** ECMA-376 `horizontal` tokens this writer emits; other schema tokens are import-only. */
        enum class HAlign(val token: String) { LEFT("left"), CENTER("center"), RIGHT("right") }

        /** ECMA-376 `vertical` tokens this writer emits. */
        enum class VAlign(val token: String) { TOP("top"), CENTER("center"), BOTTOM("bottom") }
        init {
            fontRgb?.let { require(HEX6.matches(it)) { "fontRgb must be 6-hex RGB: $it" } }
            fillRgb?.let { require(HEX6.matches(it)) { "fillRgb must be 6-hex RGB: $it" } }
            fontSize?.let { require(it in 1.0..409.0) { "fontSize $it outside 1..409 points" } }
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

    /**
     * CT_SheetState. `visible` is the schema default and is therefore never written, which keeps every
     * workbook that does not hide a sheet byte-identical to the pre-R84 output.
     */
    enum class Visibility(val token: String) { VISIBLE("visible"), HIDDEN("hidden"), VERY_HIDDEN("veryHidden") }

    data class StyledSheet(
        val name: String,
        val cells: List<StyledCell>,
        val freezeRows: Int = 0,
        val freezeCols: Int = 0,
        val columnWidths: Map<Int, Double> = emptyMap(),
        val rowHeights: Map<Int, Double> = emptyMap(),
        val hiddenRows: Set<Int> = emptySet(),
        val hiddenColumns: Set<Int> = emptySet(),
        val visibility: Visibility = Visibility.VISIBLE,
        /** 6-hex RGB tab colour; written as `FF<hex>` inside `<sheetPr><tabColor>`. */
        val tabRgb: String? = null,
        /** Sheet-view zoom percentage; Excel's UI window is 10..400. */
        val zoomScale: Int? = null,
        /** CT_SheetView `showGridLines`; `null` leaves Excel's default (gridlines shown) unwritten. */
        val showGridLines: Boolean? = null,
    ) {
        init {
            require(name.isNotBlank() && name.length <= 31) { "Invalid worksheet name: $name" }
            require(freezeRows >= 0 && freezeCols >= 0) { "Negative freeze pane split" }
            tabRgb?.let { require(HEX6.matches(it)) { "tabRgb must be 6-hex RGB: $it" } }
            zoomScale?.let { require(it in 10..400) { "zoomScale $it outside 10..400" } }
            hiddenRows.forEach { row ->
                require(row in 1..1_048_576) { "Hidden row index $row outside 1..1048576" }
            }
            hiddenColumns.forEach { column ->
                require(column in 1..16_384) { "Hidden column index $column outside 1..16384" }
            }
            rowHeights.forEach { (row, height) ->
                require(row in 1..1_048_576) { "Row height index $row outside 1..1048576" }
                require(height > 0.0 && height <= MAX_ROW_HEIGHT_POINTS) {
                    "Row height $height outside 0..$MAX_ROW_HEIGHT_POINTS points"
                }
            }
        }
    }

    data class WorkbookSpec(
        val sheets: List<StyledSheet>,
        val definedNames: List<DefinedName> = emptyList(),
        /** CT_BookView `showSheetTabs`; `null` leaves the schema default (tabs shown) unwritten. */
        val showSheetTabs: Boolean? = null,
        /** CT_BookView `firstSheet`: the index of the leftmost tab, which must be a visible sheet. */
        val firstSheet: Int? = null,
    ) {
        init {
            require(sheets.isNotEmpty()) { "Workbook requires at least one sheet" }
            // Excel (and openpyxl's own writer) refuses a workbook with no visible sheet: the tab strip
            // would be empty and the file could not be reopened into a usable state.
            require(sheets.any { it.visibility == Visibility.VISIBLE }) {
                "A workbook must keep at least one visible sheet"
            }
            // `firstSheet` names the leftmost tab. A hidden sheet there is not a tab, and Excel
            // repairs such a workbook on open, so it is refused rather than written.
            firstSheet?.let { index ->
                require(index in sheets.indices) { "firstSheet $index outside 0..${sheets.size - 1}" }
                require(sheets[index].visibility == Visibility.VISIBLE) {
                    "firstSheet $index points at a hidden sheet"
                }
            }
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
        val entries = BoundedZip.read(
            bytes = bytes,
            archiveBytes = MAX_ARCHIVE_BYTES,
            partBytes = MAX_PART_BYTES,
            expandedBytes = MAX_EXPANDED_BYTES,
            entries = MAX_ENTRIES,
            describe = "XLSX",
        ) { name ->
            require(!name.contains("..") && !name.endsWith("vbaProject.bin", ignoreCase = true)) {
                "Refusing unsafe/macro XLSX entry: $name"
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

        val view = wbRoot.firstChild("bookViews")?.firstChild("workbookView")
        val showSheetTabs = when (view?.attr("showSheetTabs")) {
            null -> null
            "0", "false" -> false
            else -> true
        }
        val sheetElems = wbRoot.firstChild("sheets")?.children?.filter { it.localName == "sheet" }.orEmpty()
        val sheets = sheetElems.mapIndexed { idx, sEl ->
            val name = sEl.attr("name") ?: "Sheet${idx + 1}"
            val shBytes = entries["xl/worksheets/sheet${idx + 1}.xml"]
                ?: error("Missing xl/worksheets/sheet${idx + 1}.xml")
            val visibility = when (sEl.attr("state")) {
                null, "visible" -> Visibility.VISIBLE
                "hidden" -> Visibility.HIDDEN
                "veryHidden" -> Visibility.VERY_HIDDEN
                else -> Visibility.VISIBLE
            }
            parseSheet(name, BoundedXml.parse(shBytes), styles, visibility)
        }
        // A firstSheet that no longer names a visible sheet is dropped on import: the sheets are all
        // still readable, and the constructor refuses that value for the same repair reason it is
        // refused on write.
        val firstSheet = view?.attr("firstSheet")?.toIntOrNull()
            ?.takeIf { it in sheets.indices && sheets[it].visibility == Visibility.VISIBLE }
        return WorkbookSpec(sheets = sheets, definedNames = defined, showSheetTabs = showSheetTabs,
            firstSheet = firstSheet)
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
        data class FontRec(val bold: Boolean, val italic: Boolean, val rgb: String?, val size: Double?)
        val fonts = root.firstChild("fonts")?.children?.filter { it.localName == "font" }?.map { f ->
            val rgb = f.firstChild("color")?.attr("rgb")?.takeLast(6)
            // 11 pt is the workbook default when a font carries no `<sz>`; only a declared size is
            // reported, so a foreign 11 pt font and an undeclared one both read back as `null`.
            val size = f.firstChild("sz")?.attr("val")?.toDoubleOrNull()?.takeIf { it in 1.0..409.0 && it != 11.0 }
            FontRec(f.firstChild("b") != null, f.firstChild("i") != null, rgb, size)
        }.orEmpty()
        val fills = root.firstChild("fills")?.children?.filter { it.localName == "fill" }?.map { fl ->
            val pf = fl.firstChild("patternFill")
            if (pf?.attr("patternType") == "solid") pf.firstChild("fgColor")?.attr("rgb")?.takeLast(6) else null
        }.orEmpty()
        val borders = root.firstChild("borders")?.children?.filter { it.localName == "border" }?.map { bd ->
            bd.firstChild("left")?.attr("style") == "thin"
        }.orEmpty()

        return root.firstChild("cellXfs")?.children?.filter { it.localName == "xf" }?.map { xf ->
            val align = xf.firstChild("alignment")
            val horizontal = when (align?.attr("horizontal")) {
                "left", "start" -> CellStyle.HAlign.LEFT
                "center", "centerContinuous" -> CellStyle.HAlign.CENTER
                "right", "end" -> CellStyle.HAlign.RIGHT
                else -> null
            }
            val vertical = when (align?.attr("vertical")) {
                "top" -> CellStyle.VAlign.TOP
                "center" -> CellStyle.VAlign.CENTER
                "bottom" -> CellStyle.VAlign.BOTTOM
                else -> null
            }
            val fIdx = xf.attr("fontId")?.toIntOrNull() ?: 0
            val flIdx = xf.attr("fillId")?.toIntOrNull() ?: 0
            val bIdx = xf.attr("borderId")?.toIntOrNull() ?: 0
            val nfId = xf.attr("numFmtId")?.toIntOrNull() ?: 0
            val font = fonts.getOrNull(fIdx)
            CellStyle(
                bold = font?.bold ?: false,
                italic = font?.italic ?: false,
                fontRgb = font?.rgb,
                fontSize = font?.size,
                fillRgb = fills.getOrNull(flIdx),
                thinBorder = borders.getOrNull(bIdx) ?: false,
                numberFormat = numFmts[nfId],
                horizontal = horizontal,
                vertical = vertical,
            )
        }.orEmpty().ifEmpty { listOf(CellStyle()) }
    }

    private fun parseSheet(
        name: String,
        root: BoundedXml.Node,
        styles: List<CellStyle>,
        visibility: Visibility = Visibility.VISIBLE,
    ): StyledSheet {
        // Foreign packages may colour a tab with a theme/indexed colour instead of `rgb`; only a full
        // 6-hex RGB value fits this model, so anything else is left unset rather than guessed.
        val tabRgb = root.firstChild("sheetPr")?.firstChild("tabColor")?.attr("rgb")
            ?.takeLast(6)?.takeIf { HEX6.matches(it) }?.uppercase()
        val pane = root.firstChild("sheetViews")?.firstChild("sheetView")?.firstChild("pane")
        val freezeRows = pane?.attr("ySplit")?.toIntOrNull() ?: 0
        val freezeCols = pane?.attr("xSplit")?.toIntOrNull() ?: 0
        val zoomScale = root.firstChild("sheetViews")?.firstChild("sheetView")
            ?.attr("zoomScale")?.toIntOrNull()?.takeIf { it in 10..400 }
        val showGridLines = when (root.firstChild("sheetViews")?.firstChild("sheetView")?.attr("showGridLines")) {
            null -> null
            "0", "false" -> false
            else -> true
        }
        val colWidths = linkedMapOf<Int, Double>()
        val hiddenColumns = linkedSetOf<Int>()
        root.firstChild("cols")?.children?.filter { it.localName == "col" }?.forEach { col ->
            val min = col.attr("min")?.toIntOrNull()
            val w = col.attr("width")?.toDoubleOrNull()
            if (min != null && w != null) colWidths[min] = w
            if (min != null && isOn(col.attr("hidden"))) hiddenColumns.add(min)
        }
        val cells = mutableListOf<StyledCell>()
        val rowHeights = linkedMapOf<Int, Double>()
        val hiddenRows = linkedSetOf<Int>()
        for (row in root.firstChild("sheetData")?.children?.filter { it.localName == "row" }.orEmpty()) {
            val rowNumber = row.attr("r")?.toIntOrNull()
            val height = row.attr("ht")?.toDoubleOrNull()
            if (rowNumber != null && height != null && height > 0.0) rowHeights[rowNumber] = height
            if (rowNumber != null && isOn(row.attr("hidden"))) hiddenRows.add(rowNumber)
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
        return StyledSheet(
            name = name,
            cells = cells,
            freezeRows = freezeRows,
            freezeCols = freezeCols,
            columnWidths = colWidths,
            rowHeights = rowHeights,
            hiddenRows = hiddenRows,
            hiddenColumns = hiddenColumns,
            visibility = visibility,
            tabRgb = tabRgb,
            zoomScale = zoomScale,
            showGridLines = showGridLines,
        )
    }

    /**
     * ST_OnOff admits `1`/`true` (and `0`/`false`); this writer emits `1` only, but a foreign package may
     * say `true`, and reading that as visible would silently unhide a row or column on import.
     */
    private fun isOn(value: String?): Boolean = value == "1" || value.equals("true", ignoreCase = true)

    private class StyleRegistry {
        private val numFmtIds = linkedMapOf<String, Int>()
        private data class FontKey(
            val bold: Boolean = false,
            val italic: Boolean = false,
            val rgb: String? = null,
            val size: Double? = null,
        )

        private val fonts = mutableListOf(FontKey())
        private val fills = mutableListOf<String?>(null, "__GRAY125__")
        private val borders = mutableListOf(false)
        private val xfs = mutableListOf(Quad(0, 0, 0, 0))
        private val styleToXf = linkedMapOf(CellStyle() to 0)

        private data class Quad(
            val numFmtId: Int,
            val fontId: Int,
            val fillId: Int,
            val borderId: Int,
            val horizontal: CellStyle.HAlign? = null,
            val vertical: CellStyle.VAlign? = null,
        )

        fun intern(style: CellStyle): Int = styleToXf.getOrPut(style) {
            val nfId = if (style.numberFormat == null) 0 else {
                numFmtIds.getOrPut(style.numberFormat) { 164 + numFmtIds.size }
            }
            val fontKey = FontKey(style.bold, style.italic, style.fontRgb?.uppercase(), style.fontSize)
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
            xfs.add(Quad(nfId, fontId, fillId, borderId, style.horizontal, style.vertical))
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
            for (font in fonts) {
                append("<font>")
                if (font.bold) append("<b/>")
                if (font.italic) append("<i/>")
                // `<sz>` keeps its pre-R89 position; only the value becomes declarable. `name` is not
                // modelled, so the workbook default family is still written after the size.
                append("""<sz val="${points(font.size ?: DEFAULT_FONT_POINTS)}"/><name val="Calibri"/>""")
                if (font.rgb != null) append("""<color rgb="FF${font.rgb}"/>""")
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
                if (q.horizontal == null && q.vertical == null) {
                    append("/>")
                    continue
                }
                append(""" applyAlignment="1">""")
                append("<alignment")
                q.horizontal?.let { append(""" horizontal="${it.token}"""") }
                q.vertical?.let { append(""" vertical="${it.token}"""") }
                append("/></xf>")
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
        append("""<workbook xmlns="$MAIN_NS" xmlns:r="$REL_NS">""")
        val hidden = spec.sheets.any { it.visibility != Visibility.VISIBLE }
        if (hidden || spec.showSheetTabs != null || spec.firstSheet != null) {
            // A workbook whose active tab is hidden is repaired on open, and the default active tab is the
            // first sheet. Naming the first visible sheet keeps the file well formed; a workbook that
            // hides nothing and declares no view option writes no bookViews at all, so its bytes stay
            // identical to the pre-R84 output.
            val activeTab = spec.sheets.indexOfFirst { it.visibility == Visibility.VISIBLE }
            append("<bookViews><workbookView")
            spec.showSheetTabs?.let { append(""" showSheetTabs="${if (it) 1 else 0}"""") }
            spec.firstSheet?.let { append(""" firstSheet="$it"""") }
            if (hidden) append(""" activeTab="$activeTab"""")
            append("/></bookViews>")
        }
        append("<sheets>")
        spec.sheets.forEachIndexed { idx, s ->
            val state = if (s.visibility == Visibility.VISIBLE) "" else
                """ state="${s.visibility.token}""""
            append("""<sheet name="${esc(s.name)}" sheetId="${idx + 1}"$state r:id="rSheet${idx + 1}"/>""")
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
        // CT_Worksheet order: sheetPr, dimension, sheetViews, sheetFormatPr, cols, sheetData. A tab
        // colour is written only when declared, so legacy sheet bytes are unchanged.
        sheet.tabRgb?.let { append("""<sheetPr><tabColor rgb="FF${it.uppercase()}"/></sheetPr>""") }
        val frozen = sheet.freezeRows > 0 || sheet.freezeCols > 0
        if (frozen || sheet.zoomScale != null || sheet.showGridLines != null) {
            append("""<sheetViews><sheetView workbookViewId="0"""")
            sheet.zoomScale?.let { append(""" zoomScale="$it"""") }
            sheet.showGridLines?.let { append(""" showGridLines="${if (it) 1 else 0}"""") }
            if (!frozen) {
                append("/></sheetViews>")
            } else {
                val topLeftCol = colLabel(sheet.freezeCols + 1)
                val topLeftRow = sheet.freezeRows + 1
                append("><pane")
                if (sheet.freezeCols > 0) append(""" xSplit="${sheet.freezeCols}"""")
                if (sheet.freezeRows > 0) append(""" ySplit="${sheet.freezeRows}"""")
                append(""" topLeftCell="$topLeftCol$topLeftRow" activePane="bottomLeft" state="frozen"/>""")
                append("</sheetView></sheetViews>")
            }
        }
        if (sheet.columnWidths.isNotEmpty() || sheet.hiddenColumns.isNotEmpty()) {
            append("<cols>")
            // One <col> per declared column, ordered by index; a column may be wide, hidden, or both.
            for (colIdx in (sheet.columnWidths.keys + sheet.hiddenColumns).toSortedSet()) {
                append("""<col min="$colIdx" max="$colIdx"""")
                sheet.columnWidths[colIdx]?.let { append(""" width="$it" customWidth="1"""") }
                if (colIdx in sheet.hiddenColumns) append(""" hidden="1"""")
                append("/>")
            }
            append("</cols>")
        }
        append("<sheetData>")
        val byRow = sheet.cells.groupBy { parseRef(it.ref).second }.toSortedMap()
        val hiddenRows = sheet.hiddenRows
        for (rowNum in (byRow.keys + sheet.rowHeights.keys + hiddenRows).toSortedSet()) {
            val rowCells = byRow[rowNum].orEmpty()
            val height = sheet.rowHeights[rowNum]
            val heightAttr = if (height == null) "" else " ht=\"$height\" customHeight=\"1\""
            val hiddenAttr = if (rowNum in hiddenRows) " hidden=\"1\"" else ""
            append("""<row r="$rowNum"$heightAttr$hiddenAttr>""")
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

    /** XML Schema `double` spelling without a trailing `.0`, so 11.0 stays `11` and 10.5 stays `10.5`. */
    private fun points(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

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
