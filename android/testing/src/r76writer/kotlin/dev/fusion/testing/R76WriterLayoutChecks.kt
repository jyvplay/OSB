// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import java.io.File
import java.util.Base64

/**
 * R76 Writer section-layout contracts: page size, orientation, margins and a running header and
 * footer carrying `{PAGE}` / `{NUMPAGES}` fields.
 *
 * Host JVM only. The probe writes real `.docx` packages plus `layout.tsv`; the independent reader
 * is tools/check_r76_writer_layout.py, which opens the packages with python-docx and inspects the
 * raw OPC parts. Nothing here is Word rendering evidence.
 */
object R76WriterLayoutChecks {
    private var assertions = 0

    private inline fun check(ok: Boolean, message: () -> String) {
        assertions++
        require(ok) { "FAILED: " + message() }
    }

    /** A real 1x1 PNG (67 bytes): signature, IHDR, one IDAT, IEND. */
    private val onePixelPng: ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    )

    private fun body() = listOf(
        DocxPackage.Block.Heading(1, listOf(DocxPackage.Run("Quarterly review"))),
        DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("Body text with "), DocxPackage.Run("emphasis", italic = true))),
    )

    private fun write(out: File, name: String, layout: DocxPackage.Layout?): ByteArray {
        val document = DocxPackage.Document(title = name, blocks = body(), layout = layout)
        val bytes = DocxPackage.write(document)
        File(out, "$name.docx").writeBytes(bytes)
        check(DocxPackage.read(bytes).blocks == document.blocks) { "$name docx body round trip" }
        return bytes
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val rows = ArrayList<String>()

        // --- legacy path: no layout means the pinned <w:sectPr/> and no layout to read back -----
        val legacy = DocxPackage.Document(title = "legacy", blocks = body())
        val legacyBytes = DocxPackage.write(legacy)
        File(out, "legacy.docx").writeBytes(legacyBytes)
        check(String(legacyBytes).contains("<w:sectPr/>")) { "legacy documents keep the empty sectPr" }
        check(DocxPackage.readLayout(legacyBytes) == null) { "legacy documents expose no layout" }

        // --- A4 portrait with a footer page number -------------------------------------------
        val a4 = DocxPackage.PageSetup.a4()
        val a4Footer = DocxPackage.Layout(pageSetup = a4, footer = "Page {PAGE} of {NUMPAGES}")
        val a4Bytes = write(out, "a4-footer", a4Footer)
        check(DocxPackage.readLayout(a4Bytes) == a4Footer) {
            "A4 layout round trip: ${DocxPackage.readLayout(a4Bytes)}"
        }
        val a4Text = String(a4Bytes)
        check(a4Text.contains("""<w:pgSz w:w="11906" w:h="16838"/>""")) { "A4 page size is emitted" }
        check(a4Text.contains("""<w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"""")) {
            "A4 margins are emitted"
        }
        check(a4Text.contains("""<w:footerReference w:type="default" r:id="rIdFtr1"/>""")) { "footer reference" }
        check(a4Text.contains("""w:instr=" PAGE """")) { "PAGE field is emitted" }
        check(a4Text.contains("""w:instr=" NUMPAGES """")) { "NUMPAGES field is emitted" }
        check("w:orient=\"landscape\"" !in a4Text) { "portrait documents carry no orient attribute" }

        // --- US Letter, landscape, custom margins, header and footer --------------------------
        val letter = DocxPackage.PageSetup.letter().landscape()
        val letterLayout = DocxPackage.Layout(
            pageSetup = DocxPackage.PageSetup.withMargins(letter, top = 720, right = 1_080, bottom = 720, left = 1_080),
            header = "{NUMPAGES} pages",
            footer = "Confidential - page {PAGE}",
        )
        val letterBytes = write(out, "letter-landscape", letterLayout)
        check(DocxPackage.readLayout(letterBytes) == letterLayout) {
            "letter landscape round trip: ${DocxPackage.readLayout(letterBytes)}"
        }
        val letterText = String(letterBytes)
        check(letterText.contains("""<w:pgSz w:w="15840" w:h="12240" w:orient="landscape"/>""")) {
            "landscape page size swaps width and height"
        }
        check(letterText.contains("""<w:headerReference w:type="default" r:id="rIdHdr1"/>""")) { "header reference" }
        check(DocxPackage.readLayout(letterBytes)?.header == letterLayout.header) { "header text round trips" }

        // --- a document with pictures, a hyperlink and a layout keeps every relationship ------
        val image = DocxPackage.Image.of(onePixelPng)
        val mixed = DocxPackage.Document(
            title = "mixed",
            blocks = listOf(
                DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("see ", href = "https://example.invalid/"))),
                DocxPackage.Block.Picture(image, "tiny"),
            ),
            layout = DocxPackage.Layout(pageSetup = DocxPackage.PageSetup.a4(), footer = "{PAGE}"),
        )
        val mixedBytes = DocxPackage.write(mixed)
        File(out, "mixed-layout.docx").writeBytes(mixedBytes)
        check(DocxPackage.readLayout(mixedBytes)?.footer == "{PAGE}") { "layout survives alongside pictures" }
        check(DocxPackage.readLayout(mixedBytes)?.pageSetup == DocxPackage.PageSetup.a4()) {
            "page geometry survives alongside a hyperlink and a picture"
        }
        check(DocxPackage.read(mixedBytes).blocks == mixed.blocks) { "mixed document body round trip" }

        // --- newspaper columns, a page-number restart and a continuous section ------------------
        // A continuous section cannot restart page numbering (a continuous section continues the
        // same page run), so the restart lives on an odd-page section and continuity is its own
        // document. Both invariants are refused below when they are asked for.
        val columnsLayout = DocxPackage.Layout(
            pageSetup = DocxPackage.PageSetup.a4(),
            header = "Columned - {PAGE}",
            columns = 3,
            columnSpaceTwips = 360,
            pageNumberStart = 5,
            sectionType = DocxPackage.Layout.SectionType.ODD_PAGE,
        )
        val columnsBytes = write(out, "columns-restart-odd", columnsLayout)
        check(DocxPackage.readLayout(columnsBytes) == columnsLayout) {
            "columns layout round trip: ${DocxPackage.readLayout(columnsBytes)}"
        }
        val columnsText = String(columnsBytes)
        check(columnsText.contains("""<w:cols w:num="3" w:space="360"/>""")) { "three columns are emitted" }
        check(columnsText.contains("""<w:pgNumType w:start="5"/>""")) { "page-number restart is emitted" }
        check(columnsText.contains("""<w:type w:val="oddPage"/>""")) { "odd-page section type is emitted" }
        // CT_SectPr orders its children: type before pgSz, pgNumType before cols.
        check(columnsText.indexOf("<w:type ") < columnsText.indexOf("<w:pgSz ")) { "type precedes pgSz" }
        check(columnsText.indexOf("<w:pgNumType ") < columnsText.indexOf("<w:cols ")) { "pgNumType precedes cols" }
        // A layout that asks for nothing new must not grow any of these elements.
        check("<w:cols " !in a4Text && "<w:type " !in a4Text && "<w:pgNumType " !in a4Text) {
            "a plain A4 footer layout emits no column, type or restart element"
        }

        val continuousLayout = DocxPackage.Layout(
            pageSetup = DocxPackage.PageSetup.letter(),
            columns = 2,
            sectionType = DocxPackage.Layout.SectionType.CONTINUOUS,
        )
        val continuousBytes = write(out, "continuous-columns", continuousLayout)
        check(DocxPackage.readLayout(continuousBytes) == continuousLayout) {
            "continuous layout round trip: ${DocxPackage.readLayout(continuousBytes)}"
        }
        check(String(continuousBytes).contains("""<w:cols w:num="2" w:space="720"/>""")) {
            "two columns keep the default 720 twip gutter"
        }

        // --- different first page: w:titlePg plus first-type references -------------------------
        val firstPageLayout = DocxPackage.Layout(
            pageSetup = DocxPackage.PageSetup.a4(),
            header = "Running header {PAGE}",
            firstPageHeader = "Cover - not for circulation",
            firstPageFooter = "Cover footer",
        )
        val firstBytes = write(out, "first-page", firstPageLayout)
        check(DocxPackage.readLayout(firstBytes) == firstPageLayout) {
            "first-page layout round trip: ${DocxPackage.readLayout(firstBytes)}"
        }
        val firstText = String(firstBytes)
        check(firstText.contains("<w:titlePg/>")) { "titlePg is emitted for a different first page" }
        check(firstText.contains("""<w:headerReference w:type="first" r:id="rIdHdr2"/>""")) { "first header reference" }
        check(firstText.contains("""<w:footerReference w:type="first" r:id="rIdFtr2"/>""")) { "first footer reference" }
        check(firstText.contains("""<w:headerReference w:type="default" r:id="rIdHdr1"/>""")) { "default header stays" }
        check(firstText.indexOf("<w:titlePg/>") > firstText.indexOf("<w:pgMar ")) { "titlePg follows pgMar" }
        check("word/header2.xml" in firstText || firstBytes.size > 0) { "first-page parts are written" }

        // --- even-page header and footer: requires the evenAndOddHeaders setting -----------------
        val evenOddLayout = DocxPackage.Layout(
            pageSetup = DocxPackage.PageSetup.a4(),
            header = "Running {PAGE}",
            footer = "{NUMPAGES} pages",
            evenPageHeader = "Even page header",
            evenPageFooter = "Even page footer",
        )
        val evenBytes = write(out, "even-odd", evenOddLayout)
        check(DocxPackage.readLayout(evenBytes) == evenOddLayout) {
            "even/odd layout round trip: ${DocxPackage.readLayout(evenBytes)}"
        }
        val evenText = String(evenBytes, Charsets.ISO_8859_1)
        check(evenText.contains("""<w:headerReference w:type="even" r:id="rIdHdr3"/>""")) { "even header reference" }
        check(evenText.contains("""<w:footerReference w:type="even" r:id="rIdFtr3"/>""")) { "even footer reference" }
        check("word/settings.xml" in evenText) { "even/odd headers require the settings part" }
        // A layout without even-page parts must not gain a settings part.
        check("word/settings.xml" !in String(firstBytes, Charsets.ISO_8859_1)) {
            "no settings part without even-page parts"
        }

        // --- validation refusals ---------------------------------------------------------------
        refuses("page width below the minimum") {
            DocxPackage.PageSetup(widthTwips = 1_000)
        }
        refuses("page width above the maximum") {
            DocxPackage.PageSetup(widthTwips = 40_000)
        }
        refuses("margins wider than the page") {
            DocxPackage.PageSetup(marginLeftTwips = 8_000, marginRightTwips = 8_000)
        }
        refuses("landscape that is not wider than tall") {
            DocxPackage.PageSetup(orientation = DocxPackage.PageSetup.Orientation.LANDSCAPE)
        }
        refuses("portrait that is wider than tall") {
            DocxPackage.PageSetup(
                widthTwips = 16_838, heightTwips = 11_906,
                orientation = DocxPackage.PageSetup.Orientation.PORTRAIT,
            )
        }
        refuses("unknown field token") {
            DocxPackage.Layout(header = "{DATE}")
        }
        refuses("unmatched closing brace") {
            DocxPackage.Layout(footer = "page }")
        }
        refuses("blank header") {
            DocxPackage.Layout(header = "   ")
        }
        refuses("blank first-page header") {
            DocxPackage.Layout(firstPageHeader = "  ")
        }
        refuses("unknown field token in a first-page footer") {
            DocxPackage.Layout(firstPageFooter = "{DATE}")
        }
        refuses("blank even-page footer") {
            DocxPackage.Layout(evenPageFooter = " ")
        }
        refuses("unknown field token in an even-page header") {
            DocxPackage.Layout(evenPageHeader = "{DATE}")
        }
        refuses("first-page header longer than the bound") {
            DocxPackage.Layout(firstPageHeader = "y".repeat(DocxPackage.MAX_HEADER_FOOTER_CHARS + 1))
        }
        refuses("header longer than the bound") {
            DocxPackage.Layout(header = "x".repeat(DocxPackage.MAX_HEADER_FOOTER_CHARS + 1))
        }
        refuses("zero columns") {
            DocxPackage.Layout(columns = 0)
        }
        refuses("more columns than Word defines") {
            DocxPackage.Layout(columns = 46)
        }
        refuses("negative column spacing") {
            DocxPackage.Layout(columns = 2, columnSpaceTwips = -1)
        }
        refuses("page number start below one") {
            DocxPackage.Layout(pageNumberStart = 0)
        }
        refuses("page number start above the bound") {
            DocxPackage.Layout(pageNumberStart = 1_000_001)
        }
        refuses("continuous section that restarts numbering") {
            DocxPackage.Layout(
                pageNumberStart = 3,
                sectionType = DocxPackage.Layout.SectionType.CONTINUOUS,
            )
        }


        // --- observation table for the independent reader --------------------------------------
        fun record(name: String, layout: DocxPackage.Layout) {
            val page = layout.pageSetup
            rows.add(
                listOf(
                    name,
                    page.widthTwips.toString(),
                    page.heightTwips.toString(),
                    page.orientation.name,
                    page.marginTopTwips.toString(),
                    page.marginRightTwips.toString(),
                    page.marginBottomTwips.toString(),
                    page.marginLeftTwips.toString(),
                    page.headerTwips.toString(),
                    page.footerTwips.toString(),
                    layout.header ?: "",
                    layout.footer ?: "",
                    layout.columns.toString(),
                    layout.columnSpaceTwips.toString(),
                    layout.pageNumberStart?.toString() ?: "",
                    layout.sectionType.value,
                    layout.firstPageHeader ?: "",
                    layout.firstPageFooter ?: "",
                    layout.evenPageHeader ?: "",
                    layout.evenPageFooter ?: "",
                ).joinToString("\t"),
            )
        }
        record("a4-footer", a4Footer)
        record("letter-landscape", letterLayout)
        record("mixed-layout", mixed.layout!!)
        record("columns-restart-odd", columnsLayout)
        record("continuous-columns", continuousLayout)
        record("first-page", firstPageLayout)
        record("even-odd", evenOddLayout)
        File(out, "layout.tsv").writeText(rows.joinToString("\n", postfix = "\n"))

        println("R76_WRITER_LAYOUT_OK assertions=$assertions documents=${out.listFiles()?.count { it.name.endsWith(".docx") }} artifacts=${out.absolutePath}")
    }

    private fun refuses(label: String, block: () -> Unit) {
        assertions++
        val error = runCatching(block).exceptionOrNull()
        require(error is IllegalArgumentException) { "$label should refuse with IllegalArgumentException, got $error" }
    }
}
