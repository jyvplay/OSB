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
 * R105 spreadsheet contracts: ranges and the seven aggregates a conditional format actually uses.
 *
 * R104 gave an `expression` rule a real grammar, but its operands were single values, so `SUM(A1:A3)>10`,
 * `AVERAGE(B1:B3)<2` and `COUNT(A1:A9)=9` — three of the most common conditional formats in existence — were
 * refused. R105 adds `from:to` ranges and `SUM`, `COUNT`, `COUNTA`, `AVERAGE`, `MIN`, `MAX`, `MEDIAN`.
 *
 * Four things are asserted, each by a mechanism that can fail on its own:
 *
 * 1. **The cases.** [CASES] is a hand-written table of `formula -> answer at a cell` covering the aggregate rules
 *    Excel documents (text, booleans and blanks are ignored inside a range; an error anywhere in a range is an error,
 *    not a zero; `AVERAGE` of no numbers is `#DIV/0!`, `MEDIAN` of no numbers is `#NUM!`, `MIN`/`MAX` of no numbers
 *    are 0), the difference between a range argument and a scalar one, reversed corners, `$` locks on either
 *    endpoint, and the R103 shift rule applied to a whole range.
 * 2. **The refusals.** A range used as a single value, a range that leaves the grid, a range above the component's
 *    `MAX_RANGE_CELLS` bound, a broken endpoint, and a text scalar in an arithmetic aggregate must all come back
 *    `Undecided` with a reason.
 * 3. **The file.** Twelve rules whose formulas are aggregate shapes are written to `r105.xlsx`, read back out of the
 *    ZIP, and read again through `XlsxStyledBook.read`.
 * 4. **The independent lane.** `tools/check_r105_ranges.py` re-tokenizes, re-parses and re-evaluates every case with
 *    its own Python implementation of the same grammar, and `tools/test_r105_ranges.py` tests that implementation.
 *
 * Host JVM only. No Excel, LibreOffice or device ran: a verdict is a claim about which rule fires, never about what
 * a screen shows.
 */
object R105Checks {
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
     * The sheet the cases are evaluated against. It is deliberately untidy — a text cell, a numeric-looking text
     * cell, an absent (blank) cell, a boolean and an error inside the ranges — because "ignore text" and "ignore
     * blank" are exactly where an aggregate implementation goes wrong.
     */
    private val VALUES: Map<SheetRange.Cell, Value> = buildMap {
        put(this, "A1", Value.Num(1.0))
        put(this, "A2", Value.Num(2.0))
        put(this, "A3", Value.Num(3.0))
        put(this, "A4", Value.Txt("x"))
        put(this, "A7", Value.Num(0.0))
        put(this, "A8", Value.Num(-4.0))
        put(this, "A9", Value.Txt(" 4 "))
        put(this, "A6", Value.Bool(true))
        put(this, "B1", Value.Num(10.0))
        put(this, "B2", Value.Num(20.0))
        put(this, "B3", Value.Num(30.0))
        put(this, "C1", Value.Err("#N/A"))
        put(this, "D1", Value.Num(2.0))
        put(this, "D2", Value.Num(4.0))
        put(this, "D3", Value.Num(6.0))
        put(this, "D4", Value.Num(8.0))
        put(this, "E1", Value.Num(2.0))
        put(this, "E2", Value.Num(4.0))
        put(this, "E3", Value.Num(6.0))
        for (row in 1..9) put(this, "F$row", Value.Num(5.0))
    }

    private class MapGrid(private val values: Map<SheetRange.Cell, Value>) : XlsxConditionalFormatVerdict.Grid {
        override fun value(cell: SheetRange.Cell): Value = values[cell] ?: Value.Blank
    }

    private val GRID = MapGrid(VALUES)

    // ---------------------------------------------------------------- the cases

    /**
     * One row of the table. [expected] is `TRUE`, `FALSE`, or `UNDECIDED` optionally followed by `~` and a
     * substring the reason must contain; [sqref] is the rule's range and anchors a relative reference.
     */
    private data class Case(
        val label: String,
        val formula: String,
        val cell: String,
        val expected: String,
        val sqref: String = "",
    )

    private val CASES: List<Case> = listOf(
        // --- scalar arguments ------------------------------------------------------------------------------
        Case("sum of literals", "SUM(1,2,3)=6", "B1", "TRUE"),
        Case("sum of a literal and a cell", "SUM(1,A1)=2", "B1", "TRUE"),
        Case("sum of a boolean", "SUM(TRUE)=1", "B1", "TRUE"),
        Case("sum of text is refused", "SUM(\"x\")=0", "B1", "UNDECIDED~is not a number for SUM"),
        Case("count ignores text", "COUNT(1,2,\"x\")=2", "B1", "TRUE"),
        Case("count of a boolean is zero", "COUNT(TRUE)=0", "B1", "TRUE"),
        Case("counta of a boolean is one", "COUNTA(TRUE)=1", "B1", "TRUE"),
        Case("counta skips a blank", "COUNTA(1,\"x\",A5)=2", "B1", "TRUE"),
        Case("average of literals", "AVERAGE(1,2,3)=2", "B1", "TRUE"),
        Case("min of literals", "MIN(3,1,2)=1", "B1", "TRUE"),
        Case("max of literals", "MAX(3,1,2)=3", "B1", "TRUE"),
        Case("median of an odd count", "MEDIAN(1,2,3)=2", "B1", "TRUE"),
        Case("median of an even count", "MEDIAN(1,2,3,4)=2.5", "B1", "TRUE"),
        Case("median of one value", "MEDIAN(7)=7", "B1", "TRUE"),
        Case("an aggregate without arguments", "SUM()=0", "B1", "UNDECIDED~takes 1 to 8"),
        Case("too many aggregate arguments", "SUM(1,2,3,4,5,6,7,8,9)=45", "B1", "UNDECIDED~more than 8"),
        // --- ranges: the documented aggregate rules ----------------------------------------------------------
        Case("sum of a range", "SUM(A1:A3)=6", "B1", "TRUE"),
        Case("sum of a range with text, a blank and a boolean", "SUM(A1:A8)=2", "B1", "TRUE"),
        Case("sum of a whole row of one column", "SUM(B1:B3)=60", "B1", "TRUE"),
        Case("count of numbers in a range", "COUNT(A1:A8)=5", "B1", "TRUE"),
        Case("counta of non-blank cells in a range", "COUNTA(A1:A8)=7", "B1", "TRUE"),
        Case("average of a range", "AVERAGE(A1:A3)=2", "B1", "TRUE"),
        Case("min of a range with negative numbers", "MIN(A1:A8)=-4", "B1", "TRUE"),
        Case("max of a range", "MAX(A1:A8)=3", "B1", "TRUE"),
        Case("median of an even range", "MEDIAN(D1:D4)=5", "B1", "TRUE"),
        Case("median of an odd range", "MEDIAN(E1:E3)=4", "B1", "TRUE"),
        Case("median of a range ignoring text", "MEDIAN(A1:A8)=1", "B1", "TRUE"),
        Case("a range and a scalar together", "SUM(B1:B3,A1)=61", "B1", "TRUE"),
        Case("two ranges together", "SUM(A1:A3,B1:B3)=66", "B1", "TRUE"),
        Case("a reversed range is normalized", "SUM(A3:A1)=6", "B1", "TRUE"),
        Case("a two-dimensional range", "SUM(A1:B3)=66", "B1", "TRUE"),
        Case("counta of a single text cell", "COUNTA(A4)=1", "B1", "TRUE"),
        Case("count of a single text cell", "COUNT(A4)=0", "B1", "TRUE"),
        Case("sum of a blank cell", "SUM(A5)=0", "B1", "TRUE"),
        Case("average of a blank cell", "AVERAGE(A5)=0", "B1", "UNDECIDED~#DIV/0!"),
        Case("min of a range without numbers", "MIN(A4:A6)=0", "B1", "TRUE"),
        Case("max of a range without numbers", "MAX(A4:A6)=0", "B1", "TRUE"),
        Case("a text scalar inside an aggregate", "SUM(A1:A3,A4)=6", "B1", "UNDECIDED~is not a number for SUM"),
        Case("count ignores a text scalar but keeps its arity", "COUNT(A1:A3,A4,TRUE)=3", "B1", "TRUE"),
        Case("counta counts a text and a boolean scalar", "COUNTA(A1:A3,A4,A5,TRUE)=5", "B1", "TRUE"),
        // --- results that are errors rather than numbers -----------------------------------------------------
        Case("average of no numbers", "AVERAGE(A4:A6)=0", "B1", "UNDECIDED~#DIV/0!"),
        Case("bare average of no numbers", "AVERAGE(A4:A6)", "B1", "UNDECIDED~evaluates to #DIV/0!"),
        Case("median of no numbers", "MEDIAN(A4:A6)", "B1", "UNDECIDED~evaluates to #NUM!"),
        Case("an error inside a range", "SUM(A1:C1)=0", "B1", "UNDECIDED~#N/A in SUM at C1"),
        Case("an error in a scalar argument", "COUNT(C1)=1", "B1", "UNDECIDED~#N/A in COUNT"),
        // --- composition -----------------------------------------------------------------------------------
        Case("an aggregate inside another function", "AND(SUM(A1:A3)=6,COUNT(A1:A3)=3)", "B1", "TRUE"),
        Case("arithmetic on an aggregate", "SUM(A1:A3)+1=7", "B1", "TRUE"),
        Case("an aggregate of an aggregate", "SUM(SUM(A1:A3),1)=7", "B1", "TRUE"),
        Case("an average of an aggregate and a literal", "AVERAGE(SUM(A1:A3),3)=4.5", "B1", "TRUE"),
        Case("a median of an aggregate and a literal", "MEDIAN(SUM(A1:A3),0)=3", "B1", "TRUE"),
        Case("rounding an aggregate", "ROUND(AVERAGE(A1:A3),1)=2", "B1", "TRUE"),
        Case("a comparison between two aggregates", "SUM(A1:A3)=COUNT(B1:B3)*2", "B1", "TRUE"),
        // --- the shift rule, inside a range ----------------------------------------------------------------
        // One rule over `F1:F3` anchors at F1, so `A1:A3` becomes `A2:A4` at F2 (2+3 = 5) and `A3:A5` at F3 (3).
        Case("a shifted range at the anchor", "SUM(A1:A3)>5", "F1", "TRUE", "F1:F3"),
        Case("a shifted range one row down", "SUM(A1:A3)>5", "F2", "FALSE", "F1:F3"),
        Case("a shifted range two rows down", "SUM(A1:A3)=3", "F3", "TRUE", "F1:F3"),
        // A row lock on both endpoints keeps the range still however far the scan moves.
        Case("a row-locked range at the anchor", "SUM(A\$1:A\$3)>5", "F4", "TRUE", "F4:F6"),
        Case("a row-locked range three rows down", "SUM(A\$1:A\$3)>5", "F6", "TRUE", "F4:F6"),
        // A column lock with a relative row still moves rows, because the row is unlocked on both endpoints.
        Case("a column-locked range still moves rows", "SUM(\$A1:\$A3)=5", "F7", "FALSE", "F7:F9"),
        Case("a column-locked range at its own row", "SUM(\$A1:\$A3)=6", "F7", "TRUE", "F7:F7"),
        // --- refusals ---------------------------------------------------------------------------------------
        Case("a range as a value", "A1:A3", "B1", "UNDECIDED~a range is not a single value"),
        Case("a range in a comparison", "A1:A3>0", "B1", "UNDECIDED~a range is not a single value"),
        Case("a range in ISBLANK", "ISBLANK(A1:A3)", "B1", "UNDECIDED~a range is not a single value"),
        Case("a range in arithmetic", "SUM(A1:A3)+A1:A3=6", "B1", "UNDECIDED~a range is not a single value"),
        Case("a missing range endpoint", "SUM(A1:A)=0", "B1", "UNDECIDED~':' must be followed by a cell reference"),
        Case("a numeric range endpoint", "SUM(A1:3)=0", "B1", "UNDECIDED~':' must be followed by a cell reference"),
        Case("a leading colon", "SUM(:A1)=0", "B1", "UNDECIDED~unexpected ':'"),
        Case("an unclosed aggregate", "SUM(A1:A3=6", "B1", "UNDECIDED~expected ',' or ')' in SUM"),
        Case("a range above the reader's bound", "SUM(A1:CV2500)=0", "B1", "UNDECIDED~aggregate bound"),
        // A whole-column range has no cell reference on its left, so the refusal names that, not the colon.
        Case("a whole-column range", "SUM(A:A)=0", "B1", "UNDECIDED~neither a function, a literal nor a cell reference"),
        // The grid edge: one rule over `M1:M3` anchors at M1, so at M2 both endpoints move down a row and
        // `A1048575:A1048576` becomes `A1048576:A1048577`, which is off the sheet.
        Case(
            "a range that shifts off the grid", "SUM(A1048575:A1048576)=0", "M2",
            "UNDECIDED~shifts outside the sheet", "M1:M3",
        ),
        Case("the same range at the anchor row", "SUM(A1048575:A1048576)=0", "M1", "TRUE", "M1:M3"),
    )

    // ---------------------------------------------------------------- the written rules

    /** Twelve aggregate shapes, on disjoint single-column ranges so the block structure stays visible. */
    private val BLOCKS = listOf(
        Block("A1:A9", listOf(Rule(Kind.EXPRESSION, priority = 1, formulas = listOf("SUM(A1:A3)>5"), format = FILL))),
        Block("B1:B9", listOf(Rule(Kind.EXPRESSION, priority = 2, formulas = listOf("AVERAGE(A1:A3)=2"), format = FILL))),
        Block("C1:C9", listOf(Rule(Kind.EXPRESSION, priority = 3, formulas = listOf("COUNT(A1:A8)>4"), format = FILL))),
        Block("D1:D9", listOf(Rule(Kind.EXPRESSION, priority = 4, formulas = listOf("COUNTA(A1:A8)>6"), format = FILL))),
        Block("E1:E9", listOf(Rule(Kind.EXPRESSION, priority = 5, formulas = listOf("MIN(A1:A8)<0"), format = FILL))),
        Block("F1:F9", listOf(Rule(Kind.EXPRESSION, priority = 6, formulas = listOf("MAX(A1:A8)>2"), format = FILL))),
        Block("G1:G9", listOf(Rule(Kind.EXPRESSION, priority = 7, formulas = listOf("MEDIAN(A1:A8)=1"), format = FILL))),
        Block("H1:H9", listOf(Rule(Kind.EXPRESSION, priority = 8, formulas = listOf("SUM(B1:B3)=60"), format = FILL))),
        Block("I1:I9", listOf(Rule(Kind.EXPRESSION, priority = 9, formulas = listOf("SUM(\$B\$1:\$B\$3)=60"), format = FILL))),
        Block(
            "J1:J9",
            listOf(Rule(Kind.EXPRESSION, priority = 10, formulas = listOf("AND(SUM(A1:A3)=6,COUNT(A1:A3)=3)"), format = FILL)),
        ),
        Block("K1:K9", listOf(Rule(Kind.EXPRESSION, priority = 11, formulas = listOf("SUM(A1:A3,B1:B3)=66"), format = FILL))),
        Block("L1:L9", listOf(Rule(Kind.EXPRESSION, priority = 12, formulas = listOf("ROUND(AVERAGE(A1:A3),1)=2"), format = FILL))),
    )

    private fun encode(outcome: Outcome): String = when (outcome) {
        is Outcome.Undecided -> "UNDECIDED~${outcome.reason}"
        is Outcome.Decided -> if (outcome.fired.isEmpty()) "FALSE" else "TRUE"
    }

    private fun shape(encoded: String): String = encoded.substringBefore('~')

    private fun reason(encoded: String): String? = if ('~' in encoded) encoded.substringAfter('~') else null

    @JvmStatic
    fun main(args: Array<String>) {
        val folder = File(args.firstOrNull() ?: "testing/build/r105/artifacts")
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
            Rule(Kind.EXPRESSION, priority = 1, formulas = listOf("=SUM(A1:A3)>0"), format = FILL)
        }
        refuses("an empty formula") { Rule(Kind.EXPRESSION, priority = 1, formulas = listOf(""), format = FILL) }

        // --- 3. the file ------------------------------------------------------------------------------------
        val sheet = XlsxStyledBook.StyledSheet(
            name = "Aggregates",
            cells = (1..4).flatMap { row ->
                listOf("A", "B", "D", "E").map { column ->
                    XlsxStyledBook.StyledCell("$column$row", XlsxStyledBook.StyledVal.Num(row.toDouble()))
                }
            },
            conditionalFormats = BLOCKS,
        )
        val bytes = XlsxStyledBook.write(XlsxStyledBook.WorkbookSpec(sheets = listOf(sheet)))
        File(folder, "r105.xlsx").writeBytes(bytes)
        val sheet1 = part(bytes, "xl/worksheets/sheet1.xml")
        check(BLOCKS.sumOf { it.rules.size } == 12) { "the fixture is supposed to hold twelve rules" }
        for (block in BLOCKS) check(block.sqref in sheet1) { "the sqref ${block.sqref} is missing from sheet1.xml" }
        val read = XlsxStyledBook.read(bytes).sheets.single { it.name == "Aggregates" }.conditionalFormats
        check(read.size == BLOCKS.size) { "read ${read.size} blocks, wrote ${BLOCKS.size}" }
        for ((index, block) in BLOCKS.withIndex()) {
            check(read[index].sqref == block.sqref) { "block $index sqref ${read[index].sqref} != ${block.sqref}" }
            for ((ruleIndex, rule) in block.rules.withIndex()) {
                val formula = rule.formulas.first()
                val escaped = formula.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                check("<formula>$escaped</formula>" in sheet1) { "the formula '$formula' is not in sheet1.xml verbatim" }
                check(read[index].rules[ruleIndex].formulas == rule.formulas) {
                    "rule ${rule.priority} round-tripped formulas ${read[index].rules[ruleIndex].formulas}"
                }
            }
        }
        // The `$` markers on both endpoints survive the writer, which is what keeps a range from moving.
        check("<formula>SUM(${'$'}B${'$'}1:${'$'}B${'$'}3)=60</formula>" in sheet1) {
            "the row- and column-locked range lost its markers"
        }

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

        val summary = "R105_RANGES_OK assertions=$assertions cases=${CASES.size} decided=$decided refused=$refused " +
            "aggregates=7 rules=${BLOCKS.sumOf { it.rules.size }}"
        File(folder, "summary.txt").writeText(summary + "\n", Charsets.UTF_8)
        println(summary)
    }
}
