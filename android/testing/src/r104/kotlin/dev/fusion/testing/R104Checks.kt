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
 * R104 spreadsheet contracts: what an `expression` rule's formula is allowed to say.
 *
 * Until R104 an `expression` rule was decided by two regexes (`ISBLANK(G4)`, `MOD(ROW(),2)=0`) and one naive
 * "split at the comparison operator" — so `AND(A1>5,B1<2)`, `LEN(A1)>3`, `ROUND(A1,2)=1.5` and every other real
 * formula were simply *not decided*, and a formula that merely *looked* like one of the two shapes was misread.
 * R104 replaces that with a total, bounded grammar: a tokenizer, a recursive-descent parser and an evaluator.
 *
 * Four things are asserted, each by a mechanism that can fail on its own:
 *
 * 1. **The cases.** [CASES] is a hand-written table of `formula -> answer at a cell`, including the arithmetic
 *    precedence that a split-on-operator reader gets wrong, the Excel type rules (`"5"=5` is FALSE, `"5">5` is
 *    TRUE), blanks, booleans, errors, and the shift interaction from R103.
 * 2. **The refusals.** Anything outside the grammar — an unknown function, a range, a sheet-qualified reference, a
 *    chained comparison, unbalanced parentheses, too many arguments, too much nesting — must come back
 *    `Undecided` with a reason, never `Decided` and never a fabricated `No`.
 * 3. **The file.** Fourteen rules whose formulas are grammar shapes are written to `r104.xlsx` and their exact
 *    formula text is read back out of the ZIP and out of `XlsxStyledBook.read`.
 * 4. **The independent lane.** `tools/check_r104_grammar.py` re-tokenizes, re-parses and re-evaluates every case
 *    with its own Python implementation of the same grammar and compares all three answers.
 *
 * Host JVM only. No Excel, LibreOffice or device ran: a verdict is a claim about which rule fires, never about what
 * a screen shows.
 */
object R104Checks {
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
     * One row of a small sheet. `A5` is deliberately absent from the map, because a cell a reader has no value for
     * is blank and that is exactly the shape `ISBLANK` has to see.
     */
    private val VALUES: Map<SheetRange.Cell, Value> = buildMap {
        put(this, "A1", Value.Num(10.0))
        put(this, "A2", Value.Num(3.5))
        put(this, "A3", Value.Txt("apple"))
        put(this, "A4", Value.Txt("Apple"))
        put(this, "A6", Value.Bool(true))
        put(this, "A7", Value.Num(0.0))
        put(this, "A8", Value.Num(-2.5))
        put(this, "A9", Value.Txt("  padded   text "))
        for (row in 1..9) put(this, "B$row", Value.Num(5.0))
        put(this, "C1", Value.Err("#N/A"))
        put(this, "C2", Value.Txt(""))
        for (row in 1..9) put(this, "D$row", Value.Num(row.toDouble()))
        put(this, "E1", Value.Num(100.0))
        put(this, "E2", Value.Num(1.0))
        for (row in 3..9) put(this, "E$row", Value.Num(0.0))
    }

    private class MapGrid(private val values: Map<SheetRange.Cell, Value>) : XlsxConditionalFormatVerdict.Grid {
        override fun value(cell: SheetRange.Cell): Value = values[cell] ?: Value.Blank
    }

    private val GRID = MapGrid(VALUES)

    // ---------------------------------------------------------------- the cases

    /**
     * One row of the table. [expected] is `TRUE`, `FALSE`, or `UNDECIDED` optionally followed by `~` and a
     * substring the reason must contain. [sqref] is the rule's range, which is what anchors a relative reference;
     * an empty [sqref] means the rule covers exactly [cell].
     */
    private data class Case(
        val label: String,
        val formula: String,
        val cell: String,
        val expected: String,
        val sqref: String = "",
    )

    private val CASES: List<Case> = listOf(
        // Arithmetic, and the precedence a "split at the operator" reader cannot see.
        Case("literal equality", "A1=10", "B1", "TRUE"),
        Case("precedence", "1+2*3=7", "B1", "TRUE"),
        Case("parentheses", "(1+2)*3=9", "B1", "TRUE"),
        Case("left-associative addition", "2*3+4=10", "B1", "TRUE"),
        Case("division", "10/4=2.5", "B1", "TRUE"),
        Case("unary minus", "-2*-3=6", "B1", "TRUE"),
        Case("false comparison", "1+2=4", "B1", "FALSE"),
        Case("greater than", "A1>5", "B1", "TRUE"),
        Case("negative literal against a cell", "A8<0", "B1", "TRUE"),
        Case("greater-or-equal on a decimal", "A2>=3.5", "B1", "TRUE"),
        Case("not-equal", "A2<>3.5", "B1", "FALSE"),
        Case("arithmetic on both sides", "A1=5+5", "B1", "TRUE"),
        Case("cell divided", "A1/4=2.5", "B1", "TRUE"),
        Case("exponent notation", "1e3=1000", "B1", "TRUE"),
        Case("leading-dot decimal", ".5*2=1", "B1", "TRUE"),
        Case("trailing-zero equality", "3=3.0", "B1", "TRUE"),
        Case("text in arithmetic is refused", "\"5\"+0=5", "B1", "UNDECIDED~is not a number"),
        Case("division by zero is refused", "1/0>0", "B1", "UNDECIDED~#DIV/0!"),
        // The Excel type rules this model already documents, now reachable through the grammar.
        Case("blank reads as zero", "A5=0", "B1", "TRUE"),
        Case("blank equals empty text", "A5=\"\"", "B1", "TRUE"),
        Case("text is case-insensitive", "A3=\"APPLE\"", "B1", "TRUE"),
        Case("boolean literal", "A6=TRUE", "B1", "TRUE"),
        Case("boolean against a number", "A6=1", "B1", "UNDECIDED~cannot"),
        Case("zero cell", "A7=0", "B1", "TRUE"),
        Case("numeric text is not a number", "\"5\"=5", "B1", "FALSE"),
        Case("numeric text sorts after numbers", "\"5\">5", "B1", "TRUE"),
        Case("text ordering is not decided", "A3<\"banana\"", "B1", "UNDECIDED~cannot order"),
        Case("error is not comparable", "C1=1", "B1", "UNDECIDED~cannot compare"),
        // A bare result is read the way Excel reads a rule's result: non-zero number fires, zero does not.
        Case("bare number", "A1", "B1", "TRUE"),
        Case("bare zero", "A7", "B1", "FALSE"),
        Case("bare arithmetic result of zero", "A1-10", "B1", "FALSE"),
        Case("bare text is not a condition", "A3", "B1", "UNDECIDED~text, not a condition"),
        Case("bare blank is false", "A5", "B1", "FALSE"),
        // Text functions.
        Case("LEN", "LEN(A3)=5", "B1", "TRUE"),
        Case("LEFT", "LEFT(A3,3)=\"app\"", "B1", "TRUE"),
        Case("RIGHT", "RIGHT(A3,3)=\"ple\"", "B1", "TRUE"),
        Case("UPPER", "UPPER(A3)=\"APPLE\"", "B1", "TRUE"),
        Case("LOWER", "LOWER(A4)=\"apple\"", "B1", "TRUE"),
        Case("TRIM collapses runs", "TRIM(A9)=\"padded text\"", "B1", "TRUE"),
        Case("TRIM then LEN", "LEN(TRIM(A9))=11", "B1", "TRUE"),
        Case("LEFT pads by truncating", "LEFT(A3,10)=A3", "B1", "TRUE"),
        Case("LEFT of a negative count", "LEFT(A3,-1)=\"\"", "B1", "UNDECIDED~whole number"),
        Case("LEN of a number", "LEN(1+2)=1", "B1", "TRUE"),
        Case("LEN of an error", "LEN(C1)=1", "B1", "UNDECIDED~error #N/A"),
        Case("quoted quote inside a literal", "LEN(\"a\"\"b\")=3", "B1", "TRUE"),
        Case("empty text", "C2=\"\"", "B1", "TRUE"),
        // Predicates.
        Case("ISBLANK on an absent cell", "ISBLANK(A5)", "B1", "TRUE"),
        Case("ISBLANK on a number", "ISBLANK(A1)", "B1", "FALSE"),
        Case("ISNUMBER", "ISNUMBER(A1)", "B1", "TRUE"),
        Case("ISNUMBER on text", "ISNUMBER(A3)", "B1", "FALSE"),
        Case("ISTEXT", "ISTEXT(A3)", "B1", "TRUE"),
        Case("ISTEXT on a number", "ISTEXT(A1)", "B1", "FALSE"),
        // Boolean functions.
        Case("AND of two true", "AND(A1>5,A2<5)", "B1", "TRUE"),
        Case("AND of one false", "AND(A1>5,A2>5)", "B1", "FALSE"),
        Case("OR", "OR(A1>100,A2>1)", "B1", "TRUE"),
        Case("NOT", "NOT(A1>5)", "B1", "FALSE"),
        Case("single-argument AND", "AND(A1>5)", "B1", "TRUE"),
        Case("NOT of a number", "NOT(A1)", "B1", "FALSE"),
        Case("a blank argument is false", "AND(A5)", "B1", "FALSE"),
        Case("text is not a condition inside AND", "AND(A3)", "B1", "UNDECIDED~not a condition"),
        Case("nested boolean functions", "OR(AND(A1>5,A2<5),NOT(A1>5))", "B1", "TRUE"),
        // Numeric functions.
        Case("ABS", "ABS(A8)=2.5", "B1", "TRUE"),
        Case("ROUND halves away from zero", "ROUND(2.5,0)=3", "B1", "TRUE"),
        Case("ROUND of a negative half", "ROUND(-2.5,0)=-3", "B1", "TRUE"),
        Case("ROUND to two places", "ROUND(2.346,2)=2.35", "B1", "TRUE"),
        // The half-away rule alone is not enough: the double nearest 2.675 is below it, so a reader that skips
        // Excel's fifteen-digit decimal step answers 2.67 here. `docs/R93-RESULTS.md` records the same case.
        Case("ROUND of a decimal half", "ROUND(2.675,2)=2.68", "B1", "TRUE"),
        Case("ROUND of a negative decimal half", "ROUND(-2.675,2)=-2.68", "B1", "TRUE"),
        Case("ROUND of a repeating binary decimal", "ROUND(1.005,2)=1.01", "B1", "TRUE"),
        Case("ROUND to a negative place", "ROUND(1234,-2)=1200", "B1", "TRUE"),
        Case("MOD", "MOD(7,3)=1", "B1", "TRUE"),
        Case("MOD takes the divisor's sign", "MOD(-7,3)=2", "B1", "TRUE"),
        Case("MOD of a negative divisor", "MOD(7,-3)=-2", "B1", "TRUE"),
        Case("MOD by zero is refused", "MOD(7,0)=1", "B1", "UNDECIDED~#DIV/0!"),
        Case("ROW", "ROW()=5", "D5", "TRUE"),
        Case("MOD of ROW", "MOD(ROW(),2)=0", "D4", "TRUE"),
        Case("MOD of ROW, not firing", "MOD(ROW(),3)=1", "D5", "FALSE"),
        Case("ARITHMETIC of a shifted reference", "A1=10", "D5", "TRUE"),
        // The R103 shift rule still holds inside the grammar: one rule over `F1:F3` anchors at F1, so the formula's
        // `A1` walks down column A (10, 3.5, "apple") and the third cell is the one that changes the answer.
        Case("a shifted reference at the anchor", "ISNUMBER(A1)", "F1", "TRUE", "F1:F3"),
        Case("a shifted reference one row down", "ISNUMBER(A1)", "F2", "TRUE", "F1:F3"),
        Case("a shifted reference onto text", "ISNUMBER(A1)", "F3", "FALSE", "F1:F3"),
        Case("a function of a shifted reference", "LEN(A1)=5", "F3", "TRUE", "F1:F3"),
        // A locked reference is read as written, wherever the scan is.
        Case("a locked reference at the anchor", "\$A\$1=10", "G1", "TRUE", "G1:G3"),
        Case("a locked reference away from the anchor", "\$A\$1=10", "G3", "TRUE", "G1:G3"),
        // Grammar refusals: each must be undecided with a reason, not silently "did not fire".
        Case("unknown function", "SUM(A1,A2)>0", "B1", "UNDECIDED~not a function"),
        Case("range operator", "A1:A3>0", "B1", "UNDECIDED~':'"),
        Case("sheet-qualified reference", "Sheet2!A1>0", "B1", "UNDECIDED~'!'"),
        Case("chained comparison", "1<2<3", "B1", "UNDECIDED~unexpected"),
        Case("unbalanced parentheses", "(1+2=3", "B1", "UNDECIDED~missing ')'"),
        Case("empty argument list", "AND()", "B1", "UNDECIDED~takes"),
        Case("too many arguments", "AND(1,2,3,4,5,6,7,8,9)", "B1", "UNDECIDED~arguments"),
        Case(
            "too much nesting",
            "(".repeat(17) + "1=1" + ")".repeat(17),
            "B1",
            "UNDECIDED~nesting",
        ),
        Case("two values in a row", "1 2", "B1", "UNDECIDED~unexpected"),
        Case("unterminated literal", "\"abc", "B1", "UNDECIDED~unterminated"),
        Case("concatenation is outside the grammar", "1&2=3", "B1", "UNDECIDED~'&'"),
        Case("truncated comparison", "A1>", "B1", "UNDECIDED~ends where a value was expected"),
        Case("row zero is not a cell", "A0>0", "B1", "UNDECIDED~nor a cell reference"),
        Case("row past the sheet", "A1048577>0", "B1", "UNDECIDED~nor a cell reference"),
        Case("a literal is not a function", "TRUE(1)", "B1", "UNDECIDED~not a function"),
        Case("a bare name is not a reference", "foo>0", "B1", "UNDECIDED~nor a cell reference"),
        Case("percent is outside the grammar", "10%>0", "B1", "UNDECIDED~'%'"),
    )

    // ---------------------------------------------------------------- the written rules

    /** Every grammar shape that a file can carry, one rule each, on disjoint single cells. */
    private val BLOCKS = listOf(
        Block("A1:A9", listOf(Rule(Kind.EXPRESSION, priority = 1, formulas = listOf("AND(A1>5,B1<6)"), format = FILL))),
        Block("C1:C9", listOf(Rule(Kind.EXPRESSION, priority = 2, formulas = listOf("LEN(A1)>3"), format = FILL))),
        Block("D1:D9", listOf(Rule(Kind.EXPRESSION, priority = 3, formulas = listOf("ROUND(A1/3,2)=3.33"), format = FILL))),
        Block("E1:E9", listOf(Rule(Kind.EXPRESSION, priority = 4, formulas = listOf("MOD(ROW(),3)=1"), format = FILL))),
        Block("F1:F9", listOf(Rule(Kind.EXPRESSION, priority = 5, formulas = listOf("TRIM(UPPER(A1))=\"APPLE\""), format = FILL))),
        Block("G1:G9", listOf(Rule(Kind.EXPRESSION, priority = 6, formulas = listOf("LEFT(A1,3)=RIGHT(A1,3)"), format = FILL))),
        Block("H1:H9", listOf(Rule(Kind.EXPRESSION, priority = 7, formulas = listOf("NOT(ISBLANK(A1))"), format = FILL))),
        Block("I1:I9", listOf(Rule(Kind.EXPRESSION, priority = 8, formulas = listOf("OR(ISNUMBER(A1),ISTEXT(A1))"), format = FILL))),
        Block("J1:J9", listOf(Rule(Kind.EXPRESSION, priority = 9, formulas = listOf("ABS(A1-5)<2"), format = FILL))),
        Block("K1:K9", listOf(Rule(Kind.EXPRESSION, priority = 10, formulas = listOf("A1*(B1-5)=0"), format = FILL))),
        Block("L1:L9", listOf(Rule(Kind.EXPRESSION, priority = 11, formulas = listOf("(A1+1)/2>=5"), format = FILL))),
        Block("M1:M9", listOf(Rule(Kind.EXPRESSION, priority = 12, formulas = listOf("1+2*3-4/2=5"), format = FILL))),
        Block("N1:N9", listOf(Rule(Kind.EXPRESSION, priority = 13, formulas = listOf("A1=\"10\""), format = FILL))),
        Block("O1:O9", listOf(Rule(Kind.EXPRESSION, priority = 14, formulas = listOf("AND(OR(A1>5,B1>5),NOT(A1>100))"), format = FILL))),
    )

    // ---------------------------------------------------------------- expectations

    private fun encode(outcome: Outcome): String = when (outcome) {
        is Outcome.Undecided -> "UNDECIDED~${outcome.reason}"
        is Outcome.Decided -> if (outcome.fired.isEmpty()) "FALSE" else "TRUE"
    }

    /** The answer a case must produce, without the reason text: `TRUE`, `FALSE` or `UNDECIDED`. */
    private fun shape(encoded: String): String = encoded.substringBefore('~')

    private fun reason(encoded: String): String? = if ('~' in encoded) encoded.substringAfter('~') else null

    @JvmStatic
    fun main(args: Array<String>) {
        val folder = File(args.firstOrNull() ?: "testing/build/r104/artifacts")
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
                    case.label,
                    case.formula,
                    case.cell,
                    sqref,
                    expected.substringBefore('~'),
                    shape(actual),
                    actual.substringAfter('~', "-"),
                ).joinToString("\t"),
            ).append('\n')
        }
        File(folder, "cases.tsv").writeText(caseRows.toString(), Charsets.UTF_8)

        // The three shapes the model itself refuses are still refused at construction.
        val block = Block("P1", listOf(Rule(Kind.EXPRESSION, priority = 1, formulas = listOf("A1>1"), format = FILL)))
        check(block.rules.size == 1) { "the fixture rule did not build" }
        refuses("a formula with a leading '='") {
            Rule(Kind.EXPRESSION, priority = 1, formulas = listOf("=A1>1"), format = FILL)
        }
        refuses("two formulas on an expression rule") {
            Rule(Kind.EXPRESSION, priority = 1, formulas = listOf("A1>1", "A2>1"), format = FILL)
        }
        refuses("an empty formula") { Rule(Kind.EXPRESSION, priority = 1, formulas = listOf(""), format = FILL) }

        // --- 3. the file ------------------------------------------------------------------------------------

        val sheet = XlsxStyledBook.StyledSheet(
            name = "Grammar",
            cells = (1..9).flatMap { row ->
                listOf("A", "B").map { column ->
                    XlsxStyledBook.StyledCell("$column$row", XlsxStyledBook.StyledVal.Num(row.toDouble()))
                }
            },
            conditionalFormats = BLOCKS,
        )
        val bytes = XlsxStyledBook.write(XlsxStyledBook.WorkbookSpec(sheets = listOf(sheet)))
        File(folder, "r104.xlsx").writeBytes(bytes)
        val sheet1 = part(bytes, "xl/worksheets/sheet1.xml")
        check(BLOCKS.sumOf { it.rules.size } == 14) { "the fixture is supposed to hold fourteen rules" }
        for (block in BLOCKS) check(block.sqref in sheet1) { "the sqref ${block.sqref} is missing from sheet1.xml" }

        val read = XlsxStyledBook.read(bytes).sheets.single { it.name == "Grammar" }.conditionalFormats
        check(read.size == BLOCKS.size) { "read ${read.size} blocks, wrote ${BLOCKS.size}" }
        for ((index, block) in BLOCKS.withIndex()) {
            check(read[index].sqref == block.sqref) { "block $index sqref ${read[index].sqref} != ${block.sqref}" }
            check(read[index].rules.size == block.rules.size) { "block $index lost a rule" }
            for ((ruleIndex, rule) in block.rules.withIndex()) {
                val formula = rule.formulas.first()
                val escaped = formula.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                // The writer may or may not escape the quote character in text content; both are the same formula.
                val quoted = escaped.replace("\"", "&quot;")
                check("<formula>$escaped</formula>" in sheet1 || "<formula>$quoted</formula>" in sheet1) {
                    "the formula '$formula' is not in sheet1.xml verbatim"
                }
                check(read[index].rules[ruleIndex].formulas == rule.formulas) {
                    "rule ${rule.priority} round-tripped formulas ${read[index].rules[ruleIndex].formulas}"
                }
                check(read[index].rules[ruleIndex].kind == rule.kind) { "rule ${rule.priority} changed kind" }
            }
        }

        val ruleRows = StringBuilder()
        for (block in BLOCKS) {
            for (rule in block.rules) {
                ruleRows.append(listOf(block.sqref, rule.priority.toString(), rule.kind.token, rule.formulas.joinToString(",")).joinToString("\t")).append('\n')
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

        val summary = "R104_GRAMMAR_OK assertions=$assertions cases=${CASES.size} decided=$decided refused=$refused " +
            "functions=16 rules=${BLOCKS.sumOf { it.rules.size }}"
        File(folder, "summary.txt").writeText(summary + "\n", Charsets.UTF_8)
        println(summary)
    }
}
