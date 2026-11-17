// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Verdicts for the conditional-format model in [XlsxConditionalFormatting]: given a sheet's blocks and the values a
 * reader has for the cells, decide which rules fire on a cell and what that means visually.
 *
 * This is the second half of the feature the file model only *states*. It is deliberately narrow, and every
 * narrowing is a refusal rather than a guess:
 *
 * * **Order.** Rules whose block contains the cell are considered in ascending `priority`; a rule with
 *   `stopIfTrue` true ends the scan after it fires.
 * * **Undecidable is not false.** A rule whose predicate this evaluator cannot decide (an unmodelled formula, an
 *   error value, a text comparison whose collation is unknown) makes the *whole* verdict [Outcome.Undecided]. It
 *   does not silently read as "did not fire", because that would change which rule wins.
 * * **Import hypotheses**, stated so they can be attacked: a blank cell compares as `0` against a numeric operand
 *   and as `""` against a text operand; `equal` on text is case-insensitive; `between` is inclusive; `top10`
 *   includes ties; `percent` thresholds interpolate linearly between the range minimum and maximum; `percentile`
 *   uses linear interpolation between order statistics; colour interpolation is per-channel sRGB with rounding
 *   half-up. None of these was observed in Excel — see `docs/R97-RESULTS.md` §C.
 * * **Blank, error, time and icon rules** (R101). A blank rule follows Excel's own formula (`LEN(TRIM(cell))=0`,
 *   where TRIM removes spaces only), an error rule follows `ISERROR(cell)`, and both treat an error cell as
 *   *no format* rather than as a negation — `notContainsBlanks` is not the complement of `containsBlanks` on an
 *   error cell, because the formula Excel writes for it also errors. A `timePeriod` rule needs a [Calendar]:
 *   without one it is undecided, and week boundaries follow [Calendar.weekStartsOn] because no attribute in the
 *   file states the first day of the week. An `iconSet` rule reports the icon index it assigns; `showValue`
 *   changes what is printed beside the icon, not which icon a value earns, so it does not move the index.
 * * **Text, average and duplicate rules** (R100), whose own hypotheses are stated at each predicate: SEARCH is
 *   unanchored and case-insensitive, a blank cell's text is `""`, a number is compared by its general-format
 *   text, `beginsWith`/`endsWith` are literal, the average band uses the *population* sigma over the rule's
 *   numeric cells, and duplicates are counted by value with case-insensitive text keys. See
 *   `docs/R100-RESULTS.md` §C, including the one hypothesis (§C.5) that no corpus here can distinguish.
 *
 * What is *not* here: font family/size, number formats, borders, alignment, the icon *images* themselves (only
 * which icon a value earns), the 2010 `x14` data-bar and icon-set extensions, and the painting itself. A verdict
 * is a claim about which rule fires and which icon index it assigns, never about pixels.
 */
object XlsxConditionalFormatVerdict {

    /** A cell's value as a reader can know it. No formulas: a formula cell is only known by its cached value. */
    sealed interface Value {
        data object Blank : Value
        data class Num(val value: Double) : Value
        data class Txt(val text: String) : Value
        data class Bool(val flag: Boolean) : Value
        data class Err(val code: String) : Value
    }

    /** Sparse value access; an absent cell is [Value.Blank]. */
    fun interface Grid {
        fun value(cell: SheetRange.Cell): Value
    }

    /** What a fired rule paints. A scale and a bar carry their own arithmetic result. */
    sealed interface Visual {
        data class Font(
            val bold: Boolean = false,
            val italic: Boolean = false,
            val strike: Boolean = false,
            val underline: Boolean = false,
            val rgb: String? = null,
        ) : Visual

        data class Fill(val rgb: String) : Visual

        /** Interpolated colour of a colour scale, six upper-case hex digits. */
        data class Scale(val rgb: String) : Visual

        /** Data-bar length as a fraction of the cell width, clamped to `0.0..1.0`. */
        data class Bar(val fraction: Double) : Visual

        /**
         * The parts of a differential format this evaluator has no opinion about — a font's family and size, a
         * number format, a fill's pattern and background, a border, alignment and protection. They are carried
         * through unchanged so a renderer does not have to re-read `styles.xml`, and nothing here changes which
         * cells fire or how a lower-priority rule's paint is overridden.
         */
        data class Detail(
            val fontName: String? = null,
            val fontSize: Double? = null,
            val numberFormat: String? = null,
            val fillPattern: XlsxConditionalFormatting.PatternType? = null,
            val fillBgRgb: String? = null,
            val border: XlsxConditionalFormatting.Border? = null,
            val alignment: XlsxConditionalFormatting.Alignment? = null,
            val protection: XlsxConditionalFormatting.Protection? = null,
        ) : Visual {
            val isEmpty: Boolean
                get() = fontName == null && fontSize == null && numberFormat == null && fillPattern == null &&
                    fillBgRgb == null && border == null && alignment == null && protection == null

            /** Field-wise first-wins merge: a rule that fired earlier keeps every field it set. */
            fun merge(lower: Detail): Detail = Detail(
                fontName = fontName ?: lower.fontName,
                fontSize = fontSize ?: lower.fontSize,
                numberFormat = numberFormat ?: lower.numberFormat,
                fillPattern = fillPattern ?: lower.fillPattern,
                fillBgRgb = fillBgRgb ?: lower.fillBgRgb,
                border = border ?: lower.border,
                alignment = alignment ?: lower.alignment,
                protection = protection ?: lower.protection,
            )
        }
    }

    /**
     * A rule that fired. [icon] is set only by an `iconSet` rule: the icon it assigns is not a [Visual] because it
     * is an index into a set, not a colour or a length, and the rendering of that index is out of scope.
     */
    data class Fired(
        val priority: Int,
        val kind: XlsxConditionalFormatting.Kind,
        val visuals: List<Visual>,
        val icon: Icon? = null,
    )

    /** The icon an `iconSet` rule assigns: the set's schema name and a zero-based index inside it. */
    data class Icon(val set: String, val index: Int)

    /**
     * A reference date for `timePeriod` rules. [today] is a whole-day serial *in the workbook's own date system*,
     * which the caller reads from the file; this model never guesses it, because a rule evaluated against the
     * wrong date system is wrong by four years and one day.
     */
    data class Calendar(
        val today: Int,
        val date1904: Boolean = false,
        val weekStartsOn: WeekStart = WeekStart.MONDAY,
    ) {
        init {
            require(today >= if (date1904) 0 else 1) { "today $today is not a valid serial in this date system" }
        }

        companion object {
            /** Builds a calendar from a civil date, converting through the workbook's date system. */
            fun ofDate(
                year: Int,
                month: Int,
                day: Int,
                date1904: Boolean = false,
                weekStartsOn: WeekStart = WeekStart.MONDAY,
            ): Calendar = Calendar(
                today = if (date1904) SerialDate.toSerial1904(year, month, day)
                else SerialDate.toSerial1900(year, month, day),
                date1904 = date1904,
                weekStartsOn = weekStartsOn,
            )
        }
    }

    /** The first day of a week, which no attribute in a spreadsheet file states, so the caller supplies it. */
    enum class WeekStart(val token: String) { MONDAY("monday"), SUNDAY("sunday") }

    /** A rule that applies to the cell but whose predicate this evaluator will not decide. */
    data class Unresolved(val priority: Int, val reason: String)

    sealed interface Outcome {
        /**
         * Rules that fired, in priority order. [stopped] is true when a rule that *fired* carried `stopIfTrue`
         * and cut the scan; it is false when the scan reached the end, and it says nothing about an unresolved rule
         * that might have stopped it — [unresolved] lists every rule that was not decided, so the caller knows the
         * painting above them and that anything below them is unknown.
         */
        data class Decided(val fired: List<Fired>, val stopped: Boolean, val unresolved: List<Unresolved>) : Outcome

        /** Nothing fired, but an applicable rule could not be decided, so the painting is unknown. */
        data class Undecided(val priority: Int, val reason: String) : Outcome
    }

    /** The merged painting for a cell: first non-null property in priority order wins, as Excel documents. */
    data class Decoration(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val strike: Boolean = false,
        val underline: Boolean = false,
        val fontRgb: String? = null,
        val fillRgb: String? = null,
        val scaleRgb: String? = null,
        val barFraction: Double? = null,
        /** The icon set the highest-priority icon rule draws from, if any fired. */
        val iconSet: String? = null,
        /** The zero-based icon index inside [iconSet] that this cell earned. */
        val iconIndex: Int? = null,
        /** Everything the fired dxfs said that is not a decoration flag or a colour; `null` when they said none. */
        val detail: Visual.Detail? = null,
    )

    private val NUMBER = Regex("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?$")
    private val CELL_REF = Regex("^[A-Z]{1,3}[1-9][0-9]{0,6}$")
    private val COMPARISON = Regex("^(.+?)\\s*(<=|>=|<>|=|<|>)\\s*(.+)$")
    private val IS_BLANK = Regex("^ISBLANK\\(\\s*([A-Z]{1,3}[1-9][0-9]{0,6})\\s*\\)$", RegexOption.IGNORE_CASE)
    private val MOD_ROW = Regex(
        "^MOD\\(\\s*ROW\\(\\)\\s*,\\s*(\\d{1,4})\\s*\\)\\s*=\\s*(\\d{1,4})$",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Every rule that applies to [cell], in priority order, evaluated against [grid]. [blocks] is a sheet's
     * conditional formats as [XlsxConditionalFormatting.parse] returns them.
     */
    fun verdict(
        blocks: List<XlsxConditionalFormatting.Block>,
        grid: Grid,
        cell: SheetRange.Cell,
        calendar: Calendar? = null,
    ): Outcome {
        val applicable = blocks
            .filter { block -> block.covers(cell) }
            .flatMap { block -> block.rules }
            .sortedBy { it.priority }
        val fired = ArrayList<Fired>()
        val unresolved = ArrayList<Unresolved>()
        var stopped = false
        for (rule in applicable) {
            when (val decision = predicate(rule, blocks, grid, cell, calendar)) {
                is Decision.Unsupported -> unresolved.add(Unresolved(rule.priority, decision.reason))
                is Decision.No -> Unit
                is Decision.Yes -> {
                    fired.add(Fired(rule.priority, rule.kind, decision.visuals, decision.icon))
                    if (rule.stopIfTrue == true) {
                        stopped = true
                        break
                    }
                }
            }
        }
        // Nothing decided but something undecided: the cell's painting is unknown, and saying "no rule fired"
        // would be a guess dressed as a verdict.
        if (fired.isEmpty() && unresolved.isNotEmpty()) {
            return Outcome.Undecided(unresolved.first().priority, unresolved.first().reason)
        }
        return Outcome.Decided(fired, stopped, unresolved)
    }

    /** The merged painting of a decided outcome; a scale or bar from a lower-priority rule is kept only if unset. */
    fun decoration(outcome: Outcome.Decided): Decoration {
        var result = Decoration()
        for (fired in outcome.fired) {
            for (visual in fired.visuals) {
                result = when (visual) {
                    is Visual.Font -> result.copy(
                        bold = result.bold || visual.bold,
                        italic = result.italic || visual.italic,
                        strike = result.strike || visual.strike,
                        underline = result.underline || visual.underline,
                        fontRgb = result.fontRgb ?: visual.rgb,
                    )
                    is Visual.Fill -> result.copy(fillRgb = result.fillRgb ?: visual.rgb)
                    is Visual.Scale -> result.copy(scaleRgb = result.scaleRgb ?: visual.rgb)
                    is Visual.Bar -> result.copy(barFraction = result.barFraction ?: visual.fraction)
                    is Visual.Detail -> result.copy(detail = result.detail?.merge(visual) ?: visual)
                }
            }
            // An icon is not merged: the first rule that assigns one owns the cell's icon, as with fills.
            fired.icon?.let { icon ->
                if (result.iconIndex == null) result = result.copy(iconSet = icon.set, iconIndex = icon.index)
            }
        }
        return result
    }

    private sealed interface Decision {
        data class Yes(val visuals: List<Visual>, val icon: Icon? = null) : Decision
        data object No : Decision
        data class Unsupported(val reason: String) : Decision
    }

    private fun XlsxConditionalFormatting.Block.covers(cell: SheetRange.Cell): Boolean =
        sqref.split(' ').any { token ->
            runCatching { SheetRange.contains(SheetRange.parse(token), cell) }.getOrDefault(false)
        }

    private fun predicate(
        rule: XlsxConditionalFormatting.Rule,
        blocks: List<XlsxConditionalFormatting.Block>,
        grid: Grid,
        cell: SheetRange.Cell,
        calendar: Calendar? = null,
    ): Decision = when (rule.kind) {
        XlsxConditionalFormatting.Kind.CELL_IS -> cellIs(rule, grid, cell)
        XlsxConditionalFormatting.Kind.EXPRESSION -> expression(rule, grid, cell)
        XlsxConditionalFormatting.Kind.TOP10 -> top10(rule, blocks, grid, cell)
        XlsxConditionalFormatting.Kind.COLOR_SCALE -> colorScale(rule, blocks, grid, cell)
        XlsxConditionalFormatting.Kind.DATA_BAR -> dataBar(rule, blocks, grid, cell)
        XlsxConditionalFormatting.Kind.TEXT_CONTAINS,
        XlsxConditionalFormatting.Kind.TEXT_NOT_CONTAINS,
        XlsxConditionalFormatting.Kind.TEXT_BEGINS_WITH,
        XlsxConditionalFormatting.Kind.TEXT_ENDS_WITH,
        -> textRule(rule, grid, cell)
        XlsxConditionalFormatting.Kind.ABOVE_AVERAGE -> aboveAverage(rule, blocks, grid, cell)
        XlsxConditionalFormatting.Kind.DUPLICATE,
        XlsxConditionalFormatting.Kind.UNIQUE,
        -> duplicates(rule, blocks, grid, cell)
        XlsxConditionalFormatting.Kind.CONTAINS_BLANKS,
        XlsxConditionalFormatting.Kind.NOT_CONTAINS_BLANKS,
        -> blanks(rule, grid, cell)
        XlsxConditionalFormatting.Kind.CONTAINS_ERRORS,
        XlsxConditionalFormatting.Kind.NOT_CONTAINS_ERRORS,
        -> errors(rule, grid, cell)
        XlsxConditionalFormatting.Kind.TIME_PERIOD -> timePeriod(rule, grid, cell, calendar)
        XlsxConditionalFormatting.Kind.ICON_SET -> iconSet(rule, blocks, grid, cell)
    }

    // --- operands ----------------------------------------------------------------------------------------

    private sealed interface Operand {
        data class Number(val value: Double) : Operand
        data class Text(val text: String) : Operand
        data class Bool(val flag: Boolean) : Operand
        data class Reference(val cell: SheetRange.Cell) : Operand
    }

    /** A formula operand this evaluator understands; anything else is refused rather than guessed. */
    private fun operand(text: String): Operand? = when {
        text.isEmpty() -> null
        NUMBER.matches(text) -> Operand.Number(text.toDouble())
        text.length >= 2 && text.startsWith('"') && text.endsWith('"') ->
            Operand.Text(text.substring(1, text.length - 1).replace("\"\"", "\""))
        text.equals("TRUE", ignoreCase = true) -> Operand.Bool(true)
        text.equals("FALSE", ignoreCase = true) -> Operand.Bool(false)
        CELL_REF.matches(text.uppercase()) -> runCatching { Operand.Reference(SheetRange.parseCell(text.uppercase())) }
            .getOrNull()
        else -> null
    }

    private fun resolve(candidate: Operand, grid: Grid): Value = when (candidate) {
        is Operand.Number -> Value.Num(candidate.value)
        is Operand.Text -> Value.Txt(candidate.text)
        is Operand.Bool -> Value.Bool(candidate.flag)
        is Operand.Reference -> grid.value(candidate.cell)
    }

    // --- cellIs ------------------------------------------------------------------------------------------

    private fun cellIs(
        rule: XlsxConditionalFormatting.Rule,
        grid: Grid,
        cell: SheetRange.Cell,
    ): Decision {
        val operator = rule.operator ?: return Decision.Unsupported("cellIs without an operator")
        val left = grid.value(cell)
        val right = rule.formulas.map { formula ->
            operand(formula.trim())?.let { resolve(it, grid) }
                ?: return Decision.Unsupported("cellIs operand '$formula' is not a literal or a cell reference")
        }
        if (operator == XlsxConditionalFormatting.Operator.BETWEEN ||
            operator == XlsxConditionalFormatting.Operator.NOT_BETWEEN
        ) {
            if (right.size != 2) return Decision.Unsupported("${operator.token} needs two operands")
            val low = order(left, right[0]) ?: return undecidable(left, right[0])
            val high = order(left, right[1]) ?: return undecidable(left, right[1])
            // Inclusive at both ends: the lower bound must not exceed the value, the upper must not fall below it.
            val inside = low >= 0 && high <= 0
            return yes(inside != (operator == XlsxConditionalFormatting.Operator.NOT_BETWEEN), rule)
        }
        if (right.size != 1) return Decision.Unsupported("${operator.token} needs one operand")
        val comparison = order(left, right[0]) ?: return undecidable(left, right[0])
        return yes(holds(comparison, operator.token), rule)
    }

    /** Maps an `ST_ConditionalFormattingOperator` token onto a three-way comparison. */
    private fun holds(comparison: Int, token: String): Boolean = when (token) {
        "lessThan" -> comparison < 0
        "lessThanOrEqual" -> comparison <= 0
        "equal" -> comparison == 0
        "notEqual" -> comparison != 0
        "greaterThan" -> comparison > 0
        "greaterThanOrEqual" -> comparison >= 0
        else -> false
    }

    /** Maps an expression comparison operator onto a three-way comparison. */
    private fun holds(comparison: Int, token: String, symbolic: Boolean): Boolean = when {
        !symbolic -> holds(comparison, token)
        token == "<" -> comparison < 0
        token == "<=" -> comparison <= 0
        token == "=" -> comparison == 0
        token == "<>" -> comparison != 0
        token == ">" -> comparison > 0
        else -> comparison >= 0
    }

    private fun undecidable(left: Value, right: Value): Decision.Unsupported = Decision.Unsupported(
        "cannot order ${describe(left)} against ${describe(right)}",
    )

    private fun describe(value: Value): String = when (value) {
        is Value.Blank -> "blank"
        is Value.Num -> "number ${value.value}"
        is Value.Txt -> "text \"${value.text}\""
        is Value.Bool -> "boolean ${value.flag}"
        is Value.Err -> "error ${value.code}"
    }

    /**
     * `-1`, `0`, `+1` for `left` against `right`, or null when the pair has no defined order here.
     *
     * The type rule is Excel's, and it is the one coercion this model refuses to invent: a number never equals a
     * text and sorts before every text (`="5"=5` is FALSE, `="5">5` is TRUE). Numeric-looking text is *not* folded
     * into a number, because doing so would make `"10"` equal `10` — a comparison Excel answers the other way.
     */
    private fun order(left: Value, right: Value): Int? {
        if (left is Value.Err || right is Value.Err) return null
        if (left is Value.Bool && right is Value.Bool) return if (left.flag == right.flag) 0 else null
        if (left is Value.Bool || right is Value.Bool) return null
        val leftIsText = left is Value.Txt
        val rightIsText = right is Value.Txt
        if (leftIsText != rightIsText) {
            val other = if (leftIsText) right else left
            val otherIsNumeric = other is Value.Num || other is Value.Blank
            if (!otherIsNumeric) return null
            return if (leftIsText) 1 else -1
        }
        if (leftIsText) return textOrder(left, right)
        val a = asNumber(left)
        val b = asNumber(right)
        return if (a != null && b != null) a.compareTo(b) else null
    }

    /** Blank reads as zero against a number, which is the one coercion this model does import. */
    private fun asNumber(value: Value): Double? = when (value) {
        is Value.Blank -> 0.0
        is Value.Num -> value.value
        is Value.Txt, is Value.Bool, is Value.Err -> null
    }

    private fun textOrder(left: Value, right: Value): Int? {
        val a = when (left) {
            is Value.Txt -> left.text
            is Value.Blank -> ""
            else -> return null
        }
        val b = when (right) {
            is Value.Txt -> right.text
            is Value.Blank -> ""
            else -> return null
        }
        // Ordering text is collation-dependent and this host has no collator, so only equality is decided: an
        // unequal pair is Unsupported rather than "greater", because a locale could order it either way.
        return if (a.equals(b, ignoreCase = true)) 0 else null
    }

    private fun yes(holds: Boolean, rule: XlsxConditionalFormatting.Rule): Decision =
        if (holds) Decision.Yes(visualsOf(rule)) else Decision.No

    private fun visualsOf(rule: XlsxConditionalFormatting.Rule): List<Visual> {
        val format = rule.format ?: return emptyList()
        val font = Visual.Font(
            bold = format.bold,
            italic = format.italic,
            strike = format.strike,
            underline = format.underline,
            rgb = format.fontRgb,
        )
        val detail = Visual.Detail(
            fontName = format.fontName,
            fontSize = format.fontSize,
            numberFormat = format.numberFormat,
            fillPattern = format.fillPattern,
            fillBgRgb = format.fillBgRgb,
            border = format.border,
            alignment = format.alignment,
            protection = format.protection,
        )
        return buildList {
            if (format.hasFont) add(font)
            format.fillRgb?.let { add(Visual.Fill(it)) }
            if (!detail.isEmpty) add(detail)
        }
    }

    // --- expression --------------------------------------------------------------------------------------

    private fun expression(
        rule: XlsxConditionalFormatting.Rule,
        grid: Grid,
        cell: SheetRange.Cell,
    ): Decision {
        val formula = rule.formulas.singleOrNull()
            ?: return Decision.Unsupported("an expression rule without exactly one formula")
        val text = formula.trim()
        IS_BLANK.matchEntire(text)?.let { match ->
            val target = runCatching { SheetRange.parseCell(match.groupValues[1].uppercase()) }.getOrNull()
                ?: return Decision.Unsupported("ISBLANK target is not a cell reference")
            return yes(grid.value(target) is Value.Blank, rule)
        }
        MOD_ROW.matchEntire(text)?.let { match ->
            val divisor = match.groupValues[1].toInt()
            val remainder = match.groupValues[2].toInt()
            if (divisor == 0) return Decision.Unsupported("MOD by zero")
            return yes(cell.row % divisor == remainder, rule)
        }
        val match = COMPARISON.matchEntire(text)
            ?: return Decision.Unsupported("expression '${shorten(text)}' is outside the decided grammar")
        val leftText = match.groupValues[1].trim()
        val operator = match.groupValues[2]
        val rightText = match.groupValues[3].trim()
        val left = operand(leftText) ?: return Decision.Unsupported("left side '${shorten(leftText)}' is not supported")
        val right = operand(rightText) ?: return Decision.Unsupported("right side '${shorten(rightText)}' is not supported")
        val a = resolve(left, grid)
        val b = resolve(right, grid)
        if (operator == "=" || operator == "<>") {
            val equal = equalValues(a, b) ?: return undecidable(a, b)
            return yes(equal == (operator == "="), rule)
        }
        val comparison = order(a, b) ?: return undecidable(a, b)
        return yes(holds(comparison, operator, symbolic = true), rule)
    }

    /** Equality with the same type rule as [order]; null when the pair has no defined equality here. */
    private fun equalValues(a: Value, b: Value): Boolean? = when {
        a is Value.Err || b is Value.Err -> null
        a is Value.Bool || b is Value.Bool -> {
            val left = a as? Value.Bool
            val right = b as? Value.Bool
            if (left != null && right != null) left.flag == right.flag else null
        }
        a is Value.Blank && b is Value.Txt -> b.text.isEmpty()
        a is Value.Txt && b is Value.Blank -> a.text.isEmpty()
        // Text and a number are never equal, in either direction.
        a is Value.Txt || b is Value.Txt -> {
            val x = (a as? Value.Txt)?.text
            val y = (b as? Value.Txt)?.text
            if (x != null && y != null) x.equals(y, ignoreCase = true) else false
        }
        else -> {
            val left = asNumber(a)
            val right = asNumber(b)
            if (left != null && right != null) left == right else null
        }
    }

    private fun shorten(text: String): String = if (text.length <= 40) text else text.take(37) + "..."

    // --- range-wide rules --------------------------------------------------------------------------------

    /**
     * The largest range a rank- or scale-relative rule is allowed to scan. A block may name `A1:A1048576`; walking
     * it per evaluated cell is unbounded work, so a range over this size is refused instead of silently hanging.
     */
    const val MAX_RANGE_CELLS = 100_000

    /**
     * Numeric values of every cell in the union of the blocks' references, or null when a reference is larger than
     * [MAX_RANGE_CELLS]: skipping the range silently would turn "too big to scan" into "no rule fires".
     */
    private fun numbersIn(ruleBlocks: List<XlsxConditionalFormatting.Block>, grid: Grid): List<Double>? =
        valuesIn(ruleBlocks, grid)?.mapNotNull { (it as? Value.Num)?.value }

    /** Every value in the union of the blocks' references, in row-major order; null when a reference is too large. */
    private fun valuesIn(ruleBlocks: List<XlsxConditionalFormatting.Block>, grid: Grid): List<Value>? {
        val out = ArrayList<Value>()
        for (block in ruleBlocks) {
            for (token in block.sqref.split(' ')) {
                val range = runCatching { SheetRange.parse(token) }.getOrNull() ?: continue
                if (range.cells > MAX_RANGE_CELLS) return null
                for (row in range.top.row..range.bottom.row) {
                    for (col in range.top.col..range.bottom.col) {
                        out.add(grid.value(SheetRange.Cell(col, row)))
                    }
                }
            }
        }
        return out
    }

    /** True when any reference of the blocks holding [rule] is larger than [MAX_RANGE_CELLS]. */
    private fun scopeTooLarge(rule: XlsxConditionalFormatting.Rule, blocks: List<XlsxConditionalFormatting.Block>): Boolean =
        blocks.filter { block -> block.rules.any { it === rule } }.any { block ->
            block.sqref.split(' ').any { token ->
                runCatching { SheetRange.parse(token).cells > MAX_RANGE_CELLS }.getOrDefault(false)
            }
        }

    private fun top10(
        rule: XlsxConditionalFormatting.Rule,
        blocks: List<XlsxConditionalFormatting.Block>,
        grid: Grid,
        cell: SheetRange.Cell,
    ): Decision {
        val rank = rule.rank ?: return Decision.Unsupported("top10 without a rank")
        if (scopeTooLarge(rule, blocks)) {
            return Decision.Unsupported("a rank-relative rule over a range larger than $MAX_RANGE_CELLS cells")
        }
        val scope = blocks.filter { block -> block.rules.any { it === rule } }
        val values = numbersIn(scope, grid)
            ?: return Decision.Unsupported("a rank-relative rule over a range larger than $MAX_RANGE_CELLS cells")
        if (values.isEmpty()) return Decision.No
        val bottom = rule.bottom == true
        val ordered = if (bottom) values.sorted() else values.sortedDescending()
        val take = if (rule.percent == true) {
            kotlin.math.ceil(ordered.size * rank / 100.0).toInt().coerceAtLeast(1)
        } else {
            rank
        }
        if (take >= ordered.size) return yes((grid.value(cell) as? Value.Num) != null, rule)
        // Ties extend the set: every value equal to the cut-off counts, which is what makes a rank-relative rule
        // predictable instead of dependent on row order.
        val threshold = ordered[take - 1]
        val value = (grid.value(cell) as? Value.Num)?.value ?: return Decision.No
        val holds = if (bottom) value <= threshold else value >= threshold
        return yes(holds, rule)
    }

    // --- text, average and duplicate rules ----------------------------------------------------------------

    /**
     * `containsText`, `notContainsText`, `beginsWith`, `endsWith`.
     *
     * Import hypotheses, stated so they can be attacked (see `docs/R100-RESULTS.md` §C): `containsText` uses
     * Excel's SEARCH grammar — `?` is one character, `*` is any run, `~` escapes the next character — matched
     * case-insensitively over a cell's displayed text; `beginsWith`/`endsWith` are plain prefix/suffix compares
     * with the same case folding, and Excel writes them as `LEFT`/`RIGHT` formulae, which are literal; a blank
     * cell reads as the empty string; an error cell is not comparable and leaves the rule undecided.
     */
    private fun textRule(
        rule: XlsxConditionalFormatting.Rule,
        grid: Grid,
        cell: SheetRange.Cell,
    ): Decision {
        val literal = rule.text ?: return Decision.Unsupported("a ${rule.kind.token} rule without text")
        val value = grid.value(cell)
        val text = textOf(value) ?: return Decision.Unsupported("an error value has no text to compare")
        val holds = when (rule.kind) {
            XlsxConditionalFormatting.Kind.TEXT_CONTAINS -> containsLiteral(literal, text)
            XlsxConditionalFormatting.Kind.TEXT_NOT_CONTAINS -> !containsLiteral(literal, text)
            XlsxConditionalFormatting.Kind.TEXT_BEGINS_WITH -> text.startsWith(literal, ignoreCase = true)
            else -> text.endsWith(literal, ignoreCase = true)
        }
        return yes(holds, rule)
    }

    /**
     * SEARCH's question — does [literal] occur *anywhere* in [text]? A leading and trailing `*` turn the
     * anchored matcher below into an unanchored one: `*P*` matches a whole text exactly when `P` occurs inside it.
     */
    private fun containsLiteral(literal: String, text: String): Boolean =
        matches(tokens("*" + literal + "*"), text)

    /** A cell's text as Excel's text predicates see it; null for an error, which has none. */
    private fun textOf(value: Value): String? = when (value) {
        is Value.Txt -> value.text
        Value.Blank -> ""
        is Value.Num -> plainNumber(value.value)
        is Value.Bool -> if (value.flag) "TRUE" else "FALSE"
        is Value.Err -> null
    }

    /** A number as Excel's general format would show it, so a text predicate can look at it. */
    private fun plainNumber(value: Double): String = when {
        !value.isFinite() -> value.toString()
        value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15 -> value.toLong().toString()
        else -> value.toString()
    }

    private sealed interface Token {
        data class Lit(val char: Char) : Token
        data object Any : Token
        data object Star : Token
    }

    private fun tokens(pattern: String): List<Token> {
        val out = ArrayList<Token>(pattern.length)
        var index = 0
        while (index < pattern.length) {
            when (val char = pattern[index]) {
                '~' ->
                    if (index + 1 < pattern.length) {
                        out.add(Token.Lit(pattern[index + 1])); index += 2
                    } else {
                        out.add(Token.Lit('~')); index += 1
                    }
                '*' -> { out.add(Token.Star); index += 1 }
                '?' -> { out.add(Token.Any); index += 1 }
                else -> { out.add(Token.Lit(char)); index += 1 }
            }
        }
        return out
    }

    /** Greedy wildcard match with one backtrack point — linear in practice, no recursion, no allocation. */
    private fun matches(tokens: List<Token>, text: String): Boolean {
        var p = 0
        var t = 0
        var star = -1
        var mark = 0
        while (t < text.length) {
            when {
                p < tokens.size && tokens[p] is Token.Star -> { star = p; mark = t; p++ }
                p < tokens.size && tokens[p] is Token.Any -> { p++; t++ }
                p < tokens.size && (tokens[p] as? Token.Lit)?.let { folds(it.char, text[t]) } == true -> { p++; t++ }
                star >= 0 -> { mark++; t = mark; p = star + 1 }
                else -> return false
            }
        }
        while (p < tokens.size && tokens[p] is Token.Star) p++
        return p == tokens.size
    }

    /** Case folding without a collator: ASCII and the Unicode simple case mapping, which is host-independent. */
    private fun folds(left: Char, right: Char): Boolean =
        left == right || left.lowercaseChar() == right.lowercaseChar()

    /**
     * `aboveAverage`: does the cell sit above (or, with `aboveAverage="0"`, below) the mean of the rule's own
     * range, widened by `stdDev` multiples of the population standard deviation? `equalAverage` includes ties.
     *
     * Import hypotheses: the band is computed over the numeric cells of the union of the rule's references — the
     * same scope `top10` uses — with non-numeric cells skipped, not coerced to zero; standard deviation is the
     * population form (divide by n); the schema default for `aboveAverage` is true and for `equalAverage` false.
     * An error cell has no rank in a numeric band and leaves the rule undecided.
     */
    private fun aboveAverage(
        rule: XlsxConditionalFormatting.Rule,
        blocks: List<XlsxConditionalFormatting.Block>,
        grid: Grid,
        cell: SheetRange.Cell,
    ): Decision {
        if (scopeTooLarge(rule, blocks)) {
            return Decision.Unsupported("an average-relative rule over a range larger than $MAX_RANGE_CELLS cells")
        }
        val value = grid.value(cell)
        if (value is Value.Err) return Decision.Unsupported("an error value has no rank among the average band")
        val number = (value as? Value.Num)?.value
            ?: return Decision.No
        val scope = blocks.filter { block -> block.rules.any { it === rule } }
        val values = numbersIn(scope, grid)
            ?: return Decision.Unsupported("an average-relative rule over a range larger than $MAX_RANGE_CELLS cells")
        if (values.isEmpty()) return Decision.No
        val mean = values.sum() / values.size
        val steps = rule.stdDev ?: 0
        val band = if (steps == 0) {
            0.0
        } else {
            steps * kotlin.math.sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        }
        val inclusive = rule.equalAverage == true
        val holds = if (rule.aboveAverage != false) {
            if (inclusive) number >= mean + band else number > mean + band
        } else {
            if (inclusive) number <= mean - band else number < mean - band
        }
        return yes(holds, rule)
    }

    /**
     * `duplicateValues` / `uniqueValues`: the cell's value occurs more than once (or exactly once) in the union of
     * the rule's references.
     *
     * Import hypotheses: duplicates are counted by *value*, across the whole reference and not within a row;
     * numbers compare numerically (`1` and `1.00` collide); text compares case-insensitively, as COUNTIF does;
     * text and number never collide; blanks and error cells are never duplicated, so a blank cell is never
     * highlighted by either rule.
     */
    private fun duplicates(
        rule: XlsxConditionalFormatting.Rule,
        blocks: List<XlsxConditionalFormatting.Block>,
        grid: Grid,
        cell: SheetRange.Cell,
    ): Decision {
        if (scopeTooLarge(rule, blocks)) {
            return Decision.Unsupported("a duplicate rule over a range larger than $MAX_RANGE_CELLS cells")
        }
        val key = keyOf(grid.value(cell)) ?: return Decision.No
        val scope = blocks.filter { block -> block.rules.any { it === rule } }
        val values = valuesIn(scope, grid)
            ?: return Decision.Unsupported("a duplicate rule over a range larger than $MAX_RANGE_CELLS cells")
        val occurrences = values.count { keyOf(it) == key }
        val holds = if (rule.kind == XlsxConditionalFormatting.Kind.DUPLICATE) {
            occurrences > 1
        } else {
            occurrences == 1
        }
        return yes(holds, rule)
    }

    /** Comparison key for duplicate detection; null when a value takes part in no comparison. */
    private fun keyOf(value: Value): String? = when (value) {
        Value.Blank -> null
        is Value.Num -> "n:" + plainNumber(value.value)
        is Value.Txt -> "t:" + value.text.lowercase()
        is Value.Bool -> "b:" + value.flag
        is Value.Err -> null
    }

    // --- blank, error, time and icon rules ----------------------------------------------------------------

    /**
     * `containsBlanks` / `notContainsBlanks`, on Excel's own formula `LEN(TRIM(cell))=0` — so a cell holding only
     * spaces counts as blank, because `TRIM` removes spaces, while a cell holding only a tab does not.
     *
     * The error case is the one place these two rules are *not* complements: Excel's argument errors out, an
     * erroring conditional-format formula applies nothing, and the negation is written as a second formula that
     * errors on the same cell. So an error cell fires neither rule, and neither is left undecided.
     */
    private fun blanks(
        rule: XlsxConditionalFormatting.Rule,
        grid: Grid,
        cell: SheetRange.Cell,
    ): Decision {
        val text = when (val value = grid.value(cell)) {
            Value.Blank -> ""
            is Value.Txt -> value.text
            is Value.Num -> plainNumber(value.value)
            is Value.Bool -> if (value.flag) "TRUE" else "FALSE"
            is Value.Err -> return Decision.No
        }
        val empty = text.trim { it == ' ' }.isEmpty()
        val holds = if (rule.kind == XlsxConditionalFormatting.Kind.CONTAINS_BLANKS) empty else !empty
        return yes(holds, rule)
    }

    /** `containsErrors` / `notContainsErrors`, on `ISERROR(cell)`; a blank is not an error, so the negative form
     *  fires on it, and an error cell fires only the positive form. */
    private fun errors(
        rule: XlsxConditionalFormatting.Rule,
        grid: Grid,
        cell: SheetRange.Cell,
    ): Decision {
        val isError = grid.value(cell) is Value.Err
        val holds = if (rule.kind == XlsxConditionalFormatting.Kind.CONTAINS_ERRORS) isError else !isError
        return yes(holds, rule)
    }

    /** The civil day a whole-day serial names, or null when the serial lies outside the modelled window. */
    private fun Calendar.day(serial: Int): SerialDate.Day? =
        if (date1904) runCatching { SerialDate.fromSerial1904(serial) }.getOrNull()
        else runCatching { SerialDate.fromSerial1900(serial) }.getOrNull()

    /**
     * Day-of-week index with Monday = 0, computed in *serial* space rather than from the civil date, so the 1900
     * phantom leap day costs nothing: the phantom occupies a serial without occupying a day, so serials after it
     * subtract one day. This reproduces Excel's own `WEEKDAY` on both date systems (serial 1 is a Monday, serial
     * 61 a Thursday) and needs no calendar table.
     */
    private fun Calendar.weekday(serial: Int): Int =
        if (date1904) Math.floorMod(serial + 4, 7)
        else Math.floorMod(serial - 1 - (if (serial >= 61) 1 else 0), 7)

    /** The serial of the first day of the week containing [serial], under the caller's first day of week. */
    private fun Calendar.weekStart(serial: Int, start: WeekStart): Int {
        val monday = weekday(serial)
        val offset = if (start == WeekStart.SUNDAY) (monday + 1) % 7 else monday
        return serial - offset
    }

    private fun monthIndex(day: SerialDate.Day): Int = day.year * 12 + (day.month - 1)

    private fun quarter(month: Int): Int = (month - 1) / 3 + 1

    /**
     * `timePeriod`: does the cell's date fall in the named preset relative to [calendar]?
     *
     * Import hypotheses, stated because no application ran: the presets mean what their Excel names say
     * (`today` is that day, `last7Days` is the six days before it plus it, `lastMonth` is the calendar month
     * before, and the four quarter presets are the named quarter *of the reference year*, mirroring `thisMonth`);
     * a cell's date is the serial with its time fraction dropped; a numeric cell that is not a date in the
     * workbook's date system decides nothing (it is not a date, so no format applies) rather than being refused;
     * week boundaries follow [Calendar.weekStartsOn] because the file states no first day of week, and the base
     * schema's quarters are the only presets with no Excel user-interface counterpart to compare against.
     */
    private fun timePeriod(
        rule: XlsxConditionalFormatting.Rule,
        grid: Grid,
        cell: SheetRange.Cell,
        calendar: Calendar?,
    ): Decision {
        val period = rule.timePeriod
            ?: return Decision.Unsupported("a timePeriod rule without a period")
        val cal = calendar
            ?: return Decision.Unsupported("a timePeriod rule needs a reference date")
        val raw = (grid.value(cell) as? Value.Num)?.value ?: return Decision.No
        val serial = kotlin.math.floor(raw).toInt()
        val day = cal.day(serial) ?: return Decision.No
        val today = cal.day(cal.today)
            ?: return Decision.Unsupported("the reference date is outside the modelled range")
        val weekOfCell = cal.weekStart(serial, cal.weekStartsOn)
        val weekOfToday = cal.weekStart(cal.today, cal.weekStartsOn)
        val holds = when (period) {
            XlsxConditionalFormatting.TimePeriod.TODAY -> serial == cal.today
            XlsxConditionalFormatting.TimePeriod.YESTERDAY -> serial == cal.today - 1
            XlsxConditionalFormatting.TimePeriod.TOMORROW -> serial == cal.today + 1
            XlsxConditionalFormatting.TimePeriod.LAST_7_DAYS -> serial in (cal.today - 6)..cal.today
            XlsxConditionalFormatting.TimePeriod.THIS_MONTH ->
                day.year == today.year && day.month == today.month
            XlsxConditionalFormatting.TimePeriod.LAST_MONTH -> monthIndex(day) == monthIndex(today) - 1
            XlsxConditionalFormatting.TimePeriod.NEXT_MONTH -> monthIndex(day) == monthIndex(today) + 1
            XlsxConditionalFormatting.TimePeriod.THIS_YEAR -> day.year == today.year
            XlsxConditionalFormatting.TimePeriod.LAST_YEAR -> day.year == today.year - 1
            XlsxConditionalFormatting.TimePeriod.NEXT_YEAR -> day.year == today.year + 1
            XlsxConditionalFormatting.TimePeriod.THIS_WEEK -> weekOfCell == weekOfToday
            XlsxConditionalFormatting.TimePeriod.LAST_WEEK -> weekOfCell == weekOfToday - 7
            XlsxConditionalFormatting.TimePeriod.NEXT_WEEK -> weekOfCell == weekOfToday + 7
            XlsxConditionalFormatting.TimePeriod.FIRST_QUARTER ->
                day.year == today.year && quarter(day.month) == 1
            XlsxConditionalFormatting.TimePeriod.SECOND_QUARTER ->
                day.year == today.year && quarter(day.month) == 2
            XlsxConditionalFormatting.TimePeriod.THIRD_QUARTER ->
                day.year == today.year && quarter(day.month) == 3
            XlsxConditionalFormatting.TimePeriod.FOURTH_QUARTER ->
                day.year == today.year && quarter(day.month) == 4
        }
        return yes(holds, rule)
    }

    /**
     * `iconSet`: which icon index the cell's value earns.
     *
     * The boundaries are resolved exactly as a colour scale's are (percent of the range's span, percentile of its
     * order statistics, or an absolute number), the index is the number of boundaries the value reaches, and
     * `reverse` flips it. `showValue` does not move the index: it decides whether the number is printed beside the
     * icon, which is a rendering question this model does not answer. A blank or text cell earns no icon at all.
     */
    private fun iconSet(
        rule: XlsxConditionalFormatting.Rule,
        blocks: List<XlsxConditionalFormatting.Block>,
        grid: Grid,
        cell: SheetRange.Cell,
    ): Decision {
        val set = rule.iconSet ?: return Decision.Unsupported("an iconSet rule without a set")
        // A cell that is not a number earns no icon, and that answer does not depend on the range's boundaries:
        // deciding it first keeps an all-blank range from turning "no icon" into "unknown".
        val value = (grid.value(cell) as? Value.Num)?.value ?: return Decision.No
        if (scopeTooLarge(rule, blocks)) {
            return Decision.Unsupported("an iconSet rule over a range larger than $MAX_RANGE_CELLS cells")
        }
        val points = resolvedThresholds(rule, blocks, grid)
            ?: return Decision.Unsupported("an iconSet rule whose boundaries cannot be resolved")
        if (points.size != set.iconCount - 1) {
            return Decision.Unsupported("a ${set.token} rule needs ${set.iconCount - 1} boundaries")
        }
        if (points.zipWithNext().any { (low, high) -> high < low }) {
            return Decision.Unsupported("an iconSet rule whose boundaries are not in ascending order")
        }
        val reached = points.count { value >= it }
        val index = if (rule.reverse == true) set.iconCount - 1 - reached else reached
        return Decision.Yes(emptyList(), Icon(set.token, index))
    }

    private fun resolvedThresholds(
        rule: XlsxConditionalFormatting.Rule,
        blocks: List<XlsxConditionalFormatting.Block>,
        grid: Grid,
    ): List<Double>? {
        val scope = blocks.filter { block -> block.rules.any { it === rule } }
        val values = numbersIn(scope, grid)?.sorted() ?: return null
        if (values.isEmpty()) return null
        val minimum = values.first()
        val maximum = values.last()
        return rule.thresholds.map { threshold ->
            when (threshold.kind) {
                XlsxConditionalFormatting.ThresholdKind.MIN -> minimum
                XlsxConditionalFormatting.ThresholdKind.MAX -> maximum
                XlsxConditionalFormatting.ThresholdKind.NUM -> threshold.value ?: return null
                XlsxConditionalFormatting.ThresholdKind.PERCENT -> {
                    val percent = threshold.value ?: return null
                    minimum + (maximum - minimum) * percent / 100.0
                }
                XlsxConditionalFormatting.ThresholdKind.PERCENTILE ->
                    percentile(values, threshold.value ?: return null)
            }
        }
    }

    /** Linear interpolation between order statistics: `pos = p/100 * (n-1)`, between the two neighbours. */
    fun percentile(sorted: List<Double>, percent: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val position = (percent / 100.0).coerceIn(0.0, 1.0) * (sorted.size - 1)
        val lower = kotlin.math.floor(position).toInt()
        val upper = kotlin.math.ceil(position).toInt()
        if (lower == upper) return sorted[lower]
        val weight = position - lower
        return sorted[lower] * (1 - weight) + sorted[upper] * weight
    }

    private fun colorScale(
        rule: XlsxConditionalFormatting.Rule,
        blocks: List<XlsxConditionalFormatting.Block>,
        grid: Grid,
        cell: SheetRange.Cell,
    ): Decision {
        if (scopeTooLarge(rule, blocks)) {
            return Decision.Unsupported("a colour scale over a range larger than $MAX_RANGE_CELLS cells")
        }
        val points = resolvedThresholds(rule, blocks, grid)
            ?: return Decision.Unsupported("a colour scale whose thresholds cannot be resolved")
        if (points.size < 2) return Decision.Unsupported("a colour scale needs two thresholds")
        if (points.zipWithNext().any { (low, high) -> high < low }) {
            return Decision.Unsupported("a colour scale whose thresholds are not in ascending order")
        }
        val value = when (val current = grid.value(cell)) {
            is Value.Num -> current.value
            // A colour scale paints every cell in its range; a blank has no position on the scale and takes the
            // first colour, which is the same choice Excel makes for an empty cell at the low end.
            is Value.Blank -> points.first()
            else -> return Decision.Unsupported("a colour scale over ${describe(current)}")
        }
        return Decision.Yes(listOf(Visual.Scale(interpolate(rule.colors, points, value))))
    }

    private fun interpolate(colors: List<String>, points: List<Double>, value: Double): String {
        if (colors.isEmpty()) return "000000"
        if (colors.size == 1) return colors[0].uppercase()
        if (value <= points.first()) return colors.first().uppercase()
        if (value >= points.last()) return colors.last().uppercase()
        for (index in 0 until points.size - 1) {
            val low = points[index]
            val high = points[index + 1]
            if (value >= low && value <= high) {
                if (high <= low) return colors[index].uppercase()
                val weight = (value - low) / (high - low)
                return mix(colors[index], colors[index + 1], weight)
            }
        }
        return colors.last().uppercase()
    }

    /** Per-channel sRGB interpolation, alpha dropped (dxf colours are opaque here), rounding half-up. */
    fun mix(from: String, to: String, weight: Double): String {
        val a = rgb(from)
        val b = rgb(to)
        val w = weight.coerceIn(0.0, 1.0)
        return buildString(6) {
            for (channel in 0..2) {
                val value = a[channel] * (1 - w) + b[channel] * w
                append(channelHex(kotlin.math.round(value).toInt()))
            }
        }
    }

    private fun rgb(hex: String): IntArray {
        require(hex.length == 6) { "A colour must be six hex digits: $hex" }
        val value = hex.toInt(16)
        return intArrayOf((value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF)
    }

    private fun channelHex(value: Int): String = value.coerceIn(0, 255).toString(16).uppercase().padStart(2, '0')

    private fun dataBar(
        rule: XlsxConditionalFormatting.Rule,
        blocks: List<XlsxConditionalFormatting.Block>,
        grid: Grid,
        cell: SheetRange.Cell,
    ): Decision {
        if (scopeTooLarge(rule, blocks)) {
            return Decision.Unsupported("a data bar over a range larger than $MAX_RANGE_CELLS cells")
        }
        val points = resolvedThresholds(rule, blocks, grid)
            ?: return Decision.Unsupported("a data bar whose thresholds cannot be resolved")
        if (points.size < 2) return Decision.Unsupported("a data bar needs two thresholds")
        val value = (grid.value(cell) as? Value.Num)?.value ?: return Decision.No
        val low = points.first()
        val high = points.last()
        val fraction = if (high <= low) {
            if (value >= high) 1.0 else 0.0
        } else {
            ((value - low) / (high - low)).coerceIn(0.0, 1.0)
        }
        return Decision.Yes(listOf(Visual.Bar(fraction)))
    }
}
