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
 * R108 spreadsheet contracts: the text functions — `MID`, `FIND`, `SEARCH`, `EXACT`, `SUBSTITUTE`, `REPT`, `VALUE`.
 *
 * The grammar could already read `LEFT(B1,4)="2024"` and `LEN(C1)>10`, but a conditional format that tests a *label*
 * almost always needs one of the seven shapes below — `SEARCH("draft",B1)>0`, `MID(D1,4,2)=="Q3"`,
 * `EXACT(E1,"paid")`, `SUBSTITUTE(F1,"-","")>` — and every one of them was refused by name. R108 adds them.
 *
 * Four things are asserted, each by a mechanism that can fail on its own:
 *
 * 1. **The cases.** [CASES] is a hand-written table of `formula -> answer at a cell`, covering Excel's documented
 *    rules: indices are one-based and whole; `MID` answers empty text past the end but `#VALUE!` for a start below 1
 *    or a negative width; `FIND` is case-sensitive and wildcard-free while `SEARCH` is case-insensitive and
 *    wildcard-aware and returns where a match *starts*; `EXACT` is the grammar's only case-sensitive comparison;
 *    `SUBSTITUTE` replaces all occurrences or one instance, case-sensitively, and leaves the text alone when the
 *    instance is past the end or the old text is empty; `REPT` is bounded by Excel's 32767-character cell text
 *    ceiling; `VALUE` reads the model's own number grammar and refuses everything else (no currency, no percent).
 * 2. **The refusals.** Arity, non-numeric index text, the search scan bound, and every `#VALUE!` the documented
 *    rules produce.
 * 3. **The file.** Twelve text-formula rules are written to `r108.xlsx`, read back out of the ZIP, and read again
 *    through `XlsxStyledBook.read`.
 * 4. **The independent lane.** `tools/check_r108_text_functions.py` re-tokenizes, re-parses and re-evaluates every
 *    case with its own Python implementation, and `tools/test_r108_text_functions.py` tests that implementation.
 *
 * Host JVM only. No Excel, LibreOffice or device ran: a verdict is a claim about which rule fires, never about what
 * a screen shows.
 */
object R108Checks {
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
     * A sheet built to break a text function: the same word in three cases, an empty-text cell, a blank, text with
     * surrounding spaces, a literal `*`, a numeric-looking text, numbers (for the text coercion), and strings whose
     * prefixes and substrings overlap (`cat`, `concat`, `scatter`).
     */
    private val VALUES: Map<SheetRange.Cell, Value> = buildMap {
        put(this, "A1", Value.Txt("alpha"))
        put(this, "A2", Value.Txt("Alpha"))
        put(this, "A3", Value.Txt("ALPHA"))
        put(this, "A4", Value.Txt("beta"))
        put(this, "A5", Value.Txt(""))
        // A6 is absent: the blank.
        put(this, "A7", Value.Txt("  padded  "))
        put(this, "A8", Value.Txt("a*b"))
        put(this, "A9", Value.Txt("100"))
        put(this, "A10", Value.Txt("banana"))
        for (row in 1..5) put(this, "B$row", Value.Num(row.toDouble()))
        put(this, "C1", Value.Txt("cat"))
        put(this, "C2", Value.Txt("concat"))
        put(this, "C3", Value.Txt("scatter"))
        put(this, "C4", Value.Txt("dog"))
        put(this, "C5", Value.Txt("CAT"))
        put(this, "D1", Value.Txt("draft-A"))
        put(this, "D2", Value.Txt("draft-B"))
        put(this, "D3", Value.Txt("final"))
        put(this, "D4", Value.Txt("Draft-C"))
        put(this, "E1", Value.Txt("x"))
        put(this, "E2", Value.Txt(""))
        put(this, "E3", Value.Txt("xx"))
        put(this, "E4", Value.Txt("abc"))
        put(this, "F1", Value.Num(12345.0))
        put(this, "F2", Value.Num(3.5))
        put(this, "F3", Value.Num(-7.0))
        put(this, "F4", Value.Bool(true))
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
        // --- MID ---------------------------------------------------------------------------------------------
        Case("mid of the middle", "MID(\"alpha\",2,3)=\"lph\"", "B1", "TRUE"),
        Case("mid from the first character", "MID(\"alpha\",1,5)=\"alpha\"", "B1", "TRUE"),
        Case("mid past the end truncates at the end", "MID(\"alpha\",4,10)=\"ha\"", "B1", "TRUE"),
        Case("mid starting one past the end is empty", "MID(\"alpha\",6,3)=\"\"", "B1", "TRUE"),
        Case("mid starting far past the end is empty", "MID(\"alpha\",7,3)=\"\"", "B1", "TRUE"),
        Case("mid with a zero width is empty", "MID(\"alpha\",2,0)=\"\"", "B1", "TRUE"),
        Case("mid of an empty text", "MID(\"\",1,2)=\"\"", "B1", "TRUE"),
        Case("mid of a number reads its general text", "MID(12345,2,2)=\"23\"", "B1", "TRUE"),
        Case("mid of a cell", "MID(A1,2,2)=\"lp\"", "B1", "TRUE"),
        Case("mid start below one is an error", "MID(\"alpha\",0,2)", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case(
            "an error value in a comparison is refused by the comparison",
            "MID(\"alpha\",0,2)=\"l\"",
            "B1",
            "UNDECIDED~cannot compare error #VALUE!",
        ),
        Case("mid negative width is an error", "MID(\"alpha\",1,-1)", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case("mid arity", "MID(\"alpha\",2)=\"lp\"", "B1", "UNDECIDED~MID takes 3"),
        // --- FIND --------------------------------------------------------------------------------------------
        Case("find a character", "FIND(\"l\",\"alpha\")=2", "B1", "TRUE"),
        Case("mid fractional start is an error", "MID(\"alpha\",1.5,2)", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case("find from a start position", "FIND(\"a\",\"alpha\",2)=5", "B1", "TRUE"),
        Case("find a substring", "FIND(\"cat\",\"concat\")=4", "B1", "TRUE"),
        Case("find an empty needle answers the start", "FIND(\"\",\"alpha\")=1", "B1", "TRUE"),
        Case("find an empty needle at a start answers that start", "FIND(\"\",\"alpha\",3)=3", "B1", "TRUE"),
        Case("find a case-sensitive miss is an error", "FIND(\"L\",\"alpha\")", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case("find a missing needle is an error", "FIND(\"z\",\"alpha\")", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case("find a start below one is an error", "FIND(\"l\",\"alpha\",0)", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case("find does not read a wildcard", "FIND(\"*\",\"a*b\")=2", "B1", "TRUE"),
        Case("find inside another function", "FIND(\"A\",UPPER(\"alpha\"))=1", "B1", "TRUE"),
        Case("find arity", "FIND(\"a\")=1", "B1", "UNDECIDED~FIND takes 2 to 3"),
        // --- SEARCH ------------------------------------------------------------------------------------------
        Case("search is case-insensitive", "SEARCH(\"L\",\"alpha\")=2", "B1", "TRUE"),
        Case("search from a start position", "SEARCH(\"a\",\"alpha\",2)=5", "B1", "TRUE"),
        Case("search a question mark", "SEARCH(\"?p\",\"alpha\")=2", "B1", "TRUE"),
        Case("search a trailing star", "SEARCH(\"a*\",\"alpha\")=1", "B1", "TRUE"),
        Case("search a leading star answers the first position", "SEARCH(\"*a\",\"alpha\")=1", "B1", "TRUE"),
        Case("search a star in the middle", "SEARCH(\"l*h\",\"alpha\")=2", "B1", "TRUE"),
        Case("search an escaped star", "SEARCH(\"a~*\",\"a*b\")=1", "B1", "TRUE"),
        Case("find a start past the end is an error", "FIND(\"a\",\"alpha\",7)", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case("search an escaped question mark misses", "SEARCH(\"~?\",\"a*b\")", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case("search an empty pattern answers the start", "SEARCH(\"\",\"alpha\")=1", "B1", "TRUE"),
        Case("search from a start at the match", "SEARCH(\"p\",\"alpha\",3)=3", "B1", "TRUE"),
        Case("search a number", "SEARCH(\"2\",\"12345\")=2", "B1", "TRUE"),
        Case(
            "search above the scan bound is refused",
            "SEARCH(\"" + "a".repeat(60) + "\",REPT(\"a\",20000))",
            "B1",
            "UNDECIDED~above the 1000000-step scan bound",
        ),
        Case("search arity", "SEARCH(\"a\",\"alpha\",1,2)=1", "B1", "UNDECIDED~SEARCH takes 2 to 3"),
        // --- EXACT -------------------------------------------------------------------------------------------
        Case("exact on equal text", "EXACT(\"alpha\",\"alpha\")", "B1", "TRUE"),
        Case("exact is case-sensitive", "EXACT(\"alpha\",\"Alpha\")", "B1", "FALSE"),
        Case("exact of two cells", "EXACT(A1,A2)", "B1", "FALSE"),
        Case("exact of a cell and a literal", "EXACT(A1,\"alpha\")", "B1", "TRUE"),
        Case("exact of two empty texts", "EXACT(\"\",\"\")", "B1", "TRUE"),
        Case("exact reads a number as its general text", "EXACT(1,\"1\")", "B1", "TRUE"),
        Case("exact reads a boolean as TRUE", "EXACT(TRUE,\"TRUE\")", "B1", "TRUE"),
        Case("exact differs from equals on case", "EXACT(\"alpha\",\"ALPHA\")", "B1", "FALSE"),
        Case("exact arity", "EXACT(\"a\")", "B1", "UNDECIDED~EXACT takes 2"),
        // --- SUBSTITUTE --------------------------------------------------------------------------------------
        Case("substitute every occurrence", "SUBSTITUTE(\"a-b-c\",\"-\",\"+\")=\"a+b+c\"", "B1", "TRUE"),
        Case("substitute one instance", "SUBSTITUTE(\"a-b-c\",\"-\",\"+\",2)=\"a-b+c\"", "B1", "TRUE"),
        Case("substitute the first instance", "SUBSTITUTE(\"alpha\",\"a\",\"X\",1)=\"Xlpha\"", "B1", "TRUE"),
        Case("substitute an instance past the end changes nothing", "SUBSTITUTE(\"a-b-c\",\"-\",\"+\",9)=\"a-b-c\"", "B1", "TRUE"),
        Case("substitute a repeated letter", "SUBSTITUTE(\"alpha\",\"a\",\"AA\")=\"AAlphAA\"", "B1", "TRUE"),
        Case("substitute is case-sensitive", "SUBSTITUTE(\"alpha\",\"A\",\"X\")=\"alpha\"", "B1", "TRUE"),
        Case("substitute an empty old text changes nothing", "SUBSTITUTE(\"alpha\",\"\",\"X\")=\"alpha\"", "B1", "TRUE"),
        Case("substitute in a number", "SUBSTITUTE(12345,\"3\",\"X\")=\"12X45\"", "B1", "TRUE"),
        Case("search a missing pattern is an error", "SEARCH(\"z\",\"alpha\")", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case("substitute arity", "SUBSTITUTE(\"a\",\"a\")", "B1", "UNDECIDED~SUBSTITUTE takes 3 to 4"),
        Case(
            "substitute above the text bound is an error",
            "SUBSTITUTE(REPT(\"a\",20000),\"a\",REPT(\"b\",2))",
            "B1",
            "UNDECIDED~evaluates to #VALUE!",
        ),
        // --- REPT --------------------------------------------------------------------------------------------
        Case("repeat a short text", "REPT(\"ab\",3)=\"ababab\"", "B1", "TRUE"),
        Case("repeat zero times is empty", "REPT(\"ab\",0)=\"\"", "B1", "TRUE"),
        Case("repeat a number", "REPT(1,3)=\"111\"", "B1", "TRUE"),
        Case("repeat an empty text is empty", "REPT(\"\",1000)=\"\"", "B1", "TRUE"),
        Case("repeat at the text bound", "LEN(REPT(\"a\",32767))=32767", "B1", "TRUE"),
        Case("substitute an instance below one is an error", "SUBSTITUTE(\"a\",\"a\",\"b\",0)", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case("repeat one past the text bound is an error", "REPT(\"a\",32768)", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case("repeat two characters past the bound is an error", "REPT(\"ab\",16384)", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case("rept arity", "REPT(\"ab\")", "B1", "UNDECIDED~REPT takes 2"),
        // --- VALUE -------------------------------------------------------------------------------------------
        Case("value of an integer text", "VALUE(\"100\")=100", "B1", "TRUE"),
        Case("value trims spaces", "VALUE(\" 42 \")=42", "B1", "TRUE"),
        Case("value of scientific notation", "VALUE(\"1e2\")=100", "B1", "TRUE"),
        Case("value of a cell holding a number text", "VALUE(A9)=100", "B1", "TRUE"),
        Case("value of a number is itself", "VALUE(5)=5", "B1", "TRUE"),
        Case("repeat a negative count is an error", "REPT(\"ab\",-1)", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case("value refuses hexadecimal text", "VALUE(\"0x10\")", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case("value refuses a comma decimal", "VALUE(\"1,5\")", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case("value refuses words", "VALUE(\"abc\")", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case("value refuses an empty text", "VALUE(\"\")", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case("value refuses a boolean", "VALUE(TRUE)", "B1", "UNDECIDED~evaluates to #VALUE!"),
        Case("value arity", "VALUE(\"1\",\"2\")=1", "B1", "UNDECIDED~VALUE takes 1"),
        // --- composition -------------------------------------------------------------------------------------
        Case("mid and left agree on a prefix", "LEFT(\"alpha\",2)=MID(\"alpha\",1,2)", "B1", "TRUE"),
        Case("search and find agree when the case matches", "SEARCH(\"l\",\"alpha\")=FIND(\"l\",\"alpha\")", "B1", "TRUE"),
        Case("len of a substitution", "LEN(SUBSTITUTE(\"banana\",\"a\",\"\"))=3", "B1", "TRUE"),
        Case("find after a substitution", "FIND(\"b\",SUBSTITUTE(\"banana\",\"a\",\"\"))=1", "B1", "TRUE"),
        Case("and of two text tests", "AND(EXACT(\"x\",\"x\"),FIND(\"a\",\"alpha\")=1)", "B1", "TRUE"),
        Case("a text result is not a condition", "MID(\"alpha\",1,2)", "B1", "UNDECIDED~is text, not a condition"),
        Case("a text comparison drives a rule", "MID(A1,1,1)=\"a\"", "B1", "TRUE"),
        // --- the shift rule ------------------------------------------------------------------------------------
        // One rule over `K1:K3` anchors at K1, so `A1` is A2 at K2 (Alpha) and A3 at K3 (ALPHA).
        Case("a shifted text reference at the anchor", "EXACT(A1,\"alpha\")", "K1", "TRUE", "K1:K3"),
        Case("a shifted text reference one row down", "EXACT(A1,\"alpha\")", "K2", "FALSE", "K1:K3"),
        Case("a shifted text reference two rows down", "EXACT(A1,\"alpha\")", "K3", "FALSE", "K1:K3"),
        Case("a locked text reference does not move", "EXACT(\$A\$1,\"alpha\")", "K3", "TRUE", "K1:K3"),
        Case("a shifted mid reads the next row", "MID(A1,2,2)=\"lp\"", "K2", "TRUE", "K1:K3"),
    )

    // ---------------------------------------------------------------- the written rules

    /** Twelve text-formula shapes, on disjoint single-column ranges so the block structure stays visible. */
    private val BLOCKS = listOf(
        Block("A1:A9", listOf(Rule(Kind.EXPRESSION, priority = 1, formulas = listOf("MID(A1,2,3)=\"lph\""), format = FILL))),
        Block("B1:B9", listOf(Rule(Kind.EXPRESSION, priority = 2, formulas = listOf("FIND(\"l\",\"alpha\")=2"), format = FILL))),
        Block("C1:C9", listOf(Rule(Kind.EXPRESSION, priority = 3, formulas = listOf("SEARCH(\"L\",\"alpha\")=2"), format = FILL))),
        Block("D1:D9", listOf(Rule(Kind.EXPRESSION, priority = 4, formulas = listOf("EXACT(D1,\"draft-A\")"), format = FILL))),
        Block("E1:E9", listOf(Rule(Kind.EXPRESSION, priority = 5, formulas = listOf("SUBSTITUTE(\"a-b\",\"-\",\"+\")=\"a+b\""), format = FILL))),
        Block("F1:F9", listOf(Rule(Kind.EXPRESSION, priority = 6, formulas = listOf("REPT(\"ab\",2)=\"abab\""), format = FILL))),
        Block("G1:G9", listOf(Rule(Kind.EXPRESSION, priority = 7, formulas = listOf("VALUE(\"1e2\")=100"), format = FILL))),
        Block(
            "H1:H9",
            listOf(
                Rule(
                    Kind.EXPRESSION,
                    priority = 8,
                    formulas = listOf("AND(EXACT(\"x\",\"x\"),FIND(\"a\",\"alpha\")=1)"),
                    format = FILL,
                ),
            ),
        ),
        Block(
            "I1:I9",
            listOf(Rule(Kind.EXPRESSION, priority = 9, formulas = listOf("LEFT(\"alpha\",2)=MID(\"alpha\",1,2)"), format = FILL)),
        ),
        Block(
            "J1:J9",
            listOf(Rule(Kind.EXPRESSION, priority = 10, formulas = listOf("LEN(SUBSTITUTE(\"banana\",\"a\",\"\"))=3"), format = FILL)),
        ),
        Block(
            "K1:K9",
            listOf(
                Rule(
                    Kind.EXPRESSION,
                    priority = 11,
                    formulas = listOf("EXACT(\$A\$1,\"alpha\")"),
                    format = FILL,
                ),
            ),
        ),
        Block("L1:L9", listOf(Rule(Kind.EXPRESSION, priority = 12, formulas = listOf("SEARCH(\"?p\",\"alpha\")=2"), format = FILL))),
    )

    private fun encode(outcome: Outcome): String = when (outcome) {
        is Outcome.Undecided -> "UNDECIDED~${outcome.reason}"
        is Outcome.Decided -> if (outcome.fired.isEmpty()) "FALSE" else "TRUE"
    }

    private fun shape(encoded: String): String = encoded.substringBefore('~')

    private fun reason(encoded: String): String? = if ('~' in encoded) encoded.substringAfter('~') else null

    @JvmStatic
    fun main(args: Array<String>) {
        val folder = File(args.firstOrNull() ?: "testing/build/r108/artifacts")
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
            Rule(Kind.EXPRESSION, priority = 1, formulas = listOf("=MID(A1,1,2)=\"al\""), format = FILL)
        }
        refuses("an empty formula") { Rule(Kind.EXPRESSION, priority = 1, formulas = listOf(""), format = FILL) }

        // --- 3. the file ------------------------------------------------------------------------------------
        val sheet = XlsxStyledBook.StyledSheet(
            name = "Text",
            cells = (1..5).flatMap { row ->
                listOf("A", "B", "C").map { column ->
                    XlsxStyledBook.StyledCell("$column$row", XlsxStyledBook.StyledVal.Num(row.toDouble()))
                }
            },
            conditionalFormats = BLOCKS,
        )
        val bytes = XlsxStyledBook.write(XlsxStyledBook.WorkbookSpec(sheets = listOf(sheet)))
        File(folder, "r108.xlsx").writeBytes(bytes)
        val sheet1 = part(bytes, "xl/worksheets/sheet1.xml")
        check(BLOCKS.sumOf { it.rules.size } == 12) { "the fixture is supposed to hold twelve rules" }
        for (block in BLOCKS) check(block.sqref in sheet1) { "the sqref ${block.sqref} is missing from sheet1.xml" }
        val read = XlsxStyledBook.read(bytes).sheets.single { it.name == "Text" }.conditionalFormats
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
        check("\$A\$1" in sheet1) { "the locked reference lost its markers" }

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

        val summary = "R108_TEXT_OK assertions=$assertions cases=${CASES.size} decided=$decided refused=$refused " +
            "functions=7 bound=$MAX_TEXT_LENGTH rules=${BLOCKS.sumOf { it.rules.size }}"
        File(folder, "summary.txt").writeText(summary + "\n", Charsets.UTF_8)
        println(summary)
    }

    private const val MAX_TEXT_LENGTH = 32767
}
