// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.XlsxConditionalFormatting
import dev.fusion.ooxml.XlsxConditionalFormatting.Block
import dev.fusion.ooxml.XlsxConditionalFormatting.Format
import dev.fusion.ooxml.XlsxConditionalFormatting.Kind
import dev.fusion.ooxml.XlsxConditionalFormatting.Operator
import dev.fusion.ooxml.XlsxConditionalFormatting.Rule
import dev.fusion.ooxml.XlsxConditionalFormatting.Threshold
import dev.fusion.ooxml.XlsxConditionalFormatting.ThresholdKind
import dev.fusion.ooxml.XlsxMerges
import dev.fusion.ooxml.XlsxStyledBook
import dev.fusion.ooxml.XlsxValidation
import java.io.File

/**
 * R96 spreadsheet contracts: conditional formatting (`<conditionalFormatting>`/`<cfRule>`, §18.3.1.18/§18.3.1.10)
 * and the differential-format table it points at (`<dxfs>` in `styles.xml`, §18.8.4).
 *
 * Host JVM only. The probe writes real `.xlsx` packages plus two intent tables (`conditional.tsv`, `dxfs.tsv`).
 * Independent witnesses: `tools/check_r96_sheet_conditional.py` (raw OPC walk + openpyxl's
 * `worksheet.conditional_formatting`) and `tools/check_r96_sheet_dxf.py` (raw OPC walk + openpyxl's
 * `_differential_styles`). No Excel or LibreOffice run: nothing here says a cell was highlighted.
 */
object R96Checks {
    private var assertions = 0

    private inline fun check(ok: Boolean, message: () -> String) {
        assertions++
        require(ok) { "FAILED: " + message() }
    }

    private fun text(bytes: ByteArray): String = String(bytes, Charsets.ISO_8859_1)

    private fun partOrNull(bytes: ByteArray, entry: String): String? {
        var found: String? = null
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { input ->
            while (found == null) {
                val current = input.nextEntry ?: break
                val payload = input.readBytes()
                if (current.name == entry) found = text(payload)
            }
        }
        return found
    }

    private fun part(bytes: ByteArray, entry: String): String =
        partOrNull(bytes, entry) ?: error("missing OPC part $entry")

    private fun cell(ref: String, value: String) = XlsxStyledBook.StyledCell(ref, XlsxStyledBook.StyledVal.Txt(value))

    private val RED = Format(fillRgb = "FFC7CE")
    private val BOLD = Format(bold = true)
    private val ITALIC_FONT = Format(italic = true, fontRgb = "9C0006")

    /** Five rule kinds on one sheet, each carrying what its kind requires. */
    private val GRID_RULES = listOf(
        Block(
            sqref = "A1:A10",
            rules = listOf(
                Rule(
                    kind = Kind.CELL_IS,
                    priority = 1,
                    stopIfTrue = true,
                    operator = Operator.GREATER_THAN,
                    formulas = listOf("5"),
                    format = RED,
                ),
                Rule(
                    kind = Kind.EXPRESSION,
                    priority = 2,
                    stopIfTrue = false,
                    formulas = listOf("MOD(ROW(),2)=0"),
                    format = BOLD,
                ),
            ),
        ),
        Block(
            sqref = "B1:B5",
            rules = listOf(
                Rule(
                    kind = Kind.COLOR_SCALE,
                    priority = 3,
                    thresholds = listOf(Threshold(ThresholdKind.MIN), Threshold(ThresholdKind.MAX)),
                    colors = listOf("F8696B", "63BE7B"),
                ),
            ),
        ),
        Block(
            sqref = "C1:C9",
            rules = listOf(
                Rule(
                    kind = Kind.DATA_BAR,
                    priority = 4,
                    thresholds = listOf(
                        Threshold(ThresholdKind.NUM, 0.0),
                        Threshold(ThresholdKind.NUM, 100.0),
                    ),
                    colors = listOf("638EC6"),
                ),
            ),
        ),
        Block(
            sqref = "D1:D4",
            rules = listOf(
                Rule(
                    kind = Kind.TOP10,
                    priority = 5,
                    rank = 3,
                    percent = false,
                    bottom = true,
                    format = ITALIC_FONT,
                ),
            ),
        ),
    )

    /** A three-stop scale and a `between` rule, plus a duplicate format that must fold into one dxf. */
    private val TWO_RULES = listOf(
        Block(
            sqref = "A1:C6",
            rules = listOf(
                Rule(
                    kind = Kind.CELL_IS,
                    priority = 7,
                    operator = Operator.BETWEEN,
                    formulas = listOf("10", "20"),
                    format = RED,
                ),
                Rule(
                    kind = Kind.EXPRESSION,
                    priority = 8,
                    stopIfTrue = true,
                    formulas = listOf("\$A1>0"),
                    format = RED,
                ),
            ),
        ),
        Block(
            sqref = "E1:E9 D1:D4",
            rules = listOf(
                Rule(
                    kind = Kind.COLOR_SCALE,
                    priority = 9,
                    thresholds = listOf(
                        Threshold(ThresholdKind.MIN),
                        Threshold(ThresholdKind.PERCENT, 50.0),
                        Threshold(ThresholdKind.MAX),
                    ),
                    colors = listOf("F8696B", "FFEB84", "63BE7B"),
                ),
            ),
        ),
    )

    private fun tsv(value: String?): String = value ?: "-"

    private fun formatSignature(format: Format?): String =
        if (format == null) {
            "-"
        } else {
            listOf(
                if (format.bold) "b" else "",
                if (format.italic) "i" else "",
                if (format.strike) "s" else "",
                if (format.underline) "u" else "",
                format.fontRgb ?: "",
                format.fillRgb ?: "",
            ).joinToString(";")
        }

    /** The dxf ids a writer assigns: one per distinct format, in first-use order across the workbook. */
    private fun expectedDxfIds(sheets: List<XlsxStyledBook.StyledSheet>): Map<Format, Int> {
        val ids = LinkedHashMap<Format, Int>()
        for (sheet in sheets) {
            for (block in sheet.conditionalFormats) {
                for (rule in block.rules) rule.format?.let { ids.getOrPut(it) { ids.size } }
            }
        }
        return ids
    }

    private fun writeIntent(folder: File, file: String, sheets: List<XlsxStyledBook.StyledSheet>) {
        val ids = expectedDxfIds(sheets)
        val ruleLines = ArrayList<String>()
        val dxfLines = ArrayList<String>()
        for (sheet in sheets) {
            if (sheet.conditionalFormats.isEmpty()) {
                ruleLines.add(
                    listOf(file, sheet.name, "0", "-", "-", "-", "-", "-", "-", "-", "-", "-", "-", "-", "-", "-", "-", "-")
                        .joinToString("\t"),
                )
                continue
            }
            sheet.conditionalFormats.forEachIndexed { blockIndex, block ->
                block.rules.forEachIndexed { ruleIndex, rule ->
                    ruleLines.add(
                        listOf(
                            file, sheet.name, "1", blockIndex.toString(), block.sqref, ruleIndex.toString(),
                            rule.kind.token, rule.priority.toString(),
                            rule.stopIfTrue?.let { if (it) "1" else "0" } ?: "-",
                            rule.operator?.token ?: "-",
                            rule.rank?.toString() ?: "-",
                            rule.percent?.let { if (it) "1" else "0" } ?: "-",
                            rule.bottom?.let { if (it) "1" else "0" } ?: "-",
                            rule.formulas.joinToString("\u001f").ifEmpty { "-" },
                            rule.thresholds.joinToString(",") { t -> t.kind.token + ":" + (t.value?.toString() ?: "-") }
                                .ifEmpty { "-" },
                            rule.colors.joinToString(",").ifEmpty { "-" },
                            rule.format?.let { ids[it].toString() } ?: "-",
                            formatSignature(rule.format),
                        ).joinToString("\t"),
                    )
                }
            }
        }
        for ((format, id) in ids) {
            dxfLines.add(
                listOf(
                    file, id.toString(),
                    if (format.bold) "1" else "0",
                    if (format.italic) "1" else "0",
                    if (format.strike) "1" else "0",
                    if (format.underline) "1" else "0",
                    tsv(format.fontRgb),
                    tsv(format.fillRgb),
                    ids.size.toString(),
                ).joinToString("\t"),
            )
        }
        if (dxfLines.isEmpty()) dxfLines.add(listOf(file, "-", "0", "0", "0", "0", "-", "-", "0").joinToString("\t"))
        File(folder, "conditional.tsv").writeText(ruleLines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        File(folder, "dxfs.tsv").writeText(dxfLines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }

    /** A package in openpyxl's shapes: attribute order, 00-alpha colours, `percent="0"`, an unmodelled kind. */
    private object ForeignPackage {
        private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val PKG = "http://schemas.openxmlformats.org/package/2006/relationships"

        fun build(): ByteArray {
            val parts = linkedMapOf(
                "[Content_Types].xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""",
                "_rels/.rels" to
                    """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="$PKG"><Relationship Id="rId1" Type="$REL/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
                "xl/workbook.xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="$MAIN" xmlns:r="$REL"><sheets><sheet name="Foreign" sheetId="1" r:id="rId1"/></sheets></workbook>""",
                "xl/_rels/workbook.xml.rels" to
                    """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="$PKG"><Relationship Id="rId1" Type="$REL/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="$REL/styles" Target="styles.xml"/></Relationships>""",
                "xl/styles.xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="$MAIN"><fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts><fills count="1"><fill><patternFill patternType="none"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs><dxfs count="2"><dxf><font><b/><color rgb="00FF0000"/></font></dxf><dxf><fill><patternFill patternType="solid"><bgColor rgb="0000FF00"/></patternFill></fill></dxf></dxfs></styleSheet>""",
                "xl/worksheets/sheet1.xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="$MAIN"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>foreign</t></is></c></row></sheetData>""" +
                    """<conditionalFormatting sqref="A1:A9"><cfRule type="cellIs" priority="2" operator="greaterThan" dxfId="0" stopIfTrue="1"><formula>7</formula></cfRule></conditionalFormatting>""" +
                    """<conditionalFormatting sqref="B1:B9"><cfRule type="expression" priority="1" dxfId="1"><formula>A1&gt;0</formula></cfRule></conditionalFormatting>""" +
                    """<conditionalFormatting sqref="C1:C9"><cfRule type="top10" rank="5" priority="3" percent="0" bottom="1"/></conditionalFormatting>""" +
                    """<conditionalFormatting sqref="D1:D9"><cfRule type="iconSet" priority="4"/></conditionalFormatting>""" +
                    // A fifth block that repeats the sheet-wide priority 1 the B block already uses.
                    """<conditionalFormatting sqref="E1:E9"><cfRule type="expression" priority="1" dxfId="0"><formula>SUM(A1:A2)&gt;3</formula></cfRule></conditionalFormatting>""" +
                    """</worksheet>""",
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
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val folder = File(args.firstOrNull() ?: "testing/build/r96/artifacts")
        folder.mkdirs()

        val grid = XlsxStyledBook.StyledSheet(
            name = "Grid",
            cells = listOf(cell("A1", "cf"), cell("B2", "4"), cell("C3", "40")),
            merges = listOf(XlsxMerges.Range("F1:G1")),
            validations = listOf(
                XlsxValidation.Rule(
                    sqref = "H1:H4",
                    type = XlsxValidation.Type.WHOLE,
                    formula1 = "1",
                    operator = XlsxValidation.Operator.BETWEEN,
                    formula2 = "5",
                ),
            ),
            conditionalFormats = GRID_RULES,
        )
        val two = XlsxStyledBook.StyledSheet(
            name = "Two",
            cells = listOf(cell("A1", "two")),
            conditionalFormats = TWO_RULES,
        )
        val plain = XlsxStyledBook.StyledSheet("Plain", listOf(cell("A1", "plain")))
        val book = XlsxStyledBook.write(XlsxStyledBook.WorkbookSpec(listOf(grid, two, plain)))

        // 1. Tail order: mergeCells, conditionalFormatting, dataValidations — CT_Worksheet's sequence.
        val sheet1 = part(book, "xl/worksheets/sheet1.xml")
        val markers = listOf("</sheetData>", "<mergeCells", "<conditionalFormatting", "<dataValidations", "</worksheet>")
        val positions = markers.map { sheet1.indexOf(it) }
        check(positions.all { it >= 0 }) { "a Grid marker is missing: $markers -> $positions" }
        check(positions == positions.sorted()) { "the Grid tail is out of order: $markers -> $positions" }

        // 2. Exact bytes: schema attribute order, dxfId before priority, only declared flags.
        val cellIs =
            """<cfRule type="cellIs" dxfId="0" priority="1" stopIfTrue="1" operator="greaterThan">""" +
                """<formula>5</formula></cfRule>"""
        check(sheet1.contains(cellIs)) {
            "unexpected cellIs rule: " + (Regex("""<cfRule type="cellIs".*?</cfRule>""").find(sheet1)?.value)
        }
        val top10 = """<cfRule type="top10" dxfId="2" priority="5" percent="0" bottom="1" rank="3"/>"""
        check(sheet1.contains(top10)) {
            "unexpected top10 rule: " + (Regex("""<cfRule type="top10"[^>]*/>""").find(sheet1)?.value)
        }
        val colourScale =
            """<conditionalFormatting sqref="B1:B5"><cfRule type="colorScale" priority="3"><colorScale>""" +
                """<cfvo type="min"/><cfvo type="max"/><color rgb="FFF8696B"/><color rgb="FF63BE7B"/>""" +
                """</colorScale></cfRule></conditionalFormatting>"""
        check(sheet1.contains(colourScale)) {
            "unexpected colour scale: " + (Regex("""<conditionalFormatting sqref="B1:B5".*?</conditionalFormatting>""")
                .find(sheet1)?.value)
        }
        val dataBar =
            """<cfRule type="dataBar" priority="4"><dataBar><cfvo type="num" val="0"/><cfvo type="num" val="100"/>""" +
                """<color rgb="FF638EC6"/></dataBar></cfRule>"""
        check(sheet1.contains(dataBar)) {
            "unexpected data bar: " + (Regex("""<cfRule type="dataBar".*?</cfRule>""").find(sheet1)?.value)
        }

        // 3. The dxf table: one entry per distinct format, font before fill, colours uppercased.
        val styles = part(book, "xl/styles.xml")
        check(styles.contains("""<dxfs count="3">""")) {
            "unexpected dxf count: " + (Regex("""<dxfs count="\d+">""").find(styles)?.value)
        }
        check(styles.contains(
            """<dxf><fill><patternFill patternType="solid"><fgColor rgb="FFFFC7CE"/>""" +
                """<bgColor indexed="64"/></patternFill></fill></dxf>""",
        )) { "unexpected fill dxf: " + (Regex("<dxf><fill>.*?</dxf>").find(styles)?.value) }
        check(styles.contains("""<dxf><font><b/></font></dxf>""")) { "unexpected bold dxf" }
        check(styles.contains("""<dxf><font><i/><color rgb="FF9C0006"/></font></dxf>""")) {
            "unexpected font dxf: " + (Regex("<dxf><font>.*?</dxf>").find(styles)?.value)
        }
        check(styles.indexOf("<dxfs") > styles.indexOf("</cellXfs>")) {
            "the dxf table must follow cellXfs in CT_Stylesheet order"
        }

        // 4. A sheet with no rules writes no block and no dxf table of its own.
        val plainOnly = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(listOf(XlsxStyledBook.StyledSheet("Plain", listOf(cell("A1", "plain"))))),
        )
        check(!part(plainOnly, "xl/worksheets/sheet1.xml").contains("conditionalFormatting")) {
            "a sheet without rules grew a conditionalFormatting block"
        }
        check(!part(plainOnly, "xl/styles.xml").contains("<dxfs")) {
            "a workbook without rules grew a dxf table"
        }
        check(part(plainOnly, "xl/worksheets/sheet1.xml").endsWith("</sheetData></worksheet>")) {
            "a sheet without rules grew a tail"
        }

        // 5. A duplicate format folds into one dxfId; a three-stop scale keeps all three stops.
        val sheet2 = part(book, "xl/worksheets/sheet2.xml")
        check(Regex("""dxfId="0"""").findAll(sheet2).count() == 2) {
            "two rules with the same format must share one dxfId: " +
                Regex("""<cfRule[^>]*>""").findAll(sheet2).map { it.value }.toList()
        }
        check(sheet2.contains("""<cfvo type="percent" val="50"/>""")) {
            "a percent threshold was written wrong: " + (Regex("""<cfvo[^>]*/>""").findAll(sheet2).map { it.value })
        }
        check(sheet2.contains("""<color rgb="FFFFEB84"/>""")) { "the middle stop colour was lost" }

        // 6. Round trip: rules, formats and the dxf table survive, byte for byte.
        val read = XlsxStyledBook.read(book)
        check(read.sheets[0].conditionalFormats == GRID_RULES) {
            "Grid rules did not round-trip: ${read.sheets[0].conditionalFormats}"
        }
        check(read.sheets[1].conditionalFormats == TWO_RULES) {
            "Two rules did not round-trip: ${read.sheets[1].conditionalFormats}"
        }
        check(read.sheets[2].conditionalFormats.isEmpty()) { "a plain sheet read back rules it does not have" }
        check(XlsxStyledBook.write(read).contentEquals(book)) { "write(read(write(spec))) is not byte-identical" }

        // 7. Priorities are unique per sheet, and the sheet refuses a duplicate at construction.
        val blocked = runCatching {
            XlsxStyledBook.StyledSheet(
                name = "Dup",
                cells = listOf(cell("A1", "x")),
                conditionalFormats = listOf(
                    Block("A1:A2", listOf(Rule(Kind.EXPRESSION, priority = 4, formulas = listOf("A1>1"), format = BOLD))),
                    Block("B1:B2", listOf(Rule(Kind.EXPRESSION, priority = 4, formulas = listOf("B1>1"), format = BOLD))),
                ),
            )
        }.isFailure
        check(blocked) { "a sheet with two rules of the same priority was accepted" }

        // 8. Refusals — every one is a shape this writer must not emit.
        fun refuses(what: String, block: () -> Unit) {
            check(runCatching(block).isFailure) { "accepted a shape that must be refused: $what" }
        }
        refuses("cellIs without an operator") { Rule(Kind.CELL_IS, 1, formulas = listOf("5"), format = BOLD) }
        refuses("cellIs without a format") {
            Rule(Kind.CELL_IS, 1, operator = Operator.EQUAL, formulas = listOf("5"))
        }
        refuses("between with one formula") {
            Rule(Kind.CELL_IS, 1, operator = Operator.BETWEEN, formulas = listOf("5"), format = BOLD)
        }
        refuses("equal with two formulas") {
            Rule(Kind.CELL_IS, 1, operator = Operator.EQUAL, formulas = listOf("5", "6"), format = BOLD)
        }
        refuses("expression with two formulas") {
            Rule(Kind.EXPRESSION, 1, formulas = listOf("A1>1", "A2>1"), format = BOLD)
        }
        refuses("expression without a format") { Rule(Kind.EXPRESSION, 1, formulas = listOf("A1>1")) }
        refuses("top10 without a rank") { Rule(Kind.TOP10, 1, format = BOLD) }
        refuses("top10 rank 0") { Rule(Kind.TOP10, 1, rank = 0, format = BOLD) }
        refuses("top10 rank 1001") { Rule(Kind.TOP10, 1, rank = 1001, format = BOLD) }
        refuses("colour scale with one threshold") {
            Rule(Kind.COLOR_SCALE, 1, thresholds = listOf(Threshold(ThresholdKind.MIN)), colors = listOf("FFFFFF"))
        }
        refuses("colour scale colour mismatch") {
            Rule(
                Kind.COLOR_SCALE, 1,
                thresholds = listOf(Threshold(ThresholdKind.MIN), Threshold(ThresholdKind.MAX)),
                colors = listOf("FFFFFF"),
            )
        }
        refuses("colour scale with a dxf") {
            Rule(
                Kind.COLOR_SCALE, 1,
                thresholds = listOf(Threshold(ThresholdKind.MIN), Threshold(ThresholdKind.MAX)),
                colors = listOf("FFFFFF", "000000"),
                format = BOLD,
            )
        }
        refuses("data bar with two colours") {
            Rule(
                Kind.DATA_BAR, 1,
                thresholds = listOf(Threshold(ThresholdKind.MIN), Threshold(ThresholdKind.MAX)),
                colors = listOf("63BE7B", "F8696B"),
            )
        }
        refuses("formula with a leading '='") { Rule(Kind.EXPRESSION, 1, formulas = listOf("=A1>1"), format = BOLD) }
        refuses("blank formula") { Rule(Kind.EXPRESSION, 1, formulas = listOf("   "), format = BOLD) }
        refuses("formula over the cap") {
            Rule(Kind.EXPRESSION, 1, formulas = listOf("A".repeat(8_193)), format = BOLD)
        }
        refuses("colour that is not six hex digits") {
            Rule(Kind.EXPRESSION, 1, formulas = listOf("A1>1"), format = Format(fillRgb = "FFC7C"))
        }
        refuses("empty format") { Format() }
        refuses("min threshold with a value") { Threshold(ThresholdKind.MIN, 3.0) }
        refuses("num threshold without a value") { Threshold(ThresholdKind.NUM) }
        refuses("percent threshold above 100") { Threshold(ThresholdKind.PERCENT, 101.0) }
        refuses("priority 0") { Rule(Kind.EXPRESSION, 0, formulas = listOf("A1>1"), format = BOLD) }
        refuses("lower-case sqref") {
            Block("a1:a9", listOf(Rule(Kind.EXPRESSION, 1, formulas = listOf("A1>1"), format = BOLD)))
        }
        refuses("repeated sqref token") {
            Block("A1:A9 A1:A9", listOf(Rule(Kind.EXPRESSION, 1, formulas = listOf("A1>1"), format = BOLD)))
        }
        refuses("empty block") { Block("A1:A9", emptyList()) }
        refuses("two rules with one priority in a block") {
            Block(
                "A1:A9",
                listOf(
                    Rule(Kind.EXPRESSION, 3, formulas = listOf("A1>1"), format = BOLD),
                    Rule(Kind.EXPRESSION, 3, formulas = listOf("A1>2"), format = RED),
                ),
            )
        }

        // 9. Accepted shapes the model must not over-refuse.
        check(Threshold(ThresholdKind.PERCENTILE, 0.0).value == 0.0) { "a 0-percentile threshold was rejected" }
        check(
            Rule(
                Kind.DATA_BAR, 2,
                thresholds = listOf(
                    Threshold(ThresholdKind.NUM, -50.0),
                    Threshold(ThresholdKind.NUM, 50.5),
                ),
                colors = listOf("638EC6"),
            ).colors.size == 1,
        ) { "a negative data-bar threshold was rejected" }
        check(true) { "placeholder" }

        // 10. A foreign package in openpyxl's shapes is read, not repaired into ours.
        val foreign = XlsxStyledBook.read(ForeignPackage.build())
        val f = foreign.sheets.single()
        // Two blocks survive: the two whose rules this model can hold. openpyxl's fill dxf (which uses bgColor,
        // not fgColor) is read; the top10 rule with no dxf and the unmodelled iconSet rule are dropped, not
        // repaired into something the file does not say.
        check(f.conditionalFormats.map { it.sqref } == listOf("A1:A9", "B1:B9")) {
            "foreign blocks were not read in document order: ${f.conditionalFormats.map { it.sqref }}"
        }
        check(f.conditionalFormats[0].rules.single().format == Format(bold = true, fontRgb = "FF0000")) {
            "a foreign dxf font was not read: ${f.conditionalFormats[0].rules.single().format}"
        }
        check(f.conditionalFormats[1].rules.single().format == Format(fillRgb = "00FF00")) {
            "a foreign dxf fill was not read: ${f.conditionalFormats[1].rules.single().format}"
        }
        check(f.conditionalFormats[1].rules.single().priority == 1) {
            "a foreign priority was not read: ${f.conditionalFormats[1].rules.single().priority}"
        }
        check(f.conditionalFormats.none { block -> block.rules.any { it.kind == Kind.TOP10 } }) {
            "a foreign top10 rule with no differential format was kept"
        }
        check(f.conditionalFormats.none { block -> block.rules.any { it.kind == Kind.COLOR_SCALE } }) {
            "a foreign iconSet rule was repaired into a colour scale"
        }
        check(f.conditionalFormats.none { it.sqref == "E1:E9" }) {
            "a foreign block that repeats a sheet-wide priority was kept"
        }

        // 11. Artifacts for the independent readers.
        writeIntent(folder, "r96.xlsx", listOf(grid, two, plain))
        // The rule-free package above is also shipped as its own artifact, so the readers can confirm the absence.
        File(folder, "conditional.tsv").appendText(
            (listOf("r96-plain.xlsx", "Plain", "0") + List(15) { "-" }).joinToString("\t") + "\n",
            Charsets.UTF_8,
        )
        File(folder, "dxfs.tsv").appendText(
            listOf("r96-plain.xlsx", "-", "0", "0", "0", "0", "-", "-", "0").joinToString("\t") + "\n",
            Charsets.UTF_8,
        )
        File(folder, "r96-plain.xlsx").writeBytes(plainOnly)
        File(folder, "r96.xlsx").writeBytes(book)
        File(folder, "r96-foreign.xlsx").writeBytes(ForeignPackage.build())
        val ruleRows = File(folder, "conditional.tsv").readLines().count { it.isNotBlank() }
        val dxfRows = File(folder, "dxfs.tsv").readLines().count { it.isNotBlank() }
        check(ruleRows == 10) { "conditional.tsv rows: $ruleRows (8 rules plus two none rows)" }
        check(dxfRows == 4) { "dxfs.tsv rows: $dxfRows (three distinct formats plus one none row)" }

        println(
            "R96_CF_OK assertions=$assertions workbooks=2 sheets=3 rules=8 blocks=6 dxfs=3 foreign=1 refused=27",
        )
    }
}
