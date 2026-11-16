// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.XlsxStyledBook
import dev.fusion.ooxml.XlsxValidation
import java.io.File

/**
 * R91 spreadsheet contracts: `CT_DataValidation` records (`<dataValidations>` after `<sheetData>`) and the
 * predicate each record describes.
 *
 * Host JVM only. The probe writes real `.xlsx` packages, an intent table (`validations.tsv`) and a decision
 * corpus (`validation_verdicts.tsv`). Independent witnesses: `tools/check_r91_sheet_validations.py`
 * (raw OPC + openpyxl's `DataValidation` model) and `tools/check_r91_validation_semantics.py`, which
 * recomputes every decision in Python with `decimal.Decimal` and compares. No Excel, LibreOffice or Android
 * run, and nothing is recalculated.
 */
object R91Checks {
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

    private fun cell(ref: String, value: String): XlsxStyledBook.StyledCell =
        XlsxStyledBook.StyledCell(ref, XlsxStyledBook.StyledVal.Txt(value))

    private val RULES = listOf(
        XlsxValidation.Rule(
            sqref = "B2:B10",
            type = XlsxValidation.Type.WHOLE,
            formula1 = "1",
            formula2 = "100",
            operator = XlsxValidation.Operator.BETWEEN,
            allowBlank = true,
            showErrorMessage = true,
            errorStyle = XlsxValidation.ErrorStyle.STOP,
            errorTitle = "Whole numbers only",
            error = "Enter a whole number from 1 to 100.",
            showInputMessage = true,
            promptTitle = "Quantity",
            prompt = "1..100",
        ),
        XlsxValidation.Rule(
            sqref = "C2:C10",
            type = XlsxValidation.Type.DECIMAL,
            formula1 = "0",
            operator = XlsxValidation.Operator.GREATER_THAN_OR_EQUAL,
            allowBlank = true,
            hideDropDown = true,
            showErrorMessage = true,
            errorStyle = XlsxValidation.ErrorStyle.WARNING,
            error = "Must be at least 0.",
        ),
        XlsxValidation.Rule(
            sqref = "D2:D10 E1",
            type = XlsxValidation.Type.LIST,
            formula1 = "\"red,green,blue\"",
            allowBlank = true,
            showErrorMessage = true,
            errorStyle = XlsxValidation.ErrorStyle.INFORMATION,
            error = "Pick a colour.",
            promptTitle = "Colour",
            showInputMessage = true,
        ),
        XlsxValidation.Rule(
            sqref = "F2:F10",
            type = XlsxValidation.Type.TEXT_LENGTH,
            formula1 = "10",
            formula2 = "20",
            operator = XlsxValidation.Operator.BETWEEN,
            showErrorMessage = true,
            error = "10 to 20 characters.",
        ),
        XlsxValidation.Rule(
            sqref = "G2:G10",
            type = XlsxValidation.Type.DATE,
            formula1 = "43831",
            formula2 = "44196",
            operator = XlsxValidation.Operator.BETWEEN,
            allowBlank = true,
            showErrorMessage = true,
            errorTitle = "Date window",
            error = "A date in 2020.",
        ),
        XlsxValidation.Rule(
            sqref = "H2:H10",
            type = XlsxValidation.Type.CUSTOM,
            formula1 = "MOD(H2,2)=0",
            showErrorMessage = true,
            errorStyle = XlsxValidation.ErrorStyle.STOP,
            error = "Even numbers only.",
        ),
    )

    /** Escaping corpus: every XML metacharacter inside an attribute and inside an element. */
    private val ESCAPE = XlsxValidation.Rule(
        sqref = "A1:A3",
        type = XlsxValidation.Type.LIST,
        formula1 = "\"a&b,c<d,e>f\"",
        showErrorMessage = true,
        errorTitle = "Q&A <checklist>",
        error = "\"quoted\" & <angled>",
        promptTitle = "a & b",
        prompt = "x < y > z",
        showInputMessage = true,
    )

    private fun verdict(rule: XlsxValidation.Rule, value: XlsxStyledBook.StyledVal): String =
        when (XlsxValidation.accepts(rule, value)) {
            null -> "undecided"
            true -> "true"
            false -> "false"
        }

    private val NUMBERS = listOf(
        "-2", "-1", "0", "0.5", "1", "2", "3", "3.5", "10", "20", "21", "100", "101", "43831", "44196",
        "1e6", "-0.0",
    )

    private val TEXTS = listOf(
        "", "a", "A", "b", "3", "3.0", "apple", "APPLE", "red", "Green", "blue", "hello world",
        "0123456789", "01234567890123456789", "\u00e9", "\uD834\uDD1E", " 3", "a&b", "c<d",
    )

    private fun tsvField(value: String?): String =
        when (value) {
            null -> "-"
            else -> value.replace("\t", "\\t").replace("\n", "\\n")
        }

    private fun writeIntent(folder: File, files: List<Triple<String, String, List<XlsxValidation.Rule>>>) {
        val lines = ArrayList<String>()
        for ((file, sheet, rules) in files) {
            rules.forEachIndexed { index, rule ->
                lines.add(
                    listOf(
                        file, sheet, index.toString(), rule.type.token,
                        if (rule.type == XlsxValidation.Type.LIST || rule.type == XlsxValidation.Type.CUSTOM) {
                            "-"
                        } else {
                            rule.operator.token
                        },
                        rule.formula1, tsvField(rule.formula2),
                        if (rule.allowBlank) "true" else "false",
                        if (rule.hideDropDown) "true" else "false",
                        if (rule.showErrorMessage) "true" else "false",
                        rule.errorStyle.token, tsvField(rule.errorTitle), tsvField(rule.error),
                        if (rule.showInputMessage) "true" else "false",
                        tsvField(rule.promptTitle), tsvField(rule.prompt), rule.sqref,
                    ).joinToString("\t"),
                )
            }
        }
        File(folder, "validations.tsv").writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }

    private fun writeVerdicts(folder: File) {
        val lines = ArrayList<String>()
        RULES.forEachIndexed { index, rule ->
            val id = "R${index + 1}"
            val op = if (rule.type == XlsxValidation.Type.LIST || rule.type == XlsxValidation.Type.CUSTOM) {
                "-"
            } else {
                rule.operator.token
            }
            for (number in NUMBERS) {
                val value = XlsxStyledBook.StyledVal.Num(number.toDouble())
                lines.add(listOf(id, rule.type.token, op, rule.formula1, tsvField(rule.formula2), "num", number,
                    verdict(rule, value)).joinToString("\t"))
            }
            for (text in TEXTS) {
                val value = XlsxStyledBook.StyledVal.Txt(text)
                lines.add(listOf(id, rule.type.token, op, rule.formula1, tsvField(rule.formula2), "txt",
                    tsvField(text), verdict(rule, value)).joinToString("\t"))
            }
            for (flag in listOf(false, true)) {
                lines.add(listOf(id, rule.type.token, op, rule.formula1, tsvField(rule.formula2), "bool",
                    flag.toString(), verdict(rule, XlsxStyledBook.StyledVal.Bool(flag))).joinToString("\t"))
            }
        }
        File(folder, "validation_verdicts.tsv").writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
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
        val folder = File(args.firstOrNull() ?: "testing/build/r91/artifacts")
        folder.mkdirs()

        // 1. A sheet without validations must be byte-identical in structure to the pre-R91 output.
        val plainSpec = XlsxStyledBook.WorkbookSpec(
            sheets = listOf(XlsxStyledBook.StyledSheet("Plain", listOf(cell("A1", "no rules")))),
        )
        val plain = XlsxStyledBook.write(plainSpec)
        File(folder, "dv-plain.xlsx").writeBytes(plain)
        val plainSheet = part(plain as ByteArray, "xl/worksheets/sheet1.xml")
        check(!plainSheet.contains("dataValidation")) { "an unvalidated sheet emitted dataValidations" }
        check(plainSheet.endsWith("</sheetData></worksheet>")) { "sheet tail changed: $plainSheet" }

        // 2. The rule workbook.
        val rulesSpec = XlsxStyledBook.WorkbookSpec(
            sheets = listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Rules",
                    cells = listOf(cell("A1", "R91"), cell("B2", "5")),
                    validations = RULES,
                ),
            ),
        )
        val rulesBytes = XlsxStyledBook.write(rulesSpec)
        File(folder, "dv.xlsx").writeBytes(rulesBytes)
        val rulesSheet = part(rulesBytes, "xl/worksheets/sheet1.xml")
        check(rulesSheet.contains("""<dataValidations count="6">""")) { "wrong dataValidations count" }
        check(rulesSheet.indexOf("<dataValidations") > rulesSheet.indexOf("</sheetData>")) {
            "dataValidations must follow sheetData"
        }
        check(rulesSheet.indexOf("</dataValidations>") < rulesSheet.indexOf("</worksheet>")) {
            "dataValidations must close before the worksheet does"
        }
        check(rulesSheet.contains("""showDropDown="1"""")) { "the inverted dropdown flag was not written" }
        check(rulesSheet.contains("""<formula1>43831</formula1><formula2>44196</formula2>""")) {
            "the date rule lost an operand"
        }
        check(!rulesSheet.contains("""type="list" errorStyle="information" operator="""")) {
            "list must not carry an operator attribute"
        }
        check(!rulesSheet.contains("""type="custom" errorStyle="stop" operator=""")) {
            "custom must not carry an operator attribute"
        }

        // 3. Escaping: metacharacters must survive as characters, never as markup.
        val escapeSpec = XlsxStyledBook.WorkbookSpec(
            sheets = listOf(XlsxStyledBook.StyledSheet("Esc", listOf(cell("B1", "x")), validations = listOf(ESCAPE))),
        )
        val escapeBytes = XlsxStyledBook.write(escapeSpec)
        File(folder, "dv-escaping.xlsx").writeBytes(escapeBytes)
        val escapeSheet = part(escapeBytes, "xl/worksheets/sheet1.xml")
        check(escapeSheet.contains("""errorTitle="Q&amp;A &lt;checklist&gt;"""")) { "errorTitle not escaped" }
        check(escapeSheet.contains("""error="&quot;quoted&quot; &amp; &lt;angled&gt;"""")) { "error not escaped" }
        // Quotes are escaped in element content too: one escaper for attributes and text keeps the writer
        // total, at the cost of a `&quot;` that the schema would not have required.
        check(escapeSheet.contains("""<formula1>&quot;a&amp;b,c&lt;d,e&gt;f&quot;</formula1>""")) { "formula1 not escaped" }

        // 4. Read-back: the same rules, attribute for attribute.
        val readBack = XlsxStyledBook.read(rulesBytes)
        check(readBack.sheets.size == 1) { "read back ${readBack.sheets.size} sheets" }
        val readRules = readBack.sheets[0].validations
        check(readRules == RULES) { "round-trip changed the rules:\n$readRules\n!=\n$RULES" }
        val escapeBack = XlsxStyledBook.read(escapeBytes).sheets[0].validations
        check(escapeBack == listOf(ESCAPE)) { "escaping round-trip changed the rule: $escapeBack" }
        val plainBack = XlsxStyledBook.read(plain).sheets[0].validations
        check(plainBack.isEmpty()) { "an unvalidated sheet read back ${plainBack.size} rules" }

        // 5. Refusals: each of these would emit a rule that means something other than it says.
        refuses("list with an operator") {
            XlsxValidation.Rule("A1", XlsxValidation.Type.LIST, "\"a,b\"", XlsxValidation.Operator.LESS_THAN)
        }
        refuses("between with one operand") {
            XlsxValidation.Rule("A1", XlsxValidation.Type.WHOLE, "1", XlsxValidation.Operator.BETWEEN)
        }
        refuses("equal with two operands") {
            XlsxValidation.Rule("A1", XlsxValidation.Type.WHOLE, "1", XlsxValidation.Operator.EQUAL, "2")
        }
        refuses("lower-case sqref") { XlsxValidation.Rule("a1", XlsxValidation.Type.WHOLE, "1", XlsxValidation.Operator.EQUAL) }
        refuses("sqref with a row 0") { XlsxValidation.Rule("A0", XlsxValidation.Type.WHOLE, "1", XlsxValidation.Operator.EQUAL) }
        refuses("blank formula1") { XlsxValidation.Rule("A1", XlsxValidation.Type.WHOLE, " ", XlsxValidation.Operator.EQUAL) }
        refuses("unterminated list literal") { XlsxValidation.Rule("A1", XlsxValidation.Type.LIST, "\"a,b") }
        refuses("custom with an operator") {
            XlsxValidation.Rule("A1", XlsxValidation.Type.CUSTOM, "A1>0", XlsxValidation.Operator.GREATER_THAN)
        }

        // 6. Predicate spot-checks with values whose verdict is fixed by the documented semantics.
        val whole = RULES[0]
        check(verdict(whole, XlsxStyledBook.StyledVal.Num(50.0)) == "true") { "50 refused by 1..100" }
        check(verdict(whole, XlsxStyledBook.StyledVal.Num(101.0)) == "false") { "101 accepted by 1..100" }
        check(verdict(whole, XlsxStyledBook.StyledVal.Num(3.5)) == "false") { "3.5 accepted by a whole rule" }
        check(verdict(whole, XlsxStyledBook.StyledVal.Txt("50")) == "undecided") { "text decided by a numeric rule" }
        val list = RULES[2]
        check(verdict(list, XlsxStyledBook.StyledVal.Txt("GREEN")) == "true") { "list matching is not case-insensitive" }
        check(verdict(list, XlsxStyledBook.StyledVal.Txt("purple")) == "false") { "purple accepted by red,green,blue" }
        check(verdict(list, XlsxStyledBook.StyledVal.Num(3.0)) == "false") { "3 accepted by a colour list" }
        val custom = RULES[5]
        check(verdict(custom, XlsxStyledBook.StyledVal.Num(2.0)) == "undecided") { "custom rule decided" }
        val ranged = XlsxValidation.Rule("A1", XlsxValidation.Type.LIST, "Config!A1:A3")
        check(verdict(ranged, XlsxStyledBook.StyledVal.Txt("a")) == "undecided") { "range list decided" }
        val dateFunc = XlsxValidation.Rule("A1", XlsxValidation.Type.DATE, "DATE(2020,1,1)", XlsxValidation.Operator.EQUAL)
        check(verdict(dateFunc, XlsxStyledBook.StyledVal.Num(43831.0)) == "undecided") { "DATE() operand decided" }

        // 7. Artifacts for the independent readers.
        writeIntent(
            folder,
            listOf(
                Triple("dv.xlsx", "Rules", RULES),
                Triple("dv-escaping.xlsx", "Esc", listOf(ESCAPE)),
            ),
        )
        writeVerdicts(folder)

        val verdictRows = File(folder, "validation_verdicts.tsv").readLines().count { it.isNotBlank() }
        check(verdictRows == RULES.size * (NUMBERS.size + TEXTS.size + 2)) { "verdict corpus is $verdictRows rows" }

        println("R91_OFFICE_OK assertions=$assertions workbooks=3 rules=${RULES.size + 1} verdicts=$verdictRows")
    }
}
