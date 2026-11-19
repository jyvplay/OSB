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
 * * **References shift** (R103). A formula's cell references are relative to the cell being evaluated, anchored at
 *   the top-left of the range token that covers it: `A1>5` on `A1:A9` asks `A5>5` when the scan reaches A5, and
 *   `$A$1>5` asks the same question of every cell. `$` locks one coordinate (`$A1`, `A$1`). A shift that leaves
 *   the grid is refused rather than clamped, because a clamped reference reads a different cell than Excel does.
 *   Cross-sheet references (`Sheet2!A1`) are still outside the grammar. See `docs/R103-RESULTS.md` §B.
 * * **The dxf's other fields** (R102) ride along in [Decoration.detail] — a font's family and size, a number
 *   format, a fill's pattern, a border, alignment and protection — merged field by field, first rule wins.
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
 * What is *not* here: the icon *images* themselves (only which icon a value earns), the vertAlign/shadow/outline
 * font flags, gradient fills inside a dxf, cross-sheet references, the 2010 `x14` data-bar and icon-set
 * extensions, and the painting itself. A verdict is a claim about which rule fires and which icon index it
 * assigns, never about pixels.
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
    /** `[$]COL[$]ROW`; each `$` locks the coordinate that follows it, so a locked reference never shifts. */
    private val CELL_REF = Regex("^(\\$?)([A-Z]{1,3})(\\$?)([1-9][0-9]{0,6})$")
    /** Comparison operators, longest first so `<=` is not read as `<` followed by `=`. */
    private val COMPARISONS = listOf("<=", ">=", "<>", "=", "<", ">")

    /** Model bounds for the formula grammar: arguments per call, nesting, tokens, and scientific notation width. */
    private const val MAX_CALL_ARGS = 8
    private const val MAX_DEPTH = 16
    private const val MAX_TOKENS = 512

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
        // A rule is evaluated with the anchor of its own block, so a multi-token `sqref` behaves like the separate
        // ranges Excel's dialog writes: the offset is measured from the token that covers this cell.
        val applicable = blocks
            .filter { block -> block.covers(cell) }
            .flatMap { block ->
                // `covers` has already established that some token contains the cell, so the anchor cannot be null
                // here; the elvis keeps the compiler happy without inventing an anchor for a block that does not
                // apply.
                val anchor = block.anchorFor(cell) ?: cell
                block.rules.map { rule -> rule to anchor }
            }
            .sortedBy { it.first.priority }
        val fired = ArrayList<Fired>()
        val unresolved = ArrayList<Unresolved>()
        var stopped = false
        for ((rule, anchor) in applicable) {
            when (val decision = predicate(rule, blocks, grid, cell, calendar, anchor)) {
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
        anchorFor(cell) != null

    /**
     * The top-left of the first `sqref` token that contains [cell], or null when the block does not cover it. This
     * is the anchor Excel measures a rule's relative references from; taking the first token unconditionally would
     * shift a rule on `C1:C9` by the origin of `A1:A9` in a block that names both.
     */
    private fun XlsxConditionalFormatting.Block.anchorFor(cell: SheetRange.Cell): SheetRange.Cell? {
        for (token in sqref.split(' ')) {
            val range = runCatching { SheetRange.parse(token) }.getOrNull() ?: continue
            if (SheetRange.contains(range, cell)) return range.top
        }
        return null
    }

    private fun predicate(
        rule: XlsxConditionalFormatting.Rule,
        blocks: List<XlsxConditionalFormatting.Block>,
        grid: Grid,
        cell: SheetRange.Cell,
        calendar: Calendar? = null,
        anchor: SheetRange.Cell = cell,
    ): Decision = when (rule.kind) {
        XlsxConditionalFormatting.Kind.CELL_IS -> cellIs(rule, grid, cell, anchor)
        XlsxConditionalFormatting.Kind.EXPRESSION -> expression(rule, grid, cell, anchor)
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

        /**
         * A cell reference as the formula spells it. [cell] is the coordinate *in the formula*, not the coordinate
         * to read: the reader of that coordinate depends on which cell is being evaluated (§ the header's
         * "References shift").
         */
        data class Reference(
            val cell: SheetRange.Cell,
            val columnLocked: Boolean = false,
            val rowLocked: Boolean = false,
        ) : Operand
    }

    /** A formula operand this evaluator understands; anything else is refused rather than guessed. */
    private fun operand(text: String): Operand? = when {
        text.isEmpty() -> null
        NUMBER.matches(text) -> Operand.Number(text.toDouble())
        text.length >= 2 && text.startsWith('"') && text.endsWith('"') ->
            Operand.Text(text.substring(1, text.length - 1).replace("\"\"", "\""))
        text.equals("TRUE", ignoreCase = true) -> Operand.Bool(true)
        text.equals("FALSE", ignoreCase = true) -> Operand.Bool(false)
        else -> reference(text)
    }

    /** A cell reference token, or null when the token is not one this grammar reads. */
    private fun reference(text: String): Operand.Reference? {
        val match = CELL_REF.matchEntire(text.uppercase().trim()) ?: return null
        val cell = runCatching { SheetRange.parseCell(match.groupValues[2] + match.groupValues[4]) }.getOrNull()
            ?: return null
        return Operand.Reference(
            cell = cell,
            columnLocked = match.groupValues[1].isNotEmpty(),
            rowLocked = match.groupValues[3].isNotEmpty(),
        )
    }

    /**
     * The value an operand names when the scan is at [cell], whose block is anchored at [anchor]. `null` means the
     * operand cannot be read there — only a reference can do that, by shifting off the grid.
     */
    private fun resolve(candidate: Operand, grid: Grid, cell: SheetRange.Cell, anchor: SheetRange.Cell): Value? =
        when (candidate) {
            is Operand.Number -> Value.Num(candidate.value)
            is Operand.Text -> Value.Txt(candidate.text)
            is Operand.Bool -> Value.Bool(candidate.flag)
            is Operand.Reference -> shifted(candidate, cell, anchor)?.let { grid.value(it) }
        }

    /**
     * Excel's conditional-format references are relative: `A1>5` written on the range `A1:A9` asks about the cell
     * `A9` when it reaches `A9`, and `$A$1>5` asks about `A1` every time. The anchor is the top-left of the range
     * token that covers [cell], which is what makes a multi-token `sqref` behave like the separate rules Excel's
     * own dialog produces. A shift outside the sheet is `null` rather than clamped: clamping would silently read a
     * different cell, and the caller turns `null` into an undecided rule.
     */
    private fun shifted(
        reference: Operand.Reference,
        cell: SheetRange.Cell,
        anchor: SheetRange.Cell,
    ): SheetRange.Cell? {
        val column = reference.cell.col + if (reference.columnLocked) 0 else cell.col - anchor.col
        val row = reference.cell.row + if (reference.rowLocked) 0 else cell.row - anchor.row
        if (column !in 1..ColLetters.MAX_COLUMN || row !in 1..SheetRange.MAX_ROW) return null
        return SheetRange.Cell(column, row)
    }

    // --- cellIs ------------------------------------------------------------------------------------------

    private fun cellIs(
        rule: XlsxConditionalFormatting.Rule,
        grid: Grid,
        cell: SheetRange.Cell,
        anchor: SheetRange.Cell,
    ): Decision {
        val operator = rule.operator ?: return Decision.Unsupported("cellIs without an operator")
        val left = grid.value(cell)
        val right = rule.formulas.map { formula ->
            val parsed = operand(formula.trim())
                ?: return Decision.Unsupported("cellIs operand '$formula' is not a literal or a cell reference")
            resolve(parsed, grid, cell, anchor)
                ?: return Decision.Unsupported("cellIs operand '$formula' shifts outside the sheet at ${cell.toA1()}")
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

    /**
     * An `expression` rule: one formula, parsed and evaluated.
     *
     * The grammar is small, total and closed — a formula outside it is [Decision.Unsupported] with the position
     * that failed, never a guess:
     *
     * ```
     * formula    := comparison
     * comparison := additive (op additive)?          op ∈ { < <= > >= = <> }, at most one
     * additive   := multiplicative (('+' | '-') multiplicative)*
     * multiplic. := unary (('*' | '/') unary)*
     * unary      := '-' unary | primary
     * primary    := number | "text" | TRUE | FALSE | ref | name '(' args ')' | '(' formula ')'
     * args       := formula (',' formula)*           at most MAX_CALL_ARGS
     * ```
     *
     * `AND`, `OR`, `NOT`, `ISBLANK`, `ISNUMBER`, `ISTEXT`, `LEN`, `TRIM`, `UPPER`, `LOWER`, `LEFT`, `RIGHT`, `ABS`,
     * `ROUND`, `MOD` and `ROW` are the functions; R105 added `SUM`, `COUNT`, `COUNTA`, `AVERAGE`, `MIN`, `MAX` and
     * `MEDIAN` over `from:to` ranges; R106 added `COUNTIF`, `SUMIF` and `AVERAGEIF`, whose second argument is a
     * criterion (`">5"`, `"<>draft"`, `"a*"`, or a plain value); R107 added the plural `COUNTIFS`, `SUMIFS` and
     * `AVERAGEIFS`, which read one or more range/criterion pairs and require every pair to hold at the same
     * position. Every other name is refused. A formula that *evaluates* to a
     * text or an error is refused rather than read as a condition, because "the formula says something that is not
     * true or false" is not the same claim as "the rule did not fire".
     */
    private fun expression(
        rule: XlsxConditionalFormatting.Rule,
        grid: Grid,
        cell: SheetRange.Cell,
        anchor: SheetRange.Cell,
    ): Decision {
        val formula = rule.formulas.singleOrNull()
            ?: return Decision.Unsupported("an expression rule without exactly one formula")
        val text = formula.trim()
        if (text.isEmpty()) return Decision.Unsupported("an empty expression")
        val outcome = try {
            evaluate(parse(text), grid, cell, anchor)
        } catch (refused: FormulaError) {
            return Decision.Unsupported("expression '${shorten(text)}': ${refused.message}")
        }
        return when (outcome) {
            is Value.Bool -> yes(outcome.flag, rule)
            // Excel reads a non-zero number as TRUE and a zero as FALSE; anything else is not a condition.
            is Value.Num -> yes(outcome.value != 0.0, rule)
            Value.Blank -> Decision.No
            is Value.Txt -> Decision.Unsupported("expression '${shorten(text)}' is text, not a condition")
            is Value.Err -> Decision.Unsupported("expression '${shorten(text)}' evaluates to ${outcome.code}")
        }
    }

    // --- the expression grammar --------------------------------------------------------------------------

    /** A parse or evaluation refusal; its message becomes the `Unsupported` reason, so it must say what and where. */
    private class FormulaError(message: String) : Exception(message)

    /** The formula grammar's syntax tree. Nothing here knows about cells; [evaluate] supplies the scan position. */
    private sealed interface Node {
        data class Literal(val value: Value) : Node
        data class Cell(val reference: Operand.Reference) : Node

        /** `from:to`, both endpoints carrying their own `$` locks and shifting like any other reference. */
        data class RangeRef(val from: Operand.Reference, val to: Operand.Reference) : Node
        data class Comparison(val left: Node, val operator: String, val right: Node) : Node
        data class Arithmetic(val left: Node, val operator: Char, val right: Node) : Node
        data class Call(val name: String, val args: List<Node>) : Node
    }

    private sealed interface FormulaToken {
        data class Num(val value: Double) : FormulaToken
        data class Str(val text: String) : FormulaToken
        data class Word(val text: String) : FormulaToken
        data class Sym(val text: String) : FormulaToken
    }

    private fun isWordStart(c: Char): Boolean = c.isLetter() || c == '$' || c == '_'
    private fun isWordPart(c: Char): Boolean = c.isLetterOrDigit() || c == '$' || c == '_' || c == '.'

    /** Turns a formula into tokens, or refuses it. No token is ever dropped: an unknown character is an error. */
    private fun tokenize(text: String): List<FormulaToken> {
        val out = ArrayList<FormulaToken>()
        var index = 0
        while (index < text.length) {
            val c = text[index]
            when {
                c.isWhitespace() -> index++
                c == '"' -> {
                    val literal = StringBuilder()
                    var scan = index + 1
                    var closed = false
                    while (scan < text.length) {
                        if (text[scan] == '"') {
                            // `""` inside a literal is one quote, as in Excel.
                            if (scan + 1 < text.length && text[scan + 1] == '"') {
                                literal.append('"')
                                scan += 2
                                continue
                            }
                            closed = true
                            scan++
                            break
                        }
                        literal.append(text[scan])
                        scan++
                    }
                    if (!closed) throw FormulaError("unterminated text literal at position ${index + 1}")
                    out.add(FormulaToken.Str(literal.toString()))
                    index = scan
                }
                c.isDigit() || (c == '.' && index + 1 < text.length && text[index + 1].isDigit()) -> {
                    var scan = index
                    while (scan < text.length && (text[scan].isDigit() || text[scan] == '.')) scan++
                    // An exponent is part of the number only when digits follow it.
                    if (scan < text.length && (text[scan] == 'e' || text[scan] == 'E')) {
                        var expo = scan + 1
                        if (expo < text.length && (text[expo] == '+' || text[expo] == '-')) expo++
                        var digits = expo
                        while (digits < text.length && text[digits].isDigit()) digits++
                        if (digits > expo) scan = digits
                    }
                    val literal = text.substring(index, scan)
                    val value = literal.toDoubleOrNull()
                        ?: throw FormulaError("'$literal' is not a number")
                    out.add(FormulaToken.Num(value))
                    index = scan
                }
                isWordStart(c) -> {
                    var scan = index
                    while (scan < text.length && isWordPart(text[scan])) scan++
                    out.add(FormulaToken.Word(text.substring(index, scan)))
                    index = scan
                }
                else -> {
                    val two = if (index + 1 < text.length) text.substring(index, index + 2) else ""
                    val symbol = if (two in COMPARISONS) two else c.toString()
                    if (symbol !in SYMBOLS) throw FormulaError("unexpected '${c}' at position ${index + 1}")
                    out.add(FormulaToken.Sym(symbol))
                    index += symbol.length
                }
            }
            if (out.size > MAX_TOKENS) throw FormulaError("more than $MAX_TOKENS tokens")
        }
        return out
    }

    /** The one-character symbols the grammar reads; everything else is refused by [tokenize]. */
    private val SYMBOLS = setOf(
        "(", ")", ",", ":", "+", "-", "*", "/", "<=", ">=", "<>", "=", "<", ">",
    )

    /**
     * Recursive descent over [tokenize]'s output. Depth and arity are bounded so a hostile formula cannot recurse
     * this reader to death; a bound reached is a refusal, never a truncated parse.
     */
    private class Parser(private val tokens: List<FormulaToken>) {
        private var position = 0
        private var depth = 0

        fun parse(): Node {
            if (tokens.isEmpty()) throw FormulaError("the formula is empty")
            val root = comparison()
            if (position != tokens.size) throw FormulaError("unexpected ${describe(tokens[position])} after the formula")
            return root
        }

        private fun comparison(): Node {
            val left = additive()
            val next = tokens.getOrNull(position)
            if (next !is FormulaToken.Sym || next.text !in XlsxConditionalFormatVerdict.COMPARISONS) return left
            position++
            // Excel has no chained comparison: `a<b<c` is a parse error, not a pairwise test.
            return Node.Comparison(left, next.text, additive())
        }

        private fun additive(): Node {
            var node = multiplicative()
            while (true) {
                val next = tokens.getOrNull(position)
                if (next !is FormulaToken.Sym || (next.text != "+" && next.text != "-")) return node
                position++
                node = Node.Arithmetic(node, next.text[0], multiplicative())
            }
        }

        private fun multiplicative(): Node {
            var node = unary()
            while (true) {
                val next = tokens.getOrNull(position)
                if (next !is FormulaToken.Sym || (next.text != "*" && next.text != "/")) return node
                position++
                node = Node.Arithmetic(node, next.text[0], unary())
            }
        }

        private fun unary(): Node {
            val next = tokens.getOrNull(position)
            if (next is FormulaToken.Sym && next.text == "-") {
                position++
                return Node.Arithmetic(Node.Literal(Value.Num(0.0)), '-', unary())
            }
            if (next is FormulaToken.Sym && next.text == "+") {
                position++
                return unary()
            }
            return primary()
        }

        private fun primary(): Node {
            val token = tokens.getOrNull(position) ?: throw FormulaError("the formula ends where a value was expected")
            position++
            return when (token) {
                is FormulaToken.Num -> Node.Literal(Value.Num(token.value))
                is FormulaToken.Str -> Node.Literal(Value.Txt(token.text))
                is FormulaToken.Word -> {
                    if (tokens.getOrNull(position) is FormulaToken.Sym && (tokens[position] as FormulaToken.Sym).text == "(") {
                        position++
                        return call(token.text.uppercase())
                    }
                    when (token.text.uppercase()) {
                        "TRUE" -> Node.Literal(Value.Bool(true))
                        "FALSE" -> Node.Literal(Value.Bool(false))
                        else -> {
                            val ref = XlsxConditionalFormatVerdict.reference(token.text)
                                ?: throw FormulaError("'${token.text}' is neither a function, a literal nor a cell reference")
                            val colon = tokens.getOrNull(position)
                            if (colon is FormulaToken.Sym && colon.text == ":") {
                                position++
                                val endpoint = tokens.getOrNull(position)
                                    ?: throw FormulaError("':' must be followed by a cell reference")
                                position++
                                val to = if (endpoint is FormulaToken.Word) {
                                    XlsxConditionalFormatVerdict.reference(endpoint.text)
                                } else {
                                    null
                                } ?: throw FormulaError("':' must be followed by a cell reference")
                                Node.RangeRef(ref, to)
                            } else {
                                Node.Cell(ref)
                            }
                        }
                    }
                }
                is FormulaToken.Sym -> {
                    if (token.text != "(") throw FormulaError("unexpected ${describe(token)}")
                    depth++
                    if (depth > XlsxConditionalFormatVerdict.MAX_DEPTH) throw FormulaError("nesting deeper than ${XlsxConditionalFormatVerdict.MAX_DEPTH}")
                    val inner = comparison()
                    val close = tokens.getOrNull(position)
                    if (close !is FormulaToken.Sym || close.text != ")") throw FormulaError("missing ')'")
                    position++
                    depth--
                    inner
                }
            }
        }

        private fun call(name: String): Node {
            if (name !in XlsxConditionalFormatVerdict.FUNCTIONS) throw FormulaError("'$name' is not a function this reader evaluates")
            val args = ArrayList<Node>()
            val first = tokens.getOrNull(position)
            if (first is FormulaToken.Sym && first.text == ")") {
                position++
            } else {
                while (true) {
                    if (args.size >= XlsxConditionalFormatVerdict.MAX_CALL_ARGS) throw FormulaError("more than ${XlsxConditionalFormatVerdict.MAX_CALL_ARGS} arguments to $name")
                    depth++
                    if (depth > XlsxConditionalFormatVerdict.MAX_DEPTH) throw FormulaError("nesting deeper than ${XlsxConditionalFormatVerdict.MAX_DEPTH}")
                    args.add(comparison())
                    depth--
                    val next = tokens.getOrNull(position)
                    when {
                        next is FormulaToken.Sym && next.text == "," -> position++
                        next is FormulaToken.Sym && next.text == ")" -> {
                            position++
                            break
                        }
                        else -> throw FormulaError("expected ',' or ')' in $name, found ${next?.let { describe(it) } ?: "the end"}")
                    }
                }
            }
            val arity = XlsxConditionalFormatVerdict.FUNCTIONS.getValue(name)
            if (args.size !in arity) {
                val expected = if (arity.first == arity.last) "${arity.first}" else "${arity.first} to ${arity.last}"
                throw FormulaError("$name takes $expected argument(s), not ${args.size}")
            }
            return Node.Call(name, args)
        }

        private fun describe(token: FormulaToken): String = when (token) {
            is FormulaToken.Num -> "number ${token.value}"
            is FormulaToken.Str -> "text \"${token.text}\""
            is FormulaToken.Word -> "name '${token.text}'"
            is FormulaToken.Sym -> "'${token.text}'"
        }
    }

    /** Argument counts each function accepts, as inclusive ranges. */
    private val FUNCTIONS: Map<String, IntRange> = mapOf(
        "AND" to 1..MAX_CALL_ARGS,
        "OR" to 1..MAX_CALL_ARGS,
        "NOT" to 1..1,
        "ISBLANK" to 1..1,
        "ISNUMBER" to 1..1,
        "ISTEXT" to 1..1,
        "LEN" to 1..1,
        "TRIM" to 1..1,
        "UPPER" to 1..1,
        "LOWER" to 1..1,
        "LEFT" to 2..2,
        "RIGHT" to 2..2,
        "ABS" to 1..1,
        "ROUND" to 2..2,
        "MOD" to 2..2,
        "ROW" to 0..0,
        // R105: aggregates. Each accepts one or more arguments, each of which may be a range.
        "SUM" to 1..MAX_CALL_ARGS,
        "COUNT" to 1..MAX_CALL_ARGS,
        "COUNTA" to 1..MAX_CALL_ARGS,
        "AVERAGE" to 1..MAX_CALL_ARGS,
        "MIN" to 1..MAX_CALL_ARGS,
        "MAX" to 1..MAX_CALL_ARGS,
        "MEDIAN" to 1..MAX_CALL_ARGS,
        // R106: criteria functions. `COUNTIF(range, criterion)`, `SUMIF(range, criterion[, sum_range])`,
        // `AVERAGEIF(range, criterion[, average_range])`.
        "COUNTIF" to 2..2,
        "SUMIF" to 2..3,
        "AVERAGEIF" to 2..3,
        // R107: plural criteria functions. `COUNTIFS(range, criterion, …)` is all pairs; `SUMIFS` and
        // `AVERAGEIFS` take the range to accumulate first and then the same pairs. The grammar's existing
        // MAX_CALL_ARGS of 8 bounds them at four pairs (COUNTIFS) and three (SUMIFS, AVERAGEIFS); Excel allows 127,
        // and that ceiling is documented rather than hidden.
        "COUNTIFS" to 2..MAX_CALL_ARGS,
        "SUMIFS" to 3..MAX_CALL_ARGS,
        "AVERAGEIFS" to 3..MAX_CALL_ARGS,
    )

    /** Parses [text], refusing anything outside the grammar. */
    private fun parse(text: String): Node = Parser(tokenize(text)).parse()

    /**
     * Evaluates [node] with the scan at [cell], whose covering range token is anchored at [anchor]. References
     * shift exactly as [shifted] says, so `AND(A2>5,$B$1="x")` written on `A2:A9` reads the row below itself each
     * time it advances. A refusal throws [FormulaError]; it never yields a fabricated value.
     */
    private fun evaluate(
        node: Node,
        grid: Grid,
        cell: SheetRange.Cell,
        anchor: SheetRange.Cell,
    ): Value = when (node) {
        is Node.Literal -> node.value
        is Node.Cell -> {
            val target = shifted(node.reference, cell, anchor)
                ?: throw FormulaError("'${node.reference.cell.toA1()}' shifts outside the sheet at ${cell.toA1()}")
            grid.value(target)
        }
        is Node.Comparison -> {
            val left = evaluate(node.left, grid, cell, anchor)
            val right = evaluate(node.right, grid, cell, anchor)
            if (node.operator == "=" || node.operator == "<>") {
                val equal = equalValues(left, right)
                    ?: throw FormulaError("cannot compare ${describe(left)} with ${describe(right)}")
                Value.Bool(equal == (node.operator == "="))
            } else {
                val ordering = order(left, right)
                    ?: throw FormulaError("cannot order ${describe(left)} against ${describe(right)}")
                Value.Bool(holds(ordering, node.operator, symbolic = true))
            }
        }
        is Node.Arithmetic -> {
            val left = number(evaluate(node.left, grid, cell, anchor), node.operator)
            val right = number(evaluate(node.right, grid, cell, anchor), node.operator)
            when (node.operator) {
                '+' -> Value.Num(left + right)
                '-' -> Value.Num(left - right)
                '*' -> Value.Num(left * right)
                else -> if (right == 0.0) Value.Err("#DIV/0!") else Value.Num(left / right)
            }
        }
        is Node.RangeRef -> throw FormulaError("a range is not a single value")
        is Node.Call -> apply(node, grid, cell, anchor)
    }

    /** A numeric operand; text is refused here even when it looks numeric, matching [order]'s type rule. */
    private fun number(value: Value, operator: Char): Double = when (value) {
        is Value.Num -> value.value
        is Value.Blank -> 0.0
        is Value.Bool -> if (value.flag) 1.0 else 0.0
        is Value.Txt -> throw FormulaError("text \"${shorten(value.text)}\" is not a number")
        is Value.Err -> throw FormulaError("error ${value.code} in '$operator'")
    }

    private fun text(value: Value): String = when (value) {
        is Value.Txt -> value.text
        is Value.Blank -> ""
        is Value.Num -> formatNumber(value.value)
        is Value.Bool -> if (value.flag) "TRUE" else "FALSE"
        is Value.Err -> throw FormulaError("error ${value.code} where text was expected")
    }

    /** Excel's general number rendering: no trailing `.0`, and at most 15 significant digits. */
    private fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) throw FormulaError("'$value' is not a number this reader renders")
        val rounded = value.toBigDecimal().round(java.math.MathContext(15)).stripTrailingZeros()
        return rounded.toPlainString()
    }

    private fun flag(value: Value, where: String): Boolean = when (value) {
        is Value.Bool -> value.flag
        is Value.Num -> value.value != 0.0
        is Value.Blank -> false
        is Value.Txt -> throw FormulaError("text \"${shorten(value.text)}\" is not a condition in $where")
        is Value.Err -> throw FormulaError("error ${value.code} in $where")
    }

    /** Functions that read a range as a set of cells rather than as a single value. */
    private val AGGREGATES = setOf("SUM", "COUNT", "COUNTA", "AVERAGE", "MIN", "MAX", "MEDIAN")

    /** Functions that read a range through a criterion. */
    private val CRITERIA_FUNCTIONS = setOf("COUNTIF", "SUMIF", "AVERAGEIF")

    /** Functions that read one or more ranges through criteria, every one of which must hold. */
    private val PLURAL_CRITERIA_FUNCTIONS = setOf("COUNTIFS", "SUMIFS", "AVERAGEIFS")

    /** The operators a criterion may begin with, longest first so `<=` is not read as `<`. */
    private val CRITERIA_OPERATORS = listOf("<=", ">=", "<>", "=", "<", ">")

    /** Excel's own limit on a criterion's length; a longer one is refused rather than scanned. */
    private const val MAX_CRITERIA_TEXT = 255

    /**
     * The cells a range names when the scan is at [cell], both endpoints shifting exactly as a single reference
     * does. A reversed pair (`B2:A1`) is normalized, which is what Excel's own address arithmetic produces. A range
     * that leaves the grid, or that is larger than the component's existing [MAX_RANGE_CELLS] bound, is refused
     * rather than truncated: one rule may not walk an arbitrary share of the sheet.
     */
    private fun rangeCells(
        range: Node.RangeRef,
        cell: SheetRange.Cell,
        anchor: SheetRange.Cell,
    ): Sequence<SheetRange.Cell> {
        val bounds = rangeBounds(range, cell, anchor)
        return sequence {
            for (row in bounds[1] until bounds[1] + bounds[3]) {
                for (column in bounds[0] until bounds[0] + bounds[2]) {
                    yield(SheetRange.Cell(column.toInt(), row.toInt()))
                }
            }
        }
    }

    /**
     * `left, top, width, height` of [range] at this scan position — the shape a second range has to match, and the
     * one place the grid-edge and area refusals live, so every reader of a range refuses identically.
     */
    private fun rangeBounds(
        range: Node.RangeRef,
        cell: SheetRange.Cell,
        anchor: SheetRange.Cell,
    ): LongArray {
        val from = shifted(range.from, cell, anchor)
            ?: throw FormulaError("'${range.from.cell.toA1()}' shifts outside the sheet at ${cell.toA1()}")
        val to = shifted(range.to, cell, anchor)
            ?: throw FormulaError("'${range.to.cell.toA1()}' shifts outside the sheet at ${cell.toA1()}")
        val firstColumn = minOf(from.col, to.col)
        val lastColumn = maxOf(from.col, to.col)
        val firstRow = minOf(from.row, to.row)
        val lastRow = maxOf(from.row, to.row)
        val width = (lastColumn - firstColumn + 1).toLong()
        val height = (lastRow - firstRow + 1).toLong()
        if (width * height > MAX_RANGE_CELLS) {
            throw FormulaError("a ${width * height}-cell range is above the $MAX_RANGE_CELLS-cell aggregate bound")
        }
        return longArrayOf(firstColumn.toLong(), firstRow.toLong(), width, height)
    }

    /**
     * The criteria functions. Excel's `COUNTIF`, `SUMIF` and `AVERAGEIF` take a *criterion* — a value that may be a
     * plain number, a comparison written as text (`">5"`, `"<>draft"`), or a wildcard pattern (`"a*"`, `"?p*"`,
     * with `~` escaping `*`, `?` and itself) — and count, sum or average the cells of a range that match it.
     */
    private fun criteriaCall(
        node: Node.Call,
        grid: Grid,
        cell: SheetRange.Cell,
        anchor: SheetRange.Cell,
    ): Value {
        val range = node.args[0]
        if (range !is Node.RangeRef) throw FormulaError("${node.name} needs a cell range as its first argument")
        val criterion = criterion(evaluate(node.args[1], grid, cell, anchor), node.name)
        val values = rangeCells(range, cell, anchor).map { grid.value(it) }.toList()
        val sums = if (node.args.size == 3) {
            val sumRange = node.args[2]
            if (sumRange !is Node.RangeRef) {
                throw FormulaError("${node.name} needs a cell range as its third argument")
            }
            val criteriaShape = rangeBounds(range, cell, anchor)
            val sumShape = rangeBounds(sumRange, cell, anchor)
            // Only the *shape* has to match: a sum range may start anywhere, and each range is read from its own
            // top-left corner. Comparing the whole bounds would refuse `SUMIF(C1:C5,">2",B1:B5)`.
            if (criteriaShape[2] != sumShape[2] || criteriaShape[3] != sumShape[3]) {
                throw FormulaError(
                    "${node.name}'s second range is ${sumShape[2]}x${sumShape[3]} cells, the criteria range is " +
                        "${criteriaShape[2]}x${criteriaShape[3]}",
                )
            }
            rangeCells(sumRange, cell, anchor).map { grid.value(it) }.toList()
        } else {
            null
        }
        var matched = 0
        var sum = 0.0
        var counted = 0
        for (index in values.indices) {
            if (!matches(criterion, values[index])) continue
            matched++
            val candidate = sums?.get(index) ?: values[index]
            when (candidate) {
                is Value.Err -> throw FormulaError("error ${candidate.code} in ${node.name}'s sum range")
                is Value.Num -> {
                    sum += candidate.value
                    counted++
                }
                // Excel sums and averages numbers only; a matched blank, text or boolean contributes nothing.
                Value.Blank, is Value.Txt, is Value.Bool -> Unit
            }
        }
        return when (node.name) {
            "COUNTIF" -> Value.Num(matched.toDouble())
            "SUMIF" -> Value.Num(sum)
            "AVERAGEIF" -> if (counted == 0) Value.Err("#DIV/0!") else Value.Num(sum / counted)
            else -> throw FormulaError("'${node.name}' is not a criteria function")
        }
    }

    /**
     * The plural criteria functions. `COUNTIFS` reads range/criterion pairs and counts the positions where every pair
     * holds; `SUMIFS` and `AVERAGEIFS` read the range to accumulate first and then the same pairs. A pair is the R106
     * grammar unchanged — one `criterion` parser serves both the singular and the plural forms — and the positions
     * are the row-major ones `rangeCells` walks, so a pair, its sibling pair and the accumulated range all line up
     * cell for cell.
     *
     * Every range must have the first one's shape: the *position* of a range is free (a sum range may start in any
     * column) but its width and height are not. All pairs are read for every position even after one has already
     * failed, because "an error value under a criterion is an error, not a zero" must not depend on which criterion
     * happened to be checked first.
     */
    private fun pluralCriteriaCall(
        node: Node.Call,
        grid: Grid,
        cell: SheetRange.Cell,
        anchor: SheetRange.Cell,
    ): Value {
        val name = node.name
        val subjectFirst = if (name == "COUNTIFS") 0 else 1
        if ((node.args.size - subjectFirst) % 2 != 0) {
            throw FormulaError(
                "$name takes criteria range/criterion pairs, so ${node.args.size} arguments leave one unpaired",
            )
        }
        val label = if (name == "SUMIFS") "sum range" else "average range"
        val subject = if (subjectFirst == 1) {
            val argument = node.args[0]
            if (argument !is Node.RangeRef) throw FormulaError("$name needs a cell range as its first argument")
            argument
        } else {
            null
        }
        val subjectShape = subject?.let { rangeBounds(it, cell, anchor) }
        val pairs = (node.args.size - subjectFirst) / 2
        val shapes = arrayOfNulls<LongArray>(pairs)
        val criteria = ArrayList<Criterion>(pairs)
        for (pair in 0 until pairs) {
            val argument = node.args[subjectFirst + pair * 2]
            if (argument !is Node.RangeRef) {
                throw FormulaError("$name needs a cell range for criteria range ${pair + 1}")
            }
            criteria.add(criterion(evaluate(node.args[subjectFirst + pair * 2 + 1], grid, cell, anchor), name))
            val shape = rangeBounds(argument, cell, anchor)
            shapes[pair] = shape
            // `shapes[0]` is assigned above before any later pair is compared against it.
            val reference = subjectShape ?: shapes[0]
            if (reference != null && (reference[2] != shape[2] || reference[3] != shape[3])) {
                val what = if (subjectShape != null) label else "first criteria range"
                throw FormulaError(
                    "$name's criteria range ${pair + 1} is ${shape[2]}x${shape[3]} cells, the $what is " +
                        "${reference[2]}x${reference[3]}",
                )
            }
        }
        val first = shapes[0] ?: throw FormulaError("$name needs at least one criteria range")
        val positions = (first[2] * first[3]).toInt()
        var matched = 0
        var sum = 0.0
        var counted = 0
        for (position in 0 until positions) {
            var holds = true
            for (pair in 0 until pairs) {
                val value = grid.value(cellAt(shapes[pair] ?: first, position))
                if (value is Value.Err) throw FormulaError("error ${value.code} under a criterion")
                if (holds && !matches(criteria[pair], value)) holds = false
            }
            if (!holds) continue
            matched++
            val candidate = subjectShape?.let { grid.value(cellAt(it, position)) }
            when (candidate) {
                null, Value.Blank, is Value.Txt, is Value.Bool -> Unit
                is Value.Err -> throw FormulaError("error ${candidate.code} in $name's $label")
                is Value.Num -> {
                    sum += candidate.value
                    counted++
                }
            }
        }
        return when (name) {
            "COUNTIFS" -> Value.Num(matched.toDouble())
            "SUMIFS" -> Value.Num(sum)
            else -> if (counted == 0) Value.Err("#DIV/0!") else Value.Num(sum / counted)
        }
    }

    /** The cell at [position] of a range, in the row-major order [rangeCells] walks. */
    private fun cellAt(bounds: LongArray, position: Int): SheetRange.Cell {
        val width = bounds[2].toInt()
        return SheetRange.Cell(bounds[0].toInt() + position % width, bounds[1].toInt() + position / width)
    }

    /** A parsed criterion: one of the six comparison operators and the value it compares against. */
    private class Criterion(val operator: String, val operand: Value)

    /** One parsed element of a wildcard pattern. */
    private sealed interface Wild {
        data class Lit(val char: Char) : Wild
        data object One : Wild
        data object Run : Wild
    }

    /**
     * A criterion as Excel reads it. A number or a boolean compares for equality. Text may begin with one of the six
     * operators; what follows is a number when it is written as one (the model's own literal rule, so `1e5` is a
     * number while `0x10` and `1,5` stay text) and otherwise a wildcard pattern. An empty operand after an operator
     * means empty text, which is how `"="` selects blanks and `"<>"` selects non-blanks.
     */
    private fun criterion(value: Value, where: String): Criterion = when (value) {
        is Value.Num -> Criterion("=", value)
        is Value.Bool -> Criterion("=", value)
        Value.Blank -> Criterion("=", Value.Txt(""))
        is Value.Err -> throw FormulaError("error ${value.code} as the criterion of $where")
        is Value.Txt -> {
            if (value.text.length > MAX_CRITERIA_TEXT) {
                throw FormulaError(
                    "a criterion of ${value.text.length} characters is above the $MAX_CRITERIA_TEXT-character bound",
                )
            }
            val operator = CRITERIA_OPERATORS.firstOrNull { value.text.startsWith(it) }
            if (operator == null) {
                Criterion("=", value)
            } else {
                val rest = value.text.substring(operator.length)
                val number = rest.trim().toDoubleOrNull()
                if (number != null && NUMBER.matches(rest.trim())) Criterion(operator, Value.Num(number))
                else Criterion(operator, Value.Txt(rest))
            }
        }
    }

    /**
     * Whether [value] satisfies [criterion]. A blank cell satisfies only an empty `=` criterion, which is what makes
     * `COUNTIF(range,"")` the blank count and `COUNTIF(range,"<>")` the non-blank count. A cell that cannot be
     * ordered against the criterion at all simply does not match; ordering two *different* texts would need a
     * collator this host does not have, and that is refused rather than guessed.
     */
    private fun matches(criterion: Criterion, value: Value): Boolean {
        if (value is Value.Blank) {
            val operand = criterion.operand
            return criterion.operator == "=" && operand is Value.Txt && operand.text.isEmpty()
        }
        if (value is Value.Err) throw FormulaError("error ${value.code} under a criterion")
        return when (criterion.operator) {
            "=" -> criterionEquals(criterion.operand, value)
            "<>" -> !criterionEquals(criterion.operand, value)
            else -> {
                val ordering = criterionOrder(criterion.operand, value)
                ordering != null && holds(ordering, criterion.operator, symbolic = true)
            }
        }
    }

    /** Equality under a criterion: text is case-insensitive and may contain wildcards; a number folds numeric text. */
    private fun criterionEquals(operand: Value, value: Value): Boolean = when (operand) {
        is Value.Num -> when (value) {
            is Value.Num -> value.value == operand.value
            // The one coercion `COUNTIF` has that `=` does not: a cell holding text that spells a number counts as
            // that number. It is pinned by a case and documented as the model's single range-matching coercion.
            is Value.Txt -> value.text.trim().toDoubleOrNull()?.let { it == operand.value } ?: false
            else -> false
        }
        is Value.Bool -> value is Value.Bool && value.flag == operand.flag
        is Value.Txt -> wildcard(operand.text, renderedText(value))
        else -> false
    }

    /** A cell as the text a text criterion sees: numbers in Excel's general form, booleans as TRUE/FALSE. */
    private fun renderedText(value: Value): String = when (value) {
        is Value.Txt -> value.text
        is Value.Num -> formatNumber(value.value)
        is Value.Bool -> if (value.flag) "TRUE" else "FALSE"
        Value.Blank -> ""
        is Value.Err -> throw FormulaError("error ${value.code} where text was expected")
    }

    /** `-1/0/+1` for a value against an ordering criterion, or null when the pair cannot be ordered at all. */
    private fun criterionOrder(operand: Value, value: Value): Int? {
        if (operand is Value.Num) {
            val number = when (value) {
                is Value.Num -> value.value
                is Value.Txt -> value.text.trim().toDoubleOrNull() ?: return null
                else -> return null
            }
            return number.compareTo(operand.value)
        }
        if (operand is Value.Txt && value is Value.Txt) {
            if (value.text.equals(operand.text, ignoreCase = true)) return 0
            throw FormulaError("cannot order two different texts under a '<' or '>' criterion")
        }
        return null
    }

    /** Excel's `*` (any run), `?` (one character) and `~` (escape) matching, case-insensitive, without a regex. */
    private fun wildcard(pattern: String, text: String): Boolean {
        if (pattern.isEmpty()) return text.isEmpty()
        val tokens = ArrayList<Wild>(pattern.length)
        var index = 0
        while (index < pattern.length) {
            when (val char = pattern[index]) {
                '*' -> tokens.add(Wild.Run)
                '?' -> tokens.add(Wild.One)
                '~' -> {
                    if (index + 1 < pattern.length) {
                        tokens.add(Wild.Lit(pattern[index + 1]))
                        index++
                    } else {
                        tokens.add(Wild.Lit('~'))
                    }
                }
                else -> tokens.add(Wild.Lit(char))
            }
            index++
        }
        val folded = text.lowercase()
        var token = 0
        var position = 0
        var star = -1
        var mark = 0
        while (position < folded.length) {
            when (val current = tokens.getOrNull(token)) {
                is Wild.One -> {
                    token++
                    position++
                }
                is Wild.Lit -> {
                    if (current.char.lowercaseChar() == folded[position]) {
                        token++
                        position++
                    } else if (star >= 0) {
                        token = star + 1
                        mark++
                        position = mark
                    } else {
                        return false
                    }
                }
                is Wild.Run -> {
                    star = token
                    mark = position
                    token++
                }
                null -> if (star >= 0) {
                    token = star + 1
                    mark++
                    position = mark
                } else {
                    return false
                }
            }
        }
        while (token < tokens.size && tokens[token] is Wild.Run) token++
        return token == tokens.size
    }

    /**
     * Excel's aggregate rules. Inside a range, text, booleans and blanks are *ignored* by `SUM`, `AVERAGE`, `MIN`,
     * `MAX` and `MEDIAN`, counted only by `COUNTA`, and an error value propagates as a refusal with its code — an
     * error is not a number and is not a zero. A scalar argument is one cell, not a range: `COUNT` classifies it by
     * type, `COUNTA` counts it unless it is blank, and the arithmetic aggregates use [number]'s existing coercion
     * (a boolean is 1 or 0, text is refused). That difference is Excel's, and it is why `SUM(A1:A8)` above ignores
     * the boolean in `A6` while `SUM(TRUE)` counts it.
     */
    private fun aggregate(
        node: Node.Call,
        grid: Grid,
        cell: SheetRange.Cell,
        anchor: SheetRange.Cell,
    ): Value {
        val numbers = ArrayList<Double>()
        var numeric = 0
        var nonBlank = 0
        for (argument in node.args) {
            if (argument is Node.RangeRef) {
                for (target in rangeCells(argument, cell, anchor)) {
                    when (val value = grid.value(target)) {
                        is Value.Err -> throw FormulaError(
                            "error ${value.code} in ${node.name} at ${target.toA1()}",
                        )
                        is Value.Num -> {
                            numbers.add(value.value)
                            numeric++
                            nonBlank++
                        }
                        Value.Blank -> Unit
                        is Value.Txt, is Value.Bool -> nonBlank++
                    }
                }
                continue
            }
            when (val value = evaluate(argument, grid, cell, anchor)) {
                is Value.Err -> throw FormulaError("error ${value.code} in ${node.name}")
                Value.Blank -> Unit
                is Value.Num -> {
                    numbers.add(value.value)
                    numeric++
                    nonBlank++
                }
                // A scalar is not a range cell: `COUNT` classifies it by type, `COUNTA` counts it when it is not
                // blank, and the arithmetic aggregates use the model's existing coercion, which turns a boolean
                // into 1 or 0 (`SUM(TRUE)` is 1, as in Excel) and refuses text outright.
                is Value.Txt -> when (node.name) {
                    "COUNTA" -> nonBlank++
                    "COUNT" -> Unit
                    else -> throw FormulaError("${describe(value)} is not a number for ${node.name}")
                }
                is Value.Bool -> when (node.name) {
                    "COUNTA" -> nonBlank++
                    "COUNT" -> Unit
                    else -> {
                        numbers.add(number(value, ' '))
                        numeric++
                        nonBlank++
                    }
                }
            }
        }
        return when (node.name) {
            "SUM" -> Value.Num(numbers.sum())
            "COUNT" -> Value.Num(numeric.toDouble())
            "COUNTA" -> Value.Num(nonBlank.toDouble())
            "AVERAGE" -> if (numbers.isEmpty()) Value.Err("#DIV/0!") else Value.Num(numbers.average())
            "MIN" -> Value.Num(numbers.minOrNull() ?: 0.0)
            "MAX" -> Value.Num(numbers.maxOrNull() ?: 0.0)
            "MEDIAN" -> median(numbers)
            else -> throw FormulaError("'${node.name}' is not an aggregate")
        }
    }

    /** Excel's `MEDIAN`: the middle value, the mean of the two middle values for an even count, `#NUM!` when empty. */
    private fun median(numbers: List<Double>): Value {
        if (numbers.isEmpty()) return Value.Err("#NUM!")
        val sorted = numbers.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) Value.Num(sorted[middle])
        else Value.Num((sorted[middle - 1] + sorted[middle]) / 2)
    }

    /** `ROUND` halves away from zero, as Excel does (`ROUND(-2.5,0)` is `-3`); `MOD` takes the divisor's sign. */
    private fun apply(node: Node.Call, grid: Grid, cell: SheetRange.Cell, anchor: SheetRange.Cell): Value {
        if (node.name == "ROW") return Value.Num(cell.row.toDouble())
        if (node.name in AGGREGATES) return aggregate(node, grid, cell, anchor)
        if (node.name in CRITERIA_FUNCTIONS) return criteriaCall(node, grid, cell, anchor)
        if (node.name in PLURAL_CRITERIA_FUNCTIONS) return pluralCriteriaCall(node, grid, cell, anchor)
        val args = node.args.map { evaluate(it, grid, cell, anchor) }
        return when (node.name) {
            "AND" -> Value.Bool(args.all { flag(it, "AND") })
            "OR" -> Value.Bool(args.any { flag(it, "OR") })
            "NOT" -> Value.Bool(!flag(args[0], "NOT"))
            "ISBLANK" -> Value.Bool(args[0] is Value.Blank)
            "ISNUMBER" -> Value.Bool(args[0] is Value.Num)
            "ISTEXT" -> Value.Bool(args[0] is Value.Txt)
            "LEN" -> Value.Num(text(args[0]).length.toDouble())
            "TRIM" -> Value.Txt(text(args[0]).trim().replace(Regex(" +"), " "))
            "UPPER" -> Value.Txt(text(args[0]).uppercase())
            "LOWER" -> Value.Txt(text(args[0]).lowercase())
            "LEFT", "RIGHT" -> {
                val body = text(args[0])
                val count = number(args[1], ' ')
                if (count < 0.0 || count != Math.floor(count)) throw FormulaError("${node.name} count must be a whole number >= 0")
                val width = count.toInt().coerceAtMost(body.length)
                Value.Txt(if (node.name == "LEFT") body.take(width) else body.takeLast(width))
            }
            "ABS" -> Value.Num(Math.abs(number(args[0], ' ')))
            "ROUND" -> {
                val digits = number(args[1], ' ')
                if (digits != Math.floor(digits) || Math.abs(digits) > 15.0) {
                    throw FormulaError("ROUND digits must be a whole number within 15")
                }
                val value = number(args[0], ' ')
                if (value.isNaN() || value.isInfinite()) throw FormulaError("'$value' is not a number ROUND can read")
                // Two steps, both required to match Excel. First read the binary double as the fifteen significant
                // decimal digits Excel keeps — without it `ROUND(2.675,2)` would answer 2.67, because the double
                // nearest 2.675 is 2.67499999999999982236..., while Excel rounds the *typed* 2.675 and says 2.68.
                // Then round half away from zero: BigDecimal's HALF_UP, not Java's Math.round, which rounds a
                // negative half toward positive infinity and would answer ROUND(-2.5,0) with -2 instead of -3.
                val decimal = java.math.BigDecimal(value).round(java.math.MathContext(15))
                val rounded = decimal.setScale(digits.toInt(), java.math.RoundingMode.HALF_UP)
                Value.Num(rounded.toDouble())
            }
            "MOD" -> {
                val divisor = number(args[1], ' ')
                if (divisor == 0.0) Value.Err("#DIV/0!")
                else Value.Num(number(args[0], ' ') - divisor * Math.floor(number(args[0], ' ') / divisor))
            }
            else -> throw FormulaError("'${node.name}' is not a function this reader evaluates")
        }
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
