// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.XlsxPrintSetup
import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R98 spreadsheet contracts: the two halves of print layout that R94 left out.
 *
 * 1. **Fit to page and page numbering** — `pageSetup@fitToWidth`/`@fitToHeight`/`@firstPageNumber`/
 *    `@useFirstPageNumber`, and the `<sheetPr><pageSetUpPr fitToPage="1"/>` flag without which the fit counts
 *    are inert in Excel. The model refuses that combination rather than writing a pageSetup Excel would ignore.
 * 2. **Print records** — `_xlnm.Print_Area` and `_xlnm.Print_Titles` as sheet-scoped `definedName`s, including
 *    quoting (`'Fit Two'!$B$2:$D$5`), the rows-then-columns spelling Excel uses, and folding them back into the
 *    sheet on read so a caller never has to chase `localSheetId` himself.
 *
 * Host JVM only. The probe writes real `.xlsx` packages plus two intent tables (`print.tsv`, `names.tsv`).
 * Independent witnesses: `tools/check_r98_sheet_print.py` (raw OPC + openpyxl's `page_setup`,
 * `sheet_properties.pageSetUpPr`, `print_area`, `print_title_rows`/`print_title_cols`) and
 * `tools/check_r98_print_records.py` (raw OPC + openpyxl's `defined_names`). No Excel, LibreOffice or Android
 * run: nothing here says a page was ever laid out, paginated or printed.
 */
object R98Checks {
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

    private fun cell(ref: String, value: String) =
        XlsxStyledBook.StyledCell(ref, XlsxStyledBook.StyledVal.Txt(value))

    private fun tsv(value: String?): String =
        when (value) {
            null -> "-"
            else -> value.replace("\t", "\\t").replace("\n", "\\n")
        }

    private fun onOff(value: Boolean): String = if (value) "1" else "0"

    // ---------------------------------------------------------------- the declared workbooks

    private val FIT = XlsxPrintSetup.Spec(
        margins = XlsxPrintSetup.Margins(left = 0.5),
        setup = XlsxPrintSetup.Setup(
            orientation = XlsxPrintSetup.Orientation.LANDSCAPE,
            paperSize = 9,
            fitToWidth = 1,
            fitToHeight = 0,
        ),
        fitToPage = true,
        area = "A1:C10",
        titles = XlsxPrintSetup.Titles(rows = "1:3", cols = "A:B"),
    )

    /** A sheet name with a space: the record has to be written `'Fit Two'!$B$2:$D$5`, not bare. */
    private val FIT_TWO = XlsxPrintSetup.Spec(
        setup = XlsxPrintSetup.Setup(fitToWidth = 2, fitToHeight = 1),
        fitToPage = true,
        area = "B2:D5",
    )

    private val NUMBERED = XlsxPrintSetup.Spec(
        options = XlsxPrintSetup.Options(gridLines = true),
        margins = XlsxPrintSetup.Margins(bottom = 0.75),
        setup = XlsxPrintSetup.Setup(
            orientation = XlsxPrintSetup.Orientation.PORTRAIT,
            paperSize = 1,
            scale = 80,
            firstPageNumber = 5,
            useFirstPageNumber = true,
        ),
    )

    /** A one-cell area, which Excel spells `$A$1` rather than `$A$1:$A$1`. */
    private val SINGLE_CELL = XlsxPrintSetup.Spec(area = "B2")

    /** Records only: no pageSetup and no fit flag, so the sheet part carries no `<sheetPr>` at all. */
    private val RECORDS = XlsxPrintSetup.Spec(
        area = "A1:A3",
        titles = XlsxPrintSetup.Titles(rows = "1:1"),
    )

    /** A tab colour and a fit flag share one `<sheetPr>`; CT_SheetPr puts `tabColor` first. */
    private val TABBED = XlsxPrintSetup.Spec(
        setup = XlsxPrintSetup.Setup(fitToWidth = 1, fitToHeight = 1),
        fitToPage = true,
    )

    private val WORKBOOK = XlsxStyledBook.WorkbookSpec(
        sheets = listOf(
            XlsxStyledBook.StyledSheet("Fit", listOf(cell("A1", "fit")), print = FIT),
            XlsxStyledBook.StyledSheet("Fit Two", listOf(cell("A1", "two")), print = FIT_TWO),
            XlsxStyledBook.StyledSheet("Numbered", listOf(cell("A1", "page")), print = NUMBERED),
            XlsxStyledBook.StyledSheet("Records", listOf(cell("A1", "record")), print = RECORDS),
            XlsxStyledBook.StyledSheet("Single", listOf(cell("A1", "one")), print = SINGLE_CELL),
            XlsxStyledBook.StyledSheet("Tabbed", listOf(cell("A1", "tab")), print = TABBED, tabRgb = "00B050"),
            XlsxStyledBook.StyledSheet("Plain", listOf(cell("A1", "plain"))),
        ),
        definedNames = listOf(XlsxStyledBook.DefinedName("Total", "SUM(Plain!\$A\$1:\$A\$9)")),
    )

    private var refusals = 0

    private fun refuses(name: String, block: () -> Unit) {
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

    // ---------------------------------------------------------------- the intent tables

    private fun writeIntent(folder: File, book: XlsxStyledBook.WorkbookSpec) {
        val printLines = ArrayList<String>()
        for (sheet in book.sheets) {
            val print = sheet.print
            if (print == null) {
                printLines.add(
                    listOf(
                        sheet.name, "0", "0", "-", "-", "-", "0", "-", "-", "-",
                        "-", "-", "-", "0", "0", "0", "0",
                    ).joinToString("\t"),
                )
                continue
            }
            printLines.add(
                listOf(
                    sheet.name,
                    "1",
                    onOff(print.fitToPage),
                    print.setup?.fitToWidth?.toString() ?: "-",
                    print.setup?.fitToHeight?.toString() ?: "-",
                    print.setup?.firstPageNumber?.toString() ?: "-",
                    onOff(print.setup?.useFirstPageNumber == true),
                    print.setup?.orientation?.token ?: "-",
                    print.setup?.paperSize?.toString() ?: "-",
                    print.setup?.scale?.toString() ?: "-",
                    tsv(print.area),
                    tsv(print.titles?.rows),
                    tsv(print.titles?.cols),
                    onOff(print.options != null),
                    onOff(print.margins != null),
                    onOff(print.setup != null),
                    onOff(sheet.tabRgb != null),
                ).joinToString("\t"),
            )
        }
        File(folder, "print.tsv").writeText(printLines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)

        val nameLines = ArrayList<String>()
        // Workbook-scoped names first, then the records the sheets declare, exactly as the writer emits them.
        for (dn in book.definedNames) {
            nameLines.add(
                listOf(dn.name, dn.localSheetId?.toString() ?: "global", tsv(dn.reference), "0").joinToString("\t"),
            )
        }
        book.sheets.forEachIndexed { idx, sheet ->
            sheet.print?.let { print ->
                for ((name, reference) in XlsxPrintSetup.records(sheet.name, print)) {
                    nameLines.add(listOf(name, idx.toString(), tsv(reference), "1").joinToString("\t"))
                }
            }
        }
        File(folder, "names.tsv").writeText(nameLines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }

    // ---------------------------------------------------------------- a foreign package

    /**
     * A package in the shapes another producer writes: print records with `localSheetId`, a `pageSetup` that
     * asks to fit while `sheetPr` leaves the flag off (inert counts), a record scoped past the last sheet, and a
     * record whose prefix names a different sheet. Reading it must keep everything it can and refuse to invent
     * meaning for the rest.
     */
    private object ForeignPackage {
        private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val PKG = "http://schemas.openxmlformats.org/package/2006/relationships"

        fun build(): ByteArray {
            val parts = linkedMapOf(
                "[Content_Types].xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
                "_rels/.rels" to
                    """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="$PKG"><Relationship Id="rId1" Type="$REL/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
                "xl/workbook.xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="$MAIN" xmlns:r="$REL"><sheets><sheet name="Foreign" sheetId="1" r:id="rId1"/></sheets><definedNames><definedName name="_xlnm.Print_Area" localSheetId="0">'Foreign'!${'$'}A${'$'}1:${'$'}B${'$'}4</definedName><definedName name="_xlnm.Print_Titles" localSheetId="0">Foreign!${'$'}1:${'$'}2</definedName><definedName name="_xlnm.Print_Titles" localSheetId="7">Gone!${'$'}1:${'$'}1</definedName><definedName name="_xlnm.Print_Area" localSheetId="0">Other!${'$'}C${'$'}3</definedName><definedName name="Total">SUM(Foreign!${'$'}A${'$'}1:${'$'}A${'$'}3)</definedName></definedNames></workbook>""",
                "xl/_rels/workbook.xml.rels" to
                    """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="$PKG"><Relationship Id="rId1" Type="$REL/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="$REL/styles" Target="styles.xml"/></Relationships>""",
                "xl/styles.xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="$MAIN"><fonts count="1"><font/></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf/></cellStyleXfs><cellXfs count="1"><xf/></cellXfs></styleSheet>""",
                // fitToWidth without pageSetUpPr@fitToPage: inert in Excel, and therefore not modelled.
                "xl/worksheets/sheet1.xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="$MAIN"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>x</t></is></c></row></sheetData><pageMargins left="0.7" right="0.7" top="0.75" bottom="0.75" header="0.3" footer="0.3"/><pageSetup fitToWidth="3" fitToHeight="2" paperSize="9" orientation="landscape"/></worksheet>""",
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

    /** The same package with the flag on, so the fit counts become meaningful and must survive the read. */
    private object ForeignFitPackage {
        fun build(): ByteArray {
            val base = ForeignPackage.build()
            val entries = linkedMapOf<String, ByteArray>()
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(base)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    entries[entry.name] = input.readBytes()
                }
            }
            val sheet = String(entries.getValue("xl/worksheets/sheet1.xml"), Charsets.UTF_8)
                .replace(
                    "<sheetData>",
                    """<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr><sheetData>""",
                )
            entries["xl/worksheets/sheet1.xml"] = sheet.toByteArray(Charsets.UTF_8)
            val buffer = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(buffer, Charsets.UTF_8).use { zip ->
                for ((name, body) in entries) {
                    zip.putNextEntry(java.util.zip.ZipEntry(name))
                    zip.write(body)
                    zip.closeEntry()
                }
            }
            return buffer.toByteArray()
        }
    }

    // ---------------------------------------------------------------- the probe

    @JvmStatic
    fun main(args: Array<String>) {
        val folder = File(args.firstOrNull() ?: "testing/build/r98/artifacts")
        folder.mkdirs()

        // 1. Nothing declared: the sheet part and the package shape stay exactly as they were before R98.
        val plainBook = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(
                sheets = listOf(XlsxStyledBook.StyledSheet("Plain", listOf(cell("A1", "open")))),
            ),
        )
        File(folder, "r98-plain.xlsx").writeBytes(plainBook)
        val plainSheet = part(plainBook, "xl/worksheets/sheet1.xml")
        check(!plainSheet.contains("sheetPr")) { "a sheet with no print layout gained a sheetPr: $plainSheet" }
        check(!part(plainBook, "xl/workbook.xml").contains("definedName")) {
            "a workbook with no print records gained a definedNames block"
        }
        // Read → write must be byte-identical when nothing is declared, the R84 stability rule.
        check(XlsxStyledBook.write(XlsxStyledBook.read(plainBook)).contentEquals(plainBook)) {
            "read→write changed a workbook with no print layout"
        }

        // 2. The full workbook, written once and read back.
        val bytes = XlsxStyledBook.write(WORKBOOK)
        File(folder, "r98.xlsx").writeBytes(bytes)
        writeIntent(folder, WORKBOOK)
        val readBack = XlsxStyledBook.read(bytes)

        for (declared in WORKBOOK.sheets) {
            val got = readBack.sheets.first { it.name == declared.name }.print
            check(got == declared.print) { "${declared.name}: print spec changed on round trip: $got vs ${declared.print}" }
        }
        check(readBack.definedNames == WORKBOOK.definedNames) {
            "workbook names changed on round trip: ${readBack.definedNames}"
        }
        // Re-writing the read workbook must reproduce every semantic value, even though the writer orders the
        // records after the user names and the foreign file may not.
        val rewritten = XlsxStyledBook.read(XlsxStyledBook.write(readBack))
        check(rewritten.sheets.map { it.print } == readBack.sheets.map { it.print }) {
            "print specs drifted on a second round trip"
        }

        // 3. sheetPr: only where a tab colour or a fit flag exists, tabColor first, pageSetUpPr second.
        val fitSheet = part(bytes, "xl/worksheets/sheet1.xml")
        check(fitSheet.contains("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>""")) {
            "the fit sheet must declare pageSetUpPr@fitToPage: $fitSheet"
        }
        val tabbedSheet = part(bytes, "xl/worksheets/sheet6.xml")
        check(tabbedSheet.contains("""<sheetPr><tabColor rgb="FF00B050"/><pageSetUpPr fitToPage="1"/></sheetPr>""")) {
            "tabColor must precede pageSetUpPr inside one sheetPr: $tabbedSheet"
        }
        val numberedSheet = part(bytes, "xl/worksheets/sheet3.xml")
        check(!numberedSheet.contains("sheetPr")) { "a sheet without a tab colour or fit flag gained a sheetPr" }
        check(!numberedSheet.contains("fitToWidth") && !numberedSheet.contains("fitToHeight")) {
            "a sheet that does not fit to page must not carry fit counts: $numberedSheet"
        }
        check(numberedSheet.contains("""firstPageNumber="5"""") && numberedSheet.contains("""useFirstPageNumber="1"""")) {
            "first-page numbering is missing from the pageSetup: $numberedSheet"
        }
        val recordsSheet = part(bytes, "xl/worksheets/sheet4.xml")
        check(!recordsSheet.contains("sheetPr")) { "records without a fit flag must not add a sheetPr" }
        check(!recordsSheet.contains("pageSetup")) { "records without a pageSetup must not add one" }

        // 4. pageSetup attribute set on the fit sheet: the declared counts, and nothing invented.
        for (needle in listOf("""fitToWidth="1"""", """fitToHeight="0"""", """paperSize="9"""",
            """orientation="landscape"""")) {
            check(fitSheet.contains(needle)) { "the fit pageSetup is missing $needle: $fitSheet" }
        }
        check(!fitSheet.contains("scale=")) { "the fit sheet declared no scale, yet one was written" }

        // 5. The records: scopes, quoting and the rows-then-columns spelling.
        val workbookXml = part(bytes, "xl/workbook.xml")
        check(workbookXml.indexOf("definedNames") > workbookXml.indexOf("</sheets>")) {
            "definedNames must follow <sheets>: $workbookXml"
        }
        for (needle in listOf(
            """<definedName name="_xlnm.Print_Area" localSheetId="0">Fit!${'$'}A${'$'}1:${'$'}C${'$'}10</definedName>""",
            """<definedName name="_xlnm.Print_Titles" localSheetId="0">Fit!${'$'}1:${'$'}3,Fit!${'$'}A:${'$'}B</definedName>""",
            """<definedName name="_xlnm.Print_Area" localSheetId="1">'Fit Two'!${'$'}B${'$'}2:${'$'}D${'$'}5</definedName>""",
            """<definedName name="_xlnm.Print_Area" localSheetId="3">Records!${'$'}A${'$'}1:${'$'}A${'$'}3</definedName>""",
            """<definedName name="Total">SUM(Plain!${'$'}A${'$'}1:${'$'}A${'$'}9)</definedName>""",
        )) {
            check(workbookXml.contains(needle)) { "missing defined name: $needle\n$workbookXml" }
        }
        check(workbookXml.contains(
            """<definedName name="_xlnm.Print_Area" localSheetId="4">Single!${'$'}B${'$'}2</definedName>""",
        )) { "a one-cell area must be spelled as one absolute cell: $workbookXml" }
        check(!workbookXml.contains("localSheetId=\"5\"")) { "a sheet without records was given one" }
        check(workbookXml.indexOf("name=\"Total\"") < workbookXml.indexOf("_xlnm.Print_Area")) {
            "user names are written before the sheet records: $workbookXml"
        }

        // 6. Quoting is a rule, not a guess: a plain word stays bare, spaces and quotes are escaped.
        check(XlsxPrintSetup.quoteSheet("Fit") == "Fit") { "a plain sheet name must stay unquoted" }
        check(XlsxPrintSetup.quoteSheet("Fit Two") == "'Fit Two'") { "a spaced sheet name must be quoted" }
        check(XlsxPrintSetup.quoteSheet("O'Brien") == "'O''Brien'") { "an apostrophe in a name must be doubled" }
        check(XlsxPrintSetup.quoteSheet("2024") == "'2024'") { "a name that starts with a digit must be quoted" }

        // 7. Clearing a record drops the name and keeps the page setup.
        val cleared = FIT.withoutRecords()
        check(cleared.area == null && cleared.titles == null) { "withoutRecords kept a record" }
        check(cleared.setup == FIT.setup && cleared.fitToPage) { "withoutRecords changed the page setup" }
        val clearedBook = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(
                sheets = listOf(XlsxStyledBook.StyledSheet("Fit", listOf(cell("A1", "x")), print = cleared)),
            ),
        )
        check(!part(clearedBook, "xl/workbook.xml").contains("_xlnm.Print")) {
            "a cleared record still produced a defined name"
        }
        check(part(clearedBook, "xl/worksheets/sheet1.xml").contains("fitToWidth=\"1\"")) {
            "clearing the records also removed the fit counts"
        }

        // 8. Refusals: the model says what it cannot honour instead of writing it.
        refuses("fitToWidth without fitToPage") {
            XlsxPrintSetup.Spec(setup = XlsxPrintSetup.Setup(fitToWidth = 1))
        }
        refuses("fitToHeight without fitToPage") {
            XlsxPrintSetup.Spec(setup = XlsxPrintSetup.Setup(fitToHeight = 2))
        }
        refuses("useFirstPageNumber without a number") {
            XlsxPrintSetup.Setup(useFirstPageNumber = true, orientation = XlsxPrintSetup.Orientation.PORTRAIT)
        }
        refuses("firstPageNumber 0") { XlsxPrintSetup.Setup(firstPageNumber = 0) }
        refuses("firstPageNumber past the cap") { XlsxPrintSetup.Setup(firstPageNumber = 32_768) }
        refuses("fitToWidth past the cap") {
            XlsxPrintSetup.Spec(setup = XlsxPrintSetup.Setup(fitToWidth = 32_768), fitToPage = true)
        }
        refuses("negative fitToWidth") {
            XlsxPrintSetup.Spec(setup = XlsxPrintSetup.Setup(fitToWidth = -1), fitToPage = true)
        }
        refuses("titles with neither rows nor columns") { XlsxPrintSetup.Titles(null, null) }
        refuses("reversed title rows") { XlsxPrintSetup.Titles(rows = "3:1") }
        refuses("reversed title columns") { XlsxPrintSetup.Titles(cols = "B:A") }
        refuses("title rows with a column") { XlsxPrintSetup.Titles(rows = "A:3") }
        refuses("print area with a sheet prefix") { XlsxPrintSetup.Spec(area = "Sheet1!A1:C10") }
        refuses("print area of a whole column") { XlsxPrintSetup.Spec(area = "A:A") }
        refuses("print area with an absolute marker") { XlsxPrintSetup.Spec(area = "${'$'}A${'$'}1") }
        refuses("a negative definedName scope") {
            XlsxStyledBook.DefinedName("_xlnm.Print_Area", "Fit!\$A\$1", -1)
        }
        refuses("a scope past the last sheet") {
            XlsxStyledBook.WorkbookSpec(
                sheets = listOf(XlsxStyledBook.StyledSheet("Only", listOf(cell("A1", "x")))),
                definedNames = listOf(XlsxStyledBook.DefinedName("Total", "1", 3)),
            )
        }
        refuses("a hand-written record duplicating the sheet's own") {
            XlsxStyledBook.WorkbookSpec(
                sheets = listOf(XlsxStyledBook.StyledSheet("Fit", listOf(cell("A1", "x")), print = FIT)),
                definedNames = listOf(XlsxStyledBook.DefinedName("_xlnm.Print_Area", "Fit!\$A\$1", 0)),
            )
        }
        refuses("two names with the same name and scope") {
            XlsxStyledBook.WorkbookSpec(
                sheets = listOf(XlsxStyledBook.StyledSheet("Only", listOf(cell("A1", "x")))),
                definedNames = listOf(
                    XlsxStyledBook.DefinedName("Total", "1"),
                    XlsxStyledBook.DefinedName("Total", "2"),
                ),
            )
        }

        // 9. Scope is a key, not a duplicate: the same name on two sheets is legal and must be kept apart.
        val scoped = XlsxStyledBook.WorkbookSpec(
            sheets = listOf(
                XlsxStyledBook.StyledSheet("A", listOf(cell("A1", "a"))),
                XlsxStyledBook.StyledSheet("B", listOf(cell("A1", "b"))),
            ),
            definedNames = listOf(
                XlsxStyledBook.DefinedName("Local", "A!\$A\$1", 0),
                XlsxStyledBook.DefinedName("Local", "B!\$A\$1", 1),
            ),
        )
        val scopedXml = part(XlsxStyledBook.write(scoped), "xl/workbook.xml")
        check(scopedXml.contains("localSheetId=\"0\"") && scopedXml.contains("localSheetId=\"1\"")) {
            "two sheet-scoped names with one spelling must both survive: $scopedXml"
        }

        // 10. A foreign package: inert counts dropped, resolvable records kept, unresolvable ones preserved.
        val foreign = ForeignPackage.build()
        File(folder, "r98-foreign.xlsx").writeBytes(foreign)
        val foreignBook = XlsxStyledBook.read(foreign)
        val foreignSheet = foreignBook.sheets.single()
        check(foreignSheet.print?.fitToPage == false) { "foreign fitToPage must read as false: ${foreignSheet.print}" }
        check(foreignSheet.print?.setup?.fitToWidth == null && foreignSheet.print?.setup?.fitToHeight == null) {
            "inert fit counts were modelled anyway: ${foreignSheet.print?.setup}"
        }
        check(foreignSheet.print?.setup?.paperSize == 9) { "the meaningful pageSetup attributes were lost" }
        check(foreignSheet.print?.area == "A1:B4") { "the scoped print area did not fold back: ${foreignSheet.print?.area}" }
        check(foreignSheet.print?.titles == XlsxPrintSetup.Titles(rows = "1:2")) {
            "the scoped print titles did not fold back: ${foreignSheet.print?.titles}"
        }
        // Two records cannot be resolved: one is scoped past the last sheet, one names a different sheet.
        // Both are dropped, because the model keeps one area per sheet and a survivor would collide on write
        // with the area that did resolve. Workbook names are untouched by that repair.
        check(foreignBook.definedNames.none { it.localSheetId != null }) {
            "no uninterpretable print record may survive as a name: ${foreignBook.definedNames}"
        }
        check(foreignBook.definedNames.none { it.reference.startsWith("Other!") }) {
            "a record naming another sheet must not be kept: ${foreignBook.definedNames}"
        }
        check(foreignBook.definedNames.any { it.name == "Total" }) { "a workbook name was dropped" }
        // Reading a foreign package and writing it back keeps the resolvable semantics.
        val foreignRewritten = XlsxStyledBook.read(XlsxStyledBook.write(foreignBook))
        check(foreignRewritten.sheets.single().print?.area == "A1:B4") {
            "a foreign print area did not survive read→write→read"
        }

        // 11. The same package with the flag on: the counts are meaningful and must survive.
        val foreignFit = ForeignFitPackage.build()
        File(folder, "r98-foreign-fit.xlsx").writeBytes(foreignFit)
        val foreignFitBook = XlsxStyledBook.read(foreignFit)
        check(foreignFitBook.sheets.single().print?.setup?.fitToWidth == 3) {
            "a meaningful fitToWidth was dropped: ${foreignFitBook.sheets.single().print}"
        }
        check(foreignFitBook.sheets.single().print?.fitToPage == true) { "the fit flag was not read" }
        refuses("a Spec whose fit counts lost their flag") {
            // The model refuses the inert pair at construction, so a caller cannot build one by accident.
            XlsxPrintSetup.Spec(setup = XlsxPrintSetup.Setup(fitToWidth = 3, fitToHeight = 2))
        }

        val sheetsWritten = WORKBOOK.sheets.size
        val records = WORKBOOK.sheets.sumOf { sheet ->
            sheet.print?.let { XlsxPrintSetup.records(sheet.name, it).size } ?: 0
        }
        println(
            "R98_PRINT_OK assertions=$assertions sheets=$sheetsWritten records=$records refusals=$refusals",
        )
    }
}
