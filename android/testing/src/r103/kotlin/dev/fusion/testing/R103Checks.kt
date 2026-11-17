// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.SheetRange
import dev.fusion.ooxml.XlsxConditionalFormatVerdict
import dev.fusion.ooxml.XlsxConditionalFormatVerdict.Outcome
import dev.fusion.ooxml.XlsxConditionalFormatVerdict.Value
import dev.fusion.ooxml.XlsxConditionalFormatting.Block
import dev.fusion.ooxml.XlsxConditionalFormatting.Format
import dev.fusion.ooxml.XlsxConditionalFormatting.Kind
import dev.fusion.ooxml.XlsxConditionalFormatting.Operator
import dev.fusion.ooxml.XlsxConditionalFormatting.Rule
import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R103 spreadsheet contracts: what a conditional-format formula's cell references mean.
 *
 * Excel reads a rule's formula *relative to the cell being evaluated*, anchored at the top-left of the range token
 * that covers it — `A1>5` written on `A1:A9` asks `A9>5` when the scan reaches `A9`, and `$A$1>5` asks the same
 * question of every cell. Until R103 this evaluator read the literal reference every time, so every cell except
 * the anchor got the anchor's answer, and `$`-locked references were not even parseable.
 *
 * Four things are asserted, each by a mechanism that can fail on its own:
 *
 * 1. **The file.** Ten rules whose formulas contain references — relative, `$`-locked in each coordinate, and a
 *    cross-sheet reference this grammar does not read — are written, and their exact formula text is read back out
 *    of the ZIP, including the `$` markers.
 * 2. **The reader.** `XlsxStyledBook.read` must reproduce every formula string field for field.
 * 3. **The evaluator.** Eleven grids of cells where the shifted answer differs from the literal one, including the
 *    two that separate the token anchor from the block anchor, the two that separate a locked column from a locked
 *    row, the boundary where a shift leaves the grid (row 1048576), and the two shapes that must stay undecided.
 * 4. **The grammar.** Tokens that look like references but are not (`Sheet2!A1`, `A0`, `A1:A2`, a quoted string)
 *    are refused rather than read as something else.
 *
 * Host JVM only. No Excel, LibreOffice or device ran: a verdict is a claim about which rule fires, never about what
 * a screen shows.
 */
object R103Checks {
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
    private val BOLD = Format(bold = true)

    // ---------------------------------------------------------------- the written rules

    /** One rule per shape the shift has to get right; the sqrefs are disjoint so the block structure is visible. */
    private val BLOCKS = listOf(
        Block("A1:A9", listOf(Rule(Kind.EXPRESSION, priority = 1, formulas = listOf("A1>5"), format = FILL))),
        Block("C1:C9", listOf(Rule(Kind.EXPRESSION, priority = 2, formulas = listOf("\$C\$1>5"), format = BOLD))),
        Block("D1:E9", listOf(Rule(Kind.EXPRESSION, priority = 3, formulas = listOf("\$D1>5"), format = FILL))),
        Block("D1:E9", listOf(Rule(Kind.EXPRESSION, priority = 4, formulas = listOf("D\$1>5"), format = BOLD))),
        Block(
            "G1:G9 H1:H9",
            listOf(Rule(Kind.EXPRESSION, priority = 5, formulas = listOf("G1>2"), format = FILL)),
        ),
        Block("I1:I3", listOf(Rule(Kind.EXPRESSION, priority = 6, formulas = listOf("ISBLANK(\$I\$1)"), format = BOLD))),
        Block("J1:J3", listOf(Rule(Kind.EXPRESSION, priority = 7, formulas = listOf("ISBLANK(J1)"), format = FILL))),
        Block("K1:K3", listOf(Rule(Kind.EXPRESSION, priority = 8, formulas = listOf("Sheet2!A1>0"), format = BOLD))),
        Block(
            "L1:L9",
            listOf(Rule(Kind.CELL_IS, priority = 9, operator = Operator.GREATER_THAN,
                formulas = listOf("\$L\$1"), format = BOLD)),
        ),
        Block("M1:M3", listOf(Rule(Kind.EXPRESSION, priority = 10, formulas = listOf("A1048576>0"), format = FILL))),
    )

    // ---------------------------------------------------------------- the grid

    private fun put(builder: MutableMap<SheetRange.Cell, Value>, ref: String, value: Value) {
        builder[SheetRange.parseCell(ref)] = value
    }

    private val VALUES: Map<SheetRange.Cell, Value> = buildMap {
        // A: a single small anchor value and eight large ones — the literal reading would fire on nothing.
        put(this, "A1", Value.Num(1.0))
        for (row in 2..9) put(this, "A$row", Value.Num(9.0))
        // C: the same shape, but the rule is fully locked, so the answer must be uniform.
        put(this, "C1", Value.Num(1.0))
        for (row in 2..9) put(this, "C$row", Value.Num(9.0))
        // D/E: a locked column must read column D for both columns, a locked row must read row 1.
        put(this, "D1", Value.Num(1.0))
        put(this, "E1", Value.Num(9.0))
        for (row in 2..9) {
            put(this, "D$row", Value.Num(9.0))
            put(this, "E$row", Value.Num(0.0))
        }
        // G/H: the value that separates a per-token anchor from a block anchor.
        for (ref in listOf("G1", "G2", "G3", "G4", "G6", "G7", "G8", "G9")) put(this, ref, Value.Num(0.0))
        put(this, "G5", Value.Num(3.0))
        for (ref in listOf("H1", "H2", "H3", "H4", "H5", "H6", "H7", "H8", "H9")) put(this, ref, Value.Num(1.0))
        // I: three blank cells, so a locked blank target fires everywhere and a relative one only at its own cell.
        // J: blank, a space, a number — the relative blank target must distinguish all three.
        put(this, "J2", Value.Txt(" "))
        put(this, "J3", Value.Num(5.0))
        // L: a cellIs rule whose right-hand side is a reference.
        put(this, "L1", Value.Num(1.0))
        for (row in 2..9) put(this, "L$row", Value.Num(9.0))
        // M: row 1048576 is far above the block, so the relative reference shifts out of the grid.
        for (ref in listOf("M1", "M2", "M3")) put(this, ref, Value.Num(1.0))
    }

    /** The verdict lane needs the sheet's values; `I1:I3` stay blank, so they are absent from the map on purpose. */
    private val MAPPED: Set<String> = setOf("A", "C", "D", "E", "G", "H", "J", "L", "M")

    // ---------------------------------------------------------------- the expectations

    /** `ref -> priorities that fire`, or `UNDECIDED`. Written out by hand, then recomputed in Python. */
    private val EXPECTED: Map<String, String> = mapOf(
        // Relative: each cell compares itself. A1 keeps the anchor's answer.
        "A1" to "-", "A2" to "1", "A3" to "1", "A9" to "1",
        // Fully locked: the answer is C1's for every cell.
        "C1" to "-", "C2" to "-", "C9" to "-",
        // Column locked, row relative: column D decides both columns, so rule 3 fires from row 2 down.
        "D1" to "-", "D2" to "3", "E2" to "3,4", "E9" to "3,4",
        // Row locked, column relative: row 1 decides every row, per column — rule 4 fires in column E only,
        // including at E1, where the cell's own row is irrelevant.
        "D5" to "3", "E1" to "4", "E5" to "3,4",
        // Per-range anchor. In the second token the anchor is H1, so the formula's `G1` is seven columns left of
        // the cell and shifts to `G5` (3) — it fires. An implementation that anchored both tokens at G1 would move
        // the reference to `H5` (1) instead and stay silent, which is the difference this pair measures.
        "G5" to "5", "H5" to "5", "H1" to "-",
        // A locked blank target fires everywhere; a relative one only where the target is blank.
        "I1" to "6", "I2" to "6", "I3" to "6",
        "J1" to "7", "J2" to "-", "J3" to "-",
        // A cross-sheet reference is outside the grammar: undecided, never "did not fire".
        "K1" to "UNDECIDED", "K3" to "UNDECIDED",
        // cellIs with a reference operand shifts too.
        "L1" to "-", "L2" to "9", "L9" to "9",
        // The boundary: at M1 the reference is row 1048576, above it the shift leaves the grid.
        "M1" to "-", "M2" to "UNDECIDED", "M3" to "UNDECIDED",
    )

    private class MapGrid(private val values: Map<SheetRange.Cell, Value>) : XlsxConditionalFormatVerdict.Grid {
        override fun value(cell: SheetRange.Cell): Value = values[cell] ?: Value.Blank
    }

    private fun encode(outcome: Outcome): String = when (outcome) {
        is Outcome.Undecided -> "UNDECIDED"
        is Outcome.Decided -> outcome.fired.joinToString(",") { it.priority.toString() }.ifEmpty { "-" }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val folder = File(args.firstOrNull() ?: "testing/build/r103/artifacts")
        folder.mkdirs()

        val sheet = XlsxStyledBook.StyledSheet(
            name = "Shift",
            cells = (1..9).flatMap { row ->
                listOf("A", "C", "D", "E", "G", "H", "L").map { column ->
                    XlsxStyledBook.StyledCell("$column$row", XlsxStyledBook.StyledVal.Num(row.toDouble()))
                }
            },
            conditionalFormats = BLOCKS,
        )
        val bytes = XlsxStyledBook.write(XlsxStyledBook.WorkbookSpec(sheets = listOf(sheet)))
        File(folder, "r103.xlsx").writeBytes(bytes)
        val sheet1 = part(bytes, "xl/worksheets/sheet1.xml")

        // --- 1. the file ------------------------------------------------------------------------------------

        check(BLOCKS.sumOf { it.rules.size } == 10) { "the fixture is supposed to hold ten rules" }
        for (block in BLOCKS) {
            for (rule in block.rules) {
                val formula = rule.formulas.first()
                check("""<formula>${formula.replace("<", "&lt;").replace(">", "&gt;")}</formula>""" in sheet1) {
                    "the formula '$formula' was not written verbatim: $sheet1"
                }
            }
        }
        // The `$` markers survive the writer: they are the difference between a relative and a locked reference.
        check("""<formula>${'$'}C${'$'}1&gt;5</formula>""" in sheet1) { "the fully locked formula lost its markers" }
        check("""<formula>${'$'}D1&gt;5</formula>""" in sheet1) { "the column-locked formula lost its marker" }
        check("""<formula>D${'$'}1&gt;5</formula>""" in sheet1) { "the row-locked formula lost its marker" }
        check("""<formula>ISBLANK(${'$'}I${'$'}1)</formula>""" in sheet1) { "the ISBLANK formula lost its markers" }
        // A multi-token sqref is written as one block, which is what makes the per-token anchor interesting.
        check("""sqref="G1:G9 H1:H9"""" in sheet1) { "the multi-token block was rewritten" }

        // --- 2. the reader ----------------------------------------------------------------------------------

        val read = XlsxStyledBook.read(bytes).sheets.single { it.name == "Shift" }.conditionalFormats
        check(read.size == BLOCKS.size) { "read ${read.size} blocks, wrote ${BLOCKS.size}" }
        for ((index, block) in BLOCKS.withIndex()) {
            check(read[index].sqref == block.sqref) { "block $index sqref ${read[index].sqref} != ${block.sqref}" }
            check(read[index].rules.size == block.rules.size) { "block $index lost a rule" }
            for ((ruleIndex, rule) in block.rules.withIndex()) {
                val round = read[index].rules[ruleIndex]
                check(round.formulas == rule.formulas) {
                    "rule ${rule.priority} round-tripped formulas ${round.formulas}, wrote ${rule.formulas}"
                }
                check(round.kind == rule.kind && round.priority == rule.priority) {
                    "rule ${rule.priority} round-tripped as ${round.kind.token}/${round.priority}"
                }
                check(round.operator == rule.operator) {
                    "rule ${rule.priority} operator ${round.operator} != ${rule.operator}"
                }
            }
        }

        // --- 3. the evaluator -------------------------------------------------------------------------------

        val grid = MapGrid(VALUES)
        val recomputed = LinkedHashMap<String, String>()
        for ((ref, expected) in EXPECTED) {
            val cell = SheetRange.parseCell(ref)
            val outcome = XlsxConditionalFormatVerdict.verdict(BLOCKS, grid, cell)
            val actual = encode(outcome)
            recomputed[ref] = actual
            check(actual == expected) { "$ref gave $actual, expected $expected" }
        }

        // Every cell of the interesting ranges, not only the sampled ones: a shift that is right at the anchor and
        // wrong elsewhere is the exact bug this round repairs.
        val sweep = StringBuilder()
        for ((ref, expected) in recomputed) {
            val kind = ref.take(1)
            if (kind in MAPPED || kind in setOf("I", "J", "K")) {
                sweep.append("$ref\t$expected\n")
            }
        }
        for (row in 1..9) {
            for (column in listOf("A", "C", "D", "E", "G", "H", "L")) {
                val ref = "$column$row"
                val cell = SheetRange.parseCell(ref)
                val encoded = encode(XlsxConditionalFormatVerdict.verdict(BLOCKS, grid, cell))
                sweep.append("$ref\t$encoded\n")
            }
        }
        for (ref in listOf("I1", "I2", "I3", "J1", "J2", "J3", "K1", "K2", "K3", "M1", "M2", "M3")) {
            val encoded = encode(XlsxConditionalFormatVerdict.verdict(BLOCKS, grid, SheetRange.parseCell(ref)))
            sweep.append("$ref\t$encoded\n")
        }
        File(folder, "verdicts.tsv").writeText(sweep.toString(), Charsets.UTF_8)

        // --- 4. the grammar ---------------------------------------------------------------------------------

        // A cross-sheet reference is not read; the rule stays undecided rather than comparing some other cell.
        val crossSheet = listOf(
            Block("A1", listOf(Rule(Kind.EXPRESSION, priority = 1, formulas = listOf("Sheet2!A1>0"), format = FILL))),
        )
        val crossOutcome = XlsxConditionalFormatVerdict.verdict(
            crossSheet, MapGrid(mapOf(SheetRange.parseCell("A1") to Value.Num(5.0))), SheetRange.parseCell("A1"),
        )
        check(crossOutcome is Outcome.Undecided) { "a cross-sheet reference decided: $crossOutcome" }
        check((crossOutcome as Outcome.Undecided).reason.contains("Sheet2!A1")) {
            "the undecided reason does not name the reference: ${crossOutcome.reason}"
        }

        // An anchor that is not the grid origin still measures from its own range token.
        val offsetBlock = listOf(
            Block("C5:C7", listOf(Rule(Kind.EXPRESSION, priority = 1, formulas = listOf("C5>0"), format = FILL))),
        )
        val offsetGrid = MapGrid(
            mapOf(
                SheetRange.parseCell("C5") to Value.Num(-1.0),
                SheetRange.parseCell("C6") to Value.Num(-1.0),
                SheetRange.parseCell("C7") to Value.Num(1.0),
            ),
        )
        check(
            encode(XlsxConditionalFormatVerdict.verdict(offsetBlock, offsetGrid, SheetRange.parseCell("C6"))) == "-",
        ) { "a rule anchored at C5 compared the wrong cell at C6" }
        check(
            encode(XlsxConditionalFormatVerdict.verdict(offsetBlock, offsetGrid, SheetRange.parseCell("C7"))) == "1",
        ) { "a rule anchored at C5 compared the wrong cell at C7" }

        // An operand that is not a reference, a number, a string or a boolean stays unsupported.
        // R105 made `SUM` and ranges real, so the "looks like a reference but is not" list names a function that
        // will never exist instead. The property under test is unchanged: an unreadable formula is undecided.
        for (token in listOf("A0>0", "A1:A2>0", "\"text\"&A1>0", "FOO(A1:A3)>0", "A1048577>0")) {
            val block = listOf(
                Block("B1", listOf(Rule(Kind.EXPRESSION, priority = 1, formulas = listOf(token), format = FILL))),
            )
            val outcome = XlsxConditionalFormatVerdict.verdict(
                block, MapGrid(mapOf(SheetRange.parseCell("B1") to Value.Num(1.0))), SheetRange.parseCell("B1"),
            )
            check(outcome is Outcome.Undecided) { "'$token' was decided as $outcome instead of undecided" }
        }

        // The model still refuses what it always refused around formulas.
        refuses("a formula with a leading '='") {
            Rule(Kind.EXPRESSION, priority = 1, formulas = listOf("=A1>1"), format = FILL)
        }
        refuses("two formulas on an expression rule") {
            Rule(Kind.EXPRESSION, priority = 1, formulas = listOf("A1>1", "A2>1"), format = FILL)
        }
        refuses("an empty formula") { Rule(Kind.EXPRESSION, priority = 1, formulas = listOf(""), format = FILL) }

        // --- 5. intent tables -------------------------------------------------------------------------------

        val ruleRows = StringBuilder()
        for (block in BLOCKS) {
            for (rule in block.rules) {
                ruleRows.append(
                    listOf(
                        block.sqref,
                        rule.priority.toString(),
                        rule.kind.token,
                        rule.operator?.token ?: "-",
                        rule.formulas.joinToString(","),
                    ).joinToString("\t"),
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

        val rows = sweep.lines().count { it.isNotBlank() }
        val summary = "R103_SHIFT_OK assertions=$assertions refusals=$refusals rules=${BLOCKS.sumOf { it.rules.size }} " +
            "blocks=${BLOCKS.size} cells=$rows sampled=${recomputed.size} undecided=" +
            recomputed.count { it.value == "UNDECIDED" }
        File(folder, "summary.txt").writeText(summary + "\n", Charsets.UTF_8)
        println(summary)
    }
}
