// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.XlsxAutoFilter
import dev.fusion.ooxml.XlsxHyperlinks
import dev.fusion.ooxml.XlsxMerges
import dev.fusion.ooxml.XlsxPrintSetup
import dev.fusion.ooxml.XlsxProtection
import dev.fusion.ooxml.XlsxStyledBook
import dev.fusion.ooxml.XlsxValidation
import java.io.File

/**
 * R94 spreadsheet contracts: merged cells (`<mergeCells>`), cell hyperlinks (`<hyperlinks>` plus the worksheet
 * relationship part) and print layout (`<printOptions>`, `<pageMargins>`, `<pageSetup>`).
 *
 * Host JVM only. The probe writes real `.xlsx` packages and three intent tables (`merges.tsv`,
 * `hyperlinks.tsv`, `print.tsv`). Independent witnesses: `tools/check_r94_sheet_structure.py` (raw OPC +
 * openpyxl's `merged_cells`, `cell.hyperlink`, and the worksheet relationship part) and
 * `tools/check_r94_sheet_print.py` (raw OPC + openpyxl's `page_margins`, `print_options`, `page_setup`).
 * No Excel, LibreOffice or Android run: nothing here says a cell was clicked, a page was printed or a range
 * was rendered as one cell.
 */
object R94Checks {
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

    private val MERGES = listOf(XlsxMerges.Range("C1:D1"), XlsxMerges.Range("A3:A4"))
    private val LINKS = listOf(
        XlsxHyperlinks.Link(
            ref = "A1",
            target = "https://example.com/a?b=1&c=2",
            // Escaping surface: a tooltip is an attribute value, so `&` and `<` must be entity-escaped.
            tooltip = "open & <go>",
            display = "example",
        ),
        XlsxHyperlinks.Link(ref = "A2", location = "'Grid'!B1"),
        XlsxHyperlinks.Link(ref = "B1", target = "mailto:someone@example.com"),
        // The same URI as A1: one relationship, two cells.
        XlsxHyperlinks.Link(ref = "A3", target = "https://example.com/a?b=1&c=2"),
    )
    private val PRINT = XlsxPrintSetup.Spec(
        options = XlsxPrintSetup.Options(horizontalCentered = true, gridLines = true),
        margins = XlsxPrintSetup.Margins(left = 0.5),
        setup = XlsxPrintSetup.Setup(
            orientation = XlsxPrintSetup.Orientation.LANDSCAPE,
            paperSize = 9,
            scale = 85,
        ),
    )

    private fun tsv(value: String?): String =
        when (value) {
            null -> "-"
            else -> value.replace("\t", "\\t").replace("\n", "\\n")
        }

    /** One `merges.tsv` row per sheet, one `hyperlinks.tsv` row per link (or a `none` row), one `print.tsv`. */
    private fun writeIntent(
        folder: File,
        sheets: List<Pair<String, XlsxStyledBook.StyledSheet>>,
    ) {
        val mergeLines = ArrayList<String>()
        val linkLines = ArrayList<String>()
        val printLines = ArrayList<String>()
        for ((file, sheet) in sheets) {
            mergeLines.add(
                listOf(file, sheet.name, sheet.merges.joinToString(",") { it.ref }.ifEmpty { "-" }).joinToString("\t"),
            )
            if (sheet.hyperlinks.isEmpty()) {
                linkLines.add(listOf(file, sheet.name, "-", "none", "-", "-", "-").joinToString("\t"))
            }
            for (link in sheet.hyperlinks) {
                val kind = if (link.target != null) "external" else "internal"
                linkLines.add(
                    listOf(
                        file, sheet.name, link.ref, kind,
                        tsv(link.target ?: link.location), tsv(link.tooltip), tsv(link.display),
                    ).joinToString("\t"),
                )
            }
            val print = sheet.print
            if (print == null) {
                printLines.add(listOf(file, sheet.name, "0", "-", "-", "-", "-", "-", "-", "-", "-", "-", "-").joinToString("\t"))
            } else {
                val options = print.options
                val flags = buildList {
                    if (options?.horizontalCentered == true) add("horizontalCentered")
                    if (options?.verticalCentered == true) add("verticalCentered")
                    if (options?.headings == true) add("headings")
                    if (options?.gridLines == true) add("gridLines")
                }.joinToString(",").ifEmpty { "-" }
                val m = print.margins ?: XlsxPrintSetup.Margins()
                printLines.add(
                    listOf(
                        file, sheet.name, "1", flags,
                        m.left.toString(), m.right.toString(), m.top.toString(),
                        m.bottom.toString(), m.header.toString(), m.footer.toString(),
                        print.setup?.orientation?.token ?: "-",
                        print.setup?.paperSize?.toString() ?: "-",
                        print.setup?.scale?.toString() ?: "-",
                    ).joinToString("\t"),
                )
            }
        }
        File(folder, "merges.tsv").writeText(mergeLines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        File(folder, "hyperlinks.tsv").writeText(linkLines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        File(folder, "print.tsv").writeText(printLines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }

    private val FOREIGN_PRINT = XlsxPrintSetup.Spec(
        options = XlsxPrintSetup.Options(gridLines = true),
        margins = XlsxPrintSetup.Margins(left = 0.7, right = 0.7, top = 0.75, bottom = 0.75, header = 0.3, footer = 0.3),
        setup = XlsxPrintSetup.Setup(orientation = XlsxPrintSetup.Orientation.PORTRAIT, paperSize = 1, scale = 50),
    )

    /** A minimal package assembled byte by byte, in the shapes openpyxl writes rather than the ones this writer emits. */
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
                    """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="$MAIN" xmlns:r="$REL"><sheets><sheet name="Foreign" sheetId="1" r:id="rId1"/></sheets></workbook>""",
                "xl/_rels/workbook.xml.rels" to
                    """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="$PKG"><Relationship Id="rId1" Type="$REL/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
                // openpyxl's own shapes: `xmlns:r` on the hyperlink, pageSetup attributes out of the order this
                // writer uses, and a relationship whose attributes are Type, Target, TargetMode, Id.
                "xl/worksheets/sheet1.xml" to
                    """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="$MAIN"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>a</t></is></c></row></sheetData><mergeCells count="1"><mergeCell ref="A1:B1"/></mergeCells><hyperlinks><hyperlink xmlns:r="$REL" ref="A1" r:id="rId1"/></hyperlinks><printOptions gridLines="1"/><pageMargins left="0.7" right="0.7" top="0.75" bottom="0.75" header="0.3" footer="0.3"/><pageSetup orientation="portrait" paperSize="1" scale="50"/></worksheet>""",
                "xl/worksheets/_rels/sheet1.xml.rels" to
                    """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="$PKG"><Relationship Type="$REL/hyperlink" Target="https://foreign.example/x" TargetMode="External" Id="rId1"/></Relationships>""",
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

    private fun refuses(name: String, block: () -> Unit) {
        assertions++
        val threw = try {
            block()
            false
        } catch (expected: IllegalArgumentException) {
            check(expected.message?.isNotBlank() == true) { "$name refused without a message" }
            true
        }
        require(threw) { "FAILED: $name was accepted" }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val folder = File(args.firstOrNull() ?: "testing/build/r94/artifacts")
        folder.mkdirs()

        // 1. Nothing declared: the sheet part and the package shape stay as they were before this feature.
        val plain = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(sheets = listOf(XlsxStyledBook.StyledSheet("Plain", listOf(cell("A1", "open"))))),
        )
        File(folder, "r94-plain.xlsx").writeBytes(plain)
        val plainSheet = part(plain, "xl/worksheets/sheet1.xml")
        check(plainSheet.endsWith("</sheetData></worksheet>")) { "the plain sheet tail changed: $plainSheet" }
        check(!plainSheet.contains("mergeCells") && !plainSheet.contains("hyperlink")) { "a plain sheet gained a record" }
        check(!plainSheet.contains("pageMargins") && !plainSheet.contains("printOptions")) { "a plain sheet gained print" }
        check(partOrNull(plain, "xl/worksheets/_rels/sheet1.xml.rels") == null) {
            "a sheet without links must not grow a relationship part"
        }

        // 2. The declared sheet, and a second sheet that declares nothing.
        val grid = XlsxStyledBook.StyledSheet(
            name = "Grid",
            cells = listOf(cell("A1", "open"), cell("A2", "jump"), cell("B1", "x"), cell("C1", "D1")),
            merges = MERGES,
            hyperlinks = LINKS,
            print = PRINT,
        )
        val bare = XlsxStyledBook.StyledSheet("Bare", listOf(cell("A1", "nothing")))
        // Every tail record this writer can emit, on one sheet: the order between them is what a strict reader
        // checks, and no single-feature probe can see it.
        val every = XlsxStyledBook.StyledSheet(
            name = "Every",
            cells = listOf(cell("A1", "a"), cell("A2", "b"), cell("B1", "c")),
            protection = XlsxProtection.SheetProtection.ofPassword("secret"),
            autoFilter = XlsxAutoFilter.AutoFilter("A1:B5", listOf(XlsxAutoFilter.Column(0, values = listOf("red")))),
            merges = listOf(XlsxMerges.Range("C1:D1")),
            validations = listOf(
                XlsxValidation.Rule(
                    sqref = "A1:A5",
                    type = XlsxValidation.Type.WHOLE,
                    operator = XlsxValidation.Operator.BETWEEN,
                    formula1 = "1",
                    formula2 = "9",
                ),
            ),
            hyperlinks = listOf(XlsxHyperlinks.Link(ref = "B1", target = "https://example.org/x")),
            print = XlsxPrintSetup.Spec(
                margins = XlsxPrintSetup.Margins(),
                setup = XlsxPrintSetup.Setup(orientation = XlsxPrintSetup.Orientation.PORTRAIT),
            ),
        )
        // Internal links only: a real hyperlink list with no relationship part at all.
        val internal = XlsxStyledBook.StyledSheet(
            name = "Internal",
            cells = listOf(cell("A1", "jump"), cell("A2", "here")),
            hyperlinks = listOf(XlsxHyperlinks.Link(ref = "A1", location = "A2")),
        )
        val book = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(sheets = listOf(grid, bare, every, internal)),
        )
        File(folder, "r94.xlsx").writeBytes(book)

        val sheet = part(book, "xl/worksheets/sheet1.xml")
        check(sheet.contains("""<mergeCells count="2"><mergeCell ref="C1:D1"/><mergeCell ref="A3:A4"/></mergeCells>""")) {
            "the merged ranges are wrong: $sheet"
        }
        // CT_Worksheet sequence over the tail this feature touches.
        val mergeAt = sheet.indexOf("<mergeCells")
        val linkAt = sheet.indexOf("<hyperlinks")
        val optionsAt = sheet.indexOf("<printOptions")
        val marginsAt = sheet.indexOf("<pageMargins")
        val setupAt = sheet.indexOf("<pageSetup")
        check(sheet.indexOf("</sheetData>") < mergeAt) { "mergeCells must follow sheetData" }
        check(mergeAt < linkAt && linkAt < optionsAt && optionsAt < marginsAt && marginsAt < setupAt) {
            "the CT_Worksheet tail order is broken: $sheet"
        }
        check(sheet.contains("""<hyperlinks xmlns:r="${XlsxHyperlinks.REL_NS}">""")) { "the hyperlinks namespace is missing" }
        check(
            sheet.contains(
                """<hyperlink ref="A1" r:id="rHl1" tooltip="open &amp; &lt;go&gt;" display="example"/>""",
            ),
        ) { "the external hyperlink attributes are wrong: $sheet" }
        check(sheet.contains("""<hyperlink ref="A2" location="'Grid'!B1"/>""")) { "the internal hyperlink is wrong" }
        check(sheet.contains("""<hyperlink ref="B1" r:id="rHl2"/>""")) { "the second external hyperlink is wrong" }
        // The duplicate URI reuses rHl1 rather than emitting a third relationship.
        check(sheet.contains("""<hyperlink ref="A3" r:id="rHl1"/>""")) { "a duplicate target got its own relationship" }
        check(sheet.contains("""<printOptions horizontalCentered="1" gridLines="1"/>""")) { "printOptions are wrong" }
        check(
            sheet.contains(
                """<pageMargins left="0.5" right="0.75" top="1" bottom="1" header="0.5" footer="0.5"/>""",
            ),
        ) { "pageMargins are wrong: $sheet" }
        check(sheet.contains("""<pageSetup paperSize="9" scale="85" orientation="landscape"/>""")) {
            "pageSetup is wrong: $sheet"
        }

        val rels = part(book, "xl/worksheets/_rels/sheet1.xml.rels")
        check(rels.contains("""Id="rHl1" Type="${XlsxHyperlinks.HYPERLINK_REL}"""") ) { "the first relationship is wrong" }
        check(rels.contains("""Target="https://example.com/a?b=1&amp;c=2" TargetMode="External"/>""")) {
            "the escaped target is wrong: $rels"
        }
        check(rels.contains("""Id="rHl2" Type="${XlsxHyperlinks.HYPERLINK_REL}" Target="mailto:someone@example.com"""") ) {
            "the second relationship is wrong: $rels"
        }
        val relationshipCount = Regex("""<Relationship [^>]*/>""").findAll(rels).count()
        check(relationshipCount == 2) {
            "the relationship part carries $relationshipCount relationships: $rels"
        }

        val everySheet = part(book, "xl/worksheets/sheet3.xml")
        val everyOrder = listOf(
            "</sheetData>", "<sheetProtection", "<autoFilter", "<mergeCells", "<dataValidations",
            "<hyperlinks", "<pageMargins", "<pageSetup",
        ).map { marker ->
            val at = everySheet.indexOf(marker)
            check(at >= 0) { "the combined sheet is missing $marker: $everySheet" }
            at
        }
        check(everyOrder == everyOrder.sorted()) { "the six-record tail is out of CT_Worksheet order: $everySheet" }

        val internalSheet = part(book, "xl/worksheets/sheet4.xml")
        check(internalSheet.contains("""<hyperlink ref="A1" location="A2"/>""")) { "the internal link is wrong" }
        check(!internalSheet.contains("r:id=")) { "an internal-only sheet declared a relationship id" }
        check(partOrNull(book, "xl/worksheets/_rels/sheet4.xml.rels") == null) {
            "an internal-only sheet must not grow a relationship part"
        }

        val bareSheet = part(book, "xl/worksheets/sheet2.xml")
        check(!bareSheet.contains("hyperlinks") && !bareSheet.contains("mergeCells")) { "the bare sheet gained a record" }
        check(partOrNull(book, "xl/worksheets/_rels/sheet2.xml.rels") == null) { "the bare sheet grew a rels part" }

        // 3. Round-trip through this code's own reader, then a byte-identical rewrite.
        val back = XlsxStyledBook.read(book)
        check(back.sheets[0].merges == MERGES) { "merges did not round-trip: ${back.sheets[0].merges}" }
        check(back.sheets[0].hyperlinks == LINKS) { "hyperlinks did not round-trip: ${back.sheets[0].hyperlinks}" }
        check(back.sheets[0].print == PRINT) { "print layout did not round-trip: ${back.sheets[0].print}" }
        check(back.sheets[1].merges.isEmpty() && back.sheets[1].hyperlinks.isEmpty()) { "the bare sheet gained state" }
        check(back.sheets[1].print == null) { "the bare sheet gained a print record" }
        val rewritten = XlsxStyledBook.write(back)
        check(part(rewritten, "xl/worksheets/sheet1.xml") == sheet) { "the sheet changed on a read/write cycle" }
        check(part(rewritten, "xl/worksheets/_rels/sheet1.xml.rels") == rels) { "the rels changed on a rewrite" }
        check(partOrNull(rewritten, "xl/worksheets/_rels/sheet2.xml.rels") == null) { "a rels part appeared on rewrite" }
        check(part(rewritten, "xl/worksheets/sheet3.xml") == everySheet) { "the combined sheet drifted" }
        check(part(rewritten, "xl/worksheets/sheet4.xml") == internalSheet) { "the internal-only sheet drifted" }
        check(back.sheets[2].merges.size == 1 && back.sheets[2].hyperlinks.size == 1) { "the combined sheet lost state" }
        check(back.sheets[3].hyperlinks.map { it.location } == listOf("A2")) { "the internal link did not round-trip" }

        // 3b. Reading bytes this writer did not produce: attributes in another order, `xmlns:r` declared on the
        // hyperlink element instead of on `<hyperlinks>`, and a relationship part whose attribute order is the
        // one openpyxl emits. The reader must not depend on any of that.
        val foreign = XlsxStyledBook.read(ForeignPackage.build())
        check(foreign.sheets.size == 1 && foreign.sheets[0].name == "Foreign") { "the foreign workbook did not read" }
        check(foreign.sheets[0].merges.map { it.ref } == listOf("A1:B1")) {
            "the foreign merge did not read: ${foreign.sheets[0].merges}"
        }
        check(foreign.sheets[0].hyperlinks.map { it.target } == listOf("https://foreign.example/x")) {
            "the foreign hyperlink did not resolve through its relationship part: ${foreign.sheets[0].hyperlinks}"
        }
        check(foreign.sheets[0].print == FOREIGN_PRINT) {
            "the foreign print layout did not read: ${foreign.sheets[0].print}"
        }

        // 4. Refusals: each would write a package Excel reports as damaged.
        refuses("overlapping merged ranges") {
            XlsxStyledBook.StyledSheet("S", listOf(cell("A1", "x")),
                merges = listOf(XlsxMerges.Range("A1:B2"), XlsxMerges.Range("B2:C3")))
        }
        refuses("a single-cell merge") { XlsxMerges.Range("A1") }
        refuses("a lower-case merge reference") { XlsxMerges.Range("a1:b2") }
        refuses("a merge outside the grid") { XlsxMerges.Range("A1:A1048577") }
        refuses("too many merged ranges") {
            XlsxMerges.requireDisjoint(List(XlsxMerges.MAX_RANGES + 1) { XlsxMerges.Range("A${it + 1}:B${it + 1}") })
        }
        refuses("a hyperlink with a target and a location") {
            XlsxHyperlinks.Link(ref = "A1", target = "https://example.com", location = "A2")
        }
        refuses("a hyperlink with neither") { XlsxHyperlinks.Link(ref = "A1") }
        refuses("a relative hyperlink target") { XlsxHyperlinks.Link(ref = "A1", target = "other.xlsx#Sheet1") }
        refuses("a hyperlink target with a space") { XlsxHyperlinks.Link(ref = "A1", target = "https://example.com/a b") }
        refuses("a hyperlink tooltip with a control character") {
            XlsxHyperlinks.Link(ref = "A1", target = "https://example.com", tooltip = "a\u0001b")
        }
        refuses("an empty hyperlink display") {
            XlsxHyperlinks.Link(ref = "A1", target = "https://example.com", display = "")
        }
        refuses("a hyperlink location that is not a reference") {
            XlsxHyperlinks.Link(ref = "A1", location = "NotASheet")
        }
        refuses("a hyperlink location with two bangs") { XlsxHyperlinks.Link(ref = "A1", location = "A!B!C1") }
        refuses("an empty printOptions") { XlsxPrintSetup.Options() }
        refuses("an empty pageSetup") { XlsxPrintSetup.Setup() }
        refuses("a scale below 10") { XlsxPrintSetup.Setup(scale = 5) }
        refuses("a scale above 400") { XlsxPrintSetup.Setup(scale = 401) }
        refuses("a paper size below 1") { XlsxPrintSetup.Setup(paperSize = 0) }
        refuses("a paper size above 118") { XlsxPrintSetup.Setup(paperSize = 119) }
        refuses("a negative margin") { XlsxPrintSetup.Margins(left = -0.1) }
        refuses("a margin above the model bound") { XlsxPrintSetup.Margins(top = 1000.0) }
        refuses("a margin with more than four decimals") { XlsxPrintSetup.Margins(bottom = 0.123456) }

        // 5. Parsing keeps what it can hold and drops what it cannot, instead of repairing silently.
        val parsedMerges = XlsxMerges.parse(
            dev.fusion.ooxml.BoundedXml.parse(
                ("""<worksheet><mergeCells count="4"><mergeCell ref="A1:B2"/><mergeCell ref="a3:b4"/>""" +
                    """<mergeCell ref="B2:C3"/><mergeCell ref="D1:E1"/></mergeCells></worksheet>""")
                    .toByteArray(Charsets.UTF_8),
            ),
        )
        // `a3:b4` is normalised; `B2:C3` overlaps `A1:B2` and is dropped; the rest is kept in file order.
        check(parsedMerges.map { it.ref } == listOf("A1:B2", "A3:B4", "D1:E1")) {
            "unreadable merges were not dropped cleanly: ${parsedMerges.map { it.ref }}"
        }
        val parsedLinks = XlsxHyperlinks.parse(
            dev.fusion.ooxml.BoundedXml.parse(
                ("""<worksheet xmlns:r="${XlsxHyperlinks.REL_NS}"><hyperlinks>""" +
                    """<hyperlink ref="A1" r:id="rHl1"/><hyperlink ref="A2" r:id="rHl9"/>""" +
                    """<hyperlink ref="A3" location="B1"/></hyperlinks></worksheet>""").toByteArray(Charsets.UTF_8),
            ),
            mapOf("rHl1" to "https://example.com/x"),
        )
        check(parsedLinks.map { it.ref } == listOf("A1", "A3")) {
            "an unresolved r:id was not dropped: ${parsedLinks.map { it.ref }}"
        }
        val relsParsed = XlsxHyperlinks.parseRels(
            ("""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="${XlsxHyperlinks.PKG_REL_NS}">""" +
                """<Relationship Id="rHl1" Type="${XlsxHyperlinks.HYPERLINK_REL}" Target="https://a.example" """ +
                """TargetMode="External"/><Relationship Id="rId9" Type="${XlsxHyperlinks.REL_NS}/drawing" """ +
                """Target="../drawings/drawing1.xml"/></Relationships>""").toByteArray(Charsets.UTF_8),
        )
        check(relsParsed == mapOf("rHl1" to "https://a.example")) { "a non-hyperlink relationship leaked: $relsParsed" }

        // 6. Artifacts for the independent readers.
        writeIntent(
            folder,
            listOf(
                "r94.xlsx" to grid,
                "r94.xlsx" to bare,
                "r94.xlsx" to every,
                "r94.xlsx" to internal,
                "r94-plain.xlsx" to XlsxStyledBook.StyledSheet("Plain", listOf(cell("A1", "open"))),
            ),
        )
        val mergeRows = File(folder, "merges.tsv").readLines().count { it.isNotBlank() }
        val linkRows = File(folder, "hyperlinks.tsv").readLines().count { it.isNotBlank() }
        val printRows = File(folder, "print.tsv").readLines().count { it.isNotBlank() }
        check(mergeRows == 5 && printRows == 5) { "intent rows: merges=$mergeRows print=$printRows" }
        check(linkRows == 8) { "intent link rows: $linkRows (6 links plus two none rows)" }

        println(
            "R94_OFFICE_OK assertions=$assertions workbooks=2 sheets=5 merges=4 links=6 relationships=2 " +
                "print=2 foreign=1",
        )
    }
}
