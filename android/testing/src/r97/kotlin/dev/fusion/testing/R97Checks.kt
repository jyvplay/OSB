// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.SheetRange
import dev.fusion.ooxml.XlsxConditionalFormatVerdict
import dev.fusion.ooxml.XlsxConditionalFormatVerdict.Value
import dev.fusion.ooxml.XlsxConditionalFormatting
import dev.fusion.ooxml.XlsxConditionalFormatting.Block
import dev.fusion.ooxml.XlsxConditionalFormatting.Format
import dev.fusion.ooxml.XlsxConditionalFormatting.Kind
import dev.fusion.ooxml.XlsxConditionalFormatting.Operator
import dev.fusion.ooxml.XlsxConditionalFormatting.Rule
import dev.fusion.ooxml.XlsxConditionalFormatting.Threshold
import dev.fusion.ooxml.XlsxConditionalFormatting.ThresholdKind
import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R97 spreadsheet contracts: the *verdict* half of conditional formatting. Given a sheet's
 * `<conditionalFormatting>` blocks and the values a reader has, which rules fire on a cell, in what order, what
 * the merged painting is, and which applicable rules this evaluator refuses to decide.
 *
 * Host JVM only, and deliberately so: no Excel, LibreOffice or renderer is involved, so an assertion here is a
 * statement about this evaluator, never about how a spreadsheet paints a cell. Independent witness:
 * `tools/check_r97_verdicts.py` re-implements the decision procedure in Python from `rules.tsv` and
 * `values.tsv` and compares every row of `verdicts.tsv`.
 */
object R97Checks {
    private var assertions = 0

    private inline fun check(ok: Boolean, message: () -> String) {
        assertions++
        require(ok) { "FAILED: " + message() }
    }

    private fun key(ref: String): SheetRange.Cell = SheetRange.parseCell(ref)

    private class MapGrid(private val values: Map<SheetRange.Cell, Value>) : XlsxConditionalFormatVerdict.Grid {
        override fun value(cell: SheetRange.Cell): Value = values[cell] ?: Value.Blank
    }

    private val RED_FILL = Format(fillRgb = "FFC7CE")
    private val BOLD = Format(bold = true)
    private val ITALIC = Format(italic = true)
    private val UNDERLINE = Format(underline = true)
    private val STRIKE = Format(strike = true)
    private val GREEN_FILL = Format(fillRgb = "C6EFCE")

    /** 1, 2, 3, 4, blank, 6, 7, 8, 9, 10 — the blank at A5 is deliberate. */
    private val NUMBER_GRID: Map<SheetRange.Cell, Value> = buildMap {
        listOf(1.0, 2.0, 3.0, 4.0, null, 6.0, 7.0, 8.0, 9.0, 10.0).forEachIndexed { index, value ->
            if (value != null) put(key("A${index + 1}"), Value.Num(value))
        }
    }

    private val TEXT_GRID: Map<SheetRange.Cell, Value> = mapOf(
        key("B1") to Value.Txt("apple"),
        key("B2") to Value.Txt("APPLE"),
        key("B3") to Value.Txt("banana"),
        key("B5") to Value.Txt("10"),
    )

    private val RANK_GRID: Map<SheetRange.Cell, Value> = mapOf(
        key("D1") to Value.Num(5.0),
        key("D2") to Value.Num(5.0),
        key("D3") to Value.Num(3.0),
        key("D4") to Value.Num(9.0),
    )

    /** Text `"10"` beside the number `10`: the pair where Excel's type rule is visible. */
    private val MIXED_GRID: Map<SheetRange.Cell, Value> = mapOf(
        key("F1") to Value.Txt("10"),
        key("F2") to Value.Num(10.0),
    )

    private val FAULT_GRID: Map<SheetRange.Cell, Value> = mapOf(
        key("E1") to Value.Err("#DIV/0!"),
        key("E2") to Value.Bool(true),
    )

    private val NUMBER_RULES = listOf(
        Block(
            sqref = "A1:A10",
            rules = listOf(
                Rule(Kind.CELL_IS, 1, stopIfTrue = true, operator = Operator.GREATER_THAN, formulas = listOf("5"), format = RED_FILL),
                Rule(Kind.CELL_IS, 2, operator = Operator.BETWEEN, formulas = listOf("2", "4"), format = BOLD),
                Rule(Kind.EXPRESSION, 3, formulas = listOf("MOD(ROW(),2)=0"), format = UNDERLINE),
            ),
        ),
        Block(
            sqref = "A1:A10",
            rules = listOf(
                Rule(
                    Kind.COLOR_SCALE,
                    4,
                    thresholds = listOf(
                        Threshold(ThresholdKind.MIN),
                        Threshold(ThresholdKind.PERCENT, 50.0),
                        Threshold(ThresholdKind.MAX),
                    ),
                    colors = listOf("FF0000", "FFFF00", "00FF00"),
                ),
            ),
        ),
        Block(
            sqref = "A1:A10",
            rules = listOf(
                Rule(
                    Kind.DATA_BAR,
                    5,
                    thresholds = listOf(Threshold(ThresholdKind.NUM, 0.0), Threshold(ThresholdKind.NUM, 10.0)),
                    colors = listOf("638EC6"),
                ),
            ),
        ),
    )

    /**
     * A scale whose middle stop is a *percentile* rather than a percentage: with values 1..10 and a hole at 5, the
     * 50th percentile of the nine numbers is 6, so A6 is exactly the middle colour. This is the arithmetic the
     * Python lane recomputes independently.
     */
    private val PERCENTILE_RULES = listOf(
        Block(
            sqref = "A1:A10",
            rules = listOf(
                Rule(
                    Kind.COLOR_SCALE,
                    6,
                    thresholds = listOf(
                        Threshold(ThresholdKind.MIN),
                        Threshold(ThresholdKind.PERCENTILE, 50.0),
                        Threshold(ThresholdKind.MAX),
                    ),
                    colors = listOf("0000FF", "FFFFFF", "000000"),
                ),
            ),
        ),
    )

    /** Just the range-relative kinds, so their arithmetic is observed without a stopping rule in front of it. */
    private val SCALE_RULES = listOf(NUMBER_RULES[1], NUMBER_RULES[2])

    private val TEXT_RULES = listOf(
        Block(
            sqref = "B1:B5",
            rules = listOf(
                Rule(Kind.CELL_IS, 1, operator = Operator.EQUAL, formulas = listOf("\"apple\""), format = ITALIC),
                Rule(Kind.EXPRESSION, 2, formulas = listOf("ISBLANK(B4)"), format = STRIKE),
                Rule(Kind.CELL_IS, 3, operator = Operator.LESS_THAN, formulas = listOf("\"banana\""), format = GREEN_FILL),
            ),
        ),
    )

    private val RANK_RULES = listOf(
        Block(
            sqref = "D1:D4",
            rules = listOf(
                Rule(Kind.TOP10, 1, rank = 2, bottom = true, format = RED_FILL),
                Rule(Kind.TOP10, 2, rank = 50, percent = true, format = BOLD),
            ),
        ),
    )

    private val MIXED_RULES = listOf(
        Block(
            sqref = "F1:F2",
            rules = listOf(
                Rule(Kind.CELL_IS, 1, operator = Operator.EQUAL, formulas = listOf("\"10\""), format = ITALIC),
                Rule(Kind.CELL_IS, 2, operator = Operator.EQUAL, formulas = listOf("10"), format = BOLD),
                Rule(Kind.CELL_IS, 3, operator = Operator.GREATER_THAN, formulas = listOf("5"), format = RED_FILL),
            ),
        ),
    )

    /** A block larger than the scan cap: refusing beats a silent "nothing fires". */
    private val HUGE_RULES = listOf(
        Block(
            sqref = "A1:A1048576",
            rules = listOf(
                Rule(
                    Kind.COLOR_SCALE,
                    1,
                    thresholds = listOf(Threshold(ThresholdKind.MIN), Threshold(ThresholdKind.MAX)),
                    colors = listOf("FF0000", "00FF00"),
                ),
            ),
        ),
    )

    private val FAULT_RULES = listOf(
        Block(
            sqref = "E1:E3",
            rules = listOf(
                Rule(Kind.CELL_IS, 1, operator = Operator.GREATER_THAN, formulas = listOf("5"), format = RED_FILL),
            ),
        ),
    )

    // --- encodings shared with the Python lane ------------------------------------------------------------

    private fun flag(value: Boolean): String = if (value) "1" else "0"

    private fun number(value: Double): String =
        if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()

    private fun visual(visual: XlsxConditionalFormatVerdict.Visual): String = when (visual) {
        is XlsxConditionalFormatVerdict.Visual.Font -> "font:${flag(visual.bold)}${flag(visual.italic)}" +
            "${flag(visual.strike)}${flag(visual.underline)}:${visual.rgb ?: "-"}"
        is XlsxConditionalFormatVerdict.Visual.Fill -> "fill:${visual.rgb}"
        is XlsxConditionalFormatVerdict.Visual.Scale -> "scale:${visual.rgb}"
        is XlsxConditionalFormatVerdict.Visual.Bar -> "bar:${number(visual.fraction)}"
        // R102 added a fifth visual for the dxf fields the evaluator has no opinion about. None of R97's formats
        // sets one, so this branch is unreachable here — it exists because the `when` is exhaustive, and a silent
        // `else` would have hidden a real sixth visual if one ever appears.
        is XlsxConditionalFormatVerdict.Visual.Detail -> "detail"
    }

    private fun valueKind(value: Value): String = when (value) {
        is Value.Blank -> "blank"
        is Value.Num -> "num"
        is Value.Txt -> "text"
        is Value.Bool -> "bool"
        is Value.Err -> "error"
    }

    private fun valuePayload(value: Value): String = when (value) {
        is Value.Blank -> "-"
        is Value.Num -> number(value.value)
        is Value.Txt -> value.text.ifEmpty { "-" }
        is Value.Bool -> flag(value.flag)
        is Value.Err -> value.code
    }

    private fun ruleRow(blockIndex: Int, ruleIndex: Int, block: Block, rule: Rule, ids: Map<Format, Int>): String =
        listOf(
            "Numbers", "1", blockIndex.toString(), block.sqref, ruleIndex.toString(), rule.kind.token, rule.priority.toString(),
            rule.stopIfTrue?.let { flag(it) } ?: "-",
            rule.operator?.token ?: "-",
            rule.rank?.toString() ?: "-",
            rule.percent?.let { flag(it) } ?: "-",
            rule.bottom?.let { flag(it) } ?: "-",
            rule.formulas.joinToString("\u001f").ifEmpty { "-" },
            rule.thresholds.joinToString(",") { it.kind.token + ":" + (it.value?.let { value -> number(value) } ?: "-") }
                .ifEmpty { "-" },
            rule.colors.joinToString(",").ifEmpty { "-" },
            rule.format?.let { ids[it]?.toString() } ?: "-",
            rule.format?.let { format ->
                listOf(
                    flag(format.bold), flag(format.italic), flag(format.strike), flag(format.underline),
                    format.fontRgb ?: "-", format.fillRgb ?: "-",
                ).joinToString(";")
            } ?: "-",
        ).joinToString("\t")

    private fun verdictRow(
        sheet: String,
        cell: SheetRange.Cell,
        outcome: XlsxConditionalFormatVerdict.Outcome,
        applicable: Int,
    ): String {
        val decoration = if (outcome is XlsxConditionalFormatVerdict.Outcome.Decided) {
            XlsxConditionalFormatVerdict.decoration(outcome)
        } else {
            null
        }
        val fired = (outcome as? XlsxConditionalFormatVerdict.Outcome.Decided)?.fired ?: emptyList()
        val stopped = (outcome as? XlsxConditionalFormatVerdict.Outcome.Decided)?.stopped == true
        val unresolved = (outcome as? XlsxConditionalFormatVerdict.Outcome.Decided)?.unresolved
            ?: listOf(XlsxConditionalFormatVerdict.Unresolved((outcome as XlsxConditionalFormatVerdict.Outcome.Undecided).priority, "x"))
        return listOf(
            sheet,
            cell.toA1(),
            if (outcome is XlsxConditionalFormatVerdict.Outcome.Decided) "decided" else "undecided",
            fired.joinToString("\u001f") { it.priority.toString() }.ifEmpty { "-" },
            fired.joinToString("\u001f") { it.kind.token }.ifEmpty { "-" },
            flag(stopped),
            unresolved.joinToString("\u001f") { it.priority.toString() }.ifEmpty { "-" },
            fired.flatMap { it.visuals }.joinToString("\u001f") { visual(it) }.ifEmpty { "-" },
            decoration?.let { flag(it.bold) } ?: "-",
            decoration?.let { flag(it.italic) } ?: "-",
            decoration?.let { flag(it.strike) } ?: "-",
            decoration?.let { flag(it.underline) } ?: "-",
            decoration?.fontRgb ?: "-",
            decoration?.fillRgb ?: "-",
            decoration?.scaleRgb ?: "-",
            decoration?.barFraction?.let { number(it) } ?: "-",
            applicable.toString(),
            (outcome as? XlsxConditionalFormatVerdict.Outcome.Undecided)?.reason ?: "-",
        ).joinToString("\t")
    }

    private fun firedPriorities(outcome: XlsxConditionalFormatVerdict.Outcome): List<Int> =
        (outcome as? XlsxConditionalFormatVerdict.Outcome.Decided)?.fired?.map { it.priority } ?: emptyList()

    private fun unresolvedPriorities(outcome: XlsxConditionalFormatVerdict.Outcome): List<Int> =
        (outcome as? XlsxConditionalFormatVerdict.Outcome.Decided)?.unresolved?.map { it.priority } ?: emptyList()

    private fun decided(outcome: XlsxConditionalFormatVerdict.Outcome): XlsxConditionalFormatVerdict.Outcome.Decided =
        outcome as? XlsxConditionalFormatVerdict.Outcome.Decided
            ?: error("expected a decided verdict, got $outcome")

    private fun decorationAt(
        blocks: List<Block>,
        grid: MapGrid,
        cell: SheetRange.Cell,
    ): XlsxConditionalFormatVerdict.Decoration = XlsxConditionalFormatVerdict.decoration(
        decided(XlsxConditionalFormatVerdict.verdict(blocks, grid, cell)),
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val folder = File(args.firstOrNull() ?: "testing/build/r97/artifacts")
        folder.mkdirs()
        val numberGrid = MapGrid(NUMBER_GRID)
        val textGrid = MapGrid(TEXT_GRID)
        val rankGrid = MapGrid(RANK_GRID)
        val faultGrid = MapGrid(FAULT_GRID)

        fun numberVerdict(ref: String) = XlsxConditionalFormatVerdict.verdict(NUMBER_RULES, numberGrid, key(ref))
        fun textVerdict(ref: String) = XlsxConditionalFormatVerdict.verdict(TEXT_RULES, textGrid, key(ref))
        fun rankVerdict(ref: String) = XlsxConditionalFormatVerdict.verdict(RANK_RULES, rankGrid, key(ref))
        fun faultVerdict(ref: String) = XlsxConditionalFormatVerdict.verdict(FAULT_RULES, faultGrid, key(ref))
        val mixedGrid = MapGrid(MIXED_GRID)
        fun mixedVerdict(ref: String) = XlsxConditionalFormatVerdict.verdict(MIXED_RULES, mixedGrid, key(ref))

        // 1. Priority order and stopIfTrue: A6..A10 are above 5 and hit the stopping rule first.
        for (row in 6..10) {
            val outcome = numberVerdict("A$row")
            check(firedPriorities(outcome) == listOf(1)) {
                "A$row should fire only the stopping rule, got ${firedPriorities(outcome)}"
            }
            check(decided(outcome).stopped) { "A$row should stop the scan at priority 1" }
            check(unresolvedPriorities(outcome).isEmpty()) { "A$row stopped, so nothing after it is evaluated" }
        }
        // 2. between is inclusive at both ends.
        for (row in 2..4) {
            check(firedPriorities(numberVerdict("A$row")).first() == 2) {
                "A$row (between 2 and 4) should fire priority 2, got ${firedPriorities(numberVerdict("A$row"))}"
            }
            check(!decided(numberVerdict("A$row")).stopped) { "A$row must not stop the scan; stopIfTrue is absent" }
        }
        check(2 !in firedPriorities(numberVerdict("A1"))) { "A1 is below the lower bound of the between" }
        check(2 !in firedPriorities(numberVerdict("A5"))) { "the blank A5 must not satisfy a numeric between" }
        // 3. MOD(ROW(),2)=0 fires on the even rows that survive the stopping rule: 2 and 4 only.
        val evenHits = (1..10).filter { row -> 3 in firedPriorities(numberVerdict("A$row")) }
        check(evenHits == listOf(2, 4)) { "MOD(ROW(),2)=0 fired on $evenHits; 6..10 are stopped and 5 is blank" }
        // 4. The colour scale: minimum, maximum, a mid-stop interpolation and the blank's low-end colour.
        check(decorationAt(SCALE_RULES, numberGrid, key("A1")).scaleRgb == "FF0000") {
            "the scale minimum should be FF0000, got ${decorationAt(SCALE_RULES, numberGrid, key("A1")).scaleRgb}"
        }
        check(decorationAt(SCALE_RULES, numberGrid, key("A10")).scaleRgb == "00FF00") {
            "the scale maximum should be 00FF00, got ${decorationAt(SCALE_RULES, numberGrid, key("A10")).scaleRgb}"
        }
        check(decorationAt(SCALE_RULES, numberGrid, key("A5")).scaleRgb == "FF0000") {
            "a blank sits at the low end of the scale, got ${decorationAt(SCALE_RULES, numberGrid, key("A5")).scaleRgb}"
        }
        // 1..10 with a 50% stop at 5.5: A4 (4) is two thirds of the way to the yellow stop.
        check(decorationAt(SCALE_RULES, numberGrid, key("A4")).scaleRgb == "FFAA00") {
            "A4 should interpolate to FFAA00, got ${decorationAt(SCALE_RULES, numberGrid, key("A4")).scaleRgb}"
        }
        check(decorationAt(SCALE_RULES, numberGrid, key("A6")).scaleRgb != "FFFF00") {
            "A6 sits just past the midpoint stop and must not be the stop colour"
        }
        // 5. The bar is a fraction of the declared num/num window, clamped, and absent for a blank.
        check(decorationAt(SCALE_RULES, numberGrid, key("A1")).barFraction == 0.1) {
            "A1 should be a tenth of the bar, got ${decorationAt(SCALE_RULES, numberGrid, key("A1")).barFraction}"
        }
        check(decorationAt(SCALE_RULES, numberGrid, key("A10")).barFraction == 1.0) { "A10 should fill the bar" }
        check(decorationAt(SCALE_RULES, numberGrid, key("A5")).barFraction == null) { "a blank cell has no bar" }
        // 6. Text equality is case-insensitive; ISBLANK(B4) is absolute, so it fires for the whole block.
        check(firedPriorities(textVerdict("B1")) == listOf(1, 2)) {
            "B1 (apple) should fire equality then the absolute blank test, got ${textVerdict("B1")}"
        }
        check(firedPriorities(textVerdict("B2")) == listOf(1, 2)) {
            "B2 (APPLE) should fire the same two rules, got ${textVerdict("B2")}"
        }
        check(1 !in firedPriorities(textVerdict("B4"))) { "the blank B4 equals \"apple\"" }
        check(2 in firedPriorities(textVerdict("B4"))) { "ISBLANK(B4) should fire for the blank row itself" }
        check(3 in unresolvedPriorities(textVerdict("B1"))) {
            "\"apple\" < \"banana\" is a collation question and must be unresolved, got ${unresolvedPriorities(textVerdict("B1"))}"
        }
        check(firedPriorities(textVerdict("B3")) == listOf(2)) {
            "B3 (banana) fires only the absolute blank test, got ${textVerdict("B3")}"
        }
        // 7. A rule set whose only applicable rule is an ordering over text yields no verdict at all.
        val orderOnly = listOf(Block("B1:B1", listOf(Rule(Kind.CELL_IS, 1, operator = Operator.LESS_THAN, formulas = listOf("\"banana\""), format = GREEN_FILL))))
        val orderVerdict = XlsxConditionalFormatVerdict.verdict(orderOnly, textGrid, key("B1"))
        check(orderVerdict is XlsxConditionalFormatVerdict.Outcome.Undecided) {
            "a text ordering with nothing else decided must be undecided, got $orderVerdict"
        }
        check((orderVerdict as XlsxConditionalFormatVerdict.Outcome.Undecided).priority == 1) {
            "the undecided verdict should name the rule it could not decide"
        }
        check(XlsxConditionalFormatVerdict.verdict(orderOnly, textGrid, key("B1")) !is XlsxConditionalFormatVerdict.Outcome.Decided) {
            "an undecidable ordering was reported as a decided verdict"
        }
        // 8. bottom-2 with ties: 3, 5 and 5 all fire because the cut-off value is 5.
        val bottomHits = (1..4).filter { row -> 1 in firedPriorities(rankVerdict("D$row")) }
        check(bottomHits == listOf(1, 2, 3)) { "bottom-2 with ties should hit D1..D3, got $bottomHits" }
        // 9. percent 50 of four values takes ceil(4 * 50 / 100) = 2, and the tie extends the set to three.
        val percentHits = (1..4).filter { row -> 2 in firedPriorities(rankVerdict("D$row")) }
        check(percentHits == listOf(1, 2, 4)) { "top 50% should hit D1, D2 and D4, got $percentHits" }
        // 10. An error cell and a boolean compared with a number are undecided with a reason.
        check(faultVerdict("E1") is XlsxConditionalFormatVerdict.Outcome.Undecided) { "an error cell should be undecided" }
        check(faultVerdict("E2") is XlsxConditionalFormatVerdict.Outcome.Undecided) {
            "a boolean compared with a number should be undecided"
        }
        check(firedPriorities(faultVerdict("E3")).isEmpty() && faultVerdict("E3") is XlsxConditionalFormatVerdict.Outcome.Decided) {
            "the blank E3 is decided and no rule fires"
        }
        // 10b. Excel's type rule: a number never equals a text and sorts before every text.
        check(firedPriorities(mixedVerdict("F1")) == listOf(1, 3)) {
            "the text \"10\" should match the text operand and be greater than the number 5, got ${mixedVerdict("F1")}"
        }
        check(firedPriorities(mixedVerdict("F2")) == listOf(2, 3)) {
            "the number 10 should match the numeric operand only, got ${mixedVerdict("F2")}"
        }
        check(2 !in firedPriorities(mixedVerdict("F1"))) {
            "numeric-looking text was folded into a number: the text \"10\" matched the literal 10"
        }
        check(1 !in firedPriorities(mixedVerdict("F2"))) { "the number 10 matched the text operand \"10\"" }

        // 10c. A range larger than the scan cap is refused, not treated as empty.
        val huge = XlsxConditionalFormatVerdict.verdict(HUGE_RULES, numberGrid, key("A1"))
        check(huge is XlsxConditionalFormatVerdict.Outcome.Undecided) {
            "a scale over a whole column must be undecided, got $huge"
        }
        check((huge as XlsxConditionalFormatVerdict.Outcome.Undecided).reason.contains("larger than")) {
            "the refusal should name the cap, got ${huge.reason}"
        }

        // 11. Merging: properties from different rules combine, first non-null wins.
        val merged = decorationAt(NUMBER_RULES, numberGrid, key("A2"))
        check(merged.bold && merged.underline) { "A2 should be bold (between) and underlined (even row), got $merged" }
        check(merged.scaleRgb != null && merged.barFraction != null) { "A2 should carry both a scale and a bar" }
        check(merged.fillRgb == null) { "A2 must not carry the fill of the stopping rule, which did not fire" }
        // 12. A cell outside every block does nothing at all.
        check(firedPriorities(numberVerdict("Z99")).isEmpty() && decorationAt(NUMBER_RULES, numberGrid, key("Z99")) == XlsxConditionalFormatVerdict.Decoration()) {
            "a cell outside every block produced a painting"
        }
        // 13. Percentile and colour arithmetic are the documented ones.
        check(XlsxConditionalFormatVerdict.percentile(listOf(1.0, 2.0, 3.0, 4.0), 50.0) == 2.5) { "p50 of 1..4 is 2.5" }
        check(XlsxConditionalFormatVerdict.percentile(listOf(1.0, 2.0, 3.0, 4.0), 0.0) == 1.0) { "p0 is the minimum" }
        check(XlsxConditionalFormatVerdict.percentile(listOf(1.0, 2.0, 3.0, 4.0), 100.0) == 4.0) { "p100 is the maximum" }
        check(XlsxConditionalFormatVerdict.percentile(listOf(7.0), 42.0) == 7.0) { "a one-value percentile is that value" }
        check(XlsxConditionalFormatVerdict.mix("000000", "FFFFFF", 0.5) == "808080") { "mid-grey is 808080" }
        check(XlsxConditionalFormatVerdict.mix("FF0000", "00FF00", 0.0) == "FF0000") { "weight 0 is the from colour" }
        check(XlsxConditionalFormatVerdict.mix("FF0000", "00FF00", 1.0) == "00FF00") { "weight 1 is the to colour" }
        check(XlsxConditionalFormatVerdict.mix("000000", "FF0000", 0.5) == "800000") { "half red rounds to 800000" }

        // 14. A percentile middle stop lands exactly on the order statistic: the 50th percentile of the nine
        // numbers 1..10 minus the hole at 5 is 6, so A6 is exactly the middle colour of that scale.
        check(XlsxConditionalFormatVerdict.percentile(listOf(1.0, 2.0, 3.0, 4.0, 6.0, 7.0, 8.0, 9.0, 10.0), 50.0) == 6.0) {
            "the 50th percentile of the holed range should be 6"
        }
        check(decorationAt(PERCENTILE_RULES, numberGrid, key("A6")).scaleRgb == "FFFFFF") {
            "A6 sits on the percentile stop and must be the middle colour, got " +
                "${decorationAt(PERCENTILE_RULES, numberGrid, key("A6")).scaleRgb}"
        }
        check(decorationAt(PERCENTILE_RULES, numberGrid, key("A1")).scaleRgb == "0000FF") {
            "the percentile scale minimum should be 0000FF"
        }
        check(decorationAt(PERCENTILE_RULES, numberGrid, key("A10")).scaleRgb == "000000") {
            "the percentile scale maximum should be 000000"
        }

        // --- artifacts -----------------------------------------------------------------------------------
        val sheets = listOf(
            Triple("Numbers", NUMBER_GRID, NUMBER_RULES),
            Triple("Percentile", NUMBER_GRID, PERCENTILE_RULES),
            Triple("Text", TEXT_GRID, TEXT_RULES),
            Triple("Rank", RANK_GRID, RANK_RULES),
            Triple("Faults", FAULT_GRID, FAULT_RULES),
            Triple("Mixed", MIXED_GRID, MIXED_RULES),
        )
        val ids = java.util.LinkedHashMap<Format, Int>()
        for ((_, _, blocks) in sheets) {
            for (block in blocks) for (rule in block.rules) rule.format?.let { format -> ids.getOrPut(format) { ids.size } }
        }
        val rules = ArrayList<String>()
        val values = ArrayList<String>()
        val verdicts = ArrayList<String>()
        for ((sheet, grid, blocks) in sheets) {
            blocks.forEachIndexed { blockIndex, block ->
                block.rules.forEachIndexed { ruleIndex, rule ->
                    rules.add(ruleRow(blockIndex, ruleIndex, block, rule, ids).replaceFirst("Numbers", sheet))
                }
            }
            val blanks = mapOf(
                "Numbers" to "A5",
                "Percentile" to null,
                "Text" to "B4",
                "Faults" to "E3",
                "Mixed" to null,
            )
            val declared = LinkedHashSet<SheetRange.Cell>()
            declared.addAll(grid.keys)
            blanks[sheet]?.let { declared.add(key(it)) }
            for (cell in declared.sortedBy { it.row * 1000 + it.col }) {
                val value = grid[cell] ?: Value.Blank
                values.add(listOf(sheet, cell.toA1(), valueKind(value), valuePayload(value)).joinToString("\t"))
            }
        }
        val verdictBlanks = mapOf(
            "Numbers" to "A5",
            "Percentile" to null,
            "Text" to "B4",
            "Rank" to null,
            "Faults" to "E3",
            "Mixed" to null,
        )
        for ((sheet, grid, blocks) in sheets) {
            val cells = LinkedHashSet<SheetRange.Cell>()
            cells.addAll(grid.keys)
            verdictBlanks[sheet]?.let { cells.add(key(it)) }
            for (cell in cells.sortedBy { it.row * 1000 + it.col }) {
                // The column counts the rules the verdict had to consider, not the blocks: a block of three rules
                // is three decisions, and a rule that never appears in any row was never tested.
                val applicable = blocks.sumOf { block ->
                    if (block.sqref.split(' ').any { token -> SheetRange.contains(SheetRange.parse(token), cell) }) {
                        block.rules.size
                    } else {
                        0
                    }
                }
                verdicts += verdictRow(
                    sheet,
                    cell,
                    XlsxConditionalFormatVerdict.verdict(blocks, MapGrid(grid), cell),
                    applicable,
                )
            }
        }
        File(folder, "rules.tsv").writeText(rules.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        File(folder, "values.tsv").writeText(values.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        File(folder, "verdicts.tsv").writeText(verdicts.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        File(folder, "dxfs.tsv").writeText(
            ids.entries.joinToString("\n", postfix = "\n") { (format, id) ->
                listOf(
                    "r97.xlsx", id.toString(), flag(format.bold), flag(format.italic), flag(format.strike),
                    flag(format.underline), format.fontRgb ?: "-", format.fillRgb ?: "-", ids.size.toString(),
                ).joinToString("\t")
            },
            Charsets.UTF_8,
        )
        File(folder, "r97.xlsx").writeBytes(
            XlsxStyledBook.write(
                XlsxStyledBook.WorkbookSpec(sheets.map { (sheet, grid, blocks) -> sheetOf(sheet, grid, blocks) }),
            ),
        )
        check(verdicts.size >= 20) { "the artifact should carry at least twenty verdicts, got ${verdicts.size}" }
        check(values.size >= 20) { "the artifact should carry at least twenty values, got ${values.size}" }
        println(
            "R97_CF_VERDICT_OK assertions=$assertions sheets=${sheets.size} values=${values.size} verdicts=${verdicts.size} " +
                "rules=${rules.size} dxfs=${ids.size}",
        )
    }

    private fun sheetOf(sheet: String, values: Map<SheetRange.Cell, Value>, rules: List<Block>): XlsxStyledBook.StyledSheet {
        val cells = values.entries.sortedBy { it.key.row * 1000 + it.key.col }.map { (cell, value) ->
            XlsxStyledBook.StyledCell(
                cell.toA1(),
                when (value) {
                    is Value.Num -> XlsxStyledBook.StyledVal.Num(value.value)
                    is Value.Txt -> XlsxStyledBook.StyledVal.Txt(value.text)
                    is Value.Bool -> XlsxStyledBook.StyledVal.Bool(value.flag)
                    is Value.Err -> XlsxStyledBook.StyledVal.Txt(value.code)
                    is Value.Blank -> XlsxStyledBook.StyledVal.Txt("")
                },
            )
        }
        return XlsxStyledBook.StyledSheet(sheet, cells, conditionalFormats = rules)
    }
}
