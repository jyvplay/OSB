// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.BoundedXml
import dev.fusion.ooxml.XlsxAutoFilter
import dev.fusion.ooxml.XlsxProtection
import dev.fusion.ooxml.XlsxStyledBook
import dev.fusion.ooxml.XlsxValidation
import java.io.File

/**
 * R93 spreadsheet contracts: the `<autoFilter>` record (`filterColumn`, `<filters>`, `<customFilters>`) and the
 * row predicate each column filter describes.
 *
 * Host JVM only. The probe writes real `.xlsx` packages, an intent table (`autofilters.tsv`) and a decision
 * corpus (`filter_verdicts.tsv`). Independent witnesses: `tools/check_r93_sheet_autofilter.py` (raw OPC +
 * openpyxl's `AutoFilter`/`FilterColumn`/`Filters`/`CustomFilters` models) and
 * `tools/check_r93_filter_semantics.py`, which recomputes every decision in Python. No Excel, LibreOffice or
 * Android run: nothing here says a row was hidden.
 */
object R93Checks {
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

    private fun cell(ref: String, value: String) =
        XlsxStyledBook.StyledCell(ref, XlsxStyledBook.StyledVal.Txt(value))

    private val VALUES_COLUMN = XlsxAutoFilter.Column(colId = 0, values = listOf("red", "green"))
    private val RANGE_COLUMN = XlsxAutoFilter.Column(
        colId = 1,
        custom = listOf(
            XlsxAutoFilter.Custom(XlsxAutoFilter.Operator.GREATER_THAN, "3"),
            XlsxAutoFilter.Custom(XlsxAutoFilter.Operator.LESS_THAN, "9"),
        ),
        join = true,
    )
    private val WILDCARD_COLUMN = XlsxAutoFilter.Column(
        colId = 2,
        custom = listOf(XlsxAutoFilter.Custom(XlsxAutoFilter.Operator.EQUAL, "x*")),
    )
    private val BUTTON_COLUMN = XlsxAutoFilter.Column(
        colId = 3,
        values = listOf("a&b", "c<d"),
        hiddenButton = true,
        showButton = false,
    )
    /** A real table plus the filter a user would apply to it, evaluated into row visibility. */
    private val APPLIED_FILTER = XlsxAutoFilter.AutoFilter(
        ref = "A1:B8",
        columns = listOf(
            XlsxAutoFilter.Column(0, values = listOf("red")),
            XlsxAutoFilter.Column(
                colId = 1,
                custom = listOf(
                    XlsxAutoFilter.Custom(XlsxAutoFilter.Operator.GREATER_THAN, "3"),
                    XlsxAutoFilter.Custom(XlsxAutoFilter.Operator.LESS_THAN, "9"),
                ),
                join = true,
            ),
        ),
    )
    private val APPLIED_ROWS = listOf(
        listOf("colour", "size"),
        listOf("red", "5"),
        listOf("blue", "5"),
        listOf("red", "2"),
        listOf("red", "ten"),
        listOf("green", "5"),
        listOf("red", ""),
        listOf("red", "9"),
    )
    private val FILTER = XlsxAutoFilter.AutoFilter(
        ref = "A1:D10",
        columns = listOf(VALUES_COLUMN, RANGE_COLUMN, WILDCARD_COLUMN, BUTTON_COLUMN),
    )
    private val RANGE_ONLY = XlsxAutoFilter.AutoFilter(ref = "A1:C5")
    private val OR_COLUMN = XlsxAutoFilter.Column(
        colId = 1,
        custom = listOf(
            XlsxAutoFilter.Custom(XlsxAutoFilter.Operator.EQUAL, "1"),
            XlsxAutoFilter.Custom(XlsxAutoFilter.Operator.EQUAL, "2"),
        ),
        join = false,
    )
    private val OR_FILTER = XlsxAutoFilter.AutoFilter(ref = "B2:D8", columns = listOf(OR_COLUMN))
    private val LOCKED_FILTER = XlsxAutoFilter.AutoFilter(
        ref = "A1:B6",
        columns = listOf(XlsxAutoFilter.Column(1, values = listOf("7"))),
    )

    private fun tsvField(value: String?): String =
        when (value) {
            null -> "-"
            else -> value.replace("\t", "\\t").replace("\n", "\\n")
        }

    private fun writeIntent(folder: File, entries: List<Triple<String, String, XlsxAutoFilter.AutoFilter?>>) {
        val lines = ArrayList<String>()
        for ((file, sheet, filter) in entries) {
            if (filter == null) {
                lines.add(listOf(file, sheet, "-", "-", "none", "-", "-", "-", "-").joinToString("\t"))
                continue
            }
            if (filter.columns.isEmpty()) {
                lines.add(listOf(file, sheet, filter.ref, "-", "none", "-", "-", "-", "-").joinToString("\t"))
                continue
            }
            for (column in filter.columns) {
                val values = column.values
                val custom = column.custom
                val kind: String
                val payload: String
                if (values != null) {
                    kind = "values"
                    payload = values.joinToString("\u001f")
                } else {
                    kind = "custom"
                    payload = (custom ?: emptyList()).joinToString("\u001f") { "${it.operator.token}=${it.value}" }
                }
                val join = when {
                    custom == null || custom.size == 1 -> "-"
                    column.join -> "and"
                    else -> "or"
                }
                lines.add(
                    listOf(
                        file, sheet, filter.ref, column.colId.toString(), kind, payload, join,
                        if (column.hiddenButton) "true" else "false",
                        if (column.showButton) "true" else "false",
                    ).joinToString("\t"),
                )
            }
        }
        File(folder, "autofilters.tsv").writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }

    private val NUMBERS = listOf("-5", "0", "1", "2", "3", "3.5", "4", "8", "9", "10", "100", "-0.0", "1e3")
    private val TEXTS = listOf(
        "", "a", "A", "b", "red", "RED", "green", "blue", "x", "xy", "xyz", "x1", "X*", "a&b", "c<d",
        "3", "3.0", "hello world", "\u00e9", "\uD834\uDD1E", " 3",
    )

    private fun verdict(column: XlsxAutoFilter.Column, value: XlsxStyledBook.StyledVal?): String =
        when (XlsxAutoFilter.matches(column, value)) {
            null -> "undecided"
            true -> "true"
            false -> "false"
        }

    private fun writeVerdicts(folder: File) {
        val rules = listOf(
            VALUES_COLUMN, RANGE_COLUMN, WILDCARD_COLUMN,
            XlsxAutoFilter.Column(colId = 0, values = listOf("a*")),
            XlsxAutoFilter.Column(
                colId = 0,
                custom = listOf(
                    XlsxAutoFilter.Custom(XlsxAutoFilter.Operator.EQUAL, "3"),
                    XlsxAutoFilter.Custom(XlsxAutoFilter.Operator.EQUAL, "9"),
                ),
                join = false,
            ),
            XlsxAutoFilter.Column(colId = 0, custom = listOf(XlsxAutoFilter.Custom(XlsxAutoFilter.Operator.NOT_EQUAL, "red"))),
            XlsxAutoFilter.Column(colId = 0, custom = listOf(XlsxAutoFilter.Custom(XlsxAutoFilter.Operator.GREATER_THAN, "5"))),
        )
        val lines = ArrayList<String>()
        rules.forEachIndexed { index, column ->
            val values = column.values
            val custom = column.custom
            val id = "C${index + 1}"
            val kind = if (values != null) "values" else "custom"
            val payload = if (values != null) {
                values.joinToString("\u001f")
            } else {
                (custom ?: emptyList()).joinToString("\u001f") { "${it.operator.token}=${it.value}" }
            }
            val join = when {
                custom == null || custom.size == 1 -> "-"
                column.join -> "and"
                else -> "or"
            }
            for (number in NUMBERS) {
                val value = XlsxStyledBook.StyledVal.Num(number.toDouble())
                lines.add(listOf(id, kind, payload, join, "num", number, verdict(column, value)).joinToString("\t"))
            }
            for (text in TEXTS) {
                val value = XlsxStyledBook.StyledVal.Txt(text)
                lines.add(listOf(id, kind, payload, join, "txt", tsvField(text), verdict(column, value)).joinToString("\t"))
            }
            for (flag in listOf(false, true)) {
                lines.add(listOf(id, kind, payload, join, "bool", flag.toString(),
                    verdict(column, XlsxStyledBook.StyledVal.Bool(flag))).joinToString("\t"))
            }
            lines.add(listOf(id, kind, payload, join, "blank", "-", verdict(column, null)).joinToString("\t"))
        }
        File(folder, "filter_verdicts.tsv").writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
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
        val folder = File(args.firstOrNull() ?: "testing/build/r93/artifacts")
        folder.mkdirs()

        // 1. No filter: the sheet part stays structurally identical to R92.
        val plain = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(
                sheets = listOf(XlsxStyledBook.StyledSheet("Plain", listOf(cell("A1", "open")))),
            ),
        )
        File(folder, "af-plain.xlsx").writeBytes(plain)
        val plainSheet = part(plain, "xl/worksheets/sheet1.xml")
        check(!plainSheet.contains("autoFilter")) { "an unfiltered sheet emitted autoFilter" }
        check(plainSheet.endsWith("</sheetData></worksheet>")) { "sheet tail changed: $plainSheet" }

        // 2. The filtered sheet.
        val filtered = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(
                sheets = listOf(
                    XlsxStyledBook.StyledSheet(
                        name = "Rows",
                        cells = listOf(cell("A1", "colour"), cell("A2", "red")),
                        autoFilter = FILTER,
                    ),
                    XlsxStyledBook.StyledSheet(
                        name = "Or",
                        cells = listOf(cell("B2", "1")),
                        autoFilter = OR_FILTER,
                    ),
                    // The whole tail: sheetProtection, then autoFilter, then dataValidations.
                    XlsxStyledBook.StyledSheet(
                        name = "Locked",
                        cells = listOf(cell("A1", "7")),
                        protection = XlsxProtection.SheetProtection.ofPassword("secret"),
                        autoFilter = LOCKED_FILTER,
                        validations = listOf(
                            XlsxValidation.Rule(
                                sqref = "A1:A6",
                                type = XlsxValidation.Type.WHOLE,
                                operator = XlsxValidation.Operator.BETWEEN,
                                formula1 = "1",
                                formula2 = "9",
                            ),
                        ),
                    ),
                ),
            ),
        )
        File(folder, "af.xlsx").writeBytes(filtered)
        val sheet = part(filtered, "xl/worksheets/sheet1.xml")
        check(sheet.contains("""<autoFilter ref="A1:D10">""")) { "autoFilter ref missing: $sheet" }
        check(sheet.indexOf("<autoFilter") > sheet.indexOf("</sheetData>")) { "autoFilter must follow sheetData" }
        check(sheet.indexOf("</autoFilter>") < sheet.indexOf("</worksheet>")) { "autoFilter must close inside sheet" }
        check(sheet.contains("""<filters><filter val="red"/><filter val="green"/></filters>""")) {
            "the value list is wrong"
        }
        check(sheet.contains("""<customFilters and="1"><customFilter operator="greaterThan" val="3"/>""")) {
            "the AND-joined criteria are wrong"
        }
        check(sheet.contains("""<customFilters><customFilter operator="equal" val="x*"/></customFilters>""")) {
            "a single criterion must not carry an and attribute"
        }
        val lockedSheet = part(filtered, "xl/worksheets/sheet3.xml")
        val protectionAt = lockedSheet.indexOf("<sheetProtection")
        val filterAt = lockedSheet.indexOf("<autoFilter")
        val validationsAt = lockedSheet.indexOf("<dataValidations")
        check(protectionAt in 0 until filterAt && filterAt < validationsAt) {
            "CT_Worksheet tail order broken: $lockedSheet"
        }
        check(lockedSheet.indexOf("</sheetData>") < protectionAt) { "sheetProtection before sheetData" }
        val orSheet = part(filtered, "xl/worksheets/sheet2.xml")
        check(orSheet.contains("""<customFilters><customFilter operator="equal" val="1"/>""")) {
            "an OR join is the schema default and must not be written: $orSheet"
        }
        check(!orSheet.contains("and=")) { "the schema default join was written explicitly: $orSheet" }
        check(sheet.contains("""<filterColumn colId="3" hiddenButton="1" showButton="0">""")) {
            "the button flags are wrong"
        }
        check(!sheet.contains("""colId="0" hiddenButton""")) { "a default button flag was written" }
        check(sheet.contains("""<filter val="a&amp;b"/>""")) { "a filter value was not escaped" }

        // 3. A filter with no columns: the range alone, written as an empty element.
        val rangeOnly = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(
                sheets = listOf(XlsxStyledBook.StyledSheet("Bare", listOf(cell("A1", "x")), autoFilter = RANGE_ONLY)),
            ),
        )
        File(folder, "af-range.xlsx").writeBytes(rangeOnly)
        check(part(rangeOnly, "xl/worksheets/sheet1.xml").contains("""<autoFilter ref="A1:C5"/>""")) {
            "a column-less filter must be an empty element"
        }

        // 4. Round-trip through this code's own reader.
        val back = XlsxStyledBook.read(filtered)
        check(back.sheets[0].autoFilter == FILTER) {
            "autoFilter round-trip changed the record: ${back.sheets[0].autoFilter}"
        }
        check(back.sheets[1].autoFilter == OR_FILTER) { "the OR sheet round-trip changed: ${back.sheets[1].autoFilter}" }
        check(back.sheets[2].autoFilter == LOCKED_FILTER) { "the locked sheet lost its filter" }
        check(back.sheets[2].protection?.passwordHash == XlsxProtection.hash("secret")) { "the locked sheet lost protection" }
        check(back.sheets[2].validations.size == 1) { "the locked sheet lost its validation" }
        check(XlsxStyledBook.read(plain).sheets[0].autoFilter == null) { "an unfiltered sheet read back filtered" }
        check(XlsxStyledBook.read(rangeOnly).sheets[0].autoFilter == RANGE_ONLY) { "column-less round-trip" }

        // 4b. Reading: a column whose criterion kind this model does not implement is dropped, not misread.
        val synthetic = XlsxAutoFilter.parse(
            BoundedXml.parse(
                ("""<worksheet><autoFilter ref="A1:D4">""" +
                    """<filterColumn colId="0"><filters blank="1"><filter val="x"/></filters></filterColumn>""" +
                    """<filterColumn colId="1"><filters><dateGroupItem year="2020" dateTimeGrouping="year"/></filters></filterColumn>""" +
                    """<filterColumn colId="2"><top10 val="5"/></filterColumn>""" +
                    """<filterColumn colId="3"><filters><filter val="z"/></filters></filterColumn>""" +
                    "</autoFilter></worksheet>").toByteArray(Charsets.UTF_8),
            ),
        )
        check(synthetic != null && synthetic.ref == "A1:D4") { "the synthetic filter did not parse: $synthetic" }
        check(synthetic?.columns?.map { it.colId } == listOf(3)) {
            "unmodelled criteria were not dropped cleanly: ${synthetic?.columns}"
        }
        check(synthetic?.columns?.single()?.values == listOf("z")) { "the modelled column was misread" }

        // 5. Refusals: each would write an element Excel treats as corrupt.
        refuses("values and custom together") {
            XlsxAutoFilter.Column(0, values = listOf("a"),
                custom = listOf(XlsxAutoFilter.Custom(XlsxAutoFilter.Operator.EQUAL, "a")))
        }
        refuses("neither values nor custom") { XlsxAutoFilter.Column(0) }
        refuses("three custom criteria") {
            XlsxAutoFilter.Column(0, custom = List(3) { XlsxAutoFilter.Custom(XlsxAutoFilter.Operator.EQUAL, "a") })
        }
        refuses("an AND join on a single criterion") {
            XlsxAutoFilter.Column(0, custom = listOf(XlsxAutoFilter.Custom(XlsxAutoFilter.Operator.EQUAL, "a")), join = true)
        }
        refuses("an empty value list") { XlsxAutoFilter.Column(0, values = emptyList()) }
        refuses("an empty custom operand") {
            XlsxAutoFilter.Column(0, custom = listOf(XlsxAutoFilter.Custom(XlsxAutoFilter.Operator.EQUAL, "")))
        }
        refuses("a negative colId") { XlsxAutoFilter.Column(-1, values = listOf("a")) }
        refuses("a duplicate colId") {
            XlsxAutoFilter.AutoFilter("A1:C5", listOf(
                XlsxAutoFilter.Column(0, values = listOf("a")),
                XlsxAutoFilter.Column(0, values = listOf("b")),
            ))
        }
        refuses("a colId outside the range") {
            XlsxAutoFilter.AutoFilter("A1:C5", listOf(XlsxAutoFilter.Column(3, values = listOf("a"))))
        }
        refuses("a lower-case ref") { XlsxAutoFilter.AutoFilter("a1:c5") }
        refuses("a ref with a row 0") { XlsxAutoFilter.AutoFilter("A0:C5") }

        // 6. Predicate spot-checks whose verdicts are fixed by the documented semantics.
        check(verdict(VALUES_COLUMN, XlsxStyledBook.StyledVal.Txt("RED")) == "true") { "value matching is case-sensitive" }
        check(verdict(VALUES_COLUMN, XlsxStyledBook.StyledVal.Txt("blue")) == "false") { "blue accepted by red,green" }
        check(verdict(RANGE_COLUMN, XlsxStyledBook.StyledVal.Num(5.0)) == "true") { "5 not inside 3..9" }
        check(verdict(RANGE_COLUMN, XlsxStyledBook.StyledVal.Num(9.0)) == "false") { "9 accepted by <9" }
        check(verdict(WILDCARD_COLUMN, XlsxStyledBook.StyledVal.Txt("XY")) == "true") { "wildcard x* refused xy" }
        check(verdict(WILDCARD_COLUMN, XlsxStyledBook.StyledVal.Txt("y")) == "false") { "wildcard x* accepted y" }
        check(verdict(VALUES_COLUMN, null) == "undecided") { "a blank cell was decided" }
        check(verdict(VALUES_COLUMN, XlsxStyledBook.StyledVal.Bool(true)) == "undecided") { "a boolean cell was decided" }
        val orderingOnText = XlsxAutoFilter.Column(
            colId = 0,
            custom = listOf(XlsxAutoFilter.Custom(XlsxAutoFilter.Operator.GREATER_THAN, "5")),
        )
        check(verdict(orderingOnText, XlsxStyledBook.StyledVal.Txt("abc")) == "undecided") {
            "a text ordering comparison was decided"
        }
        check(verdict(orderingOnText, XlsxStyledBook.StyledVal.Num(6.0)) == "true") { "6 not greater than 5" }

        // 6b. Applying a filter: the hidden-row set is computed from the predicate, never guessed.
        val appliedCells = ArrayList<XlsxStyledBook.StyledCell>()
        APPLIED_ROWS.forEachIndexed { index, row ->
            val rowNumber = index + 1
            row.forEachIndexed { column, value ->
                if (value.isEmpty()) return@forEachIndexed
                // A table read as cells: what parses as a number is stored as a number, the rest as text.
                val stored = value.toDoubleOrNull()
                    ?.let { XlsxStyledBook.StyledVal.Num(it) }
                    ?: XlsxStyledBook.StyledVal.Txt(value)
                appliedCells.add(XlsxStyledBook.StyledCell("${('A'.code + column).toChar()}$rowNumber", stored))
            }
        }
        val outcome = XlsxAutoFilter.evaluate(APPLIED_FILTER, appliedCells)
        check(outcome.hidden == setOf(3, 4, 6, 8)) { "the excluded rows are ${outcome.hidden}" }
        check(outcome.undecided == setOf(5, 7)) { "the undecided rows are ${outcome.undecided}" }
        val applied = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(
                sheets = listOf(
                    XlsxStyledBook.StyledSheet(
                        name = "Applied",
                        cells = appliedCells,
                        hiddenRows = outcome.hidden,
                        autoFilter = APPLIED_FILTER,
                    ),
                ),
            ),
        )
        File(folder, "af-applied.xlsx").writeBytes(applied)
        val appliedSheet = part(applied, "xl/worksheets/sheet1.xml")
        for (row in outcome.hidden) {
            check(appliedSheet.contains("<row r=\"$row\" hidden=\"1\">")) { "row $row is not hidden" }
        }
        for (row in outcome.undecided) {
            check(!appliedSheet.contains("<row r=\"$row\" hidden=\"1\"")) {
                "row $row was hidden although its verdict is undecided"
            }
        }
        check(!appliedSheet.contains("<row r=\"2\" hidden=\"1\">")) { "the header row was hidden" }
        check(appliedSheet.contains("<autoFilter ref=\"A1:B8\">")) { "the applied workbook lost its filter" }
        // The reader must not lose the two things that make the filter visible: the record and the row flags.
        val appliedBack = XlsxStyledBook.read(applied)
        check(appliedBack.sheets[0].autoFilter == APPLIED_FILTER) { "the applied filter did not round-trip" }
        check(appliedBack.sheets[0].hiddenRows == outcome.hidden) { "the hidden rows did not round-trip" }
        // Re-writing the model reproduces the same worksheet part: no drift on a read/write cycle.
        val rewritten = part(XlsxStyledBook.write(appliedBack), "xl/worksheets/sheet1.xml")
        check(rewritten == appliedSheet) { "the applied sheet changed on a read/write cycle" }

        // 7. Artifacts for the independent readers.
        writeIntent(
            folder,
            listOf(
                Triple("af.xlsx", "Rows", FILTER),
                Triple("af.xlsx", "Or", OR_FILTER),
                Triple("af.xlsx", "Locked", LOCKED_FILTER),
                Triple("af-applied.xlsx", "Applied", APPLIED_FILTER),
                Triple("af-range.xlsx", "Bare", RANGE_ONLY),
                Triple("af-plain.xlsx", "Plain", null),
            ),
        )
        writeVerdicts(folder)
        // The typed table travels with the intent so a second implementation can re-derive the row set.
        val encodedTable = APPLIED_ROWS.joinToString("\u001e") { row ->
            row.joinToString("\u001f") { value ->
                when {
                    value.isEmpty() -> ""
                    value.toDoubleOrNull() != null -> "n$value"
                    else -> "t$value"
                }
            }
        }
        val appliedIntent = listOf(
            "af-applied.xlsx",
            "Applied",
            outcome.hidden.joinToString(","),
            outcome.undecided.joinToString(","),
            APPLIED_ROWS.indices.joinToString(",") { (it + 1).toString() },
            encodedTable,
        ).joinToString("\t")
        File(folder, "applied_filters.tsv").writeText(appliedIntent + "\n", Charsets.UTF_8)
        val verdictRows = File(folder, "filter_verdicts.tsv").readLines().count { it.isNotBlank() }
        check(verdictRows == 7 * (NUMBERS.size + TEXTS.size + 2 + 1)) { "verdict corpus is $verdictRows rows" }

        println(
            "R93_OFFICE_OK assertions=$assertions workbooks=4 filters=3 verdicts=$verdictRows " +
                "hidden=${outcome.hidden.size} undecided=${outcome.undecided.size}",
        )
    }
}
