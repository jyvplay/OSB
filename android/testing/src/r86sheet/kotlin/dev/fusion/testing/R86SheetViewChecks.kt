// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R86 spreadsheet sheet-tab and workbook-view contracts: `<sheetPr><tabColor rgb="…"/></sheetPr>` from
 * `StyledSheet.tabRgb`, per-sheet `<sheetView zoomScale="…"/>` from `StyledSheet.zoomScale`, and the
 * workbook-level `<bookViews><workbookView showSheetTabs="…" firstSheet="…"/></bookViews>` from
 * `WorkbookSpec.showSheetTabs`/`firstSheet`, all readable back.
 *
 * Host JVM only. The probe writes real `.xlsx` workbooks plus `views.tsv`; the independent reader is
 * tools/check_r86_sheet_views.py, which opens them with openpyxl (`sheet_properties.tabColor`,
 * `sheet_view.zoomScale`, `wb.views[0]`) and inspects the raw OPC parts. Nothing here is Excel's tab
 * strip, navigation or repair behaviour, and no caller sets the new fields yet.
 */
object R86SheetViewChecks {
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

    private fun sheetXml(bytes: ByteArray, index: Int): String = part(bytes, "xl/worksheets/sheet$index.xml")

    private fun workbookXml(bytes: ByteArray): String = part(bytes, "xl/workbook.xml")

    private fun views(bytes: ByteArray): String =
        workbookXml(bytes).substringAfter("<bookViews>").substringBefore("</bookViews>")

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
        check(readBack == spec) { "$name workbook round trip: $readBack" }
        return bytes
    }

    private fun record(rows: MutableList<String>, name: String, spec: XlsxStyledBook.WorkbookSpec) {
        val showTabs = spec.showSheetTabs?.toString() ?: "-"
        val first = spec.firstSheet?.toString() ?: "-"
        spec.sheets.forEachIndexed { index, sheet ->
            rows.add(
                listOf(
                    name, index, sheet.name, sheet.visibility.token, sheet.tabRgb ?: "-",
                    sheet.zoomScale ?: "-", sheet.cells.size, showTabs, first, sheet.freezeRows,
                ).joinToString("\t"),
            )
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val rows = ArrayList<String>()

        // --- the unchanged path: no colour, no zoom, no view option -------------------------------
        val plain = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(name = "One", cells = listOf(txt("A1", "a"))),
                XlsxStyledBook.StyledSheet(name = "Two", cells = listOf(txt("A1", "b"))),
            ),
        )
        val plainBytes = write(out, "default", plain)
        check("<bookViews>" !in workbookXml(plainBytes)) {
            "a workbook that hides nothing and declares no view option must not emit bookViews"
        }
        check("<sheetPr" !in sheetXml(plainBytes, 1)) { "a sheet without a tab colour must not emit sheetPr" }
        check("<sheetViews" !in sheetXml(plainBytes, 1)) { "a sheet without freeze or zoom must not emit sheetViews" }
        check(sheetXml(plainBytes, 1).startsWith("<?xml") && "<worksheet " in sheetXml(plainBytes, 1)) {
            "the sheet part is a worksheet document"
        }
        record(rows, "default", plain)

        // --- a tab colour on two sheets, normalised to upper case ---------------------------------
        val coloured = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(name = "One", cells = listOf(txt("A1", "a")), tabRgb = "00B050"),
                XlsxStyledBook.StyledSheet(name = "Two", cells = listOf(txt("A1", "b")), tabRgb = "FF0000"),
            ),
        )
        val colouredBytes = write(out, "tab-colour", coloured)
        check("<sheetPr><tabColor rgb=\"FF00B050\"/></sheetPr>" in sheetXml(colouredBytes, 1)) {
            "a declared tab colour is written as ARGB (FF prefix): ${sheetXml(colouredBytes, 1)}"
        }
        val lowerCase = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(
                listOf(XlsxStyledBook.StyledSheet(name = "One", cells = emptyList(), tabRgb = "00b050")),
            ),
        )
        check("<sheetPr><tabColor rgb=\"FF00B050\"/></sheetPr>" in sheetXml(lowerCase, 1)) {
            "a lower-case declaration is normalised to upper case"
        }
        check("<sheetPr><tabColor rgb=\"FFFF0000\"/></sheetPr>" in sheetXml(colouredBytes, 2)) {
            "the second sheet keeps its own colour"
        }
        check(sheetXml(colouredBytes, 1).substringAfter("<worksheet xmlns=\"").substringAfter("\">")
            .startsWith("<sheetPr>")) { "sheetPr is the first child of the worksheet: ${sheetXml(colouredBytes, 1)}" }
        check("<bookViews>" !in workbookXml(colouredBytes)) { "a tab colour is not a workbook view option" }
        record(rows, "tab-colour", coloured)

        // --- hiding the tab strip -----------------------------------------------------------------
        val hiddenTabs = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(name = "One", cells = listOf(txt("A1", "a"))),
                XlsxStyledBook.StyledSheet(name = "Two", cells = listOf(txt("A1", "b"))),
            ),
            showSheetTabs = false,
        )
        val hiddenTabsBytes = write(out, "hide-tabs", hiddenTabs)
        check("<bookViews><workbookView showSheetTabs=\"0\"/></bookViews>" in workbookXml(hiddenTabsBytes)) {
            "showSheetTabs=false is written as 0 and no activeTab is invented: ${workbookXml(hiddenTabsBytes)}"
        }
        record(rows, "hide-tabs", hiddenTabs)

        // --- naming the leftmost tab ---------------------------------------------------------------
        val firstSheet = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(name = "One", cells = listOf(txt("A1", "a"))),
                XlsxStyledBook.StyledSheet(name = "Two", cells = listOf(txt("A1", "b"))),
                XlsxStyledBook.StyledSheet(name = "Three", cells = listOf(txt("A1", "c"))),
            ),
            firstSheet = 2,
        )
        val firstSheetBytes = write(out, "first-sheet", firstSheet)
        check("<bookViews><workbookView firstSheet=\"2\"/></bookViews>" in workbookXml(firstSheetBytes)) {
            "firstSheet is written verbatim: ${workbookXml(firstSheetBytes)}"
        }
        record(rows, "first-sheet", firstSheet)

        // --- zoom, alone and beside a frozen pane ---------------------------------------------------
        val zoom = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(name = "Wide", cells = listOf(txt("A1", "a")), zoomScale = 150),
                XlsxStyledBook.StyledSheet(
                    name = "Frozen", cells = listOf(txt("A1", "b")), zoomScale = 75, freezeRows = 1,
                ),
            ),
        )
        val zoomBytes = write(out, "zoom", zoom)
        check("<sheetViews><sheetView workbookViewId=\"0\" zoomScale=\"150\"/></sheetViews>" in
            sheetXml(zoomBytes, 1)) { "zoom without a pane is a self-closing sheetView: ${sheetXml(zoomBytes, 1)}" }
        check("<sheetView workbookViewId=\"0\" zoomScale=\"75\"><pane ySplit=\"1\" topLeftCell=\"A2\" " +
            "activePane=\"bottomLeft\" state=\"frozen\"/></sheetView>" in sheetXml(zoomBytes, 2)) {
            "zoom and a frozen pane share one sheetView: ${sheetXml(zoomBytes, 2)}"
        }
        record(rows, "zoom", zoom)

        // --- a hidden sheet with no view option: the pre-R86 bookViews bytes are unchanged --------
        val hiddenOnly = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(name = "Shown", cells = listOf(txt("A1", "shown"))),
                XlsxStyledBook.StyledSheet(
                    name = "Hidden", cells = listOf(txt("A1", "hidden")), visibility = XlsxStyledBook.Visibility.HIDDEN,
                ),
            ),
        )
        val hiddenOnlyBytes = write(out, "hidden-only", hiddenOnly)
        check("<bookViews><workbookView activeTab=\"0\"/></bookViews>" in workbookXml(hiddenOnlyBytes)) {
            "hiding a sheet without view options keeps the R84 bookViews bytes: ${workbookXml(hiddenOnlyBytes)}"
        }
        record(rows, "hidden-only", hiddenOnly)

        // --- every view option at once, beside a hidden sheet ---------------------------------------
        val mixed = XlsxStyledBook.WorkbookSpec(
            listOf(
                XlsxStyledBook.StyledSheet(name = "Front", cells = listOf(txt("A1", "front")), tabRgb = "123456"),
                XlsxStyledBook.StyledSheet(
                    name = "Back", cells = listOf(txt("A1", "back"), txt("B2", "kept")),
                    visibility = XlsxStyledBook.Visibility.HIDDEN, tabRgb = "ABCDEF", zoomScale = 200,
                ),
            ),
            showSheetTabs = false,
            firstSheet = 0,
        )
        val mixedBytes = write(out, "view-mix", mixed)
        check("<workbookView showSheetTabs=\"0\" firstSheet=\"0\" activeTab=\"0\"/>" in workbookXml(mixedBytes)) {
            "three workbookView attributes coexist in a fixed order: ${workbookXml(mixedBytes)}"
        }
        check("<sheetPr><tabColor rgb=\"FFABCDEF\"/></sheetPr>" in sheetXml(mixedBytes, 2)) {
            "a hidden sheet keeps its tab colour"
        }
        check("state=\"hidden\"" in workbookXml(mixedBytes)) { "the hidden sheet keeps its state" }
        record(rows, "view-mix", mixed)

        // --- import: spellings a foreign package may use --------------------------------------------
        val patchedView = rezip(mixedBytes, "xl/workbook.xml") {
            it.replaceFirst("showSheetTabs=\"0\"", "showSheetTabs=\"false\"")
        }
        val patchedRead = XlsxStyledBook.read(patchedView)
        check(patchedRead.showSheetTabs == false) { "showSheetTabs=\"false\" imports as off" }
        check(patchedRead.firstSheet == 0) { "firstSheet survives the patched attribute" }

        val patchedState = rezip(mixedBytes, "xl/workbook.xml") {
            it.replaceFirst("state=\"hidden\"", "state=\"visible\"")
        }
        check(XlsxStyledBook.read(patchedState).sheets[1].visibility == XlsxStyledBook.Visibility.VISIBLE) {
            "an explicit state=\"visible\" imports as visible"
        }

        val badFirstSheet = rezip(mixedBytes, "xl/workbook.xml") {
            it.replaceFirst("firstSheet=\"0\"", "firstSheet=\"1\"")
        }
        val badFirstSheetRead = XlsxStyledBook.read(badFirstSheet)
        check(badFirstSheetRead.firstSheet == null) {
            "a firstSheet naming a hidden sheet is dropped on import: ${badFirstSheetRead.firstSheet}"
        }
        check(badFirstSheetRead.sheets.size == 2) { "dropping firstSheet keeps both sheets" }

        val themeColour = rezip(mixedBytes, "xl/worksheets/sheet2.xml") {
            it.replaceFirst("rgb=\"FFABCDEF\"", "theme=\"4\"")
        }
        check(XlsxStyledBook.read(themeColour).sheets[1].tabRgb == null) {
            "a theme tab colour is not guessed into an RGB value"
        }

        val wildZoom = rezip(mixedBytes, "xl/worksheets/sheet2.xml") {
            it.replaceFirst("zoomScale=\"200\"", "zoomScale=\"500\"")
        }
        check(XlsxStyledBook.read(wildZoom).sheets[1].zoomScale == null) {
            "a zoom outside 10..400 is dropped on import"
        }

        val withoutFirstSheet = rezip(mixedBytes, "xl/workbook.xml") {
            it.replaceFirst("firstSheet=\"0\" ", "")
        }
        check(XlsxStyledBook.read(withoutFirstSheet).firstSheet == null) {
            "a workbookView without firstSheet imports as unset"
        }

        // --- boundaries and refusals ---------------------------------------------------------------
        check(
            XlsxStyledBook.StyledSheet(name = "Grid", cells = emptyList(), zoomScale = 10).zoomScale == 10 &&
                XlsxStyledBook.StyledSheet(name = "Grid", cells = emptyList(), zoomScale = 400).zoomScale == 400,
        ) { "the 10 and 400 zoom limits are accepted" }
        fails("zoom below 10") { XlsxStyledBook.StyledSheet(name = "Bad", cells = emptyList(), zoomScale = 9) }
        fails("zoom above 400") { XlsxStyledBook.StyledSheet(name = "Bad", cells = emptyList(), zoomScale = 401) }
        fails("five-digit tab colour") {
            XlsxStyledBook.StyledSheet(name = "Bad", cells = emptyList(), tabRgb = "00B05")
        }
        fails("non-hex tab colour") { XlsxStyledBook.StyledSheet(name = "Bad", cells = emptyList(), tabRgb = "GGGGGG") }
        fails("ARGB tab colour") { XlsxStyledBook.StyledSheet(name = "Bad", cells = emptyList(), tabRgb = "FF00B050") }
        fails("firstSheet past the last sheet") {
            XlsxStyledBook.WorkbookSpec(
                listOf(
                    XlsxStyledBook.StyledSheet(name = "One", cells = emptyList()),
                    XlsxStyledBook.StyledSheet(name = "Two", cells = emptyList()),
                    XlsxStyledBook.StyledSheet(name = "Three", cells = emptyList()),
                ),
                firstSheet = 3,
            )
        }
        fails("negative firstSheet") {
            XlsxStyledBook.WorkbookSpec(listOf(XlsxStyledBook.StyledSheet(name = "One", cells = emptyList())), firstSheet = -1)
        }
        fails("firstSheet on a hidden sheet") {
            XlsxStyledBook.WorkbookSpec(
                listOf(
                    XlsxStyledBook.StyledSheet(name = "One", cells = emptyList()),
                    XlsxStyledBook.StyledSheet(
                        name = "Two", cells = emptyList(), visibility = XlsxStyledBook.Visibility.HIDDEN,
                    ),
                ),
                firstSheet = 1,
            )
        }
        check(
            XlsxStyledBook.WorkbookSpec(
                listOf(XlsxStyledBook.StyledSheet(name = "One", cells = emptyList())),
            ) == XlsxStyledBook.WorkbookSpec(
                listOf(XlsxStyledBook.StyledSheet(name = "One", cells = emptyList())), emptyList(), null, null,
            ),
        ) { "the default arguments keep the plain WorkbookSpec shape" }

        File(out, "views.tsv").writeText(rows.joinToString("\n") + "\n")
        println("R86_SHEET_VIEWS_OK assertions=$assertions workbooks=${out.listFiles { f -> f.name.endsWith(".xlsx") }?.size}")
    }
}
