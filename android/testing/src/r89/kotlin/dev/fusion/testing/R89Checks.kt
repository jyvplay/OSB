// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R89 office contracts: Writer run styling (`w:rPr` colour and size — `<w:color w:val>` and `<w:sz
 * w:val>` in CT_RPr order) and SpreadsheetML cell font size (`CT_Font <sz val>`).
 *
 * Host JVM only. The probe writes real `.docx`/`.xlsx` packages plus `runs.tsv` / `fonts.tsv`.
 * Independent witnesses: `tools/check_r89_writer_run_style.py` (raw OPC + the python-docx `Font` API),
 * `tools/check_r89_sheet_fonts.py` (raw OPC + openpyxl `cell.font`), the docx-preview render lane (CSS
 * `color`/`font-size`) and the exceljs reader lane (`font.size`/`font.color`). No Word, Excel, LibreOffice
 * or Android execution is involved.
 */
object R89Checks {
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

    private fun body(bytes: ByteArray): String =
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

    private fun String.replaceOnce(old: String, new: String): String {
        val at = indexOf(old)
        return if (at < 0) this else substring(0, at) + new + substring(at + old.length)
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun paragraph(vararg runs: DocxPackage.Run) =
        DocxPackage.Block.Paragraph(runs = runs.toList())

    private fun runIntent(run: DocxPackage.Run): List<String> =
        listOf(
            run.colorRgb?.uppercase() ?: "-",
            run.sizeHalfPoints?.toString() ?: "-",
            run.bold.toString(), run.italic.toString(), run.underline.toString(), run.strike.toString(),
        )

    private fun canonical(blocks: List<DocxPackage.Block>): String =
        blocks.toString().replace(Regex("[0-9A-Fa-f]{6}")) { it.value.uppercase() }

    private fun writeDocx(out: File, name: String, blocks: List<DocxPackage.Block>): ByteArray {
        val bytes = DocxPackage.write(DocxPackage.Document(title = name, blocks = blocks))
        File(out, "$name.docx").writeBytes(bytes)
        val readBack = DocxPackage.read(bytes)
        check(canonical(readBack.blocks) == canonical(blocks)) {
            "$name block round trip: ${readBack.blocks}"
        }
        return bytes
    }

    private fun recordRuns(rows: MutableList<String>, name: String, blocks: List<DocxPackage.Block>) {
        var paragraphIndex = 0
        for (block in blocks) {
            if (block is DocxPackage.Block.Table) continue
            val runs = when (block) {
                is DocxPackage.Block.Heading -> block.runs
                is DocxPackage.Block.Paragraph -> block.runs
                else -> emptyList()
            }
            runs.forEachIndexed { index, run ->
                rows.add(
                    (listOf(name, paragraphIndex.toString(), index.toString(), escape(run.text)) +
                        runIntent(run)).joinToString("\t"),
                )
            }
            paragraphIndex++
        }
    }

    private fun writeXlsx(out: File, name: String, spec: XlsxStyledBook.WorkbookSpec): ByteArray {
        val bytes = XlsxStyledBook.write(spec)
        File(out, "$name.xlsx").writeBytes(bytes)
        val readBack = XlsxStyledBook.read(bytes)
        check(readBack.sheets.map { it.name } == spec.sheets.map { it.name }) { "$name sheet names round trip" }
        spec.sheets.forEachIndexed { sheetIndex, sheet ->
            val back = readBack.sheets[sheetIndex]
            check(back.cells.map { it.ref } == sheet.cells.map { it.ref }) { "$name sheet ${sheet.name} refs" }
            sheet.cells.forEachIndexed { cellIndex, cell ->
                check(back.cells[cellIndex].style.fontSize == cell.style.fontSize) {
                    "$name ${sheet.name}!${cell.ref} fontSize ${back.cells[cellIndex].style.fontSize} != ${cell.style.fontSize}"
                }
                check(back.cells[cellIndex].style.bold == cell.style.bold) { "$name ${sheet.name}!${cell.ref} bold" }
                check(back.cells[cellIndex].style.fontRgb == cell.style.fontRgb?.uppercase()) {
                    "$name ${sheet.name}!${cell.ref} font colour"
                }
            }
        }
        return bytes
    }

    private fun cell(ref: String, value: String, style: XlsxStyledBook.CellStyle = XlsxStyledBook.CellStyle()) =
        XlsxStyledBook.StyledCell(ref, XlsxStyledBook.StyledVal.Txt(value), style)

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val rows = ArrayList<String>()

        // --- the unchanged path -------------------------------------------------------------------
        val plainBlocks = listOf(paragraph(DocxPackage.Run("Body")))
        val plain = writeDocx(out, "plain", plainBlocks)
        check("<w:rPr>" !in body(plain)) { "an unstyled run must not emit w:rPr" }
        check("<w:r><w:t xml:space=\"preserve\">Body</w:t></w:r>" in body(plain)) {
            "the pre-R89 run bytes are unchanged: ${body(plain)}"
        }
        recordRuns(rows, "plain", plainBlocks)

        // --- colour, including the auto case -------------------------------------------------------
        val colourBlocks = listOf(
            paragraph(
                DocxPackage.Run("Red", colorRgb = "FF0000"),
                DocxPackage.Run("Plain"),
                DocxPackage.Run("BoldRed", bold = true, colorRgb = "00B050"),
            ),
        )
        val colour = writeDocx(out, "color", colourBlocks)
        val colourBody = body(colour)
        check("""<w:rPr><w:color w:val="FF0000"/></w:rPr><w:t xml:space="preserve">Red</w:t>""" in colourBody) {
            "a coloured run writes only w:color: $colourBody"
        }
        check((colourBody.split("<w:color").size - 1) == 2) { "exactly two coloured runs: $colourBody" }
        check("""<w:rPr><w:b/><w:color w:val="00B050"/></w:rPr>""" in colourBody) {
            "w:b precedes w:color inside CT_RPr: $colourBody"
        }
        recordRuns(rows, "color", colourBlocks)

        // --- size in half-points -------------------------------------------------------------------
        val sizeBlocks = listOf(
            paragraph(
                DocxPackage.Run("Big", sizeHalfPoints = 48),
                DocxPackage.Run("Small", sizeHalfPoints = 16),
            ),
        )
        val sized = writeDocx(out, "size", sizeBlocks)
        val sizeBody = body(sized)
        check("""<w:rPr><w:sz w:val="48"/></w:rPr>""" in sizeBody) { "24 pt is 48 half-points: $sizeBody" }
        check("""<w:rPr><w:sz w:val="16"/></w:rPr>""" in sizeBody) { "8 pt is 16 half-points: $sizeBody" }
        recordRuns(rows, "size", sizeBlocks)

        // --- full CT_RPr ordering with every modelled attribute ------------------------------------
        val bothBlocks = listOf(
            paragraph(
                DocxPackage.Run(
                    "Styled", bold = true, italic = true, underline = true, strike = true, code = true,
                    colorRgb = "1F4E79", sizeHalfPoints = 28,
                ),
            ),
        )
        val both = writeDocx(out, "both", bothBlocks)
        val bothBody = body(both)
        val expected = """<w:rPr><w:b/><w:i/><w:color w:val="1F4E79"/><w:sz w:val="28"/>""" +
            """<w:u w:val="single"/><w:strike/><w:rFonts w:ascii="monospace" w:hAnsi="monospace"/></w:rPr>"""
        check(expected in bothBody) { "CT_RPr order for the full set: $bothBody" }
        recordRuns(rows, "both", bothBlocks)

        // --- window boundaries and colour normalisation --------------------------------------------
        val boundaryBlocks = listOf(
            paragraph(
                DocxPackage.Run("Tiny", sizeHalfPoints = 2, colorRgb = "abcdef"),
                DocxPackage.Run("Huge", sizeHalfPoints = 1638),
            ),
        )
        val boundary = writeDocx(out, "boundary", boundaryBlocks)
        val boundaryBody = body(boundary)
        check("""<w:color w:val="ABCDEF"/>""" in boundaryBody) { "a lower-case colour is upper-cased: $boundaryBody" }
        check("""<w:sz w:val="2"/>""" in boundaryBody) { "1 pt is the smallest declared size" }
        check("""<w:sz w:val="1638"/>""" in boundaryBody) { "819 pt is the largest declared size" }
        recordRuns(rows, "boundary", boundaryBlocks)

        // --- interaction with the paragraph-level fields R84/R87/R88 added -------------------------
        val mixedBlocks = listOf(
            DocxPackage.Block.Paragraph(
                runs = listOf(DocxPackage.Run("Tabbed\tRed", colorRgb = "C00000", sizeHalfPoints = 22)),
                shadingRgb = "FFF2CC",
                borders = DocxPackage.ParagraphBorders(top = DocxPackage.Border(sizeEighths = 8)),
                tabStops = listOf(DocxPackage.TabStop(4320, DocxPackage.TabAlign.LEFT)),
            ),
        )
        val mixed = writeDocx(out, "mixed", mixedBlocks)
        val mixedBody = body(mixed)
        check("<w:pBdr>" in mixedBody && "<w:shd" in mixedBody && "<w:tabs>" in mixedBody) {
            "the R84/R87/R88 paragraph fields are still written: $mixedBody"
        }
        check("<w:tab/>" in mixedBody) { "the tab character survives a styled run: $mixedBody" }
        check("""<w:color w:val="C00000"/><w:sz w:val="22"/>""" in mixedBody) {
            "run styling precedes the run content: $mixedBody"
        }
        recordRuns(rows, "mixed", mixedBlocks)

        // --- refusals ------------------------------------------------------------------------------
        fails("colour ABCDE") { DocxPackage.Run("x", colorRgb = "ABCDE") }
        fails("colour #ABCDEF") { DocxPackage.Run("x", colorRgb = "#ABCDEF") }
        fails("size 1") { DocxPackage.Run("x", sizeHalfPoints = 1) }
        fails("size 1639") { DocxPackage.Run("x", sizeHalfPoints = 1639) }
        fails("size -1") { DocxPackage.Run("x", sizeHalfPoints = -1) }

        // --- import leniency on real bytes ----------------------------------------------------------
        val autoColour = rezip(colour, "word/document.xml") {
            it.replaceOnce("""<w:color w:val="FF0000"/>""", """<w:color w:val="auto"/>""")
        }
        val autoRun = DocxPackage.read(autoColour).blocks.filterIsInstance<DocxPackage.Block.Paragraph>()
            .single().runs.first()
        check(autoRun.colorRgb == null) { "w:color=auto reads as null, was ${autoRun.colorRgb}" }
        val themed = rezip(colour, "word/document.xml") {
            it.replaceOnce("""<w:color w:val="FF0000"/>""", """<w:color w:themeColor="accent1"/>""")
        }
        val themedRun = DocxPackage.read(themed).blocks.filterIsInstance<DocxPackage.Block.Paragraph>()
            .single().runs.first()
        check(themedRun.colorRgb == null) { "a theme colour is dropped, not guessed" }
        val tinySize = rezip(sized, "word/document.xml") {
            it.replaceOnce("""<w:sz w:val="48"/>""", """<w:sz w:val="1"/>""")
        }
        val tinyRun = DocxPackage.read(tinySize).blocks.filterIsInstance<DocxPackage.Block.Paragraph>()
            .single().runs.first()
        check(tinyRun.sizeHalfPoints == null) { "a 0.5 pt size is below the modelled window and is dropped" }
        val garbageSize = rezip(sized, "word/document.xml") {
            it.replaceOnce("""<w:sz w:val="48"/>""", """<w:sz w:val="huge"/>""")
        }
        check(
            DocxPackage.read(garbageSize).blocks.filterIsInstance<DocxPackage.Block.Paragraph>()
                .single().runs.first().sizeHalfPoints == null,
        ) { "a non-numeric size is dropped" }
        File(out, "runs.tsv").writeText(rows.joinToString("\n") + "\n")

        // --- spreadsheet: cell font size ------------------------------------------------------------
        val fontRows = ArrayList<String>()

        fun recordFonts(name: String, spec: XlsxStyledBook.WorkbookSpec) {
            for (sheet in spec.sheets) {
                for (c in sheet.cells) {
                    fontRows.add(
                        listOf(
                            name, sheet.name, c.ref, c.style.bold.toString(), c.style.italic.toString(),
                            c.style.fontRgb?.uppercase() ?: "-",
                            c.style.fontSize?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "-",
                            c.style.fillRgb?.uppercase() ?: "-",
                        ).joinToString("\t"),
                    )
                }
            }
        }

        val defaultSpec = XlsxStyledBook.WorkbookSpec(
            sheets = listOf(XlsxStyledBook.StyledSheet(name = "One", cells = listOf(cell("A1", "a")))),
        )
        val defaultBytes = writeXlsx(out, "font-default", defaultSpec)
        val defaultStyles = part(defaultBytes, "xl/styles.xml")
        check("""<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>""" in defaultStyles) {
            "the undeclared default is still Calibri 11 pt: $defaultStyles"
        }
        recordFonts("font-default", defaultSpec)

        val sizeSpec = XlsxStyledBook.WorkbookSpec(
            sheets = listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Sizes",
                    cells = listOf(
                        cell("A1", "eight", XlsxStyledBook.CellStyle(fontSize = 8.0)),
                        cell("A2", "twenty-four", XlsxStyledBook.CellStyle(fontSize = 24.0)),
                        cell("A3", "ten and a half", XlsxStyledBook.CellStyle(fontSize = 10.5)),
                        cell("A4", "default"),
                        cell("A5", "eight again", XlsxStyledBook.CellStyle(fontSize = 8.0)),
                    ),
                ),
            ),
        )
        val sizeBytes = writeXlsx(out, "font-size", sizeSpec)
        val sizeStyles = part(sizeBytes, "xl/styles.xml")
        check("""<sz val="8"/>""" in sizeStyles) { "8 pt is written as an integer: $sizeStyles" }
        check("""<sz val="24"/>""" in sizeStyles) { "24 pt is written as an integer: $sizeStyles" }
        check("""<sz val="10.5"/>""" in sizeStyles) { "10.5 pt keeps its fraction: $sizeStyles" }
        // Five cells, one repeated 8 pt style and one default: four fonts, not five (deduplicated).
        check("""<fonts count="4">""" in sizeStyles) { "the 8 pt style is deduplicated: $sizeStyles" }
        recordFonts("font-size", sizeSpec)

        val mixedSpec = XlsxStyledBook.WorkbookSpec(
            sheets = listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Mixed",
                    cells = listOf(
                        cell(
                            "A1", "banner",
                            XlsxStyledBook.CellStyle(
                                bold = true, fontRgb = "C00000", fontSize = 14.0, fillRgb = "FFF2CC",
                            ),
                        ),
                        cell("A2", "plain"),
                    ),
                ),
            ),
        )
        val mixedBytes = writeXlsx(out, "font-mixed", mixedSpec)
        val mixedStyles = part(mixedBytes, "xl/styles.xml")
        check("""<font><b/><sz val="14"/><name val="Calibri"/><color rgb="FFC00000"/></font>""" in mixedStyles) {
            "bold, size and colour share one CT_Font in schema-relative order: $mixedStyles"
        }
        recordFonts("font-mixed", mixedSpec)

        fails("fontSize 0.5") {
            XlsxStyledBook.StyledCell("A1", XlsxStyledBook.StyledVal.Txt("x"), XlsxStyledBook.CellStyle(fontSize = 0.5))
        }
        fails("fontSize 410") {
            XlsxStyledBook.StyledCell("A1", XlsxStyledBook.StyledVal.Txt("x"), XlsxStyledBook.CellStyle(fontSize = 410.0))
        }

        // A foreign font that declares the default size reads back as "undeclared", not as a 11 pt claim.
        val eleven = rezip(defaultBytes, "xl/styles.xml") {
            it.replaceOnce("""<sz val="11"/>""", """<sz val="11.0"/>""")
        }
        check(XlsxStyledBook.read(eleven).sheets[0].cells[0].style.fontSize == null) {
            "the default 11 pt font reads back as null"
        }
        File(out, "fonts.tsv").writeText(fontRows.joinToString("\n") + "\n")
        println(
            "R89_OFFICE_OK assertions=$assertions documents=${out.listFiles { f -> f.name.endsWith(".docx") }?.size} " +
                "workbooks=${out.listFiles { f -> f.name.endsWith(".xlsx") }?.size}",
        )
    }
}
