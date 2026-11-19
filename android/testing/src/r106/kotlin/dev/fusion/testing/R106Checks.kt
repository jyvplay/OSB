// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.SheetRange
import dev.fusion.ooxml.XlsxConditionalFormatVerdict
import dev.fusion.ooxml.XlsxConditionalFormatVerdict.Outcome
import dev.fusion.ooxml.XlsxConditionalFormatVerdict.Value
import dev.fusion.ooxml.XlsxConditionalFormatting.Block
import dev.fusion.ooxml.XlsxConditionalFormatting.Format
import dev.fusion.ooxml.XlsxConditionalFormatting.Kind
import dev.fusion.ooxml.XlsxConditionalFormatting.Rule
import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R106 spreadsheet contracts: the criteria functions — `COUNTIF`, `SUMIF`, `AVERAGEIF` — and the criterion grammar.
 *
 * R105 could sum a range but not *select* cells from one, so `COUNTIF(A1:A9,">5")=3` and `SUMIF(B1:B9,"draft")>100`
 * — the two shapes a real workbook uses most after plain `SUM` — were refused by name. R106 adds three functions
 * whose second argument is a **criterion**: a plain number or boolean, a comparison written as text (`">5"`,
 * `"<=0"`, `"<>draft"`, `"="`, `"<>"`), or a wildcard pattern (`"a*"`, `"?p*"`, with `~` escaping `*`, `?` and
 * itself). Text matching in a criterion is case-insensitive.
 *
 * Four things are asserted, each by a mechanism that can fail on its own:
 *
 * 1. **The cases.** [CASES] is a hand-written table of `formula -> answer at a cell` over a grid that contains
 *    numbers, negative numbers, a zero, text, numeric-looking text, an empty-text cell, booleans, a blank and an
 *    error, so the criteria rules are exercised on every type the model knows, not on numbers alone.
 * 2. **The refusals.** A criterion that would need a collator, an error under a criterion, a range as the criterion,
 *    a non-range first argument, a sum range whose shape does not match, a criterion above Excel's 255-character
 *    bound, and the arities.
 * 3. **The file.** Twelve criteria rules are written to `r106.xlsx`, read back out of the ZIP, and read again through
 *    `XlsxStyledBook.read`.
 * 4. **The independent lane.** `tools/check_r106_criteria.py` re-tokenizes, re-parses and re-evaluates every case
 *    with its own Python implementation of the criterion grammar, and `tools/test_r106_criteria.py` tests that
 *    implementation.
 *
 * Host JVM only. No Excel, LibreOffice or device ran: a verdict is a claim about which rule fires, never about what
 * a screen shows.
 */
object R106Checks {
    private var assertions = 0
    private var refusals = 0

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

    private fun refuses(name: String, block: () -> Unit) {
        assertions++
        refusals++
        val threw = try {
            block()
            false
        } catch (expected: IllegalArgumentException) {
            check(expected.message?.isNotBlank() == true) { "$name refused without a message" }
            true
        }
        require(threw) { "FAILED: $name was accepted" }
    }

    private val FILL = Format(fillRgb = "FFC7CE")

    // ---------------------------------------------------------------- the grid

    private fun put(builder: MutableMap<SheetRange.Cell, Value>, ref: String, value: Value) {
        builder[SheetRange.parseCell(ref)] = value
    }

    /**
     * A sheet built to break a criterion implementation: numbers with a negative and a zero, a text cell among the
     * numbers, a numeric-looking text (`" 4 "`), an empty-text cell that is *not* blank, booleans, an absent cell,
     * wildcard-shaped text, and an error.
     */
    private val VALUES: Map<SheetRange.Cell, Value> = buildMap {
        put(this, "A1", Value.Num(1.0))
        put(this, "A2", Value.Num(2.0))
        put(this, "A3", Value.Num(3.0))
        put(this, "A4", Value.Txt("x"))
        put(this, "A6", Value.Bool(true))
        put(this, "A7", Value.Num(0.0))
        put(this, "A8", Value.Num(-4.0))
        put(this, "A9", Value.Txt(" 4 "))
        for (row in 1..5) put(this, "B$row", Value.Num(row * 10.0))
        for (row in 1..5) put(this, "C$row", Value.Num(row.toDouble()))
        put(this, "D1", Value.Txt("apple"))
        put(this, "D2", Value.Txt("Apricot"))
        put(this, "D3", Value.Txt("banana"))
        put(this, "D4", Value.Txt(""))
        put(this, "E1", Value.Txt("draft"))
        put(this, "E2", Value.Txt("Draft"))
        put(this, "E3", Value.Txt("final"))
        put(this, "E4", Value.Txt("FINAL "))
        put(this, "F1", Value.Num(100.0))
        put(this, "F2", Value.Num(200.0))
        put(this, "F3", Value.Num(300.0))
        put(this, "H1", Value.Err("#N/A"))
        put(this, "I1", Value.Txt("a*b"))
        put(this, "I2", Value.Txt("a?b"))
        put(this, "J1", Value.Txt(">2"))
    }

    private class MapGrid(private val values: Map<SheetRange.Cell, Value>) : XlsxConditionalFormatVerdict.Grid {
        override fun value(cell: SheetRange.Cell): Value = values[cell] ?: Value.Blank
    }

    private val GRID = MapGrid(VALUES)

    // ---------------------------------------------------------------- the cases

    /**
     * One row of the table. [expected] is `TRUE`, `FALSE`, or `UNDECIDED` optionally followed by `~` and a substring
     * the reason must contain; [sqref] is the rule's range and anchors a relative reference.
     */
    private data class Case(
        val label: String,
        val formula: String,
        val cell: String,
        val expected: String,
        val sqref: String = "",
    )

    private val CASES: List<Case> = listOf(
        // --- numeric criteria -----------------------------------------------------------------------------
        Case("count of one number", "COUNTIF(A1:A8,3)=1", "B1", "TRUE"),
        Case("count of a number written as text", "COUNTIF(A1:A8,\"3\")=1", "B1", "TRUE"),
        Case("greater than", "COUNTIF(A1:A8,\">1\")=2", "B1", "TRUE"),
        Case("greater or equal", "COUNTIF(A1:A8,\">=1\")=3", "B1", "TRUE"),
        Case("less than", "COUNTIF(A1:A8,\"<0\")=1", "B1", "TRUE"),
        Case("less or equal", "COUNTIF(A1:A8,\"<=0\")=2", "B1", "TRUE"),
        // `<>3` is true of every non-blank cell that is not 3, text and booleans included: that is Excel's rule.
        Case("not equal counts text and booleans", "COUNTIF(A1:A8,\"<>3\")=6", "B1", "TRUE"),
        Case("zero is a value not a blank", "COUNTIF(A1:A8,0)=1", "B1", "TRUE"),
        Case("blank count", "COUNTIF(A1:A8,\"\")=1", "B1", "TRUE"),
        Case("non-blank count", "COUNTIF(A1:A8,\"<>\")=7", "B1", "TRUE"),
        Case("boolean criterion", "COUNTIF(A1:A8,TRUE)=1", "B1", "TRUE"),
        // The one coercion COUNTIF has that `=` does not: text that spells a number matches the number.
        Case("numeric text matches a number criterion", "COUNTIF(A9:A9,4)=1", "B1", "TRUE"),
        Case("a range of one column", "COUNTIF(C1:C5,\">2\")=3", "B1", "TRUE"),
        Case("a two-dimensional range", "COUNTIF(A1:B3,\">0\")=6", "B1", "TRUE"),
        Case("a criterion that matches nothing", "COUNTIF(C1:C5,\">99\")=0", "B1", "TRUE"),
        // --- text criteria and wildcards ---------------------------------------------------------------------
        Case("text equality is case-insensitive", "COUNTIF(E1:E4,\"draft\")=2", "B1", "TRUE"),
        Case("text criteria are not trimmed", "COUNTIF(E1:E4,\"FINAL \")=1", "B1", "TRUE"),
        Case("a trailing space matters", "COUNTIF(E1:E4,\"final\")=1", "B1", "TRUE"),
        Case("star wildcard", "COUNTIF(D1:D4,\"a*\")=2", "B1", "TRUE"),
        Case("question wildcard", "COUNTIF(D1:D4,\"?p*\")=2", "B1", "TRUE"),
        Case("wildcard in the middle", "COUNTIF(D1:D4,\"*an*\")=1", "B1", "TRUE"),
        Case("exact text", "COUNTIF(D1:D4,\"apple\")=1", "B1", "TRUE"),
        Case("empty text is not blank", "COUNTIF(D1:D4,\"\")=1", "B1", "TRUE"),
        Case("wildcard with no match", "COUNTIF(D1:D4,\"z*\")=0", "B1", "TRUE"),
        Case("escaped star is literal", "COUNTIF(I1:I2,\"a~*b\")=1", "B1", "TRUE"),
        Case("escaped question is literal", "COUNTIF(I1:I2,\"a~?b\")=1", "B1", "TRUE"),
        Case("an unescaped star matches both", "COUNTIF(I1:I2,\"a*b\")=2", "B1", "TRUE"),
        // --- a criterion that comes from a cell ---------------------------------------------------------------
        Case("criterion from a cell", "COUNTIF(C1:C5,J1)=3", "B1", "TRUE"),
        Case("sum criterion from a cell", "SUMIF(C1:C5,J1,B1:B5)=120", "B1", "TRUE"),
        // --- SUMIF and AVERAGEIF ------------------------------------------------------------------------------
        Case("sumif with a sum range", "SUMIF(C1:C5,\">2\",B1:B5)=120", "B1", "TRUE"),
        Case("sumif without a sum range", "SUMIF(C1:C5,\">2\")=12", "B1", "TRUE"),
        Case("sumif over negatives and zero", "SUMIF(A1:A8,\"<=0\")=-4", "B1", "TRUE"),
        Case("sumif matches a blank in the criteria range", "SUMIF(A1:A8,\"\",B1:B8)=50", "B1", "TRUE"),
        Case("sumif over text criteria", "SUMIF(E1:E4,\"draft\",B1:B4)=30", "B1", "TRUE"),
        Case("averageif with a sum range", "AVERAGEIF(C1:C5,\">2\",B1:B5)=40", "B1", "TRUE"),
        Case("averageif without a sum range", "AVERAGEIF(C1:C5,\">2\")=4", "B1", "TRUE"),
        Case("averageif with no matches", "AVERAGEIF(C1:C5,\">99\")", "B1", "UNDECIDED~evaluates to #DIV/0!"),
        Case("averageif with a sum range and no matches", "AVERAGEIF(C1:C5,\">99\",B1:B5)", "B1", "UNDECIDED~#DIV/0!"),
        // A matched text cell and a matched boolean add nothing: only numbers are summed or averaged.
        Case("a matched text and boolean add nothing", "SUMIF(A1:A8,\"<>\",A1:A8)=2", "B1", "TRUE"),
        Case("an error in the sum range", "SUMIF(A1:A8,\"<>\",H1:H8)=0", "B1", "UNDECIDED~#N/A in SUMIF's sum range"),
        // --- composition ---------------------------------------------------------------------------------------
        Case(
            "criteria inside a boolean function",
            "AND(COUNTIF(C1:C5,\">2\")=3,SUMIF(C1:C5,\">2\")=12)",
            "B1",
            "TRUE",
        ),
        Case("arithmetic on a criteria result", "COUNTIF(C1:C5,\">2\")+1=4", "B1", "TRUE"),
        Case("a criteria result compared with an aggregate", "COUNTIF(C1:C5,\">2\")=COUNT(B1:B3)+0", "B1", "TRUE"),
        // --- the operators in their least obvious spellings -----------------------------------------------------
        Case("an equals sign alone counts blanks", "COUNTIF(A1:A8,\"=\")=1", "B1", "TRUE"),
        Case("a space after the operator is ignored", "COUNTIF(C1:C5,\"> 2\")=3", "B1", "TRUE"),
        Case("a wildcard under not-equal", "COUNTIF(D1:D4,\"<>a*\")=2", "B1", "TRUE"),
        // --- the shift rule -------------------------------------------------------------------------------------
        // One rule over `K1:K3` anchors at K1, so `A1:A4` reads A2:A5 at K2 (2, 3, text, blank) and A3:A6 at K3.
        Case("a shifted criteria range at the anchor", "COUNTIF(A1:A4,\">0\")=3", "K1", "TRUE", "K1:K3"),
        Case("a shifted criteria range one row down", "COUNTIF(A1:A4,\">0\")=2", "K2", "TRUE", "K1:K3"),
        Case("a shifted criteria range two rows down", "COUNTIF(A1:A4,\">0\")=1", "K3", "TRUE", "K1:K3"),
        Case("a locked criteria range does not move", "COUNTIF(\$A\$1:\$A\$4,\">0\")=3", "K3", "TRUE", "K1:K3"),
        // --- refusals -------------------------------------------------------------------------------------------
        Case("an error under a criterion", "COUNTIF(H1:H1,1)=0", "B1", "UNDECIDED~#N/A under a criterion"),
        Case(
            "ordering two different texts",
            "COUNTIF(A1:A8,\"<m\")=0",
            "B1",
            "UNDECIDED~cannot order two different texts",
        ),
        Case("a range as the criterion", "COUNTIF(A1:A3,A1:A3)=1", "B1", "UNDECIDED~a range is not a single value"),
        Case("a first argument that is not a range", "COUNTIF(A1,1)=0", "B1", "UNDECIDED~needs a cell range"),
        Case("a third argument that is not a range", "SUMIF(A1:A3,1,A1)=0", "B1", "UNDECIDED~needs a cell range"),
        Case(
            "a sum range of the wrong shape",
            "SUMIF(C1:C5,\">2\",B1:B4)=0",
            "B1",
            "UNDECIDED~is 1x4 cells, the criteria range is 1x5",
        ),
        Case("countif arity", "COUNTIF(A1:A8)=1", "B1", "UNDECIDED~takes 2"),
        Case("countif rejects a third argument", "COUNTIF(A1:A8,1,B1:B5)=0", "B1", "UNDECIDED~takes 2"),
        Case("sumif arity", "SUMIF(A1:A8,1,B1:B5,C1:C5)=0", "B1", "UNDECIDED~takes 2 to 3"),
        Case(
            "a criterion one character above the length bound",
            "COUNTIF(A1:A8,\"" + "a".repeat(256) + "\")=0",
            "B1",
            "UNDECIDED~above the 255-character bound",
        ),
        // The bound itself is inside it: 255 characters is a legal criterion and simply matches nothing here.
        Case("a criterion at the length bound", "COUNTIF(D1:D4,\"" + "a".repeat(255) + "\")=0", "B1", "TRUE"),
        // A trailing `~` with nothing to escape is a literal tilde, not a broken pattern.
        Case("a trailing escape is a literal tilde", "COUNTIF(I1:I2,\"a~\")=0", "B1", "TRUE"),
        // A `>` criterion against text is not orderable and therefore simply does not match; only text-against-text
        // ordering is refused, because only that needs a collator.
        Case("an ordering criterion against text does not match", "COUNTIF(A1:A8,\">x\")=0", "B1", "TRUE"),
        Case("an ordering criterion with a numeric operand ignores text", "COUNTIF(A1:A2,\"<5\")=2", "B1", "TRUE"),
    )

    // ---------------------------------------------------------------- the written rules

    /** Twelve criteria shapes, on disjoint single-column ranges. */
    private val BLOCKS = listOf(
        Block("A1:A9", listOf(Rule(Kind.EXPRESSION, priority = 1, formulas = listOf("COUNTIF(A1:A8,\">1\")=2"), format = FILL))),
        Block("B1:B9", listOf(Rule(Kind.EXPRESSION, priority = 2, formulas = listOf("SUMIF(C1:C5,\">2\",B1:B5)=120"), format = FILL))),
        Block("C1:C9", listOf(Rule(Kind.EXPRESSION, priority = 3, formulas = listOf("AVERAGEIF(C1:C5,\">2\")=4"), format = FILL))),
        Block("D1:D9", listOf(Rule(Kind.EXPRESSION, priority = 4, formulas = listOf("COUNTIF(D1:D4,\"a*\")=2"), format = FILL))),
        Block("E1:E9", listOf(Rule(Kind.EXPRESSION, priority = 5, formulas = listOf("COUNTIF(E1:E4,\"draft\")=2"), format = FILL))),
        Block("F1:F9", listOf(Rule(Kind.EXPRESSION, priority = 6, formulas = listOf("SUMIF(E1:E4,\"draft\",F1:F3)>0"), format = FILL))),
        Block("G1:G9", listOf(Rule(Kind.EXPRESSION, priority = 7, formulas = listOf("COUNTIF(A1:A8,\"\")=1"), format = FILL))),
        Block("H1:H9", listOf(Rule(Kind.EXPRESSION, priority = 8, formulas = listOf("COUNTIF(A1:A8,\"<>\")=7"), format = FILL))),
        Block("I1:I9", listOf(Rule(Kind.EXPRESSION, priority = 9, formulas = listOf("COUNTIF(I1:I2,\"a~*b\")=1"), format = FILL))),
        Block("J1:J9", listOf(Rule(Kind.EXPRESSION, priority = 10, formulas = listOf("COUNTIF(C1:C5,J1)=3"), format = FILL))),
        Block(
            "K1:K9",
            listOf(
                Rule(Kind.EXPRESSION, priority = 11, formulas = listOf("COUNTIF(\$A\$1:\$A\$3,\">1\")=2"), format = FILL),
            ),
        ),
        Block(
            "L1:L9",
            listOf(Rule(Kind.EXPRESSION, priority = 12, formulas = listOf("AND(COUNTIF(C1:C5,\">2\")=3,SUMIF(C1:C5,\">2\")=12)"), format = FILL)),
        ),
    )

    private fun encode(outcome: Outcome): String = when (outcome) {
        is Outcome.Undecided -> "UNDECIDED~${outcome.reason}"
        is Outcome.Decided -> if (outcome.fired.isEmpty()) "FALSE" else "TRUE"
    }

    private fun shape(encoded: String): String = encoded.substringBefore('~')

    private fun reason(encoded: String): String? = if ('~' in encoded) encoded.substringAfter('~') else null

    @JvmStatic
    fun main(args: Array<String>) {
        val folder = File(args.firstOrNull() ?: "testing/build/r106/artifacts")
        folder.mkdirs()

        // --- 1/2. the cases and the refusals ---------------------------------------------------------------
        val caseRows = StringBuilder()
        var decided = 0
        var refused = 0
        for (case in CASES) {
            val sqref = case.sqref.ifEmpty { case.cell }
            val block = Block(
                sqref,
                listOf(Rule(Kind.EXPRESSION, priority = 1, formulas = listOf(case.formula), format = FILL)),
            )
            val outcome = XlsxConditionalFormatVerdict.verdict(listOf(block), GRID, SheetRange.parseCell(case.cell))
            val actual = encode(outcome)
            val expected = case.expected
            check(shape(actual) == shape(expected)) {
                "${case.label}: '${case.formula}' at ${case.cell} gave ${shape(actual)}, expected ${shape(expected)}" +
                    " (${actual.substringAfter('~', "-")})"
            }
            reason(expected)?.let { wanted ->
                check(reason(actual)?.contains(wanted) == true) {
                    "${case.label}: '${case.formula}' refused with '${reason(actual)}', expected it to mention '$wanted'"
                }
            }
            if (shape(actual) == "UNDECIDED") refused++ else decided++
            caseRows.append(
                listOf(
                    case.label, case.formula, case.cell, sqref,
                    expected.substringBefore('~'), shape(actual), actual.substringAfter('~', "-"),
                ).joinToString("\t"),
            ).append('\n')
        }
        File(folder, "cases.tsv").writeText(caseRows.toString(), Charsets.UTF_8)

        refuses("a formula with a leading '='") {
            Rule(Kind.EXPRESSION, priority = 1, formulas = listOf("=COUNTIF(A1:A3,1)>0"), format = FILL)
        }
        refuses("an empty formula") { Rule(Kind.EXPRESSION, priority = 1, formulas = listOf(""), format = FILL) }

        // --- 3. the file ------------------------------------------------------------------------------------
        val sheet = XlsxStyledBook.StyledSheet(
            name = "Criteria",
            cells = (1..5).flatMap { row ->
                listOf("A", "B", "C").map { column ->
                    XlsxStyledBook.StyledCell("$column$row", XlsxStyledBook.StyledVal.Num(row.toDouble()))
                }
            },
            conditionalFormats = BLOCKS,
        )
        val bytes = XlsxStyledBook.write(XlsxStyledBook.WorkbookSpec(sheets = listOf(sheet)))
        File(folder, "r106.xlsx").writeBytes(bytes)
        val sheet1 = part(bytes, "xl/worksheets/sheet1.xml")
        check(BLOCKS.sumOf { it.rules.size } == 12) { "the fixture is supposed to hold twelve rules" }
        for (block in BLOCKS) check(block.sqref in sheet1) { "the sqref ${block.sqref} is missing from sheet1.xml" }
        val read = XlsxStyledBook.read(bytes).sheets.single { it.name == "Criteria" }.conditionalFormats
        check(read.size == BLOCKS.size) { "read ${read.size} blocks, wrote ${BLOCKS.size}" }
        for ((index, block) in BLOCKS.withIndex()) {
            check(read[index].sqref == block.sqref) { "block $index sqref ${read[index].sqref} != ${block.sqref}" }
            for ((ruleIndex, rule) in block.rules.withIndex()) {
                val formula = rule.formulas.first()
                val escaped = formula
                    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                val plain = formula.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                check("<formula>$escaped</formula>" in sheet1 || "<formula>$plain</formula>" in sheet1) {
                    "the formula '$formula' is not in sheet1.xml verbatim"
                }
                check(read[index].rules[ruleIndex].formulas == rule.formulas) {
                    "rule ${rule.priority} round-tripped formulas ${read[index].rules[ruleIndex].formulas}"
                }
            }
        }
        // A criterion written with an escaped wildcard keeps both characters through the writer.
        check("a~*b" in sheet1) { "the escaped-wildcard criterion lost its tilde" }

        val ruleRows = StringBuilder()
        for (block in BLOCKS) {
            for (rule in block.rules) {
                ruleRows.append(
                    listOf(block.sqref, rule.priority.toString(), rule.kind.token, rule.formulas.joinToString(","))
                        .joinToString("\t"),
                ).append('\n')
            }
        }
        File(folder, "rules.tsv").writeText(ruleRows.toString(), Charsets.UTF_8)

        val valueRows = StringBuilder()
        for ((cell, value) in VALUES.entries.sortedWith(compareBy({ it.key.row }, { it.key.col }))) {
            val encoded = when (value) {
                is Value.Num -> "n\t${value.value}"
                is Value.Txt -> "t\t${value.text}"
                is Value.Bool -> "b\t${if (value.flag) 1 else 0}"
                is Value.Err -> "e\t${value.code}"
                Value.Blank -> error("a blank cell is not in the grid")
            }
            valueRows.append("${cell.toA1()}\t$encoded\n")
        }
        File(folder, "values.tsv").writeText(valueRows.toString(), Charsets.UTF_8)

        val summary = "R106_CRITERIA_OK assertions=$assertions cases=${CASES.size} decided=$decided refused=$refused " +
            "criteria=3 rules=${BLOCKS.sumOf { it.rules.size }}"
        File(folder, "summary.txt").writeText(summary + "\n", Charsets.UTF_8)
        println(summary)
    }
}
