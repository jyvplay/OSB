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
 * R107 spreadsheet contracts: the plural criteria functions — `COUNTIFS`, `SUMIFS`, `AVERAGEIFS`.
 *
 * R106 could ask one question of a range ("how many cells are above 5?"). A workbook that says "how many rows are
 * above 5 *and* marked high?" could not be expressed at all, so `COUNTIFS`, `SUMIFS` and `AVERAGEIFS` — the plural
 * forms Excel's own dialog writes — were refused by name. R107 adds them: one or more range/criterion pairs, where
 * every pair must hold **at the same position**, and the accumulated range for `SUMIFS`/`AVERAGEIFS` is the first
 * argument.
 *
 * Four things are asserted, each by a mechanism that can fail on its own:
 *
 * 1. **The cases.** [CASES] is a hand-written table of `formula -> answer at a cell`. It covers the conjunction
 *    itself (two pairs, three pairs, contradictory pairs), the position alignment between a pair, its sibling pair
 *    and the accumulated range, singular/plural agreement (`COUNTIFS(x,c) = COUNTIF(x,c)`,
 *    `SUMIFS(s,x,c) = SUMIF(x,c,s)`), every criterion shape R106 introduced reused through the plural path
 *    (wildcards, case folding, blanks, `"<>"`, numeric-text folding, booleans, a criterion held in a cell), the
 *    shape rule (a range's position is free but its width and height are not), error refusal under a criterion and
 *    in the accumulated range, and the per-range shift rule.
 * 2. **The refusals.** An unpaired argument, a non-range pair or accumulated range, a shape mismatch naming which
 *    pair and which reference, an error anywhere under a criterion even when an earlier criterion already failed,
 *    and the arity bounds.
 * 3. **The file.** Twelve plural rules are written to `r107.xlsx`, read back out of the ZIP, and read again through
 *    `XlsxStyledBook.read`.
 * 4. **The independent lane.** `tools/check_r107_plural_criteria.py` re-tokenizes, re-parses and re-evaluates every
 *    case with its own Python implementation, and `tools/test_r107_plural_criteria.py` tests that implementation.
 *
 * Host JVM only. No Excel, LibreOffice or device ran: a verdict is a claim about which rule fires, never about what
 * a screen shows.
 */
object R107Checks {
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
     * A sheet built to break a conjunction: `A` mixes numbers, text, a blank, a boolean, a zero, a negative and a
     * numeric-looking text; `B` and `C` are two aligned number columns; `D` and `E` are text columns for wildcards
     * and case folding; `K` is the second criterion ("low"/"high"), so a pair can be satisfied or not independently
     * of the first; `H` holds one error.
     */
    private val VALUES: Map<SheetRange.Cell, Value> = buildMap {
        put(this, "A1", Value.Num(1.0))
        put(this, "A2", Value.Num(2.0))
        put(this, "A3", Value.Num(3.0))
        put(this, "A4", Value.Txt("x"))
        // A5 is absent: the blank.
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
        put(this, "K1", Value.Txt("low"))
        put(this, "K2", Value.Txt("high"))
        put(this, "K3", Value.Txt("high"))
        put(this, "K4", Value.Txt("low"))
        put(this, "K5", Value.Txt("high"))
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
        // --- the conjunction ---------------------------------------------------------------------------------
        Case("one pair agrees with countif", "COUNTIFS(C1:C5,\">2\")=3", "B1", "TRUE"),
        Case("one pair agrees with countif on text", "COUNTIFS(E1:E4,\"draft\")=2", "B1", "TRUE"),
        Case("two pairs on one range", "COUNTIFS(C1:C5,\">1\",C1:C5,\"<4\")=2", "B1", "TRUE"),
        Case("two pairs on two ranges", "COUNTIFS(C1:C5,\">2\",K1:K5,\"high\")=2", "B1", "TRUE"),
        Case("pair order does not matter", "COUNTIFS(K1:K5,\"high\",C1:C5,\">2\")=2", "B1", "TRUE"),
        Case("three pairs", "COUNTIFS(C1:C5,\">1\",C1:C5,\"<5\",K1:K5,\"high\")=2", "B1", "TRUE"),
        Case("a pair that matches nothing", "COUNTIFS(C1:C5,\">9\",K1:K5,\"high\")=0", "B1", "TRUE"),
        Case("contradictory pairs count nothing", "COUNTIFS(A1:A8,\"\",A1:A8,\"<>\")=0", "B1", "TRUE"),
        Case("a two-dimensional pair", "COUNTIFS(A1:B3,\">0\",A1:B3,\"<10\")=3", "B1", "TRUE"),
        Case("a reversed pair is normalized", "COUNTIFS(C5:C1,\">2\")=3", "B1", "TRUE"),
        Case("a criterion held in a cell in the second pair", "COUNTIFS(C1:C5,\">1\",C1:C5,J1)=3", "B1", "TRUE"),
        // --- the criterion shapes, reused through the plural path ----------------------------------------------
        Case("a wildcard pair", "COUNTIFS(D1:D4,\"a*\",D1:D4,\"*e\")=1", "B1", "TRUE"),
        Case("case folding in a pair", "COUNTIFS(E1:E4,\"draft\",K1:K4,\"low\")=1", "B1", "TRUE"),
        Case("an empty-text cell satisfies both a blank and a wildcard pair", "COUNTIFS(D1:D4,\"\",D1:D4,\"*\")=1", "B1", "TRUE"),
        Case("a blank pair with a number pair", "COUNTIFS(A1:A8,\"\",B1:B8,\">0\")=1", "B1", "TRUE"),
        Case("numeric text folds in both pairs", "COUNTIFS(A9:A9,4,A9:A9,\"<5\")=1", "B1", "TRUE"),
        Case("a boolean criterion with a non-blank pair", "COUNTIFS(A1:A8,TRUE,A1:A8,\"<>\")=1", "B1", "TRUE"),
        Case("not-equal and an ordering pair", "COUNTIFS(A1:A8,\"<>3\",A1:A8,\">0\")=2", "B1", "TRUE"),
        Case("non-blank and positive", "COUNTIFS(A1:A8,\"<>\",A1:A8,\">0\")=3", "B1", "TRUE"),
        Case("a low band", "COUNTIFS(C1:C5,\">1\",K1:K5,\"low\")=1", "B1", "TRUE"),
        // C > 1, C > 2, C < 5 and K = "high" leave row 3 alone: (3 is the only value above 2, below 5, and high).
        Case("four pairs", "COUNTIFS(C1:C5,\">1\",C1:C5,\">2\",C1:C5,\"<5\",K1:K5,\"high\")=1", "B1", "TRUE"),
        // --- SUMIFS -------------------------------------------------------------------------------------------
        Case("sumifs with one pair", "SUMIFS(B1:B5,C1:C5,\">2\")=120", "B1", "TRUE"),
        Case("sumifs with two pairs", "SUMIFS(B1:B5,C1:C5,\">1\",K1:K5,\"high\")=100", "B1", "TRUE"),
        Case("sumifs with three pairs", "SUMIFS(B1:B5,C1:C5,\">1\",C1:C5,\"<5\",K1:K5,\"high\")=50", "B1", "TRUE"),
        Case("sumifs agrees with sumif", "SUMIFS(B1:B5,C1:C5,\">2\")=SUMIF(C1:C5,\">2\",B1:B5)", "B1", "TRUE"),
        Case("sumifs matches nothing and sums zero", "SUMIFS(B1:B5,C1:C5,\">99\")=0", "B1", "TRUE"),
        Case("sumifs over a text criterion", "SUMIFS(C1:C4,E1:E4,\"draft\")=3", "B1", "TRUE"),
        Case("a matched text and boolean add nothing to a sum", "SUMIFS(A1:A8,A1:A8,\"<>\")=2", "B1", "TRUE"),
        // The error sits at H1, whose row matches; an error only matters where the sum range is read.
        Case("an error in the sum range on a matched row", "SUMIFS(H1:H8,A1:A8,\"<>\")=0", "B1", "UNDECIDED~#N/A in SUMIFS's sum range"),
        Case("an error in the sum range on an unmatched row is not read", "SUMIFS(H1:H8,A1:A8,\"\")=0", "B1", "TRUE"),
        // --- AVERAGEIFS ---------------------------------------------------------------------------------------
        Case("averageifs with one pair", "AVERAGEIFS(B1:B5,C1:C5,\">2\")=40", "B1", "TRUE"),
        Case("averageifs with two pairs", "AVERAGEIFS(B1:B5,C1:C5,\">1\",C1:C5,\"<4\")=25", "B1", "TRUE"),
        Case("an average over text and booleans", "AVERAGEIFS(A1:A8,A1:A8,\"<>\")=0.4", "B1", "TRUE"),
        Case("averageifs with no matches", "AVERAGEIFS(B1:B5,C1:C5,\">99\")", "B1", "UNDECIDED~evaluates to #DIV/0!"),
        Case("an error in the average range", "AVERAGEIFS(H1:H8,A1:A8,\"<>\")=0", "B1", "UNDECIDED~#N/A in AVERAGEIFS's average range"),
        // --- composition --------------------------------------------------------------------------------------
        Case(
            "a plural result inside a boolean function",
            "AND(COUNTIFS(C1:C5,\">2\")=3,AVERAGEIFS(B1:B5,C1:C5,\">2\")=40)",
            "B1",
            "TRUE",
        ),
        Case("arithmetic on a plural result", "COUNTIFS(C1:C5,\">2\")+1=4", "B1", "TRUE"),
        Case(
            "plural forms agree with their singular forms on the same data",
            "SUMIFS(B1:B5,C1:C5,\">2\")=SUMIF(C1:C5,\">2\",B1:B5)",
            "B1",
            "TRUE",
        ),
        // --- the shift rule, per range -----------------------------------------------------------------------
        // One rule over `K1:K3` anchors at K1, so `A1:A4` reads A2:A5 at K2 and A3:A6 at K3.
        Case("a shifted pair at the anchor", "COUNTIFS(A1:A4,\">0\")=3", "K1", "TRUE", "K1:K3"),
        Case("a shifted pair one row down", "COUNTIFS(A1:A4,\">0\")=2", "K2", "TRUE", "K1:K3"),
        Case("a shifted pair two rows down", "COUNTIFS(A1:A4,\">0\")=1", "K3", "TRUE", "K1:K3"),
        Case("a locked pair does not move", "COUNTIFS(\$A\$1:\$A\$4,\">0\")=3", "K3", "TRUE", "K1:K3"),
        // Both ranges shift together, so the alignment is preserved: at K2 the rows are (A2,C2), (A3,C3), ...
        // (2,2) and (3,3) both satisfy ">0" and ">1"; (x,4) fails the first, and the blank row fails both.
        Case("two ranges shift together", "COUNTIFS(A1:A4,\">0\",C1:C4,\">1\")=2", "K2", "TRUE", "K1:K3"),
        // One range locked, one free: A stays at rows 1..4 while C moves to rows 2..5, so the answer changes.
        Case("a locked and a free range together", "COUNTIFS(\$A\$1:\$A\$4,\">0\",C1:C4,\">1\")=3", "K2", "TRUE", "K1:K3"),
        // --- refusals -----------------------------------------------------------------------------------------
        Case("an odd argument count", "COUNTIFS(C1:C5,\">2\",K1:K5)=0", "B1", "UNDECIDED~so 3 arguments leave one unpaired"),
        Case("an odd argument count with a sum range", "SUMIFS(B1:B5,C1:C5,\">2\",K1:K5)=0", "B1", "UNDECIDED~so 4 arguments leave one unpaired"),
        Case("a first argument that is not a range", "SUMIFS(1,C1:C5,\">2\")=0", "B1", "UNDECIDED~SUMIFS needs a cell range as its first argument"),
        Case("a first pair that is not a range", "COUNTIFS(C1,\">2\")=0", "B1", "UNDECIDED~needs a cell range for criteria range 1"),
        Case("a second pair that is not a range", "COUNTIFS(C1:C5,\">2\",7,\"high\")=0", "B1", "UNDECIDED~needs a cell range for criteria range 2"),
        Case("a criterion that is a range", "COUNTIFS(C1:C5,C1:C5)=1", "B1", "UNDECIDED~a range is not a single value"),
        Case("a shape mismatch in the second pair", "COUNTIFS(C1:C5,\">2\",K1:K4,\"high\")=0", "B1", "UNDECIDED~criteria range 2 is 1x4 cells, the first criteria range is 1x5"),
        Case("a sum range of the wrong shape", "SUMIFS(B1:B4,C1:C5,\">2\")=0", "B1", "UNDECIDED~criteria range 1 is 1x5 cells, the sum range is 1x4"),
        Case("an average range of the wrong shape", "AVERAGEIFS(B1:B4,C1:C5,\">2\")", "B1", "UNDECIDED~the average range is 1x4"),
        Case("a single-cell pair against a tall range", "COUNTIFS(C1:C5,\">2\",K1:K1,\"low\")=0", "B1", "UNDECIDED~criteria range 2 is 1x1 cells"),
        Case("an error under a second criterion", "COUNTIFS(C1:C5,\">2\",H1:H5,\">0\")=0", "B1", "UNDECIDED~#N/A under a criterion"),
        // The first criterion already fails everywhere, and the error must still be found: every pair is read.
        Case("an error is found even when an earlier pair never matches", "COUNTIFS(C1:C5,\">99\",H1:H5,\">0\")=0", "B1", "UNDECIDED~#N/A under a criterion"),
        Case("countifs arity", "COUNTIFS(C1:C5)=1", "B1", "UNDECIDED~COUNTIFS takes 2 to 8"),
        Case("countifs above the argument bound", "COUNTIFS(C1:C5,\">0\",C1:C5,\">1\",C1:C5,\">2\",C1:C5,\">3\",C1:C5,\">4\")=0", "B1", "UNDECIDED~more than 8 arguments"),
        Case("sumifs arity", "SUMIFS(B1:B5)=1", "B1", "UNDECIDED~SUMIFS takes 3 to 8"),
        Case("averageifs arity", "AVERAGEIFS(B1:B5,C1:C5)=1", "B1", "UNDECIDED~AVERAGEIFS takes 3 to 8"),
    )

    // ---------------------------------------------------------------- the written rules

    /** Twelve plural shapes, on disjoint single-column ranges so the block structure stays visible. */
    private val BLOCKS = listOf(
        Block("A1:A9", listOf(Rule(Kind.EXPRESSION, priority = 1, formulas = listOf("COUNTIFS(A1:A8,\">1\")=2"), format = FILL))),
        Block("B1:B9", listOf(Rule(Kind.EXPRESSION, priority = 2, formulas = listOf("SUMIFS(B1:B5,C1:C5,\">2\")=120"), format = FILL))),
        Block("C1:C9", listOf(Rule(Kind.EXPRESSION, priority = 3, formulas = listOf("AVERAGEIFS(B1:B5,C1:C5,\">2\")=40"), format = FILL))),
        Block("D1:D9", listOf(Rule(Kind.EXPRESSION, priority = 4, formulas = listOf("COUNTIFS(D1:D4,\"a*\",D1:D4,\"*e\")=1"), format = FILL))),
        Block("E1:E9", listOf(Rule(Kind.EXPRESSION, priority = 5, formulas = listOf("COUNTIFS(E1:E4,\"draft\",K1:K4,\"low\")=1"), format = FILL))),
        Block("F1:F9", listOf(Rule(Kind.EXPRESSION, priority = 6, formulas = listOf("SUMIFS(C1:C4,E1:E4,\"draft\")=3"), format = FILL))),
        Block("G1:G9", listOf(Rule(Kind.EXPRESSION, priority = 7, formulas = listOf("COUNTIFS(A1:A8,\"\",B1:B8,\">0\")=1"), format = FILL))),
        Block("H1:H9", listOf(Rule(Kind.EXPRESSION, priority = 8, formulas = listOf("COUNTIFS(A1:A8,\"<>\",A1:A8,\">0\")=3"), format = FILL))),
        Block("I1:I9", listOf(Rule(Kind.EXPRESSION, priority = 9, formulas = listOf("COUNTIFS(I1:I2,\"a~*b\")=1"), format = FILL))),
        Block("J1:J9", listOf(Rule(Kind.EXPRESSION, priority = 10, formulas = listOf("COUNTIFS(C1:C5,J1)=3"), format = FILL))),
        Block(
            "K1:K9",
            listOf(
                Rule(
                    Kind.EXPRESSION,
                    priority = 11,
                    formulas = listOf("COUNTIFS(\$A\$1:\$A\$4,\">0\")=3"),
                    format = FILL,
                ),
            ),
        ),
        Block(
            "L1:L9",
            listOf(
                Rule(
                    Kind.EXPRESSION,
                    priority = 12,
                    formulas = listOf("AND(COUNTIFS(C1:C5,\">2\")=3,SUMIFS(B1:B5,C1:C5,\">2\")=120)"),
                    format = FILL,
                ),
            ),
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
        val folder = File(args.firstOrNull() ?: "testing/build/r107/artifacts")
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
            Rule(Kind.EXPRESSION, priority = 1, formulas = listOf("=COUNTIFS(A1:A3,1)>0"), format = FILL)
        }
        refuses("an empty formula") { Rule(Kind.EXPRESSION, priority = 1, formulas = listOf(""), format = FILL) }

        // --- 3. the file ------------------------------------------------------------------------------------
        val sheet = XlsxStyledBook.StyledSheet(
            name = "Plural",
            cells = (1..5).flatMap { row ->
                listOf("A", "B", "C").map { column ->
                    XlsxStyledBook.StyledCell("$column$row", XlsxStyledBook.StyledVal.Num(row.toDouble()))
                }
            },
            conditionalFormats = BLOCKS,
        )
        val bytes = XlsxStyledBook.write(XlsxStyledBook.WorkbookSpec(sheets = listOf(sheet)))
        File(folder, "r107.xlsx").writeBytes(bytes)
        val sheet1 = part(bytes, "xl/worksheets/sheet1.xml")
        check(BLOCKS.sumOf { it.rules.size } == 12) { "the fixture is supposed to hold twelve rules" }
        for (block in BLOCKS) check(block.sqref in sheet1) { "the sqref ${block.sqref} is missing from sheet1.xml" }
        val read = XlsxStyledBook.read(bytes).sheets.single { it.name == "Plural" }.conditionalFormats
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

        val summary = "R107_PLURAL_OK assertions=$assertions cases=${CASES.size} decided=$decided refused=$refused " +
            "criteria=3 rules=${BLOCKS.sumOf { it.rules.size }}"
        File(folder, "summary.txt").writeText(summary + "\n", Charsets.UTF_8)
        println(summary)
    }
}
