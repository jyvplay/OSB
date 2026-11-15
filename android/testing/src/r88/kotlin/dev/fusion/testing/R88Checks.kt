// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R88 office contracts: Writer paragraph borders (`w:pBdr`, six edges in CT_PBdr order, `w:val`/`w:sz`/
 * `w:space`/`w:color` on every edge) and sheet-view gridline suppression (`CT_SheetView showGridLines`),
 * both readable back through the same model that wrote them.
 *
 * Host JVM only. The probe writes real `.docx` and `.xlsx` packages plus `borders.tsv` / `grid.tsv`.
 * Independent witnesses: tools/check_r88_writer_borders.py (raw OPC + python-docx),
 * tools/check_r88_sheet_gridlines.py (raw OPC + openpyxl), the docx-preview render lane (a real HTML
 * renderer turns `w:pBdr` into CSS border properties) and the exceljs reader lane (which models
 * `showGridLines`). No Word, Excel, LibreOffice or Android execution is involved.
 */
object R88Checks {
    private var assertions = 0

    private inline fun check(ok: Boolean, message: () -> String) {
        assertions++
        require(ok) { "FAILED: " + message() }
    }

    private fun fails(message: String, block: () -> Unit) {
        assertions++
        val failure = runCatching(block).exceptionOrNull()
        require(failure != null) { "expected a refusal for $message" }
        require(failure is IllegalArgumentException) { "refusal for $message must be IllegalArgumentException, was $failure" }
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

    private fun documentBody(bytes: ByteArray): String =
        part(bytes, "word/document.xml").substringAfter("<w:body>").substringBefore("<w:sectPr")

    private fun rezip(bytes: ByteArray, entry: String, transform: (String) -> String): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { input ->
            java.util.zip.ZipOutputStream(buffer).use { output ->
                while (true) {
                    val current = input.nextEntry ?: break
                    val payload = input.readBytes()
                    val patched =
                        if (current.name == entry) transform(text(payload)).toByteArray(Charsets.ISO_8859_1)
                        else payload
                    output.putNextEntry(java.util.zip.ZipEntry(current.name))
                    output.write(patched)
                    output.closeEntry()
                }
            }
        }
        return buffer.toByteArray()
    }

    /** Kotlin's `String.replace` has no occurrence limit, and these probes patch one element only. */
    private fun String.replaceOnce(old: String, new: String): String {
        val at = indexOf(old)
        return if (at < 0) this else substring(0, at) + new + substring(at + old.length)
    }

    private fun edge(
        style: DocxPackage.BorderStyle = DocxPackage.BorderStyle.SINGLE,
        size: Int = 8,
        color: String? = null,
        space: Int = 1,
    ) = DocxPackage.Border(style, size, color, space)

    private fun paragraph(
        value: String,
        borders: DocxPackage.ParagraphBorders? = null,
        shading: String? = null,
        tabs: List<DocxPackage.TabStop> = emptyList(),
        left: Int? = null,
        pageBreakBefore: Boolean? = null,
        spacingBefore: Int? = null,
    ) = DocxPackage.Block.Paragraph(
        runs = listOf(DocxPackage.Run(value)),
        spaceBeforeTwips = spacingBefore,
        indentLeftTwips = left,
        pageBreakBefore = pageBreakBefore,
        tabStops = tabs,
        shadingRgb = shading,
        borders = borders,
    )

    private fun heading(level: Int, value: String, borders: DocxPackage.ParagraphBorders? = null) =
        DocxPackage.Block.Heading(level = level, runs = listOf(DocxPackage.Run(value)), borders = borders)

    private fun borderIntent(box: DocxPackage.ParagraphBorders?): String {
        if (box == null) return "-"
        val parts = ArrayList<String>()
        for ((name, border) in listOf(
            "top" to box.top, "left" to box.left, "bottom" to box.bottom,
            "right" to box.right, "between" to box.between, "bar" to box.bar,
        )) {
            if (border == null) continue
            parts.add("$name:${border.style.token}:${border.sizeEighths}:${border.spacePoints}:${border.colorRgb?.uppercase() ?: "auto"}")
        }
        return parts.joinToString(";")
    }

    private fun tabIntent(stops: List<DocxPackage.TabStop>): String =
        if (stops.isEmpty()) "-" else stops.joinToString(";") { s ->
            "${s.positionTwips}:${s.align.token}:${s.leader?.token ?: "none"}"
        }

    private fun canonical(blocks: List<DocxPackage.Block>): String =
        blocks.toString().replace(Regex("[0-9A-Fa-f]{6}")) { it.value.uppercase() }

    private fun writeDocx(out: File, name: String, blocks: List<DocxPackage.Block>): ByteArray {
        val bytes = DocxPackage.write(DocxPackage.Document(title = name, blocks = blocks))
        File(out, "$name.docx").writeBytes(bytes)
        val readBack = DocxPackage.read(bytes)
        // A border colour keeps the caller's case in the model (the wire form is upper-case), so the
        // round trip is compared on the canonical upper-case spelling of every 6-hex run.
        check(canonical(readBack.blocks) == canonical(blocks)) {
            "$name block round trip: ${readBack.blocks}"
        }
        return bytes
    }

    private fun recordBorders(rows: MutableList<String>, name: String, blocks: List<DocxPackage.Block>) {
        var index = 0
        for (block in blocks) {
            if (block is DocxPackage.Block.Table) continue
            val kind = when (block) {
                is DocxPackage.Block.Heading -> "heading"
                is DocxPackage.Block.Paragraph -> "paragraph"
                else -> "other"
            }
            val borders = when (block) {
                is DocxPackage.Block.Heading -> block.borders
                is DocxPackage.Block.Paragraph -> block.borders
                else -> null
            }
            val shading = when (block) {
                is DocxPackage.Block.Heading -> block.shadingRgb
                is DocxPackage.Block.Paragraph -> block.shadingRgb
                else -> null
            }
            val tabs = when (block) {
                is DocxPackage.Block.Heading -> block.tabStops
                is DocxPackage.Block.Paragraph -> block.tabStops
                else -> emptyList()
            }
            rows.add(
                listOf(name, index, kind, borderIntent(borders), shading?.uppercase() ?: "-", tabIntent(tabs))
                    .joinToString("\t"),
            )
            index++
        }
    }

    private fun writeXlsx(out: File, name: String, spec: XlsxStyledBook.WorkbookSpec): ByteArray {
        val bytes = XlsxStyledBook.write(spec)
        File(out, "$name.xlsx").writeBytes(bytes)
        val readBack = XlsxStyledBook.read(bytes)
        check(readBack.sheets.map { it.name } == spec.sheets.map { it.name }) { "$name sheet names round trip" }
        check(readBack.sheets.map { it.showGridLines } == spec.sheets.map { it.showGridLines }) {
            "$name showGridLines round trip: ${readBack.sheets.map { it.showGridLines }}"
        }
        check(readBack.sheets.map { it.zoomScale } == spec.sheets.map { it.zoomScale }) {
            "$name zoomScale round trip: ${readBack.sheets.map { it.zoomScale }}"
        }
        check(readBack.sheets.map { it.visibility } == spec.sheets.map { it.visibility }) {
            "$name visibility round trip"
        }
        return bytes
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val rows = ArrayList<String>()

        // --- the unchanged path -------------------------------------------------------------------
        val plainBlocks = listOf(heading(1, "Plain"), paragraph("Body"))
        val plain = writeDocx(out, "plain", plainBlocks)
        check("<w:pBdr" !in documentBody(plain)) { "a block without borders must not emit w:pBdr" }
        recordBorders(rows, "plain", plainBlocks)

        // --- a four-edge box, and a heading with only a bottom rule -------------------------------
        val box = DocxPackage.ParagraphBorders(
            top = edge(color = "1F4E79"),
            left = edge(color = "1F4E79"),
            bottom = edge(color = "1F4E79"),
            right = edge(color = "1F4E79"),
        )
        val rule = DocxPackage.ParagraphBorders(bottom = edge(size = 6, color = "808080", space = 4))
        val boxBlocks = listOf(heading(2, "Boxed", borders = rule), paragraph("Callout", borders = box))
        val boxBytes = writeDocx(out, "box", boxBlocks)
        val boxBody = documentBody(boxBytes)
        for (name in listOf("top", "left", "bottom", "right")) {
            check("""<w:$name w:val="single" w:sz="8" w:space="1" w:color="1F4E79"/>""" in boxBody) {
                "the $name edge is written with every attribute: $boxBody"
            }
        }
        check("""<w:bottom w:val="single" w:sz="6" w:space="4" w:color="808080"/>""" in boxBody) {
            "the heading carries only a bottom rule: $boxBody"
        }
        check(boxBody.split("<w:pBdr>").size - 1 == 2) { "exactly two bordered paragraphs: $boxBody" }
        recordBorders(rows, "box", boxBlocks)

        // --- every style token, one per edge ------------------------------------------------------
        val stylesBlocks = listOf(
            paragraph(
                "Styles",
                borders = DocxPackage.ParagraphBorders(
                    top = edge(DocxPackage.BorderStyle.DOUBLE, 8, "000000"),
                    left = edge(DocxPackage.BorderStyle.DASHED, 8, "000000"),
                    bottom = edge(DocxPackage.BorderStyle.DOTTED, 8, "000000"),
                    right = edge(DocxPackage.BorderStyle.THICK, 24, "000000"),
                    between = edge(DocxPackage.BorderStyle.SINGLE, 4, "808080"),
                    bar = edge(DocxPackage.BorderStyle.NIL, 2, "FFFFFF", 0),
                ),
            ),
        )
        val stylesBytes = writeDocx(out, "styles", stylesBlocks)
        val stylesBody = documentBody(stylesBytes)
        for ((name, token) in listOf(
            "top" to "double", "left" to "dashed", "bottom" to "dotted",
            "right" to "thick", "between" to "single", "bar" to "nil",
        )) {
            check("<w:$name w:val=\"$token\"" in stylesBody) { "the $name edge must carry $token: $stylesBody" }
        }
        // CT_PBdr order: top, left, bottom, right, between, bar.
        val order = listOf("top", "left", "bottom", "right", "between", "bar")
            .map { stylesBody.indexOf("<w:$it w:val=") }
        check(order == order.sorted() && order.all { it >= 0 }) { "CT_PBdr order is top..bar: $stylesBody" }
        check(stylesBody.indexOf("<w:bar ") > stylesBody.indexOf("<w:between ")) { "bar follows between" }
        recordBorders(rows, "styles", stylesBlocks)

        // --- boundaries: the smallest and largest legal measures ----------------------------------
        val boundaryBlocks = listOf(
            paragraph(
                "Boundary",
                borders = DocxPackage.ParagraphBorders(
                    top = edge(size = 2, space = 0, color = null),
                    bottom = edge(size = 96, space = 31, color = "abcdef"),
                ),
            ),
        )
        val boundaryBytes = writeDocx(out, "boundary", boundaryBlocks)
        val boundaryBody = documentBody(boundaryBytes)
        check("""<w:top w:val="single" w:sz="2" w:space="0" w:color="auto"/>""" in boundaryBody) {
            "the minimum measure and an automatic colour: $boundaryBody"
        }
        check("""<w:bottom w:val="single" w:sz="96" w:space="31" w:color="ABCDEF"/>""" in boundaryBody) {
            "the maximum measure and an upper-cased colour: $boundaryBody"
        }
        recordBorders(rows, "boundary", boundaryBlocks)

        // --- pBdr sits between the widow flag and the fill, and before the tab stops ---------------
        val combinedBlocks = listOf(
            paragraph(
                "Combined",
                borders = DocxPackage.ParagraphBorders(top = edge(color = "FF0000")),
                shading = "FFFF00",
                tabs = listOf(DocxPackage.TabStop(7200, DocxPackage.TabAlign.RIGHT, DocxPackage.TabLeader.DOT)),
                left = 720,
                pageBreakBefore = false,
                spacingBefore = 120,
            ),
        )
        val combinedBytes = writeDocx(out, "combined", combinedBlocks)
        val combinedBody = documentBody(combinedBytes)
        val positions = listOf("<w:pageBreakBefore", "<w:pBdr>", "<w:shd", "<w:tabs>", "<w:spacing", "<w:ind")
            .map { combinedBody.indexOf(it) }
        check(positions.all { it >= 0 } && positions == positions.sorted()) {
            "CT_PPr order pageBreakBefore < pBdr < shd < tabs < spacing < ind: $combinedBody"
        }
        recordBorders(rows, "combined", combinedBlocks)

        // --- refusals ------------------------------------------------------------------------------
        fails("size 1") { edge(size = 1) }
        fails("size 97") { edge(size = 97) }
        fails("space 32") { edge(space = 32) }
        fails("space -1") { edge(space = -1) }
        fails("colour ABCDE") { edge(color = "ABCDE") }
        fails("colour #ABCDEF") { edge(color = "#ABCDEF") }
        fails("empty box") { DocxPackage.ParagraphBorders() }

        // --- import leniency on real bytes ---------------------------------------------------------
        // The first `w:sz="8"` in the body is the callout's top edge, so only that edge may disappear.
        val tooWide = rezip(boxBytes, "word/document.xml") { it.replaceOnce("w:sz=\"8\"", "w:sz=\"200\"") }
        val tooWideBox = DocxPackage.read(tooWide).blocks.filterIsInstance<DocxPackage.Block.Paragraph>().single()
        check(tooWideBox.borders?.top == null) { "a 200-eighth edge is dropped rather than clamped" }
        check(tooWideBox.borders?.left?.sizeEighths == 8) { "the untouched edges keep their size" }
        val themed = rezip(boxBytes, "word/document.xml") { it.replaceOnce("w:color=\"1F4E79\"", "w:color=\"theme\"") }
        val themedBox = DocxPackage.read(themed).blocks.filterIsInstance<DocxPackage.Block.Paragraph>().single()
        check(themedBox.borders?.top?.colorRgb == null) { "a themed colour is dropped, not guessed" }
        val wave = rezip(boxBytes, "word/document.xml") {
            it.replaceOnce("<w:pBdr><w:top w:val=\"single\"", "<w:pBdr><w:top w:val=\"wave\"")
        }
        val waveBox = DocxPackage.read(wave).blocks.filterIsInstance<DocxPackage.Block.Paragraph>().single()
        check(waveBox.borders?.top == null) { "an unsupported line style drops that edge" }
        check(waveBox.borders?.left != null) { "the other edges survive an unsupported one" }
        val duplicated = rezip(boxBytes, "word/document.xml") {
            it.replaceOnce("<w:pBdr><w:top", "<w:pBdr><w:top w:val=\"double\" w:sz=\"12\" w:space=\"2\" w:color=\"00FF00\"/><w:top")
        }
        val duplicatedBox = DocxPackage.read(duplicated).blocks.filterIsInstance<DocxPackage.Block.Paragraph>().single()
        check(duplicatedBox.borders?.top?.style == DocxPackage.BorderStyle.DOUBLE) {
            "the first of two w:top edges wins, was ${duplicatedBox.borders?.top}"
        }
        // A foreign `w:sz="0"` edge is legal ST_EighthPointMeasure but means "hairline/none" here: dropped.
        val hairline = rezip(boxBytes, "word/document.xml") { it.replaceOnce("w:sz=\"8\"", "w:sz=\"0\"") }
        val hairlineBox = DocxPackage.read(hairline).blocks.filterIsInstance<DocxPackage.Block.Paragraph>().single()
        check(hairlineBox.borders?.top == null) { "a zero-width edge is dropped, not clamped to 2" }

        val allGone = rezip(boxBytes, "word/document.xml") {
            it.replace("w:val=\"single\"", "w:val=\"wave\"")
        }
        val allGoneBox = DocxPackage.read(allGone).blocks.filterIsInstance<DocxPackage.Block.Paragraph>().single()
        check(allGoneBox.borders == null) { "a box with no representable edge becomes null, was ${allGoneBox.borders}" }

        File(out, "borders.tsv").writeText(rows.joinToString("\n") + "\n")

        // --- spreadsheet: gridline suppression ------------------------------------------------------
        val gridRows = ArrayList<String>()

        fun recordGrid(name: String, spec: XlsxStyledBook.WorkbookSpec) {
            spec.sheets.forEachIndexed { index, sheet ->
                gridRows.add(
                    listOf(
                        name, index, sheet.name, sheet.showGridLines?.toString() ?: "-",
                        sheet.zoomScale?.toString() ?: "-", sheet.freezeRows.toString(),
                        sheet.visibility.token,
                    ).joinToString("\t"),
                )
            }
        }

        fun txt(ref: String, value: String) =
            XlsxStyledBook.StyledCell(ref, XlsxStyledBook.StyledVal.Txt(value))

        val defaultSpec = XlsxStyledBook.WorkbookSpec(
            sheets = listOf(XlsxStyledBook.StyledSheet(name = "One", cells = listOf(txt("A1", "a")))),
        )
        val defaultBytes = writeXlsx(out, "grid-default", defaultSpec)
        check("<sheetViews" !in part(defaultBytes, "xl/worksheets/sheet1.xml")) {
            "a sheet with no view option must not emit sheetViews"
        }
        recordGrid("grid-default", defaultSpec)

        val offSpec = XlsxStyledBook.WorkbookSpec(
            sheets = listOf(
                XlsxStyledBook.StyledSheet(name = "Off", cells = listOf(txt("A1", "a")), showGridLines = false),
                XlsxStyledBook.StyledSheet(name = "On", cells = listOf(txt("A1", "b")), showGridLines = true),
                XlsxStyledBook.StyledSheet(
                    name = "Combo", cells = listOf(txt("A1", "c")), showGridLines = false,
                    zoomScale = 150, freezeRows = 1,
                ),
            ),
        )
        val offBytes = writeXlsx(out, "grid-off", offSpec)
        val offSheet1 = part(offBytes, "xl/worksheets/sheet1.xml")
        check("""<sheetViews><sheetView workbookViewId="0" showGridLines="0"/></sheetViews>""" in offSheet1) {
            "a suppressed grid is written as showGridLines=\"0\": $offSheet1"
        }
        val onSheet = part(offBytes, "xl/worksheets/sheet2.xml")
        check("showGridLines=\"1\"" in onSheet) { "an explicit true is written as \"1\": $onSheet" }
        val comboSheet = part(offBytes, "xl/worksheets/sheet3.xml")
        check("""<sheetView workbookViewId="0" zoomScale="150" showGridLines="0"><pane""" in comboSheet) {
            "zoom, gridlines and a frozen pane share the one sheetView: $comboSheet"
        }
        check(comboSheet.split("<sheetView ").size - 1 == 1) { "exactly one sheetView element: $comboSheet" }
        recordGrid("grid-off", offSpec)

        val mixedSpec = XlsxStyledBook.WorkbookSpec(
            sheets = listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Front", cells = listOf(txt("A1", "x")), showGridLines = false, tabRgb = "00B050",
                ),
                XlsxStyledBook.StyledSheet(
                    name = "Back", cells = listOf(txt("A1", "y")), visibility = XlsxStyledBook.Visibility.HIDDEN,
                ),
            ),
            showSheetTabs = false,
        )
        val mixedBytes = writeXlsx(out, "grid-mixed", mixedSpec)
        val front = part(mixedBytes, "xl/worksheets/sheet1.xml")
        check("""showGridLines="0"""" in front && "FF00B050" in front) {
            "gridlines and tab colour coexist on one sheet: $front"
        }
        recordGrid("grid-mixed", mixedSpec)

        // Excel writes `showGridLines="false"` in some paths; the reader must agree with the "0" form.
        val spelled = rezip(offBytes, "xl/worksheets/sheet1.xml") {
            it.replaceOnce("showGridLines=\"0\"", "showGridLines=\"false\"")
        }
        check(XlsxStyledBook.read(spelled).sheets[0].showGridLines == false) {
            "showGridLines=\"false\" must read back as false"
        }
        val loudly = rezip(offBytes, "xl/worksheets/sheet2.xml") {
            it.replaceOnce("showGridLines=\"1\"", "showGridLines=\"true\"")
        }
        check(XlsxStyledBook.read(loudly).sheets[1].showGridLines == true) {
            "showGridLines=\"true\" must read back as true"
        }

        // refusals: the view stays inside Excel's own window
        fails("zoom 9") {
            XlsxStyledBook.StyledSheet(name = "Z", cells = emptyList(), zoomScale = 9)
        }
        fails("zoom 401") {
            XlsxStyledBook.StyledSheet(name = "Z", cells = emptyList(), zoomScale = 401)
        }

        File(out, "grid.tsv").writeText(gridRows.joinToString("\n") + "\n")
        println(
            "R88_OFFICE_OK assertions=$assertions documents=${out.listFiles { f -> f.name.endsWith(".docx") }?.size} " +
                "workbooks=${out.listFiles { f -> f.name.endsWith(".xlsx") }?.size}",
        )
    }
}
