// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R82 spreadsheet cell-alignment contracts: `CellStyle.horizontal` / `CellStyle.vertical` emit
 * `<alignment>` inside the deduplicated `cellXfs` entry (`applyAlignment="1"`) and read back.
 *
 * Host JVM only. The probe writes real `.xlsx` workbooks plus `align.tsv`; the independent reader is
 * tools/check_r82_sheet_align.py, which opens the workbooks with openpyxl (`cell.alignment`) and
 * inspects the raw OPC parts. Nothing here is Excel recalculation or rendering evidence, and no
 * caller sets alignment yet.
 */
object R82SheetAlignChecks {
    private var assertions = 0

    private inline fun check(ok: Boolean, message: () -> String) {
        assertions++
        require(ok) { "FAILED: " + message() }
    }

    private fun text(bytes: ByteArray): String = String(bytes, Charsets.ISO_8859_1)

    private fun txt(ref: String, value: String, style: XlsxStyledBook.CellStyle = XlsxStyledBook.CellStyle()) =
        XlsxStyledBook.StyledCell(ref, XlsxStyledBook.StyledVal.Txt(value), style)

    private fun style(
        horizontal: XlsxStyledBook.CellStyle.HAlign? = null,
        vertical: XlsxStyledBook.CellStyle.VAlign? = null,
        bold: Boolean = false,
        fillRgb: String? = null,
        numberFormat: String? = null,
        thinBorder: Boolean = false,
    ) = XlsxStyledBook.CellStyle(
        bold = bold,
        fillRgb = fillRgb,
        thinBorder = thinBorder,
        numberFormat = numberFormat,
        horizontal = horizontal,
        vertical = vertical,
    )

    private fun write(out: File, name: String, spec: XlsxStyledBook.WorkbookSpec): ByteArray {
        val bytes = XlsxStyledBook.write(spec)
        File(out, "$name.xlsx").writeBytes(bytes)
        val readBack = XlsxStyledBook.read(bytes)
        check(readBack.sheets == spec.sheets) { "$name round trip: ${readBack.sheets}" }
        return bytes
    }

    private fun xfCount(bytes: ByteArray): Int =
        Regex("<cellXfs count=\"(\\d+)\"").find(text(bytes))?.groupValues?.get(1)?.toInt()
            ?: error("no cellXfs count")

    /**
     * One intent row per cell. The style signature is recorded as declared fields rather than as an
     * interned index: the verifier derives the expected `s` sharing and the `cellXfs` count from these
     * fields, so no expectation is copied out of the implementation under test.
     */
    private fun record(rows: MutableList<String>, name: String, spec: XlsxStyledBook.WorkbookSpec) {
        spec.sheets.forEachIndexed { sheetIndex, sheet ->
            for (cell in sheet.cells) {
                rows.add(
                    listOf(
                        name, sheetIndex, cell.ref,
                        cell.style.horizontal?.token ?: "-",
                        cell.style.vertical?.token ?: "-",
                        if (cell.style.bold) 1 else 0,
                        cell.style.fillRgb ?: "-",
                        cell.style.numberFormat ?: "-",
                        if (cell.style.thinBorder) 1 else 0,
                    ).joinToString("\t"),
                )
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val rows = ArrayList<String>()

        // --- default: no alignment anywhere -------------------------------------------------------
        val plain = XlsxStyledBook.WorkbookSpec(
            listOf(XlsxStyledBook.StyledSheet(name = "Plain", cells = listOf(txt("A1", "a"), txt("B1", "b"), txt("A2", "c")))),
        )
        val plainBytes = write(out, "default", plain)
        check("<alignment" !in text(plainBytes)) { "a workbook without alignment must not emit <alignment>" }
        check("applyAlignment" !in text(plainBytes)) { "no applyAlignment without alignment" }
        check(xfCount(plainBytes) == 1) { "a uniform sheet keeps one xf (found ${xfCount(plainBytes)})" }
        record(rows, "default", plain)

        // --- horizontal only ----------------------------------------------------------------------
        val horizontal = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Horizontal",
                    cells = listOf(
                        txt("A1", "l", style(horizontal = XlsxStyledBook.CellStyle.HAlign.LEFT)),
                        txt("A2", "c", style(horizontal = XlsxStyledBook.CellStyle.HAlign.CENTER)),
                        txt("A3", "r", style(horizontal = XlsxStyledBook.CellStyle.HAlign.RIGHT)),
                    ),
                ),
            ),
        )
        val horizontalBytes = write(out, "horizontal", horizontal)
        val horizontalText = text(horizontalBytes)
        check("<alignment horizontal=\"left\"/>" in horizontalText) { "left token" }
        check("<alignment horizontal=\"center\"/>" in horizontalText) { "center token" }
        check("<alignment horizontal=\"right\"/>" in horizontalText) { "right token" }
        check("vertical=" !in horizontalText) { "horizontal-only styles must not declare a vertical token" }
        check(xfCount(horizontalBytes) == 4) { "three distinct alignments plus the default (found ${xfCount(horizontalBytes)})" }
        record(rows, "horizontal", horizontal)

        // --- vertical only ------------------------------------------------------------------------
        val vertical = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Vertical",
                    cells = listOf(
                        txt("A1", "t", style(vertical = XlsxStyledBook.CellStyle.VAlign.TOP)),
                        txt("A2", "m", style(vertical = XlsxStyledBook.CellStyle.VAlign.CENTER)),
                        txt("A3", "b", style(vertical = XlsxStyledBook.CellStyle.VAlign.BOTTOM)),
                    ),
                ),
            ),
        )
        val verticalBytes = write(out, "vertical", vertical)
        check("<alignment vertical=\"bottom\"/>" in text(verticalBytes)) { "bottom token" }
        check("horizontal=" !in text(verticalBytes)) { "vertical-only styles must not declare a horizontal token" }
        record(rows, "vertical", vertical)

        // --- both axes ---------------------------------------------------------------------------
        val combined = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Combined",
                    cells = listOf(
                        txt("A1", "x", style(XlsxStyledBook.CellStyle.HAlign.CENTER, XlsxStyledBook.CellStyle.VAlign.TOP)),
                        txt("A2", "y", style(XlsxStyledBook.CellStyle.HAlign.RIGHT, XlsxStyledBook.CellStyle.VAlign.BOTTOM)),
                    ),
                ),
            ),
        )
        val combinedBytes = write(out, "combined", combined)
        check("<alignment horizontal=\"center\" vertical=\"top\"/>" in text(combinedBytes)) { "center/top" }
        check("<alignment horizontal=\"right\" vertical=\"bottom\"/>" in text(combinedBytes)) { "right/bottom" }
        check("applyAlignment=\"1\"" in text(combinedBytes)) { "applyAlignment accompanies an alignment" }
        record(rows, "combined", combined)

        // --- deduplication: equal styles share one xf ----------------------------------------------
        val shared = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Shared",
                    cells = listOf(
                        txt("A1", "1", style(XlsxStyledBook.CellStyle.HAlign.CENTER, XlsxStyledBook.CellStyle.VAlign.TOP)),
                        txt("A2", "2", style(XlsxStyledBook.CellStyle.HAlign.CENTER, XlsxStyledBook.CellStyle.VAlign.TOP)),
                        txt("A3", "3", style(XlsxStyledBook.CellStyle.HAlign.RIGHT, XlsxStyledBook.CellStyle.VAlign.TOP)),
                    ),
                ),
            ),
        )
        val sharedBytes = write(out, "shared", shared)
        check(xfCount(sharedBytes) == 3) { "two identical alignments must intern to one xf (found ${xfCount(sharedBytes)})" }
        val sharedCells = XlsxStyledBook.read(sharedBytes).sheets.single().cells
        check(sharedCells[0].style == sharedCells[1].style && sharedCells[2].style != sharedCells[0].style) {
            "identical alignments stay equal across the round trip"
        }
        record(rows, "shared", shared)

        // --- alignment beside fonts, fills, borders and number formats -------------------------------
        val styled = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Styled",
                    cells = listOf(
                        txt("A1", "header", style(
                            horizontal = XlsxStyledBook.CellStyle.HAlign.CENTER,
                            vertical = XlsxStyledBook.CellStyle.VAlign.CENTER,
                            bold = true,
                            fillRgb = "FFF2CC",
                            thinBorder = true,
                        )),
                        txt("A2", "plain"),
                        // Declared in sheet order: the reader returns cells in row/column order.
                        XlsxStyledBook.StyledCell(
                            "B2",
                            XlsxStyledBook.StyledVal.Num(12.5),
                            style(horizontal = XlsxStyledBook.CellStyle.HAlign.RIGHT, numberFormat = "#,##0.00"),
                        ),
                    ),
                ),
            ),
        )
        val styledBytes = write(out, "styled", styled)
        val styledText = text(styledBytes)
        check("applyFont=\"1\"" in styledText && "applyFill=\"1\"" in styledText && "applyBorder=\"1\"" in styledText) {
            "font/fill/border applies survive beside alignment"
        }
        check("applyNumberFormat=\"1\"" in styledText) { "number format applies beside alignment" }
        check(styledText.contains("<alignment horizontal=\"center\" vertical=\"center\"/>")) { "styled alignment" }
        val styledBack = XlsxStyledBook.read(styledBytes).sheets.single().cells
        check(styledBack.first { it.ref == "A1" }.style.bold) { "bold round trips beside alignment" }
        check(styledBack.first { it.ref == "B2" }.style.numberFormat == "#,##0.00") { "format round trips" }
        record(rows, "styled", styled)

        // --- tokens --------------------------------------------------------------------------------
        check(XlsxStyledBook.CellStyle.HAlign.entries.map { it.token } == listOf("left", "center", "right")) {
            "horizontal token set"
        }
        check(XlsxStyledBook.CellStyle.VAlign.entries.map { it.token } == listOf("top", "center", "bottom")) {
            "vertical token set"
        }

        File(out, "align.tsv").writeText(rows.joinToString("\n") + "\n")
        println("R82_SHEET_ALIGN_OK assertions=$assertions workbooks=${out.listFiles { f -> f.name.endsWith(".xlsx") }?.size}")
    }
}
