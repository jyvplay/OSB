// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R81 spreadsheet row-height contracts: `StyledSheet.rowHeights` emits `<row ht customHeight>`,
 * including rows that hold no cells, and survives a round trip.
 *
 * Host JVM only. The probe writes real `.xlsx` workbooks plus `rows.tsv`; the independent reader is
 * tools/check_r81_sheet_rows.py, which opens the workbooks with openpyxl and inspects the raw OPC
 * parts. Nothing here is Excel recalculation or rendering evidence, and no caller sets row heights
 * yet.
 */
object R81SheetRowChecks {
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

    private fun num(ref: String, value: Double) =
        XlsxStyledBook.StyledCell(ref, XlsxStyledBook.StyledVal.Num(value))

    private fun txt(ref: String, value: String) =
        XlsxStyledBook.StyledCell(ref, XlsxStyledBook.StyledVal.Txt(value))

    private fun text(bytes: ByteArray): String = String(bytes, Charsets.ISO_8859_1)

    private fun sheetData(bytes: ByteArray): String =
        text(bytes).substringAfter("<sheetData>").substringBefore("</sheetData>")

    private fun write(out: File, name: String, spec: XlsxStyledBook.WorkbookSpec): ByteArray {
        val bytes = XlsxStyledBook.write(spec)
        File(out, "$name.xlsx").writeBytes(bytes)
        val readBack = XlsxStyledBook.read(bytes)
        check(readBack.sheets == spec.sheets) { "$name sheet round trip: ${readBack.sheets}" }
        check(readBack.sheets.map { it.rowHeights } == spec.sheets.map { it.rowHeights }) {
            "$name row heights round trip"
        }
        check(readBack.sheets.map { it.cells.size } == spec.sheets.map { it.cells.size }) {
            "$name cell count round trip"
        }
        check(readBack.definedNames == spec.definedNames) { "$name defined names round trip" }
        return bytes
    }

    private fun record(rows: MutableList<String>, name: String, spec: XlsxStyledBook.WorkbookSpec) {
        spec.sheets.forEachIndexed { index, sheet ->
            val heights = if (sheet.rowHeights.isEmpty()) "-" else
                sheet.rowHeights.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }
            val cellRows = sheet.cells.map { it.ref.dropWhile(Char::isLetter).toInt() }.distinct().sorted()
            val maxCellRow = cellRows.lastOrNull() ?: 0
            val widths = if (sheet.columnWidths.isEmpty()) "-" else
                sheet.columnWidths.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }
            rows.add(
                listOf(
                    name, index, sheet.name, heights,
                    if (cellRows.isEmpty()) "-" else cellRows.joinToString(","),
                    sheet.cells.size, maxCellRow,
                    if (sheet.freezeRows > 0 || sheet.freezeCols > 0) "${sheet.freezeRows}/${sheet.freezeCols}" else "-",
                    widths,
                ).joinToString("\t"),
            )
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val rows = ArrayList<String>()

        // --- the unchanged path: no heights means no ht attribute -------------------------------
        val plain = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Plain",
                    cells = listOf(txt("A1", "Item"), num("B1", 1.0), txt("A2", "Widget"), num("B2", 2.5)),
                ),
            ),
        )
        val plainBytes = write(out, "plain", plain)
        check("ht=" !in sheetData(plainBytes)) { "a sheet without row heights must not emit ht" }
        check("customHeight" !in sheetData(plainBytes)) { "a sheet without row heights must not emit customHeight" }
        record(rows, "plain", plain)

        // --- heights, including a row that holds no cells ---------------------------------------
        val heights = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Sized",
                    cells = listOf(txt("A1", "Header"), num("B1", 3.0), txt("A2", "Body"), num("B2", 4.0)),
                    rowHeights = mapOf(1 to 24.0, 3 to 9.75),
                ),
            ),
        )
        val heightBytes = write(out, "heights", heights)
        val heightData = sheetData(heightBytes)
        check(heightData.contains("""<row r="1" ht="24.0" customHeight="1">""")) {
            "row 1 declares its height: $heightData"
        }
        check(heightData.contains("""<row r="3" ht="9.75" customHeight="1"></row>""")) {
            "a height-only row is still emitted: $heightData"
        }
        check(heightData.contains("""<row r="2">""")) { "row 2 keeps no height attribute: $heightData" }
        check(heightData.indexOf("<row r=\"1\"") < heightData.indexOf("<row r=\"2\"") &&
            heightData.indexOf("<row r=\"2\"") < heightData.indexOf("<row r=\"3\"")) {
            "rows stay in ascending order: $heightData"
        }
        check(Regex("<row r=\"4\"").find(heightData) == null) { "a height must not invent a fourth row" }
        record(rows, "heights", heights)

        // --- the documented maximum and a fractional height --------------------------------------
        val maximum = XlsxStyledBook.WorkbookSpec(
            listOf(XlsxStyledBook.StyledSheet(name = "Tall", cells = listOf(num("A1", 1.0)), rowHeights = mapOf(2 to 409.0))),
        )
        val maximumBytes = write(out, "max-height", maximum)
        check(sheetData(maximumBytes).contains("""<row r="2" ht="409.0" customHeight="1"></row>""")) { "maximum height" }
        record(rows, "max-height", maximum)
        val fractional = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Fractional",
                    cells = listOf(num("A1", 1.0), num("A2", 2.0)),
                    rowHeights = mapOf(1 to 13.5, 2 to 0.75),
                ),
            ),
        )
        val fractionalBytes = write(out, "fractional", fractional)
        check(sheetData(fractionalBytes).contains("""ht="13.5"""") && sheetData(fractionalBytes).contains("""ht="0.75"""")) {
            "fractional heights are preserved: ${sheetData(fractionalBytes)}"
        }
        record(rows, "fractional", fractional)

        // --- heights coexist with panes, widths, defined names and styles -------------------------
        val styled = XlsxStyledBook.WorkbookSpec(
            sheets = listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Styled",
                    // Declared in sheet order: the reader returns cells in row/column order, so an
                    // unsorted declaration would compare unequal for a reason unrelated to heights.
                    cells = listOf(
                        XlsxStyledBook.StyledCell(
                            "A1",
                            XlsxStyledBook.StyledVal.Txt("Bold"),
                            XlsxStyledBook.CellStyle(bold = true, fillRgb = "FFF2CC"),
                        ),
                        num("B1", 12.5),
                        XlsxStyledBook.StyledCell(
                            "B2",
                            XlsxStyledBook.StyledVal.Formula("SUM(B1:B1)", 12.5),
                            XlsxStyledBook.CellStyle(numberFormat = "#,##0.00"),
                        ),
                    ),
                    freezeRows = 1,
                    freezeCols = 1,
                    columnWidths = mapOf(1 to 18.0, 2 to 12.5),
                    rowHeights = mapOf(1 to 30.0),
                ),
            ),
            definedNames = listOf(XlsxStyledBook.DefinedName("Total", "Styled!\$B\$1:\$B\$2")),
        )
        val styledBytes = write(out, "styled", styled)
        val styledData = sheetData(styledBytes)
        check(styledData.contains("""<row r="1" ht="30.0" customHeight="1">""")) { "styled row height: $styledData" }
        check("<pane" in text(styledBytes)) { "freeze panes survive the height change" }
        check("""<col min="1" max="1" width="18.0" customWidth="1"/>""" in text(styledBytes)) { "column widths survive" }
        check("Total" in text(styledBytes)) { "defined names survive" }
        record(rows, "styled", styled)

        // --- two sheets keep their own heights ---------------------------------------------------
        val multi = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(name = "First", cells = listOf(num("A1", 1.0)), rowHeights = mapOf(1 to 21.0)),
                XlsxStyledBook.StyledSheet(name = "Second", cells = listOf(num("A1", 2.0)), rowHeights = mapOf(1 to 42.0)),
            ),
        )
        val multiBytes = write(out, "multi-sheet", multi)
        check(text(multiBytes).split("""ht="21.0"""").size == 2) { "first sheet height" }
        check(text(multiBytes).split("""ht="42.0"""").size == 2) { "second sheet height" }
        record(rows, "multi-sheet", multi)

        // --- refusals ----------------------------------------------------------------------------
        fails("zero height") { XlsxStyledBook.StyledSheet("Bad", emptyList(), rowHeights = mapOf(1 to 0.0)) }
        fails("negative height") { XlsxStyledBook.StyledSheet("Bad", emptyList(), rowHeights = mapOf(1 to -3.0)) }
        fails("height above the maximum") { XlsxStyledBook.StyledSheet("Bad", emptyList(), rowHeights = mapOf(1 to 409.01)) }
        fails("row zero") { XlsxStyledBook.StyledSheet("Bad", emptyList(), rowHeights = mapOf(0 to 12.0)) }
        fails("row beyond the sheet") {
            XlsxStyledBook.StyledSheet("Bad", emptyList(), rowHeights = mapOf(1_048_577 to 12.0))
        }
        assertions++
        check(
            XlsxStyledBook.StyledSheet("Edge", emptyList(), rowHeights = mapOf(1_048_576 to 409.0)).rowHeights
                .getValue(1_048_576) == 409.0,
        ) { "the last row and the maximum height are accepted" }

        File(out, "rows.tsv").writeText(rows.joinToString("\n") + "\n")
        println("R81_SHEET_ROW_OK assertions=$assertions workbooks=${out.listFiles { f -> f.name.endsWith(".xlsx") }?.size}")
    }
}
