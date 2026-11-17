// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.SheetRange
import dev.fusion.ooxml.XlsxConditionalFormatVerdict
import dev.fusion.ooxml.XlsxConditionalFormatVerdict.Outcome
import dev.fusion.ooxml.XlsxConditionalFormatVerdict.Value
import dev.fusion.ooxml.XlsxConditionalFormatting
import dev.fusion.ooxml.XlsxConditionalFormatting.Alignment
import dev.fusion.ooxml.XlsxConditionalFormatting.Block
import dev.fusion.ooxml.XlsxConditionalFormatting.Border
import dev.fusion.ooxml.XlsxConditionalFormatting.BorderStyle
import dev.fusion.ooxml.XlsxConditionalFormatting.Edge
import dev.fusion.ooxml.XlsxConditionalFormatting.Format
import dev.fusion.ooxml.XlsxConditionalFormatting.Horizontal
import dev.fusion.ooxml.XlsxConditionalFormatting.Kind
import dev.fusion.ooxml.XlsxConditionalFormatting.Operator
import dev.fusion.ooxml.XlsxConditionalFormatting.PatternType
import dev.fusion.ooxml.XlsxConditionalFormatting.Protection
import dev.fusion.ooxml.XlsxConditionalFormatting.Rule
import dev.fusion.ooxml.XlsxConditionalFormatting.Vertical
import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R102 spreadsheet contracts: the differential format — the `<dxf>` entries that every painting `cfRule` points at.
 *
 * R96 modelled a dxf as "bold/italic/strike/underline, a font colour and a solid fill", which is what the
 * evaluator itself needed to say. R102 completes the element: font family and size, a number format, patterned
 * fills, a bordered box, alignment and protection. R100 and R101 then carry the result to the caller unchanged, so
 * a renderer gets the whole paint instruction instead of re-reading `styles.xml`.
 *
 * Five things are asserted, each by a mechanism that could fail on its own:
 *
 * 1. **The file.** Six formats are written into one workbook and their XML is read back out of the ZIP: the
 *    `CT_Dxf` section order, `<numFmt numFmtId formatCode>`, `<sz val>`/`<name val>`, each border edge with its
 *    colour, the alignment attributes, `<protection locked hidden>` and `<patternFill patternType>`.
 * 2. **The reader.** `XlsxStyledBook.read` must reproduce every format field for field, including a number format
 *    that the file states only as an id (`<numFmt numFmtId="164"/>` resolved through `<numFmts>`).
 * 3. **The model.** Twenty-five shapes that look like formats but are not are refused with a message.
 * 4. **The evaluator.** A cell where three rules fire must merge their dxfs field by field, first rule wins, and
 *    the merged result must carry every detail of every fired dxf.
 * 5. **The foreign package.** Constructs this model cannot hold — a gradient fill, `textRotation="255"`, an
 *    unknown border style — must make the dxf `null` rather than shift every later `dxfId`.
 *
 * Host JVM only. No Excel, LibreOffice or renderer ran: a `Detail` is what a dxf says, never what a screen shows.
 */
object R102Checks {
    private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PKG = "http://schemas.openxmlformats.org/package/2006/relationships"

    private var assertions = 0
    private var refusals = 0

    private inline fun check(ok: Boolean, message: () -> String) {
        assertions++
        require(ok) { "FAILED: " + message() }
    }

    private fun text(bytes: ByteArray): String = String(bytes, Charsets.ISO_8859_1)

    private fun part(bytes: ByteArray, entry: String): String {
        var found: String? = null
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { input ->
            while (found == null) {
                val current = input.nextEntry ?: break
                val payload = input.readBytes()
                if (current.name == entry) found = text(payload)
            }
        }
        return found ?: error("missing OPC part $entry")
    }

    private fun rejects(name: String, block: () -> Unit) {
        assertions++
        refusals++
        val threw = try {
            block()
            false
        } catch (expected: IllegalArgumentException) {
            check(expected.message?.isNotBlank() == true) { "$name refused without a message" }
            true
        }
        require(threw) { "FAILED: $name was accepted" }
    }

    private fun yes(value: Boolean?): String = when (value) {
        null -> "-"
        true -> "1"
        false -> "0"
    }

    private fun borderText(border: Border?): String {
        if (border == null) return "-"
        val sides = border.edges.joinToString(",") { (side, edge) -> "$side:${edge.style.token}:${edge.rgb ?: "-"}" }
        // `/` and `,` inside the field, so every description splits on `|` alone.
        return sides + "/" + (if (border.diagonalUp) "up" else "-") + "/" + (if (border.diagonalDown) "down" else "-")
    }

    private fun alignmentText(a: Alignment?): String =
        a?.let {
            listOf(
                it.horizontal?.token ?: "-",
                it.vertical?.token ?: "-",
                it.textRotation?.toString() ?: "-",
                yes(it.wrapText),
                it.indent?.toString() ?: "-",
                yes(it.shrinkToFit),
                it.readingOrder?.toString() ?: "-",
            ).joinToString(";")
        } ?: "-"

    private fun protectionText(p: Protection?): String =
        p?.let { "${yes(it.locked)};${yes(it.hidden)}" } ?: "-"

    /** Every field of a format, in one string, so a round trip is compared without trusting `equals`. */
    private fun describe(format: Format?): String {
        if (format == null) return "-"
        return listOf(
            yes(format.bold),
            yes(format.italic),
            yes(format.strike),
            yes(format.underline),
            format.fontRgb ?: "-",
            format.fontName ?: "-",
            format.fontSize?.toString() ?: "-",
            format.fillRgb ?: "-",
            format.fillPattern?.token ?: "-",
            format.fillBgRgb ?: "-",
            format.numberFormat ?: "-",
            borderText(format.border),
            alignmentText(format.alignment),
            protectionText(format.protection),
        ).joinToString("|")
    }

    /** The same eight fields as the evaluator carries them, so the decoration lane has one encoding as well. */
    private fun detailText(detail: XlsxConditionalFormatVerdict.Visual.Detail?): String =
        detail?.let {
            listOf(
                it.fontName ?: "-",
                it.fontSize?.toString() ?: "-",
                it.numberFormat ?: "-",
                it.fillPattern?.token ?: "-",
                it.fillBgRgb ?: "-",
                borderText(it.border),
                alignmentText(it.alignment),
                protectionText(it.protection),
            ).joinToString("|")
        } ?: "-"

    // ---------------------------------------------------------------- the six formats

    private val FONT_ONLY = Format(fontName = "Consolas", fontSize = 14.0)
    private val NUMFMT_ONLY = Format(numberFormat = "0.00%")
    private val BORDER_ONLY = Format(
        border = Border(
            top = Edge(BorderStyle.THIN, "00FF00"),
            bottom = Edge(BorderStyle.DOUBLE),
            right = Edge(BorderStyle.DASHED, "0000FF"),
        ),
    )
    private val LAYOUT_ONLY = Format(
        alignment = Alignment(
            horizontal = Horizontal.CENTER,
            vertical = Vertical.TOP,
            textRotation = 45,
            wrapText = true,
            indent = 2,
            readingOrder = 1,
        ),
        protection = Protection(locked = false, hidden = true),
    )
    private val PATTERN_ONLY = Format(fillRgb = "FF0000", fillPattern = PatternType.DARK_GRID, fillBgRgb = "FFFF00")
    private val KITCHEN_SINK = Format(
        bold = true,
        italic = true,
        strike = true,
        underline = true,
        fontRgb = "AA0000",
        fontName = "Courier New",
        fontSize = 11.5,
        fillRgb = "C6EFCE",
        fillPattern = PatternType.LIGHT_UP,
        fillBgRgb = "FFFFFF",
        numberFormat = "#,##0.00",
        border = Border(
            top = Edge(BorderStyle.MEDIUM, "FF0000"),
            left = Edge(BorderStyle.HAIR, "0000FF"),
            bottom = Edge(BorderStyle.DOUBLE, "00FF00"),
            diagonal = Edge(BorderStyle.SLANT_DASH_DOT, "808080"),
            diagonalUp = true,
            diagonalDown = true,
        ),
        alignment = Alignment(horizontal = Horizontal.RIGHT, vertical = Vertical.BOTTOM, shrinkToFit = false),
        protection = Protection(locked = true),
    )

    private val FORMATS = listOf(FONT_ONLY, NUMFMT_ONLY, BORDER_ONLY, LAYOUT_ONLY, PATTERN_ONLY, KITCHEN_SINK)

    private val PAINT_BLOCKS = listOf(
        Block(
            "A1:A6",
            FORMATS.mapIndexed { index, format ->
                Rule(kind = Kind.EXPRESSION, priority = index + 1, formulas = listOf("A${index + 1}>0"), format = format)
            },
        ),
    )

    // ---------------------------------------------------------------- merge lane

    private val MERGE_BLOCKS = listOf(
        Block(
            "B1:B1",
            listOf(
                // First rule sets the family and a number format.
                Rule(
                    kind = Kind.CELL_IS, priority = 1, operator = Operator.GREATER_THAN,
                    formulas = listOf("0"),
                    format = Format(fontName = "Georgia", numberFormat = "0.0"),
                ),
                // Second sets the size, a border and an alignment: none of which the first set.
                Rule(
                    kind = Kind.CELL_IS, priority = 2, operator = Operator.GREATER_THAN,
                    formulas = listOf("0"),
                    format = Format(
                        fontSize = 18.0,
                        border = Border(bottom = Edge(BorderStyle.THICK, "000000")),
                        alignment = Alignment(vertical = Vertical.CENTER),
                    ),
                ),
                // Third tries to override the family and the number format: both must be ignored.
                Rule(
                    kind = Kind.CELL_IS, priority = 3, operator = Operator.GREATER_THAN,
                    formulas = listOf("0"),
                    format = Format(fontName = "Impact", numberFormat = "0%", protection = Protection(hidden = true)),
                ),
            ),
        ),
    )

    private class MapGrid(private val values: Map<SheetRange.Cell, Value>) : XlsxConditionalFormatVerdict.Grid {
        override fun value(cell: SheetRange.Cell): Value = values[cell] ?: Value.Blank
    }

    // ---------------------------------------------------------------- the foreign package

    private fun foreignPackage(): ByteArray {
        val parts = linkedMapOf(
            "[Content_Types].xml" to
                """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""",
            "xl/workbook.xml" to
                """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="$MAIN" xmlns:r="$REL"><sheets><sheet name="Foreign" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to
                """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="$PKG"><Relationship Id="rId1" Type="$REL/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="$REL/styles" Target="styles.xml"/></Relationships>""",
            "xl/styles.xml" to
                """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="$MAIN">""" +
                """<numFmts count="1"><numFmt numFmtId="164" formatCode="0.000"/></numFmts>""" +
                """<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>""" +
                """<fills count="1"><fill><patternFill patternType="none"/></fill></fills>""" +
                """<borders count="1"><border/></borders>""" +
                """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
                """<cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>""" +
                """<dxfs count="5">""" +
                // 0: Excel's own decoration form — every flag carries an explicit val, including "off".
                """<dxf><font><b val="1"/><i val="0"/><u val="none"/><sz val="9"/><name val="Arial"/></font></dxf>""" +
                // 1: an id-only number format, resolvable only through <numFmts>.
                """<dxf><numFmt numFmtId="164"/></dxf>""" +
                // 2: a gradient fill — a dxf section this model does not hold, and nothing else to represent.
                """<dxf><fill><gradientFill degree="90"><stop position="0"><color rgb="FFFF0000"/></stop><stop position="1"><color rgb="FF0000FF"/></stop></gradientFill></fill></dxf>""" +
                // 3: vertical text, which is not a rotation; refused rather than read as 180 degrees.
                """<dxf><alignment textRotation="255"/></dxf>""" +
                // 4: an edge with an unknown style and a valid edge beside it: the unknown edge is dropped, the
                //    known one survives, and the dxf stays representable.
                """<dxf><border><left style="notAStyle"/><right style="medium"><color rgb="FF112233"/></right></border></dxf>""" +
                """</dxfs></styleSheet>""",
            "xl/worksheets/sheet1.xml" to
                """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="$MAIN"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>foreign</t></is></c></row></sheetData>""" +
                """<conditionalFormatting sqref="A1:A9"><cfRule type="expression" priority="1" dxfId="0"><formula>A1&gt;0</formula></cfRule></conditionalFormatting>""" +
                """<conditionalFormatting sqref="B1:B9"><cfRule type="expression" priority="2" dxfId="1"><formula>B1&gt;0</formula></cfRule></conditionalFormatting>""" +
                // A gradient-only dxf cannot be held, so this rule carries no format and must be refused: an
                // expression rule without a dxf changes nothing.
                """<conditionalFormatting sqref="C1:C9"><cfRule type="expression" priority="3" dxfId="2"><formula>C1&gt;0</formula></cfRule></conditionalFormatting>""" +
                """<conditionalFormatting sqref="D1:D9"><cfRule type="expression" priority="4" dxfId="3"><formula>D1&gt;0</formula></cfRule></conditionalFormatting>""" +
                """<conditionalFormatting sqref="E1:E9"><cfRule type="expression" priority="5" dxfId="4"><formula>E1&gt;0</formula></cfRule></conditionalFormatting>""" +
                """<conditionalFormatting sqref="F1:F9"><cfRule type="iconSet" priority="6"><iconSet iconSet="3Arrows"><cfvo type="percent" val="0"/><cfvo type="percent" val="33"/><cfvo type="percent" val="67"/></iconSet></cfRule></conditionalFormatting>""" +
                "</worksheet>",
        )
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            for ((name, xml) in parts) {
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val folder = File(args.firstOrNull() ?: "testing/build/r102/artifacts")
        folder.mkdirs()

        val paint = XlsxStyledBook.StyledSheet(
            name = "Paint",
            cells = (1..6).map {
                XlsxStyledBook.StyledCell("A$it", XlsxStyledBook.StyledVal.Num(it.toDouble()))
            },
            conditionalFormats = PAINT_BLOCKS,
        )
        val bytes = XlsxStyledBook.write(XlsxStyledBook.WorkbookSpec(sheets = listOf(paint)))
        File(folder, "r102.xlsx").writeBytes(bytes)
        val styles = part(bytes, "xl/styles.xml")
        val sheet1 = part(bytes, "xl/worksheets/sheet1.xml")

        // --- 1. the file ------------------------------------------------------------------------------------

        val dxfBlocks = Regex("<dxf>(.*?)</dxf>").findAll(styles).map { it.groupValues[1] }.toList()
        check(dxfBlocks.size == FORMATS.size) { "wrote ${dxfBlocks.size} dxfs, expected ${FORMATS.size}" }
        // CT_Dxf is a sequence; a shuffled one makes Excel offer to repair the file.
        val SECTION_ORDER = listOf("font", "numFmt", "fill", "alignment", "border", "protection")
        for ((index, body) in dxfBlocks.withIndex()) {
            val present = SECTION_ORDER.filter { section -> "<$section" in body }
            val positions = present.map { section -> body.indexOf("<$section") }
            check(positions == positions.sorted()) {
                "dxf $index writes its sections out of CT_Dxf order: $body"
            }
        }
        check("""<name val="Consolas"/>""" in dxfBlocks[0]) { "the font family was not written: ${dxfBlocks[0]}" }
        check("""<sz val="14"/>""" in dxfBlocks[0]) { "the font size was not written: ${dxfBlocks[0]}" }
        check("""<numFmt numFmtId="164" formatCode="0.00%"/>""" in dxfBlocks[1]) {
            "the number format was not written with its id and code: ${dxfBlocks[1]}"
        }
        // The id must also resolve in <numFmts>, or a reader that only follows ids sees General.
        check("""<numFmt numFmtId="164" formatCode="0.00%"/>""" in styles.substringBefore("<dxfs")) {
            "the dxf number format was not registered in <numFmts>"
        }
        check("""<numFmt numFmtId="165" formatCode="#,##0.00"/>""" in styles.substringBefore("<dxfs")) {
            "the second dxf number format did not get its own id"
        }
        check("""<top style="thin"><color rgb="FF00FF00"/></top>""" in dxfBlocks[2]) {
            "the coloured top edge was not written: ${dxfBlocks[2]}"
        }
        check("""<bottom style="double"/>""" in dxfBlocks[2]) {
            "an uncoloured edge must be written without a <color> child: ${dxfBlocks[2]}"
        }
        check("""<right style="dashed"><color rgb="FF0000FF"/></right>""" in dxfBlocks[2]) { "the right edge" }
        check(
            """<alignment horizontal="center" vertical="top" textRotation="45" wrapText="1" indent="2" readingOrder="1"/>""" in
                dxfBlocks[3],
        ) { "the alignment was not written: ${dxfBlocks[3]}" }
        check("""<protection locked="0" hidden="1"/>""" in dxfBlocks[3]) {
            "the protection was not written: ${dxfBlocks[3]}"
        }
        check("""<patternFill patternType="darkGrid">""" in dxfBlocks[4]) { "the pattern was not written" }
        check("""<fgColor rgb="FFFF0000"/>""" in dxfBlocks[4]) { "the pattern foreground" }
        check("""<bgColor rgb="FFFFFF00"/>""" in dxfBlocks[4]) { "the pattern background" }
        check("""<border diagonalUp="1" diagonalDown="1">""" in dxfBlocks[5]) {
            "the diagonal flags were not written: ${dxfBlocks[5]}"
        }
        check("""<diagonal style="slantDashDot">""" in dxfBlocks[5]) { "the diagonal edge" }
        check("""shrinkToFit="0"/>""" in dxfBlocks[5]) { "an explicit false must be written, not omitted" }
        check("""<protection locked="1"/>""" in dxfBlocks[5]) { "a protection with one flag set" }
        // A default-valued flag that nobody asked for must stay absent.
        check("wrapText" !in dxfBlocks[5]) { "an alignment that does not set wrapText wrote it anyway" }
        check("shrinkToFit" !in dxfBlocks[2]) { "an unrelated dxf wrote an alignment" }
        // The rules point at the six ids in order, and nothing else.
        val ids = Regex("""dxfId="(\d+)"""").findAll(sheet1).map { it.groupValues[1].toInt() }.toList()
        check(ids == listOf(0, 1, 2, 3, 4, 5)) { "the rules point at dxfs $ids" }

        // --- 2. the reader ----------------------------------------------------------------------------------

        val read = XlsxStyledBook.read(bytes).sheets.single { it.name == "Paint" }.conditionalFormats
            .flatMap { it.rules }
        check(read.size == FORMATS.size) { "read ${read.size} rules, wrote ${FORMATS.size}" }
        for ((index, format) in FORMATS.withIndex()) {
            val round = read[index].format
            check(describe(round) == describe(format)) {
                "dxf $index round-tripped as ${describe(round)}, wrote ${describe(format)}"
            }
        }

        // --- 3. the model -----------------------------------------------------------------------------------

        rejects("an empty format") { Format() }
        rejects("a font name of 32 characters") { Format(fontName = "x".repeat(32)) }
        rejects("an empty font name") { Format(fontName = "") }
        rejects("a font name with a control character") { Format(fontName = "Cal\u0001ibri") }
        rejects("a font size of 0") { Format(fontSize = 0.0) }
        rejects("a font size below 1") { Format(fontSize = 0.5) }
        rejects("a font size above 409") { Format(fontSize = 410.24) }
        rejects("a non-finite font size") { Format(fontSize = Double.NaN) }
        rejects("an empty number format") { Format(numberFormat = "") }
        rejects("a number format with a newline") { Format(numberFormat = "0.0\n") }
        rejects("a number format of 256 characters") { Format(numberFormat = "0".repeat(256)) }
        rejects("a background colour without a pattern") { Format(fillBgRgb = "FFFFFF") }
        rejects("a solid pattern without a foreground") { Format(fillPattern = PatternType.SOLID) }
        rejects("a border with no edges") { Format(border = Border()) }
        rejects("a diagonal flag with no diagonal edge") { Format(border = Border(top = Edge(BorderStyle.THIN), diagonalUp = true)) }
        rejects("a diagonal edge with neither flag") { Format(border = Border(diagonal = Edge(BorderStyle.THIN))) }
        rejects("a border colour of five hex digits") { Format(border = Border(top = Edge(BorderStyle.THIN, "12345"))) }
        rejects("an alignment that sets nothing") { Format(alignment = Alignment()) }
        rejects("a text rotation of 255") {
            Format(alignment = Alignment(textRotation = 255))
        }
        rejects("a text rotation of 181") { Format(alignment = Alignment(textRotation = 181)) }
        rejects("a negative indent") { Format(alignment = Alignment(indent = -1)) }
        rejects("an indent above 250") { Format(alignment = Alignment(indent = 251)) }
        rejects("a reading order of 3") { Format(alignment = Alignment(readingOrder = 3)) }
        rejects("a protection that sets nothing") { Format(protection = Protection()) }
        rejects("a font colour of five hex digits") { Format(fontRgb = "12345") }

        // Every enum token is a schema token, and no two share one.
        check(BorderStyle.entries.map { it.token }.toSet().size == BorderStyle.entries.size) { "duplicate border token" }
        check(BorderStyle.entries.size == 13) { "ST_BorderStyle has 13 non-none values, have ${BorderStyle.entries.size}" }
        check(PatternType.entries.size == 18) { "ST_PatternType has 18 non-none values, have ${PatternType.entries.size}" }
        check(Horizontal.entries.size == 8) { "ST_HorizontalAlignment has 8 values, have ${Horizontal.entries.size}" }
        check(Vertical.entries.size == 5) { "ST_VerticalAlignment has 5 values, have ${Vertical.entries.size}" }

        // --- 4. the evaluator -------------------------------------------------------------------------------

        val mergeCell = SheetRange.parseCell("B1")
        val mergeGrid = MapGrid(mapOf(mergeCell to Value.Num(7.0)))
        val merged = XlsxConditionalFormatVerdict.verdict(MERGE_BLOCKS, mergeGrid, mergeCell)
        check(merged is Outcome.Decided) { "the merge cell did not decide: $merged" }
        val decided = merged as Outcome.Decided
        check(decided.fired.size == 3) { "expected 3 fired rules, got ${decided.fired.size}" }
        val painted = XlsxConditionalFormatVerdict.decoration(decided)
        val detail = painted.detail
        check(detail != null) { "the decoration carried no detail" }
        val mergedDetail = detail ?: error("no detail")
        check(mergedDetail.fontName == "Georgia") { "first rule must own the family, got ${mergedDetail.fontName}" }
        check(mergedDetail.numberFormat == "0.0") { "first rule must own the number format, got ${mergedDetail.numberFormat}" }
        check(mergedDetail.fontSize == 18.0) { "the second rule's size was lost: ${mergedDetail.fontSize}" }
        check(mergedDetail.border == Border(bottom = Edge(BorderStyle.THICK, "000000"))) {
            "the second rule's border was lost: ${mergedDetail.border}"
        }
        check(mergedDetail.alignment == Alignment(vertical = Vertical.CENTER)) {
            "the second rule's alignment was lost: ${mergedDetail.alignment}"
        }
        check(mergedDetail.protection == Protection(hidden = true)) {
            "the third rule's protection was lost: ${mergedDetail.protection}"
        }
        // Higher-priority rules must not be able to take a field back by omitting it.
        check(mergedDetail.fontName != "Impact") { "a lower-priority rule overrode the font family" }
        check(mergedDetail.numberFormat != "0%") { "a lower-priority rule overrode the number format" }

        // A format with no detail fields must not add a Detail visual at all.
        val plainCell = SheetRange.parseCell("A1")
        val plainBlocks = listOf(
            Block("A1", listOf(Rule(kind = Kind.CELL_IS, priority = 1, operator = Operator.GREATER_THAN,
                formulas = listOf("0"), format = Format(bold = true, fillRgb = "FFC7CE")))),
        )
        val plain = XlsxConditionalFormatVerdict.verdict(plainBlocks, MapGrid(mapOf(plainCell to Value.Num(1.0))), plainCell)
        val plainPaint = XlsxConditionalFormatVerdict.decoration(plain as Outcome.Decided)
        check(plainPaint.detail == null) { "a decoration-only dxf produced a detail: ${plainPaint.detail}" }
        check(plainPaint.bold && plainPaint.fillRgb == "FFC7CE") { "the decoration itself was lost: $plainPaint" }

        // --- 5. the foreign package -------------------------------------------------------------------------

        val foreignBytes = foreignPackage()
        File(folder, "r102-foreign.xlsx").writeBytes(foreignBytes)
        val foreign = XlsxStyledBook.read(foreignBytes).sheets.single().conditionalFormats.flatMap { it.rules }
        check(foreign.size == 4) { "expected 4 surviving foreign rules, got ${foreign.size}: ${foreign.map { it.priority }}" }
        check(foreign.map { it.priority } == listOf(1, 2, 5, 6)) {
            "surviving priorities: ${foreign.map { it.priority }}"
        }
        val byPriority = foreign.associateBy { it.priority }
        byPriority[1]?.let { rule ->
            val format = rule.format ?: error("priority 1 lost its format")
            check(format.bold) { "an explicit b val=1 was not read as bold" }
            check(!format.italic) { "an explicit i val=0 was read as italic" }
            check(!format.underline) { "u val=none was read as an underline" }
            check(format.fontSize == 9.0) { "sz val=9 read as ${format.fontSize}" }
            check(format.fontName == "Arial") { "name val=Arial read as ${format.fontName}" }
        }
        byPriority[2]?.let { rule ->
            check(rule.format?.numberFormat == "0.000") {
                "an id-only numFmt did not resolve through <numFmts>: ${rule.format?.numberFormat}"
            }
        }
        byPriority[5]?.let { rule ->
            val border = rule.format?.border ?: error("priority 5 lost its border")
            check(border.right == Edge(BorderStyle.MEDIUM, "112233")) { "the known edge: ${border.right}" }
            check(border.left == null) { "an unknown border style must be dropped, not guessed: ${border.left}" }
        }
        byPriority[6]?.let { rule ->
            check(rule.format == null) { "an iconSet rule with no dxf gained one: ${rule.format}" }
        }

        // --- 6. intent tables -------------------------------------------------------------------------------

        val formatRows = StringBuilder()
        for (block in PAINT_BLOCKS) {
            for ((index, rule) in block.rules.withIndex()) {
                formatRows.append(
                    listOf(
                        block.sqref,
                        rule.priority.toString(),
                        rule.kind.token,
                        index.toString(),
                        describe(rule.format),
                    ).joinToString("\t"),
                ).append('\n')
            }
        }
        File(folder, "formats.tsv").writeText(formatRows.toString(), Charsets.UTF_8)

        val mergeRows = StringBuilder()
        for (rule in MERGE_BLOCKS[0].rules) {
            val format = rule.format ?: error("a merge rule has no format")
            mergeRows.append(
                listOf(
                    rule.priority.toString(),
                    detailText(
                        XlsxConditionalFormatVerdict.Visual.Detail(
                            fontName = format.fontName,
                            fontSize = format.fontSize,
                            numberFormat = format.numberFormat,
                            fillPattern = format.fillPattern,
                            fillBgRgb = format.fillBgRgb,
                            border = format.border,
                            alignment = format.alignment,
                            protection = format.protection,
                        ),
                    ),
                ).joinToString("\t"),
            ).append('\n')
        }
        File(folder, "merge.tsv").writeText(mergeRows.toString(), Charsets.UTF_8)

        File(folder, "detail.tsv").writeText(
            "B1\tmerged\t" + detailText(mergedDetail) + "\n" +
                "A1\tplain\t-\n",
            Charsets.UTF_8,
        )

        val summary = "R102_DXF_OK assertions=$assertions refusals=$refusals formats=${FORMATS.size} " +
            "sections=${SECTION_ORDER.size} merged=${decided.fired.size} foreign=${foreign.size}"
        File(folder, "summary.txt").writeText(summary + "\n", Charsets.UTF_8)
        println(summary)
    }
}
