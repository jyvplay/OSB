// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R90 office contracts: Writer run decorations (`w:vertAlign`, `w:highlight` — ST_VerticalAlignRun and the
 * closed ST_HighlightColor set) and SpreadsheetML font decorations (`<u/>`, `<strike/>` in CT_Font).
 *
 * Host JVM only. The probe writes real `.docx`/`.xlsx` packages plus `decorations.tsv` / `decorations_xlsx.tsv`.
 * Independent witnesses: `tools/check_r90_writer_decorations.py` (raw OPC + the python-docx `Font` API),
 * `tools/check_r90_sheet_decorations.py` (raw OPC + openpyxl `Font`), the docx-preview render lane (CSS
 * `background-color` for `w:highlight`, and the renderer's own `verticalAlign` reading for `w:vertAlign`)
 * and the exceljs reader lane (`font.underline`/`font.strike`). No Word, Excel, browser or Android run.
 */
object R90Checks {
    private var assertions = 0

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
                    listOf(
                        name, paragraphIndex.toString(), index.toString(), escape(run.text),
                        run.verticalAlign?.token ?: "-", run.highlight?.token ?: "-",
                        run.colorRgb?.uppercase() ?: "-", run.sizeHalfPoints?.toString() ?: "-",
                        run.bold.toString(), run.italic.toString(), run.underline.toString(),
                        run.strike.toString(),
                    ).joinToString("\t"),
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
                val actual = back.cells[cellIndex].style
                check(actual.underline == cell.style.underline) {
                    "$name ${sheet.name}!${cell.ref} underline ${actual.underline} != ${cell.style.underline}"
                }
                check(actual.strike == cell.style.strike) {
                    "$name ${sheet.name}!${cell.ref} strike ${actual.strike} != ${cell.style.strike}"
                }
                check(actual.bold == cell.style.bold) { "$name ${sheet.name}!${cell.ref} bold" }
                check(actual.italic == cell.style.italic) { "$name ${sheet.name}!${cell.ref} italic" }
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
        recordRuns(rows, "plain", plainBlocks)

        // --- superscript / subscript ---------------------------------------------------------------
        val alignBlocks = listOf(
            paragraph(
                DocxPackage.Run("Super", verticalAlign = DocxPackage.VerticalAlign.SUPERSCRIPT),
                DocxPackage.Run("Base"),
                DocxPackage.Run("Sub", verticalAlign = DocxPackage.VerticalAlign.SUBSCRIPT),
            ),
        )
        val align = writeDocx(out, "align", alignBlocks)
        val alignBody = body(align)
        check("""<w:rPr><w:vertAlign w:val="superscript"/></w:rPr>""" in alignBody) {
            "superscript is written as its ST_VerticalAlignRun token: $alignBody"
        }
        check("""<w:vertAlign w:val="subscript"/>""" in alignBody) { "subscript token: $alignBody" }
        check((alignBody.split("<w:vertAlign").size - 1) == 2) { "exactly two aligned runs: $alignBody" }
        recordRuns(rows, "align", alignBlocks)

        // --- highlight (all sixteen tokens are constructible, three are exercised here) ------------
        val highlightBlocks = listOf(
            paragraph(
                DocxPackage.Run("Y", highlight = DocxPackage.HighlightColor.YELLOW),
                DocxPackage.Run("B", highlight = DocxPackage.HighlightColor.DARK_BLUE),
                DocxPackage.Run("W", highlight = DocxPackage.HighlightColor.WHITE),
                DocxPackage.Run("None"),
            ),
        )
        val highlighted = writeDocx(out, "highlight", highlightBlocks)
        val highlightBody = body(highlighted)
        check("""<w:rPr><w:highlight w:val="yellow"/></w:rPr>""" in highlightBody) { "yellow token: $highlightBody" }
        check("""<w:highlight w:val="darkBlue"/>""" in highlightBody) { "darkBlue token keeps its camel case: $highlightBody" }
        check("""<w:highlight w:val="white"/>""" in highlightBody) { "white token: $highlightBody" }
        check((highlightBody.split("<w:highlight").size - 1) == 3) { "exactly three highlighted runs: $highlightBody" }
        check(DocxPackage.HighlightColor.values().size == 16) { "the closed set has sixteen tokens" }
        recordRuns(rows, "highlight", highlightBlocks)

        // --- the full CT_RPr sequence ---------------------------------------------------------------
        val combinedBlocks = listOf(
            paragraph(
                DocxPackage.Run(
                    "All", bold = true, italic = true, underline = true, strike = true, code = true,
                    colorRgb = "1F4E79", sizeHalfPoints = 28,
                    verticalAlign = DocxPackage.VerticalAlign.SUPERSCRIPT,
                    highlight = DocxPackage.HighlightColor.CYAN,
                ),
            ),
        )
        val combined = writeDocx(out, "combined", combinedBlocks)
        val combinedBody = body(combined)
        val expected = """<w:rPr><w:b/><w:i/><w:color w:val="1F4E79"/><w:sz w:val="28"/>""" +
            """<w:highlight w:val="cyan"/><w:u w:val="single"/><w:strike/>""" +
            """<w:vertAlign w:val="superscript"/>""" +
            """<w:rFonts w:ascii="monospace" w:hAnsi="monospace"/></w:rPr>"""
        check(expected in combinedBody) { "CT_RPr order for the full set: $combinedBody" }
        recordRuns(rows, "combined", combinedBlocks)

        // --- interaction with the paragraph fields R84–R89 added ------------------------------------
        val mixedBlocks = listOf(
            DocxPackage.Block.Paragraph(
                runs = listOf(
                    DocxPackage.Run("Note", verticalAlign = DocxPackage.VerticalAlign.SUPERSCRIPT,
                        highlight = DocxPackage.HighlightColor.GREEN, colorRgb = "C00000"),
                ),
                shadingRgb = "FFF2CC",
                borders = DocxPackage.ParagraphBorders(top = DocxPackage.Border(sizeEighths = 8)),
            ),
        )
        val mixed = writeDocx(out, "mixed", mixedBlocks)
        val mixedBody = body(mixed)
        check("<w:pBdr>" in mixedBody && "<w:shd" in mixedBody) { "paragraph fields survive: $mixedBody" }
        check("""<w:highlight w:val="green"/>""" in mixedBody) { "run decoration inside a bordered paragraph" }
        recordRuns(rows, "mixed", mixedBlocks)

        // --- import leniency ------------------------------------------------------------------------
        val unknownHighlight = rezip(highlighted, "word/document.xml") {
            it.replaceOnce("""<w:highlight w:val="yellow"/>""", """<w:highlight w:val="orange"/>""")
        }
        val unknownRun = DocxPackage.read(unknownHighlight).blocks.filterIsInstance<DocxPackage.Block.Paragraph>()
            .single().runs.first()
        check(unknownRun.highlight == null) { "an unknown highlight token is dropped, was ${unknownRun.highlight}" }
        val baselineAlign = rezip(align, "word/document.xml") {
            it.replaceOnce("""<w:vertAlign w:val="superscript"/>""", """<w:vertAlign w:val="baseline"/>""")
        }
        val baselineRun = DocxPackage.read(baselineAlign).blocks.filterIsInstance<DocxPackage.Block.Paragraph>()
            .single().runs.first()
        check(baselineRun.verticalAlign == null) { "w:vertAlign=baseline is the absence of alignment" }
        File(out, "decorations.tsv").writeText(rows.joinToString("\n") + "\n")

        // --- spreadsheet font decorations -----------------------------------------------------------
        val xlsxRows = ArrayList<String>()

        fun recordCells(name: String, spec: XlsxStyledBook.WorkbookSpec) {
            for (sheet in spec.sheets) {
                for (c in sheet.cells) {
                    xlsxRows.add(
                        listOf(
                            name, sheet.name, c.ref,
                            c.style.bold.toString(), c.style.italic.toString(),
                            c.style.underline.toString(), c.style.strike.toString(),
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
        val defaultBytes = writeXlsx(out, "deco-default", defaultSpec)
        val defaultStyles = part(defaultBytes, "xl/styles.xml")
        check("""<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>""" in defaultStyles) {
            "an undeclared font still writes the pre-R89 record: $defaultStyles"
        }
        recordCells("deco-default", defaultSpec)

        val decoSpec = XlsxStyledBook.WorkbookSpec(
            sheets = listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Deco",
                    cells = listOf(
                        cell("A1", "under", XlsxStyledBook.CellStyle(underline = true)),
                        cell("A2", "struck", XlsxStyledBook.CellStyle(strike = true)),
                        cell("A3", "both", XlsxStyledBook.CellStyle(underline = true, strike = true)),
                        cell("A4", "neither"),
                        cell("A5", "both again", XlsxStyledBook.CellStyle(underline = true, strike = true)),
                    ),
                ),
            ),
        )
        val decoBytes = writeXlsx(out, "deco", decoSpec)
        val decoStyles = part(decoBytes, "xl/styles.xml")
        check("""<fonts count="4">""" in decoStyles) {
            "five cells with one repeated shape intern four fonts: $decoStyles"
        }
        check("""<font><u/><sz val="11"/><name val="Calibri"/></font>""" in decoStyles) {
            "an underline-only font: $decoStyles"
        }
        check("""<font><strike/><sz val="11"/><name val="Calibri"/></font>""" in decoStyles) {
            "a strike-only font: $decoStyles"
        }
        recordCells("deco", decoSpec)

        val mixedSpec = XlsxStyledBook.WorkbookSpec(
            sheets = listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Mixed",
                    cells = listOf(
                        cell(
                            "A1", "everything",
                            XlsxStyledBook.CellStyle(
                                bold = true, italic = true, underline = true, strike = true,
                                fontRgb = "C00000", fontSize = 14.0,
                            ),
                        ),
                        cell("A2", "plain"),
                    ),
                ),
            ),
        )
        val mixedBytes = writeXlsx(out, "deco-mixed", mixedSpec)
        val mixedStyles = part(mixedBytes, "xl/styles.xml")
        check("""<font><b/><i/><strike/><u/><sz val="14"/><name val="Calibri"/><color rgb="FFC00000"/></font>""" in mixedStyles) {
            "CT_Font order b, i, strike, u, sz, colour: $mixedStyles"
        }
        recordCells("deco-mixed", mixedSpec)

        // A foreign `<u val="double"/>` is still an underline; the underline *style* is not modelled.
        val doubleUnderline = rezip(decoBytes, "xl/styles.xml") {
            it.replaceOnce("<font><u/>", """<font><u val="double"/>""")
        }
        check(XlsxStyledBook.read(doubleUnderline).sheets[0].cells[0].style.underline) {
            "any <u> element reads as underlined, whatever its val"
        }
        File(out, "decorations_xlsx.tsv").writeText(xlsxRows.joinToString("\n") + "\n")
        println(
            "R90_OFFICE_OK assertions=$assertions documents=${out.listFiles { f -> f.name.endsWith(".docx") }?.size} " +
                "workbooks=${out.listFiles { f -> f.name.endsWith(".xlsx") }?.size}",
        )
    }
}
