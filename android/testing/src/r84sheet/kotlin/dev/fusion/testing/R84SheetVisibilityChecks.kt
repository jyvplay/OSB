// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R84 spreadsheet sheet-visibility contracts: `StyledSheet.visibility` emits `state="hidden"` /
 * `state="veryHidden"` on the `<sheet>` element of `xl/workbook.xml`, omits the attribute for a visible
 * sheet, refuses a workbook with no visible sheet, and reads the state back.
 *
 * Host JVM only. The probe writes real `.xlsx` workbooks plus `visibility.tsv`; the independent reader is
 * tools/check_r84_sheet_visibility.py, which opens the workbooks with openpyxl (`ws.sheet_state`) and
 * inspects `xl/workbook.xml` directly. Nothing here is Excel rendering or UX evidence, and no caller hides
 * a sheet yet.
 */
object R84SheetVisibilityChecks {
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

    private fun sheetsXml(bytes: ByteArray): String =
        text(bytes).substringAfter("<sheets>").substringBefore("</sheets>")

    private fun bookViews(bytes: ByteArray): String =
        text(bytes).substringAfter("<workbook ").substringBefore("<sheets>").let {
            it.substringAfter(">").substringBefore("<sheets>")
        }

    private fun txt(ref: String, value: String) =
        XlsxStyledBook.StyledCell(ref, XlsxStyledBook.StyledVal.Txt(value))

    private fun write(out: File, name: String, spec: XlsxStyledBook.WorkbookSpec): ByteArray {
        val bytes = XlsxStyledBook.write(spec)
        File(out, "$name.xlsx").writeBytes(bytes)
        val readBack = XlsxStyledBook.read(bytes)
        check(readBack.sheets.map { it.name } == spec.sheets.map { it.name }) { "$name sheet names" }
        check(readBack.sheets.map { it.visibility } == spec.sheets.map { it.visibility }) { "$name visibility" }
        return bytes
    }

    private fun record(rows: MutableList<String>, name: String, spec: XlsxStyledBook.WorkbookSpec) {
        spec.sheets.forEachIndexed { index, sheet ->
            rows.add(
                listOf(name, index, sheet.name, sheet.visibility.token, sheet.cells.size).joinToString("\t"),
            )
        }
    }

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

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val rows = ArrayList<String>()

        // --- the unchanged path: every sheet visible, so no state attribute at all -----------------
        val plain = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(name = "One", cells = listOf(txt("A1", "a"))),
                XlsxStyledBook.StyledSheet(name = "Two", cells = listOf(txt("A1", "b"))),
            ),
        )
        val plainBytes = write(out, "default", plain)
        check("state=" !in sheetsXml(plainBytes)) { "a visible sheet must not carry a state attribute: ${sheetsXml(plainBytes)}" }
        check(
            sheetsXml(plainBytes) ==
                "<sheet name=\"One\" sheetId=\"1\" r:id=\"rSheet1\"/><sheet name=\"Two\" sheetId=\"2\" r:id=\"rSheet2\"/>",
        ) { "the pre-R84 sheet bytes are unchanged: ${sheetsXml(plainBytes)}" }
        record(rows, "default", plain)

        // --- one hidden sheet ----------------------------------------------------------------------
        val hidden = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(name = "Shown", cells = listOf(txt("A1", "shown"))),
                XlsxStyledBook.StyledSheet(
                    name = "Hidden", cells = listOf(txt("A1", "hidden")), visibility = XlsxStyledBook.Visibility.HIDDEN,
                ),
            ),
        )
        val hiddenBytes = write(out, "hidden", hidden)
        check(
            "<sheet name=\"Shown\" sheetId=\"1\" r:id=\"rSheet1\"/>" in sheetsXml(hiddenBytes) &&
                "<sheet name=\"Hidden\" sheetId=\"2\" state=\"hidden\" r:id=\"rSheet2\"/>" in sheetsXml(hiddenBytes),
        ) { "only the hidden sheet carries state: ${sheetsXml(hiddenBytes)}" }
        record(rows, "hidden", hidden)

        // --- a very hidden sheet (not reachable from the Excel UI) ---------------------------------
        val veryHidden = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(name = "Shown", cells = listOf(txt("A1", "shown"))),
                XlsxStyledBook.StyledSheet(
                    name = "Deep", cells = listOf(txt("A1", "deep")), visibility = XlsxStyledBook.Visibility.VERY_HIDDEN,
                ),
            ),
        )
        val veryHiddenBytes = write(out, "very-hidden", veryHidden)
        check("<sheet name=\"Deep\" sheetId=\"2\" state=\"veryHidden\" r:id=\"rSheet2\"/>" in sheetsXml(veryHiddenBytes)) {
            "a very hidden sheet is spelled veryHidden: ${sheetsXml(veryHiddenBytes)}"
        }
        record(rows, "very-hidden", veryHidden)

        // --- all three states in one workbook, in order --------------------------------------------
        val mixed = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(name = "Front", cells = listOf(txt("A1", "1"))),
                XlsxStyledBook.StyledSheet(
                    name = "Back", cells = listOf(txt("A1", "2")), visibility = XlsxStyledBook.Visibility.HIDDEN,
                ),
                XlsxStyledBook.StyledSheet(
                    name = "Archive", cells = listOf(txt("A1", "3")), visibility = XlsxStyledBook.Visibility.VERY_HIDDEN,
                ),
            ),
        )
        val mixedBytes = write(out, "mixed", mixed)
        check(
            sheetsXml(mixedBytes) ==
                "<sheet name=\"Front\" sheetId=\"1\" r:id=\"rSheet1\"/>" +
                "<sheet name=\"Back\" sheetId=\"2\" state=\"hidden\" r:id=\"rSheet2\"/>" +
                "<sheet name=\"Archive\" sheetId=\"3\" state=\"veryHidden\" r:id=\"rSheet3\"/>",
        ) { "the three states keep their sheet order: ${sheetsXml(mixedBytes)}" }
        record(rows, "mixed", mixed)

        // --- a hidden first sheet: bookViews must point the active tab at the first visible sheet ----
        check("<bookViews>" !in text(plainBytes)) {
            "a workbook that hides nothing must not emit bookViews: ${bookViews(plainBytes)}"
        }
        val firstHidden = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Cover", cells = listOf(txt("A1", "cover")), visibility = XlsxStyledBook.Visibility.HIDDEN,
                ),
                XlsxStyledBook.StyledSheet(name = "Body", cells = listOf(txt("A1", "body"))),
            ),
        )
        val firstHiddenBytes = write(out, "first-hidden", firstHidden)
        check(text(firstHiddenBytes).contains("<bookViews><workbookView activeTab=\"1\"/></bookViews>")) {
            "the first visible sheet becomes the active tab: ${bookViews(firstHiddenBytes)}"
        }
        record(rows, "first-hidden", firstHidden)

        // --- visibility is orthogonal to styles, panes, hidden dimensions and defined names --------
        val styled = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Live",
                    cells = listOf(
                        XlsxStyledBook.StyledCell(
                            "A1", XlsxStyledBook.StyledVal.Num(42.0),
                            XlsxStyledBook.CellStyle(bold = true, numberFormat = "#,##0.00"),
                        ),
                        XlsxStyledBook.StyledCell("B2", XlsxStyledBook.StyledVal.Txt("x")),
                    ),
                    freezeRows = 1, columnWidths = mapOf(1 to 12.0), hiddenRows = setOf(2),
                ),
                XlsxStyledBook.StyledSheet(
                    name = "Stowed", cells = listOf(txt("A1", "stowed")),
                    visibility = XlsxStyledBook.Visibility.HIDDEN,
                ),
            ),
            definedNames = listOf(XlsxStyledBook.DefinedName("Range1", "Live!\$A\$1:\$B\$2")),
        )
        val styledBytes = write(out, "styled", styled)
        val styledXml = text(styledBytes)
        check("<pane" in styledXml) { "freeze panes survive sheet visibility" }
        check("<row r=\"2\" hidden=\"1\">" in styledXml) { "hidden rows survive sheet visibility" }
        check("Range1" in styledXml) { "defined names survive sheet visibility" }
        check("state=\"hidden\"" in sheetsXml(styledBytes)) { "the hidden sheet keeps its state" }
        record(rows, "styled", styled)

        // --- import leniency: an explicit state="visible" is the schema default, not an unknown -----
        val explicit = rezip(plainBytes, "xl/workbook.xml") {
            it.replace("<sheet name=\"One\" sheetId=\"1\"", "<sheet name=\"One\" sheetId=\"1\" state=\"visible\"")
        }
        val explicitSheets = part(explicit, "xl/workbook.xml").substringAfter("<sheets>").substringBefore("</sheets>")
        check("state=\"visible\"" in explicitSheets) { "the patched part really says state=\"visible\": $explicitSheets" }
        check(XlsxStyledBook.read(explicit).sheets.map { it.visibility } == plain.sheets.map { it.visibility }) {
            "an explicit state=\"visible\" imports as visible: ${XlsxStyledBook.read(explicit).sheets.map { it.visibility }}"
        }

        // --- boundaries and refusals ---------------------------------------------------------------
        check(
            XlsxStyledBook.WorkbookSpec(
                listOf(
                    XlsxStyledBook.StyledSheet("Only", emptyList(), visibility = XlsxStyledBook.Visibility.HIDDEN),
                    XlsxStyledBook.StyledSheet("Shown", emptyList()),
                ),
            ).sheets.size == 2,
        ) { "one visible sheet is enough" }
        fails("the only sheet hidden") {
            XlsxStyledBook.WorkbookSpec(
                listOf(XlsxStyledBook.StyledSheet("Only", emptyList(), visibility = XlsxStyledBook.Visibility.HIDDEN)),
            )
        }
        fails("the only sheet very hidden") {
            XlsxStyledBook.WorkbookSpec(
                listOf(XlsxStyledBook.StyledSheet("Only", emptyList(), visibility = XlsxStyledBook.Visibility.VERY_HIDDEN)),
            )
        }
        fails("every sheet hidden") {
            XlsxStyledBook.WorkbookSpec(
                listOf(
                    XlsxStyledBook.StyledSheet("A", emptyList(), visibility = XlsxStyledBook.Visibility.HIDDEN),
                    XlsxStyledBook.StyledSheet("B", emptyList(), visibility = XlsxStyledBook.Visibility.VERY_HIDDEN),
                ),
            )
        }
        fails("no sheets at all") { XlsxStyledBook.WorkbookSpec(emptyList()) }
        check(XlsxStyledBook.StyledSheet("Plain", emptyList()).visibility == XlsxStyledBook.Visibility.VISIBLE) {
            "the default visibility is visible"
        }

        File(out, "visibility.tsv").writeText(rows.joinToString("\n") + "\n")
        println("R84_SHEET_VISIBILITY_OK assertions=$assertions workbooks=${out.listFiles { f -> f.name.endsWith(".xlsx") }?.size}")
    }
}
