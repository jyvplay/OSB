// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.XlsxOutline
import dev.fusion.ooxml.XlsxPrintSetup
import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R99 spreadsheet contracts: worksheet outlining.
 *
 * Three parts of one shape: `<sheetPr><outlinePr/></sheetPr>` sits *between* `tabColor` and `pageSetUpPr` and
 * outside them it is a repair prompt; `<row outlineLevel collapsed>` and `<col outlineLevel collapsed>` carry the
 * grouping; and a `collapsed` marker with no group is a dangling flag. Grouping is deliberately **not** hiding:
 * a collapsed group's members are `hidden="1"` in the file, and this probe asserts both halves separately.
 *
 * Host JVM only. The probe writes `r99.xlsx` and `r99-foreign.xlsx` plus two intent tables (`outline.tsv`,
 * `levels.tsv`). Independent witnesses: `tools/check_r99_outline.py` (raw OPC + openpyxl's
 * `sheet_properties.outlinePr`, `row_dimensions[i].outlineLevel/.collapsed` and
 * `column_dimensions[letter].outlineLevel/.collapsed`). Nothing here opens a workbook in an application, and no
 * claim is made about the `+`/`-` buttons an interface would draw.
 */
object R99Checks {
    private var assertions = 0
    private var refusals = 0

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

    private fun cell(ref: String, value: String) =
        XlsxStyledBook.StyledCell(ref, XlsxStyledBook.StyledVal.Txt(value))

    private fun onOff(value: Boolean): String = if (value) "1" else "0"

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

    // ---------------------------------------------------------------- declared workbooks

    private val GROUPED = XlsxOutline.Levels(
        rows = mapOf(2 to 1, 3 to 1, 4 to 1),
        cols = mapOf(2 to 1, 3 to 1),
        collapsedRows = setOf(5),
        collapsedCols = setOf(4),
    )

    private val NESTED = XlsxOutline.Levels(
        rows = mapOf(1 to 1, 2 to 2, 3 to 3, 7 to XlsxOutline.MAX_LEVEL),
        cols = mapOf(1 to 2),
    )

    private val DEFAULT_SETTINGS = XlsxOutline.Settings()

    private val WORKBOOK = XlsxStyledBook.WorkbookSpec(
        sheets = listOf(
            XlsxStyledBook.StyledSheet(
                name = "Grouped",
                cells = (2..5).map { cell("A$it", "g$it") },
                hiddenRows = setOf(3, 4),
                outline = GROUPED,
                outlineSettings = XlsxOutline.Settings(summaryBelow = false),
            ),
            XlsxStyledBook.StyledSheet(
                name = "Nested",
                cells = (1..8).map { cell("A$it", "n$it") },
                outline = NESTED,
                outlineSettings = XlsxOutline.Settings(summaryRight = false, applyStyles = true),
            ),
            XlsxStyledBook.StyledSheet(
                name = "Tabbed",
                cells = listOf(cell("A1", "tab")),
                tabRgb = "CC00FF",
                outline = XlsxOutline.Levels(rows = mapOf(1 to 1)),
                outlineSettings = XlsxOutline.Settings(summaryBelow = false),
                print = XlsxPrintSetup.Spec(
                    setup = XlsxPrintSetup.Setup(fitToWidth = 1, fitToHeight = 1),
                    fitToPage = true,
                ),
            ),
            XlsxStyledBook.StyledSheet(
                name = "FitOnly",
                cells = listOf(cell("A1", "fit")),
                print = XlsxPrintSetup.Spec(
                    setup = XlsxPrintSetup.Setup(fitToWidth = 2, fitToHeight = 0),
                    fitToPage = true,
                ),
            ),
            XlsxStyledBook.StyledSheet(
                name = "Default",
                cells = listOf(cell("A1", "d1"), cell("A2", "d2")),
                outline = XlsxOutline.Levels(rows = mapOf(1 to 1, 2 to 1)),
                outlineSettings = DEFAULT_SETTINGS,
            ),
            XlsxStyledBook.StyledSheet("Plain", listOf(cell("A1", "plain"))),
        ),
    )

    private fun writeIntent(folder: File, book: XlsxStyledBook.WorkbookSpec) {
        val outlineLines = ArrayList<String>()
        val levelLines = ArrayList<String>()
        for (sheet in book.sheets) {
            val settings = sheet.outlineSettings
            val levels = sheet.outline
            outlineLines.add(
                listOf(
                    sheet.name,
                    settings?.let { onOff(it.summaryBelow) } ?: "-",
                    settings?.let { onOff(it.summaryRight) } ?: "-",
                    settings?.let { onOff(it.applyStyles) } ?: "-",
                    (levels?.rows?.size ?: 0).toString(),
                    (levels?.cols?.size ?: 0).toString(),
                    (levels?.collapsedRows?.size ?: 0).toString(),
                    (levels?.collapsedCols?.size ?: 0).toString(),
                    ((levels?.rows?.values?.maxOrNull() ?: 0)
                        .coerceAtLeast(levels?.cols?.values?.maxOrNull() ?: 0)).toString(),
                ).joinToString("\t"),
            )
            val rows = (levels?.rowIndexes.orEmpty() + sheet.hiddenRows).toSortedSet()
            for (row in rows) {
                levelLines.add(
                    listOf(
                        sheet.name, "row", row.toString(),
                        (levels?.rows?.get(row) ?: 0).toString(),
                        onOff(row in levels?.collapsedRows.orEmpty()),
                        onOff(row in sheet.hiddenRows),
                    ).joinToString("\t"),
                )
            }
            for (col in (levels?.columnIndexes.orEmpty()).toSortedSet()) {
                levelLines.add(
                    listOf(
                        sheet.name, "col", col.toString(),
                        (levels?.cols?.get(col) ?: 0).toString(),
                        onOff(col in levels?.collapsedCols.orEmpty()),
                        "0",
                    ).joinToString("\t"),
                )
            }
        }
        File(folder, "outline.tsv").writeText(outlineLines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        File(folder, "levels.tsv").writeText(levelLines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }

    /** Another producer's shapes: a grouped column span, an unknown outlinePr attribute, and a huge span. */
    private object ForeignPackage {
        private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val PKG = "http://schemas.openxmlformats.org/package/2006/relationships"

        fun build(): ByteArray {
            val parts = linkedMapOf(
                "[Content_Types].xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
                "_rels/.rels" to
                    """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="$PKG"><Relationship Id="rId1" Type="$REL/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
                "xl/workbook.xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="$MAIN" xmlns:r="$REL"><sheets><sheet name="Span" sheetId="1" r:id="rId1"/><sheet name="Huge" sheetId="2" r:id="rId2"/></sheets></workbook>""",
                "xl/_rels/workbook.xml.rels" to
                    """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="$PKG"><Relationship Id="rId1" Type="$REL/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="$REL/worksheet" Target="worksheets/sheet2.xml"/></Relationships>""",
                // A grouped span (min=2 max=4) with a summary column, and an attribute this model does not know.
                "xl/worksheets/sheet1.xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="$MAIN"><sheetPr><outlinePr summaryBelow="0" applyStyles="0" codeName="Sheet1"/></sheetPr><cols><col min="2" max="4" outlineLevel="1" collapsed="1"/><col min="6" max="6" width="11" customWidth="1"/></cols><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>a</t></is></c></row><row r="2" outlineLevel="2" collapsed="1"><c r="A2" t="inlineStr"><is><t>b</t></is></c></row></sheetData></worksheet>""",
                // A span wider than this reader will expand, and an out-of-range level that must be ignored.
                "xl/worksheets/sheet2.xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="$MAIN"><cols><col min="1" max="16384" outlineLevel="1"/><col min="9" max="9" outlineLevel="42"/></cols><sheetData><row r="3" outlineLevel="9"><c r="A3" t="inlineStr"><is><t>c</t></is></c></row></sheetData></worksheet>""",
            )
            val buffer = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(buffer, Charsets.UTF_8).use { zip ->
                for ((name, body) in parts) {
                    zip.putNextEntry(java.util.zip.ZipEntry(name))
                    zip.write(body.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            return buffer.toByteArray()
        }
    }

    // ---------------------------------------------------------------- the probe

    @JvmStatic
    fun main(args: Array<String>) {
        val folder = File(args.firstOrNull() ?: "testing/build/r99/artifacts")
        folder.mkdirs()

        // 1. Nothing declared: no sheetPr, no outline attributes, and read→write is byte-identical.
        val plainBook = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(
                sheets = listOf(XlsxStyledBook.StyledSheet("Plain", listOf(cell("A1", "open")))),
            ),
        )
        File(folder, "r99-plain.xlsx").writeBytes(plainBook)
        val plainSheet = part(plainBook, "xl/worksheets/sheet1.xml")
        check(!plainSheet.contains("sheetPr") && !plainSheet.contains("outlineLevel")) {
            "a sheet with no outline gained outline XML: $plainSheet"
        }
        check(XlsxStyledBook.write(XlsxStyledBook.read(plainBook)).contentEquals(plainBook)) {
            "read→write changed a workbook with no outline"
        }

        // 2. The full workbook.
        val bytes = XlsxStyledBook.write(WORKBOOK)
        File(folder, "r99.xlsx").writeBytes(bytes)
        writeIntent(folder, WORKBOOK)
        val readBack = XlsxStyledBook.read(bytes)
        // An explicit Settings() writes no element, so it is indistinguishable from "absent" in the file and
        // reads back as null. The comparison normalises both sides; the rule is stated once, here.
        fun normalize(settings: XlsxOutline.Settings?): XlsxOutline.Settings? =
            settings?.takeUnless { it.isDefault }
        check(normalize(XlsxOutline.Settings()) == null) { "defaults must normalise to absent" }
        for (declared in WORKBOOK.sheets) {
            val got = readBack.sheets.first { it.name == declared.name }
            check(got.outline == declared.outline) {
                "${declared.name}: outline changed on round trip: ${got.outline} vs ${declared.outline}"
            }
            check(got.outlineSettings == normalize(declared.outlineSettings)) {
                "${declared.name}: outline settings changed on round trip: " +
                    "${got.outlineSettings} vs ${normalize(declared.outlineSettings)}"
            }
        }

        // 3. sheetPr is a sequence: tabColor, outlinePr, pageSetUpPr — and nothing else.
        val tabbed = part(bytes, "xl/worksheets/sheet3.xml")
        check(tabbed.contains(
            """<sheetPr><tabColor rgb="FFCC00FF"/><outlinePr summaryBelow="0"/><pageSetUpPr fitToPage="1"/></sheetPr>""",
        )) { "sheetPr children are out of order: $tabbed" }
        val grouped = part(bytes, "xl/worksheets/sheet1.xml")
        check(grouped.contains("""<sheetPr><outlinePr summaryBelow="0"/></sheetPr>""")) {
            "a sheet with only outline settings must write only outlinePr: $grouped"
        }
        val fitOnly = part(bytes, "xl/worksheets/sheet4.xml")
        check(fitOnly.contains("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>""")) {
            "the R98 fit-only shape changed: $fitOnly"
        }
        val defaultSheet = part(bytes, "xl/worksheets/sheet5.xml")
        check(!defaultSheet.contains("outlinePr")) {
            "default outline settings must write no outlinePr: $defaultSheet"
        }
        check(!defaultSheet.contains("sheetPr")) {
            "a sheet whose only outline settings are the defaults must write no sheetPr: $defaultSheet"
        }

        // 4. Row and column attributes, and the hidden/collapsed pair kept distinct.
        for (needle in listOf(
            """<row r="2" outlineLevel="1">""",
            """<row r="3" hidden="1" outlineLevel="1">""",
            """<row r="5" collapsed="1">""",
        )) {
            check(grouped.contains(needle)) { "missing row shape $needle in $grouped" }
        }
        check(!grouped.contains("""<row r="2" hidden="1"""")) {
            "grouping must not imply hiding: row 2 is grouped and visible"
        }
        check(grouped.contains("""<col min="2" max="2" outlineLevel="1"/>""")) {
            "missing grouped column in $grouped"
        }
        check(grouped.contains("""<col min="4" max="4" collapsed="1"/>""")) {
            "missing collapsed column in $grouped"
        }
        val nested = part(bytes, "xl/worksheets/sheet2.xml")
        for (needle in listOf(
            """<row r="3" outlineLevel="3">""",
            """<row r="7" outlineLevel="7">""",
            """<col min="1" max="1" outlineLevel="2"/>""",
            """<outlinePr summaryRight="0" applyStyles="1"/>""",
        )) {
            check(nested.contains(needle)) { "missing nested shape $needle in $nested" }
        }

        // 5. The settings type: defaults write nothing, non-defaults write only what differs.
        check(XlsxOutline.Settings().isDefault) { "the schema default must report itself as default" }
        check(XlsxOutline.outlinePrXml(null) == null) { "a sheet without settings writes no outlinePr" }
        check(XlsxOutline.outlinePrXml(XlsxOutline.Settings()) == null) { "defaults write no outlinePr" }
        check(XlsxOutline.outlinePrXml(XlsxOutline.Settings(applyStyles = true)) == """<outlinePr applyStyles="1"/>""") {
            "applyStyles alone must be written alone"
        }
        check(XlsxOutline.outlinePrXml(XlsxOutline.Settings(summaryBelow = false, summaryRight = false)) ==
            """<outlinePr summaryBelow="0" summaryRight="0"/>""") { "both summaries off must be written in order" }

        // 6. Refusals: levels, indexes, and the dangling collapsed marker.
        rejects("level 0") { XlsxOutline.Levels(rows = mapOf(1 to 0)) }
        rejects("level past 7") { XlsxOutline.Levels(rows = mapOf(1 to 8)) }
        rejects("column level past 7") { XlsxOutline.Levels(cols = mapOf(1 to 8)) }
        rejects("row 0") { XlsxOutline.Levels(rows = mapOf(0 to 1)) }
        rejects("row past the grid") { XlsxOutline.Levels(rows = mapOf(1_048_577 to 1)) }
        rejects("column 0") { XlsxOutline.Levels(cols = mapOf(0 to 1)) }
        rejects("column past the grid") { XlsxOutline.Levels(cols = mapOf(16_385 to 1)) }
        rejects("collapsed row with no group") { XlsxOutline.Levels(collapsedRows = setOf(3)) }
        rejects("collapsed column with no group") { XlsxOutline.Levels(collapsedCols = setOf(3)) }
        rejects("collapsed row outside the grid") {
            XlsxOutline.Levels(rows = mapOf(1 to 1), collapsedRows = setOf(1_048_577))
        }

        // 7. A foreign package: spans expand, unknown attributes are ignored, caps hold.
        val foreign = ForeignPackage.build()
        File(folder, "r99-foreign.xlsx").writeBytes(foreign)
        val foreignBook = XlsxStyledBook.read(foreign)
        val span = foreignBook.sheets[0]
        check(span.outline?.cols == mapOf(2 to 1, 3 to 1, 4 to 1)) {
            "a grouped column span must expand to every column: ${span.outline?.cols}"
        }
        check(span.outline?.collapsedCols == setOf(2, 3, 4)) {
            "the summary marker must follow the span: ${span.outline?.collapsedCols}"
        }
        check(span.outline?.rows == mapOf(2 to 2)) { "the row level did not survive: ${span.outline?.rows}" }
        check(span.outline?.collapsedRows == setOf(2)) { "the row summary marker did not survive" }
        check(span.outlineSettings == XlsxOutline.Settings(summaryBelow = false)) {
            "unknown outlinePr attributes must be ignored and the known one kept: ${span.outlineSettings}"
        }
        val huge = foreignBook.sheets[1]
        check(huge.outline?.cols == mapOf(1 to 1)) {
            "a span past the expansion cap must read as its first column only: ${huge.outline?.cols}"
        }
        check(huge.outline?.rows?.isEmpty() == true) {
            "an out-of-range row level must be ignored: ${huge.outline?.rows}"
        }
        check(huge.outlineSettings == null) { "a sheet without outlinePr must read no settings" }

        // 8. The foreign package survives read→write→read with its semantics intact.
        val rewritten = XlsxStyledBook.read(XlsxStyledBook.write(foreignBook))
        check(rewritten.sheets[0].outline == span.outline) {
            "the expanded span drifted on re-write: ${rewritten.sheets[0].outline}"
        }
        check(rewritten.sheets[0].outlineSettings == span.outlineSettings) {
            "the foreign outline settings drifted on re-write"
        }

        // 9. A column with a width and a group keeps both, in one element.
        val both = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(
                sheets = listOf(
                    XlsxStyledBook.StyledSheet(
                        name = "Both",
                        cells = listOf(cell("A1", "x")),
                        columnWidths = mapOf(2 to 12.5),
                        hiddenColumns = setOf(3),
                        outline = XlsxOutline.Levels(cols = mapOf(2 to 1, 3 to 1)),
                    ),
                ),
            ),
        )
        val bothSheet = part(both, "xl/worksheets/sheet1.xml")
        check(bothSheet.contains("""<col min="2" max="2" width="12.5" customWidth="1" outlineLevel="1"/>""")) {
            "a wide grouped column must keep both attributes: $bothSheet"
        }
        check(bothSheet.contains("""<col min="3" max="3" hidden="1" outlineLevel="1"/>""")) {
            "a hidden grouped column must keep both attributes: $bothSheet"
        }
        check(XlsxStyledBook.read(both).sheets[0].outline == XlsxOutline.Levels(cols = mapOf(2 to 1, 3 to 1))) {
            "the wide/hidden grouped columns did not round trip"
        }

        val touchedRows = WORKBOOK.sheets.sumOf { (it.outline?.rowIndexes.orEmpty()).size + it.hiddenRows.size }
        val touchedCols = WORKBOOK.sheets.sumOf { (it.outline?.columnIndexes.orEmpty()).size }
        println(
            "R99_OUTLINE_OK assertions=$assertions sheets=${WORKBOOK.sheets.size} " +
                "rows=$touchedRows cols=$touchedCols refusals=$refusals",
        )
    }
}
