// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R83 spreadsheet hidden-dimension contracts: `StyledSheet.hiddenRows` / `hiddenColumns` emit
 * `hidden="1"` on `<row>` / `<col>`, including hidden rows that carry no cells, and read back.
 *
 * Host JVM only. The probe writes real `.xlsx` workbooks plus `hidden.tsv`; the independent reader is
 * tools/check_r83_sheet_hidden.py, which opens the workbooks with openpyxl (`row_dimensions[n].hidden`,
 * `column_dimensions[c].hidden`) and inspects the raw OPC parts. Nothing here is Excel recalculation or
 * rendering evidence, and no caller hides rows or columns yet.
 */
object R83SheetHiddenChecks {
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

    private fun sheetXml(bytes: ByteArray): String =
        text(bytes).substringAfter("<worksheet ").substringBefore("</worksheet>")

    private fun txt(ref: String, value: String) =
        XlsxStyledBook.StyledCell(ref, XlsxStyledBook.StyledVal.Txt(value))

    /** Reads one OPC part back out of a package, compressed or not. */
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

    /** Rewrites one OPC part of a real package, so import leniency is tested on bytes, not on a mock. */
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

    private fun write(out: File, name: String, spec: XlsxStyledBook.WorkbookSpec): ByteArray {
        val bytes = XlsxStyledBook.write(spec)
        File(out, "$name.xlsx").writeBytes(bytes)
        val readBack = XlsxStyledBook.read(bytes)
        check(readBack.sheets == spec.sheets) { "$name round trip: ${readBack.sheets}" }
        check(readBack.sheets.map { it.hiddenRows } == spec.sheets.map { it.hiddenRows }) { "$name hidden rows" }
        check(readBack.sheets.map { it.hiddenColumns } == spec.sheets.map { it.hiddenColumns }) { "$name hidden columns" }
        return bytes
    }

    private fun record(rows: MutableList<String>, name: String, spec: XlsxStyledBook.WorkbookSpec) {
        spec.sheets.forEachIndexed { sheetIndex, sheet ->
            val cellRows = sheet.cells.map { it.ref.dropWhile(Char::isLetter).toInt() }.distinct().sorted()
            rows.add(
                listOf(
                    name, sheetIndex, sheet.name,
                    if (sheet.hiddenRows.isEmpty()) "-" else sheet.hiddenRows.sorted().joinToString(","),
                    if (sheet.hiddenColumns.isEmpty()) "-" else sheet.hiddenColumns.sorted().joinToString(","),
                    if (sheet.columnWidths.isEmpty()) "-" else
                        sheet.columnWidths.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" },
                    if (cellRows.isEmpty()) "-" else cellRows.joinToString(","),
                    sheet.cells.size,
                ).joinToString("\t"),
            )
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val rows = ArrayList<String>()

        // --- the unchanged path: nothing hidden ---------------------------------------------------
        val plain = XlsxStyledBook.WorkbookSpec(
            listOf(XlsxStyledBook.StyledSheet(name = "Plain", cells = listOf(txt("A1", "a"), txt("B2", "b")))),
        )
        val plainBytes = write(out, "default", plain)
        check("hidden" !in sheetXml(plainBytes)) { "a sheet with nothing hidden must not emit hidden" }
        check("<cols>" !in sheetXml(plainBytes)) { "a sheet without widths or hidden columns must not emit cols" }
        record(rows, "default", plain)

        // --- a hidden row that holds cells, and one that holds none --------------------------------
        val hiddenRows = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Rows",
                    cells = listOf(txt("A1", "visible"), txt("A2", "hidden")),
                    hiddenRows = setOf(2, 5),
                ),
            ),
        )
        val hiddenRowsBytes = write(out, "rows", hiddenRows)
        val rowsXml = sheetXml(hiddenRowsBytes)
        check(rowsXml.contains("<row r=\"2\" hidden=\"1\">")) { "row 2 is hidden: $rowsXml" }
        check(rowsXml.contains("<row r=\"5\" hidden=\"1\"></row>")) { "a hidden row with no cells is still emitted" }
        check(rowsXml.contains("<row r=\"1\">")) { "row 1 keeps no hidden attribute" }
        record(rows, "rows", hiddenRows)

        // --- hidden columns, ordered by index ------------------------------------------------------
        val hiddenColumns = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Columns",
                    cells = listOf(txt("A1", "1"), txt("B1", "2"), txt("C1", "3")),
                    hiddenColumns = setOf(3, 2),
                ),
            ),
        )
        val hiddenColumnsBytes = write(out, "columns", hiddenColumns)
        val columnsXml = sheetXml(hiddenColumnsBytes)
        check(columnsXml.contains("<cols><col min=\"2\" max=\"2\" hidden=\"1\"/><col min=\"3\" max=\"3\" hidden=\"1\"/></cols>")) {
            "hidden columns are emitted in ascending order: $columnsXml"
        }
        check(columnsXml.count { it == '<' } > 0) { "sanity" }
        record(rows, "columns", hiddenColumns)

        // --- a column that is both wide and hidden, plus a visible width ---------------------------
        val both = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Both",
                    cells = listOf(txt("A1", "a"), txt("B1", "b"), txt("C1", "c")),
                    columnWidths = mapOf(1 to 18.0, 2 to 12.0),
                    hiddenColumns = setOf(2),
                    hiddenRows = setOf(1),
                ),
            ),
        )
        val bothBytes = write(out, "both", both)
        val bothXml = sheetXml(bothBytes)
        check(bothXml.contains("<col min=\"1\" max=\"1\" width=\"18.0\" customWidth=\"1\"/>")) {
            "a visible width is unchanged: $bothXml"
        }
        check(bothXml.contains("<col min=\"2\" max=\"2\" width=\"12.0\" customWidth=\"1\" hidden=\"1\"/>")) {
            "a column may be wide and hidden at once: $bothXml"
        }
        record(rows, "both", both)

        // --- import leniency: a foreign producer may say hidden="true" instead of hidden="1" --------
        val spelled = rezip(bothBytes, "xl/worksheets/sheet1.xml") { it.replace("hidden=\"1\"", "hidden=\"true\"") }
        check("hidden=\"true\"" in part(spelled, "xl/worksheets/sheet1.xml")) {
            "the patched part really says hidden=\"true\""
        }
        val spelledRead = XlsxStyledBook.read(spelled)
        check(spelledRead.sheets.map { it.hiddenRows } == both.sheets.map { it.hiddenRows }) {
            "hidden=\"true\" rows still import as hidden: ${spelledRead.sheets.map { it.hiddenRows }}"
        }
        check(spelledRead.sheets.map { it.hiddenColumns } == both.sheets.map { it.hiddenColumns }) {
            "hidden=\"true\" columns still import as hidden: ${spelledRead.sheets.map { it.hiddenColumns }}"
        }

        // --- a row that is both tall and hidden ----------------------------------------------------
        val tall = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Tall",
                    cells = listOf(txt("A1", "x")),
                    rowHeights = mapOf(3 to 24.0),
                    hiddenRows = setOf(3),
                ),
            ),
        )
        val tallBytes = write(out, "height-hidden", tall)
        check(sheetXml(tallBytes).contains("<row r=\"3\" ht=\"24.0\" customHeight=\"1\" hidden=\"1\"></row>")) {
            "a row may be tall and hidden at once: ${sheetXml(tallBytes)}"
        }
        record(rows, "height-hidden", tall)

        // --- hidden dimensions beside styles, panes and defined names ------------------------------
        val styled = XlsxStyledBook.WorkbookSpec(
            sheets = listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Styled",
                    cells = listOf(
                        XlsxStyledBook.StyledCell(
                            "A1", XlsxStyledBook.StyledVal.Txt("h"),
                            XlsxStyledBook.CellStyle(bold = true, horizontal = XlsxStyledBook.CellStyle.HAlign.CENTER),
                        ),
                        txt("A2", "secret"),
                        txt("B2", "public"),
                    ),
                    freezeRows = 1,
                    hiddenRows = setOf(2),
                    hiddenColumns = setOf(3),
                    columnWidths = mapOf(3 to 9.0),
                ),
            ),
            definedNames = listOf(XlsxStyledBook.DefinedName("Range1", "Styled!\$A\$1:\$B\$2")),
        )
        val styledBytes = write(out, "styled", styled)
        val styledXml = text(styledBytes)
        check("<pane" in styledXml) { "freeze panes survive hidden dimensions" }
        check("<alignment horizontal=\"center\"/>" in styledXml) { "alignment survives hidden dimensions" }
        check("Range1" in styledXml) { "defined names survive" }
        record(rows, "styled", styled)

        // --- boundaries and refusals ---------------------------------------------------------------
        assertions++
        check(
            XlsxStyledBook.StyledSheet("Edge", emptyList(), hiddenRows = setOf(1_048_576),
                hiddenColumns = setOf(16_384)).hiddenColumns.contains(16_384),
        ) { "the last row and column are accepted" }
        fails("row zero") { XlsxStyledBook.StyledSheet("Bad", emptyList(), hiddenRows = setOf(0)) }
        fails("row beyond the sheet") { XlsxStyledBook.StyledSheet("Bad", emptyList(), hiddenRows = setOf(1_048_577)) }
        fails("column zero") { XlsxStyledBook.StyledSheet("Bad", emptyList(), hiddenColumns = setOf(0)) }
        fails("column beyond the sheet") { XlsxStyledBook.StyledSheet("Bad", emptyList(), hiddenColumns = setOf(16_385)) }

        File(out, "hidden.tsv").writeText(rows.joinToString("\n") + "\n")
        println("R83_SHEET_HIDDEN_OK assertions=$assertions workbooks=${out.listFiles { f -> f.name.endsWith(".xlsx") }?.size}")
    }
}
