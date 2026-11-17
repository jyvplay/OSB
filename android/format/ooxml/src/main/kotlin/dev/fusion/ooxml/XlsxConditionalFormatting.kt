// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Conditional formatting: `<conditionalFormatting sqref>` (ECMA-376 Part 1 §18.3.1.18) with its `<cfRule>`
 * children (§18.3.1.10) and the differential formats those rules point at — the `<dxfs>` table in `styles.xml`
 * (§18.8.3) and its `<dxf>` entries (§18.8.3.2). Only the element sections are cited; the complex-type
 * subsections are named, never numbered, because a wrong number is worse than none.
 *
 * Modelled: all eighteen rule kinds the base schema defines, so a file this writer produces can state every
 * `cfRule` Excel writes outside the 2010 `x14` extensions — `cellIs`, `expression`, `top10`, `colorScale`,
 * `dataBar`, the four text predicates (`containsText`, `notContainsText`, `beginsWith`, `endsWith` with their
 * `text`), `aboveAverage` (with `aboveAverage`/`equalAverage`/`stdDev`), `duplicateValues`, `uniqueValues`, the
 * four blank/error predicates, `timePeriod` (with its 17 presets) and `iconSet` (with its set name, its
 * boundaries and its `showValue`/`percent`/`reverse` flags) — the `priority` that must be unique per sheet,
 * `stopIfTrue`, the cellIs operators, formula text, thresholds (`cfvo`), colours, and a differential format that
 * says everything a `<dxf>` can say about a cell's paint: font decorations, family and size; a number format; a
 * fill (solid or patterned); a border with up to five edges; alignment; and protection.
 *
 * A text rule is stated by its attributes alone. Excel also writes a `<formula>` beside it (`NOT(ISERROR(
 * SEARCH("abc",A1)))`, or `LEFT(B1,2)="xy"`), which is redundant with `type`/`operator`/`text` under the reading
 * this model takes; a foreign formula is dropped on read rather than echoed back, because re-emitting a formula
 * this writer did not derive would be a claim about its spelling.
 *
 * Two model rules are self-imposed rather than schema rules, and are stated because they refuse files Excel
 * would accept:
 *
 * * `cellIs`, `expression` and `top10` **must** carry a differential format. A rule with no format changes no
 *   cell: writing one would put a condition in the file with no consequence, which is exactly the "record
 *   theatre" this project refuses.
 * * `colorScale` and `dataBar` must **not** carry one: their visuals are the gradient or the bar, and Excel
 *   ignores a dxf on those kinds.
 *
 * Not modelled, and not claimed: a `<dxf>`'s gradient fill, `extLst`, vertical-text rotation (the schema's
 * `textRotation="255"`), and the two `x14` border-ish extensions; the 2010 `x14` extensions (`x14:dataBar`
 * gradient/negative-bar options, `x14:iconSet` with its fifth icon, `<extLst>`); `expression` rules beyond the
 * evaluator's grammar; and the evaluation itself — whether Excel highlights a cell is not something a file can
 * state. A dxf that carries only constructs this model cannot hold is read as `null`, so a `dxfId` never shifts.
 *
 * One deliberate exception to "write no default": an `iconSet` rule always writes its `iconSet` name, because the
 * name is the rule's subject rather than a modifier, and a file that left it out would describe an anonymous
 * icon set. Its `showValue`/`percent`/`reverse` follow the usual rule and are written only when they differ from
 * the schema defaults (`showValue` true, `percent` true, `reverse` false).
 */
object XlsxConditionalFormatting {

    /** Self-imposed model bounds, not schema bounds. */
    const val MAX_BLOCKS = 1024
    const val MAX_RULES_PER_BLOCK = 64
    const val MAX_FORMULA = 8_192
    const val MAX_PRIORITY = 1_000_000
    const val MAX_SQREF_TOKENS = 64

    /** Longest literal a text predicate may look for; Excel's own dialog accepts far less. */
    const val MAX_TEXT = 255

    /** Largest standard-deviation multiple an aboveAverage rule may widen its band by. */
    const val MAX_STD_DEV = 100

    /** Longest font family name a dxf may set; Excel's own limit. */
    const val MAX_FONT_NAME = 31

    /** Smallest and largest font size a dxf may set, in points; Excel's dialog refuses both ends. */
    const val MIN_FONT_SIZE = 1.0
    const val MAX_FONT_SIZE = 409.0

    /** Longest number-format code a dxf may carry. */
    const val MAX_FORMAT_CODE = 255

    /** Largest alignment indent a dxf may set (§18.18.47 caps `indent` at 250). */
    const val MAX_INDENT = 250

    /** Rule kinds this model can write completely. */
    enum class Kind(val token: String) {
        CELL_IS("cellIs"),
        EXPRESSION("expression"),
        TOP10("top10"),
        COLOR_SCALE("colorScale"),
        DATA_BAR("dataBar"),
        TEXT_CONTAINS("containsText"),
        TEXT_NOT_CONTAINS("notContainsText"),
        TEXT_BEGINS_WITH("beginsWith"),
        TEXT_ENDS_WITH("endsWith"),
        ABOVE_AVERAGE("aboveAverage"),
        DUPLICATE("duplicateValues"),
        UNIQUE("uniqueValues"),
        CONTAINS_BLANKS("containsBlanks"),
        NOT_CONTAINS_BLANKS("notContainsBlanks"),
        CONTAINS_ERRORS("containsErrors"),
        NOT_CONTAINS_ERRORS("notContainsErrors"),
        TIME_PERIOD("timePeriod"),
        ICON_SET("iconSet"),
        ;

        /**
         * Kinds whose only visual is a differential format, so a rule of this kind must carry one. The four text
         * predicates, `aboveAverage`, `duplicateValues` and `uniqueValues` paint through a dxf too, but accept a
         * missing one on read: a foreign file may legitimately state such a rule with no format, and refusing it
         * would drop the rule from the model rather than record that it fires and paints nothing.
         */
        val needsFormat: Boolean get() = this == CELL_IS || this == EXPRESSION || this == TOP10

        /** Kinds that paint through their own colour data, so a dxf on one would be a second, unused visual. */
        val selfPainted: Boolean get() = this == COLOR_SCALE || this == DATA_BAR

        /** The four predicates that carry `text`; each also writes its own `operator` token. */
        val isText: Boolean
            get() = this == TEXT_CONTAINS || this == TEXT_NOT_CONTAINS ||
                this == TEXT_BEGINS_WITH || this == TEXT_ENDS_WITH

        /** The `operator` attribute a text rule carries — Excel's token, which differs from the type for the
         *  negative form (`notContainsText` is written `operator="notContains"`). */
        val textOperator: String?
            get() = when (this) {
                TEXT_CONTAINS -> "containsText"
                TEXT_NOT_CONTAINS -> "notContains"
                TEXT_BEGINS_WITH -> "beginsWith"
                TEXT_ENDS_WITH -> "endsWith"
                else -> null
            }

        companion object {
            fun of(token: String): Kind? = values().firstOrNull { it.token == token }
        }
    }

    /** `ST_TimePeriod`: the 17 relative-date presets a `timePeriod` rule can carry. */
    enum class TimePeriod(val token: String) {
        TODAY("today"),
        YESTERDAY("yesterday"),
        TOMORROW("tomorrow"),
        LAST_7_DAYS("last7Days"),
        THIS_MONTH("thisMonth"),
        LAST_MONTH("lastMonth"),
        NEXT_MONTH("nextMonth"),
        THIS_WEEK("thisWeek"),
        LAST_WEEK("lastWeek"),
        NEXT_WEEK("nextWeek"),
        THIS_YEAR("thisYear"),
        LAST_YEAR("lastYear"),
        NEXT_YEAR("nextYear"),
        FIRST_QUARTER("firstQuarter"),
        SECOND_QUARTER("secondQuarter"),
        THIRD_QUARTER("thirdQuarter"),
        FOURTH_QUARTER("fourthQuarter"),
        ;

        companion object {
            fun of(token: String): TimePeriod? = values().firstOrNull { it.token == token }
        }
    }

    /**
     * `ST_IconSetType` in the base schema: seventeen sets, named by their icon count (the leading digit).
     *
     * A five-icon set needs four boundaries, which is exactly the schema's `maxOccurs` for `<cfvo>`: the base
     * element can describe 3, 4 and 5 icons, and the `x14` extension is needed only for the sets it adds (for
     * example `5Boxes`), which this model does not claim.
     */
    enum class IconSet(val token: String) {
        THREE_ARROWS("3Arrows"),
        THREE_ARROWS_GRAY("3ArrowsGray"),
        THREE_FLAGS("3Flags"),
        THREE_TRAFFIC_LIGHTS_1("3TrafficLights1"),
        THREE_TRAFFIC_LIGHTS_2("3TrafficLights2"),
        THREE_SIGNS("3Signs"),
        THREE_SYMBOLS("3Symbols"),
        THREE_SYMBOLS_2("3Symbols2"),
        FOUR_ARROWS("4Arrows"),
        FOUR_ARROWS_GRAY("4ArrowsGray"),
        FOUR_RED_TO_BLACK("4RedToBlack"),
        FOUR_RATING("4Rating"),
        FOUR_TRAFFIC_LIGHTS("4TrafficLights"),
        FIVE_ARROWS("5Arrows"),
        FIVE_ARROWS_GRAY("5ArrowsGray"),
        FIVE_RATING("5Rating"),
        FIVE_QUARTERS("5Quarters"),
        ;

        /** How many icons the set draws — and therefore how many boundaries a rule stores (`count - 1`). */
        val iconCount: Int get() = token[0] - '0'

        companion object {
            fun of(token: String): IconSet? = values().firstOrNull { it.token == token }

            /** The schema default, written when a caller names no set. */
            val DEFAULT = THREE_TRAFFIC_LIGHTS_1
        }
    }

    /** `ST_ConditionalFormattingOperator`; `between` and `notBetween` take exactly two formulas. */
    enum class Operator(val token: String) {
        LESS_THAN("lessThan"),
        LESS_THAN_OR_EQUAL("lessThanOrEqual"),
        EQUAL("equal"),
        NOT_EQUAL("notEqual"),
        GREATER_THAN_OR_EQUAL("greaterThanOrEqual"),
        GREATER_THAN("greaterThan"),
        BETWEEN("between"),
        NOT_BETWEEN("notBetween"),
        ;

        val takesTwoFormulas: Boolean get() = this == BETWEEN || this == NOT_BETWEEN

        companion object {
            fun of(token: String): Operator? = values().firstOrNull { it.token == token }
        }
    }

    /** `ST_CfvoType`: `min` and `max` carry no value; the other three require one. */
    enum class ThresholdKind(val token: String) {
        MIN("min"),
        MAX("max"),
        NUM("num"),
        PERCENT("percent"),
        PERCENTILE("percentile"),
        ;

        val needsValue: Boolean get() = this == NUM || this == PERCENT || this == PERCENTILE

        companion object {
            fun of(token: String): ThresholdKind? = values().firstOrNull { it.token == token }
        }
    }

    /** One `<cfvo>`. */
    data class Threshold(val kind: ThresholdKind, val value: Double? = null) {
        init {
            require(kind.needsValue == (value != null)) {
                "A ${kind.token} threshold ${if (kind.needsValue) "requires" else "does not take"} a value"
            }
            value?.let {
                require(!it.isNaN() && !it.isInfinite()) { "A threshold value must be finite: $it" }
                if (kind == ThresholdKind.PERCENT || kind == ThresholdKind.PERCENTILE) {
                    require(it in 0.0..100.0) { "A ${kind.token} threshold must be 0..100: $it" }
                }
            }
        }
    }

    private val HEX6 = Regex("^[0-9A-Fa-f]{6}$")

    /**
     * `ST_OnOff` as this reader understands it: `1`/`true`/`on` and `0`/`false`/`off`, anything else `null`.
     * A dxf is the one place where "absent" and "off" mean different things (`<b val="0"/>` clears bold while a
     * missing `<b>` leaves it alone), so both the value and its absence are representable.
     */
    private fun readOnOff(raw: String?): Boolean? = when (raw?.trim()?.lowercase()) {
        null, "" -> null
        "1", "true", "on" -> true
        "0", "false", "off" -> false
        else -> null
    }

    /** `ST_BorderStyle`; `none` is an absent [Edge] rather than a token, so "no line" cannot be written by accident. */
    enum class BorderStyle(val token: String) {
        THIN("thin"),
        MEDIUM("medium"),
        DASHED("dashed"),
        DOTTED("dotted"),
        THICK("thick"),
        DOUBLE("double"),
        HAIR("hair"),
        MEDIUM_DASHED("mediumDashed"),
        DASH_DOT("dashDot"),
        MEDIUM_DASH_DOT("mediumDashDot"),
        DASH_DOT_DOT("dashDotDot"),
        MEDIUM_DASH_DOT_DOT("mediumDashDotDot"),
        SLANT_DASH_DOT("slantDashDot"),
        ;

        companion object {
            fun of(token: String?): BorderStyle? = entries.firstOrNull { it.token == token }
        }
    }

    /** One edge of a dxf border: a line style, optionally coloured. */
    data class Edge(val style: BorderStyle, val rgb: String? = null) {
        init {
            rgb?.let { require(HEX6.matches(it)) { "A border colour must be six hex digits: $it" } }
        }
    }

    /**
     * `CT_Border` inside a dxf: up to five edges. A diagonal edge is only drawn when `diagonalUp` or
     * `diagonalDown` is set, and those flags draw nothing without the edge, so neither half is accepted alone.
     */
    data class Border(
        val top: Edge? = null,
        val right: Edge? = null,
        val bottom: Edge? = null,
        val left: Edge? = null,
        val diagonal: Edge? = null,
        val diagonalUp: Boolean = false,
        val diagonalDown: Boolean = false,
    ) {
        init {
            require(top != null || right != null || bottom != null || left != null || diagonal != null) {
                "A border needs at least one edge; a border that draws nothing changes nothing"
            }
            require(diagonal != null || (!diagonalUp && !diagonalDown)) {
                "A diagonal flag draws nothing without a diagonal edge"
            }
            require(diagonal == null || diagonalUp || diagonalDown) {
                "A diagonal edge is only drawn when diagonalUp or diagonalDown is set"
            }
        }

        /** The edges in `CT_Border` order, so the writer and the checker agree on one sequence. */
        val edges: List<Pair<String, Edge>>
            get() = listOfNotNull(
                left?.let { "left" to it },
                right?.let { "right" to it },
                top?.let { "top" to it },
                bottom?.let { "bottom" to it },
                diagonal?.let { "diagonal" to it },
            )
    }

    /** `ST_HorizontalAlignment`. */
    enum class Horizontal(val token: String) {
        GENERAL("general"),
        LEFT("left"),
        CENTER("center"),
        RIGHT("right"),
        FILL("fill"),
        JUSTIFY("justify"),
        CENTER_CONTINUOUS("centerContinuous"),
        DISTRIBUTED("distributed"),
        ;

        companion object {
            fun of(token: String?): Horizontal? = entries.firstOrNull { it.token == token }
        }
    }

    /** `ST_VerticalAlignment`. */
    enum class Vertical(val token: String) {
        TOP("top"),
        CENTER("center"),
        BOTTOM("bottom"),
        JUSTIFY("justify"),
        DISTRIBUTED("distributed"),
        ;

        companion object {
            fun of(token: String?): Vertical? = entries.firstOrNull { it.token == token }
        }
    }

    /**
     * `CT_CellAlignment` inside a dxf. `textRotation` is `0..180`; the schema's `255` (stacked vertically) is
     * refused rather than rounded into a rotation, because it is a different behaviour, not a larger angle.
     */
    data class Alignment(
        val horizontal: Horizontal? = null,
        val vertical: Vertical? = null,
        val textRotation: Int? = null,
        val wrapText: Boolean? = null,
        val indent: Int? = null,
        val shrinkToFit: Boolean? = null,
        val readingOrder: Int? = null,
    ) {
        init {
            require(
                horizontal != null || vertical != null || textRotation != null || wrapText != null ||
                    indent != null || shrinkToFit != null || readingOrder != null,
            ) { "An alignment that sets nothing is not written" }
            textRotation?.let {
                require(it in 0..180) {
                    "A text rotation is 0..180 degrees; 255 is vertical text, which this model does not hold: $it"
                }
            }
            indent?.let { require(it in 0..MAX_INDENT) { "An indent is 0..$MAX_INDENT: $it" } }
            readingOrder?.let { require(it in 0..2) { "A reading order is 0..2 (context, left-to-right, right-to-left): $it" } }
        }
    }

    /** `CT_Protection` inside a dxf: `locked` and `hidden`, each written only when it differs from the default. */
    data class Protection(val locked: Boolean? = null, val hidden: Boolean? = null) {
        init {
            require(locked != null || hidden != null) { "A protection that sets nothing is not written" }
        }
    }

    /** `ST_PatternType`; `none` is an absent pattern, for the same reason `none` is an absent border edge. */
    enum class PatternType(val token: String) {
        SOLID("solid"),
        MEDIUM_GRAY("mediumGray"),
        DARK_GRAY("darkGray"),
        LIGHT_GRAY("lightGray"),
        DARK_HORIZONTAL("darkHorizontal"),
        DARK_VERTICAL("darkVertical"),
        DARK_DOWN("darkDown"),
        DARK_UP("darkUp"),
        DARK_GRID("darkGrid"),
        DARK_TRELLIS("darkTrellis"),
        LIGHT_HORIZONTAL("lightHorizontal"),
        LIGHT_VERTICAL("lightVertical"),
        LIGHT_DOWN("lightDown"),
        LIGHT_UP("lightUp"),
        LIGHT_GRID("lightGrid"),
        LIGHT_TRELLIS("lightTrellis"),
        GRAY_125("gray125"),
        GRAY_0625("gray0625"),
        ;

        companion object {
            fun of(token: String?): PatternType? = entries.firstOrNull { it.token == token }
        }
    }

    /**
     * A `<dxf>`: every section `CT_Dxf` allows except a gradient fill, `extLst` and vertical text. Each section
     * is optional — an absent section means "leave that property alone" — but a format that sets nothing at all
     * is refused, because it would point a rule at a differential format that changes nothing.
     */
    data class Format(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val strike: Boolean = false,
        val underline: Boolean = false,
        val fontRgb: String? = null,
        val fillRgb: String? = null,
        val fontName: String? = null,
        val fontSize: Double? = null,
        /** Number-format code, e.g. `0.0%`; the writer also registers it in `<numFmts>` so the id resolves. */
        val numberFormat: String? = null,
        val fillPattern: PatternType? = null,
        val fillBgRgb: String? = null,
        val border: Border? = null,
        val alignment: Alignment? = null,
        val protection: Protection? = null,
    ) {
        init {
            require(
                bold || italic || strike || underline || fontRgb != null || fillRgb != null ||
                    fontName != null || fontSize != null || numberFormat != null || fillPattern != null ||
                    fillBgRgb != null || border != null || alignment != null || protection != null,
            ) { "An empty differential format is not written" }
            fontRgb?.let { require(HEX6.matches(it)) { "A font colour must be six hex digits: $it" } }
            fillRgb?.let { require(HEX6.matches(it)) { "A fill colour must be six hex digits: $it" } }
            fillBgRgb?.let {
                require(HEX6.matches(it)) { "A fill colour must be six hex digits: $it" }
                require(fillPattern != null) { "A background colour sets nothing without a pattern" }
            }
            fillPattern?.let { require(it != PatternType.SOLID || fillRgb != null) {
                "A solid pattern needs a foreground colour"
            } }
            fontName?.let {
                require(it.length in 1..MAX_FONT_NAME) { "A font name is 1..$MAX_FONT_NAME characters: $it" }
                require(it.none { c -> c.code < 0x20 }) { "A font name must not contain control characters" }
            }
            fontSize?.let {
                require(it.isFinite() && it >= MIN_FONT_SIZE && it <= MAX_FONT_SIZE) {
                    "A font size is $MIN_FONT_SIZE..$MAX_FONT_SIZE points: $it"
                }
            }
            numberFormat?.let {
                require(it.length in 1..MAX_FORMAT_CODE) { "A number format is 1..$MAX_FORMAT_CODE characters" }
                require(it.none { c -> c.code < 0x20 }) { "A number format must not contain control characters" }
            }
        }

        /** The `<font>` half is written only when it says something; the fill half likewise. */
        val hasFont: Boolean
            get() = bold || italic || strike || underline || fontRgb != null || fontName != null || fontSize != null

        /** A fill needs a colour or a pattern; `fillPattern` alone still paints (a hatch with no colour is legal). */
        val hasFill: Boolean get() = fillRgb != null || fillPattern != null || fillBgRgb != null
    }

    /**
     * One `<cfRule>`. Which fields are legal depends on [kind]; the initialiser is the single place that decides,
     * so an impossible combination cannot reach the writer.
     */
    data class Rule(
        val kind: Kind,
        val priority: Int,
        val stopIfTrue: Boolean? = null,
        val operator: Operator? = null,
        val formulas: List<String> = emptyList(),
        val rank: Int? = null,
        /**
         * `@percent`, which two different elements spell: on `top10` it is `CT_CfRule/@percent` (default false)
         * and on an icon set it is `CT_IconSet/@percent` (default true). Both are the same attribute name, so the
         * field is shared; the writer puts it on whichever element the kind owns.
         */
        val percent: Boolean? = null,
        val bottom: Boolean? = null,
        val thresholds: List<Threshold> = emptyList(),
        val colors: List<String> = emptyList(),
        val format: Format? = null,
        /** The literal a text rule looks for; refused on every other kind. */
        val text: String? = null,
        /** `aboveAverage`; absent means the schema default `true`. Only an aboveAverage rule carries it. */
        val aboveAverage: Boolean? = null,
        /** `equalAverage`; absent means `false` — ties are excluded. */
        val equalAverage: Boolean? = null,
        /** `stdDev`: multiples of the population standard deviation the band is widened by. */
        val stdDev: Int? = null,
        /** The `timePeriod` preset; required on a `timePeriod` rule and refused everywhere else. */
        val timePeriod: TimePeriod? = null,
        /** The icon set an `iconSet` rule draws from; refused on every other kind. */
        val iconSet: IconSet? = null,
        /** `<iconSet showValue>`: whether the cell's number is still shown beside the icon. Default true. */
        val showValue: Boolean? = null,
        /** `<iconSet reverse>`: whether the icon order is flipped. Default false. */
        val reverse: Boolean? = null,
    ) {
        init {
            require(priority in 1..MAX_PRIORITY) { "A rule priority must be in 1..$MAX_PRIORITY: $priority" }
            for (formula in formulas) {
                require(formula.isNotBlank()) { "A rule formula may not be blank" }
                require(formula.length <= MAX_FORMULA) {
                    "A rule formula may hold at most $MAX_FORMULA characters, got ${formula.length}"
                }
                require(!formula.startsWith("=")) {
                    "A rule formula is stored without a leading '=': $formula"
                }
            }
            for (color in colors) require(HEX6.matches(color)) { "A rule colour must be six hex digits: $color" }
            // `text` belongs to the four text predicates and to nothing else; the average attributes belong to
            // aboveAverage. Keeping that split here means a caller cannot express a rule the schema would not.
            if (!kind.isText) require(text == null) { "A ${kind.token} rule does not carry text" }
            if (kind != Kind.TIME_PERIOD) {
                require(timePeriod == null) { "A ${kind.token} rule does not carry a time period" }
            }
            if (kind != Kind.ICON_SET) {
                require(iconSet == null && showValue == null && reverse == null) {
                    "Only an iconSet rule carries iconSet/showValue/reverse"
                }
            }
            if (kind != Kind.ABOVE_AVERAGE) {
                require(aboveAverage == null && equalAverage == null && stdDev == null) {
                    "Only an aboveAverage rule carries aboveAverage/equalAverage/stdDev"
                }
            }
            if (kind.needsFormat) {
                require(format != null) {
                    "A ${kind.token} rule must carry a differential format, or it changes no cell"
                }
            }
            if (kind.selfPainted) {
                require(format == null) { "A ${kind.token} rule carries its own visual and must not carry a dxf" }
            }
            when (kind) {
                Kind.CELL_IS -> {
                    require(operator != null) { "A cellIs rule requires an operator" }
                    val expected = if (operator.takesTwoFormulas) 2 else 1
                    require(formulas.size == expected) {
                        "A cellIs rule with ${operator.token} takes $expected formula(s), got ${formulas.size}"
                    }
                    require(rank == null && thresholds.isEmpty() && colors.isEmpty()) {
                        "A cellIs rule carries only an operator, formulas and a format"
                    }
                    require(percent == null && bottom == null) { "A cellIs rule takes no rank flags" }
                }
                Kind.EXPRESSION -> {
                    require(formulas.size == 1) { "An expression rule takes exactly one formula" }
                    require(operator == null && rank == null && thresholds.isEmpty() && colors.isEmpty()) {
                        "An expression rule carries only a formula and a format"
                    }
                    require(percent == null && bottom == null) { "An expression rule takes no rank flags" }
                }
                Kind.TOP10 -> {
                    require(rank != null && rank in 1..1000) { "A top10 rule requires a rank in 1..1000" }
                    require(formulas.isEmpty() && thresholds.isEmpty() && colors.isEmpty() && operator == null) {
                        "A top10 rule carries only its rank and flags"
                    }
                }
                Kind.COLOR_SCALE -> {
                    require(thresholds.size in 2..3) { "A colour scale takes 2 or 3 thresholds: ${thresholds.size}" }
                    require(colors.size == thresholds.size) {
                        "A colour scale needs one colour per threshold: ${colors.size} colours, ${thresholds.size} thresholds"
                    }
                    require(formulas.isEmpty() && operator == null && rank == null) {
                        "A colour scale carries only thresholds and colours"
                    }
                    require(percent == null && bottom == null) { "A colour scale takes no rank flags" }
                }
                Kind.DATA_BAR -> {
                    require(thresholds.size in 2..3) { "A data bar takes 2 or 3 thresholds: ${thresholds.size}" }
                    require(colors.size == 1) { "A data bar takes exactly one colour: ${colors.size}" }
                    require(formulas.isEmpty() && operator == null && rank == null) {
                        "A data bar carries only thresholds and a colour"
                    }
                    require(percent == null && bottom == null) { "A data bar takes no rank flags" }
                }
                Kind.TEXT_CONTAINS, Kind.TEXT_NOT_CONTAINS, Kind.TEXT_BEGINS_WITH, Kind.TEXT_ENDS_WITH -> {
                    require(text != null && text.isNotBlank()) {
                        "A ${kind.token} rule requires the text it looks for"
                    }
                    require(text.length <= MAX_TEXT) {
                        "A text rule may look for at most $MAX_TEXT characters, got ${text.length}"
                    }
                    // The predicate is stated by type/operator/text; a formula would be a second, unverified
                    // spelling of the same rule, so this model writes none and refuses one in its own input.
                    require(formulas.isEmpty() && operator == null) {
                        "A ${kind.token} rule is stated by its attributes, not by a formula"
                    }
                    require(rank == null && thresholds.isEmpty() && colors.isEmpty()) {
                        "A ${kind.token} rule carries only its text and a format"
                    }
                    require(percent == null && bottom == null) { "A text rule takes no rank flags" }
                }
                Kind.ABOVE_AVERAGE -> {
                    require(formulas.isEmpty() && operator == null && rank == null) {
                        "An aboveAverage rule carries only its flags"
                    }
                    require(thresholds.isEmpty() && colors.isEmpty()) { "An aboveAverage rule carries no cfvo" }
                    require(percent == null && bottom == null) { "An aboveAverage rule takes no rank flags" }
                    stdDev?.let {
                        require(it in 0..MAX_STD_DEV) { "stdDev $it outside 0..$MAX_STD_DEV" }
                    }
                }
                Kind.DUPLICATE, Kind.UNIQUE -> {
                    require(formulas.isEmpty() && operator == null && rank == null) {
                        "A ${kind.token} rule carries only its format"
                    }
                    require(thresholds.isEmpty() && colors.isEmpty()) { "A ${kind.token} rule carries no cfvo" }
                    require(percent == null && bottom == null) {
                        "A ${kind.token} rule takes no rank flags"
                    }
                }
                Kind.CONTAINS_BLANKS, Kind.NOT_CONTAINS_BLANKS,
                Kind.CONTAINS_ERRORS, Kind.NOT_CONTAINS_ERRORS,
                -> {
                    require(formulas.isEmpty() && operator == null && rank == null) {
                        "A ${kind.token} rule carries only its format"
                    }
                    require(thresholds.isEmpty() && colors.isEmpty()) { "A ${kind.token} rule carries no cfvo" }
                    require(percent == null && bottom == null) {
                        "A ${kind.token} rule takes no rank flags"
                    }
                }
                Kind.TIME_PERIOD -> {
                    require(timePeriod != null) { "A timePeriod rule requires the period it looks for" }
                    require(formulas.isEmpty() && operator == null && rank == null) {
                        "A timePeriod rule carries only its preset and a format"
                    }
                    require(thresholds.isEmpty() && colors.isEmpty()) { "A timePeriod rule carries no cfvo" }
                    require(percent == null && bottom == null) { "A timePeriod rule takes no rank flags" }
                }
                Kind.ICON_SET -> {
                    require(iconSet != null) { "An iconSet rule requires the set it draws" }
                    val icons = iconSet.iconCount
                    require(formulas.isEmpty() && operator == null && rank == null) {
                        "An iconSet rule carries only its boundaries and flags"
                    }
                    require(colors.isEmpty()) { "An iconSet rule carries no colours" }
                    require(bottom == null) { "An iconSet rule takes no bottom flag" }
                    require(thresholds.size == icons - 1) {
                        "A ${iconSet.token} rule takes ${icons - 1} boundary threshold(s), got ${thresholds.size}"
                    }
                    require(thresholds.none { it.kind == ThresholdKind.MIN || it.kind == ThresholdKind.MAX }) {
                        "An iconSet boundary is a percent, percentile or number; min and max are not boundaries"
                    }
                }
            }
        }
    }

    private val CELL = Regex("^[A-Z]{1,3}[1-9][0-9]{0,6}$")
    private val RANGE = Regex("^[A-Z]{1,3}[1-9][0-9]{0,6}:[A-Z]{1,3}[1-9][0-9]{0,6}$")

    /** One `<conditionalFormatting>` element: a square-reference target and the rules that apply to it. */
    data class Block(val sqref: String, val rules: List<Rule>) {
        init {
            val tokens = sqref.trim().split(' ').filter { it.isNotEmpty() }
            require(tokens.isNotEmpty()) { "A conditionalFormatting block needs a sqref" }
            require(tokens.size <= MAX_SQREF_TOKENS) {
                "At most $MAX_SQREF_TOKENS references per block, got ${tokens.size}"
            }
            require(tokens.distinct().size == tokens.size) { "A sqref may not repeat a reference: $sqref" }
            for (token in tokens) {
                require(CELL.matches(token) || RANGE.matches(token)) {
                    "A sqref reference must be an upper-case A1 or A1:B2 range: $token"
                }
            }
            require(rules.isNotEmpty()) { "An empty <conditionalFormatting> element is not written" }
            require(rules.size <= MAX_RULES_PER_BLOCK) {
                "At most $MAX_RULES_PER_BLOCK rules per block, got ${rules.size}"
            }
            require(rules.distinctBy { it.priority }.size == rules.size) {
                "Rule priorities must differ inside a block: ${rules.map { it.priority }}"
            }
        }
    }

    /**
     * Excel keys a rule's priority across the whole *sheet*, not per block, and repairs a sheet with two rules of
     * the same priority. Called from `StyledSheet`'s initialiser so such a sheet cannot be constructed.
     */
    fun requireDistinctPriorities(blocks: List<Block>) {
        require(blocks.size <= MAX_BLOCKS) { "At most $MAX_BLOCKS conditional-formatting blocks: ${blocks.size}" }
        val priorities = blocks.flatMap { block -> block.rules.map { it.priority } }
        require(priorities.distinct().size == priorities.size) {
            "Rule priorities must be unique on a sheet: ${priorities.sorted()}"
        }
    }

    private fun esc(s: String): String = buildString(s.length) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(ch)
            }
        }
    }

    /** XML Schema `double` spelling: `3.0` is written `3`, `2.5` stays `2.5`, never exponent notation. */
    private fun number(value: Double): String =
        if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            // `BigDecimal.valueOf` uses the canonical `Double.toString`, i.e. the shortest text that round-trips.
            // `BigDecimal(double)` would write the exact binary expansion (0.1 as 0.1000000000000000055...).
            java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        }

    /**
     * `<dxf>` for one format. `CT_Dxf` is a sequence — `font`, `numFmt`, `fill`, `alignment`, `border`,
     * `protection` — and the sections are written in exactly that order, because a file whose sections are
     * shuffled is one Excel offers to repair.
     *
     * `numFmtId` is the id the caller registered the code under in `<numFmts>`; the code is written beside it, so
     * a reader that trusts only the attribute still sees the format. `0` means "no code", i.e. no `<numFmt>`.
     */
    fun dxfToXml(format: Format, numFmtId: Int = 0): String = buildString {
        append("<dxf>")
        if (format.hasFont) {
            append("<font>")
            if (format.bold) append("<b/>")
            if (format.italic) append("<i/>")
            if (format.strike) append("<strike/>")
            if (format.underline) append("<u/>")
            // `CT_Font` allows these in any order; `sz` before `color` before `name` is the order Excel writes.
            format.fontSize?.let { append("""<sz val="${number(it)}"/>""") }
            format.fontRgb?.let { append("""<color rgb="FF${it.uppercase()}"/>""") }
            format.fontName?.let { append("""<name val="${esc(it)}"/>""") }
            append("</font>")
        }
        format.numberFormat?.let { append("""<numFmt numFmtId="$numFmtId" formatCode="${esc(it)}"/>""") }
        if (format.hasFill) {
            append("<fill>")
            val pattern = format.fillPattern
            when {
                pattern != null -> {
                    append("""<patternFill patternType="${pattern.token}">""")
                    format.fillRgb?.let { append("""<fgColor rgb="FF${it.uppercase()}"/>""") }
                    format.fillBgRgb?.let { append("""<bgColor rgb="FF${it.uppercase()}"/>""") }
                    append("</patternFill>")
                }
                else -> {
                    append("""<patternFill patternType="solid">""")
                    append("""<fgColor rgb="FF${format.fillRgb!!.uppercase()}"/><bgColor indexed="64"/>""")
                    append("</patternFill>")
                }
            }
            append("</fill>")
        }
        format.alignment?.let { a ->
            append("<alignment")
            a.horizontal?.let { append(""" horizontal="${it.token}"""") }
            a.vertical?.let { append(""" vertical="${it.token}"""") }
            a.textRotation?.let { append(""" textRotation="$it"""") }
            a.wrapText?.let { append(""" wrapText="${onOff(it)}"""") }
            a.indent?.let { append(""" indent="$it"""") }
            a.shrinkToFit?.let { append(""" shrinkToFit="${onOff(it)}"""") }
            a.readingOrder?.let { append(""" readingOrder="$it"""") }
            append("/>")
        }
        format.border?.let { b ->
            append("<border")
            if (b.diagonalUp) append(""" diagonalUp="1"""")
            if (b.diagonalDown) append(""" diagonalDown="1"""")
            append(">")
            for ((side, edge) in b.edges) {
                val colour = edge.rgb
                if (colour == null) {
                    append("""<$side style="${edge.style.token}"/>""")
                } else {
                    append("""<$side style="${edge.style.token}">""")
                    append("""<color rgb="FF${colour.uppercase()}"/></$side>""")
                }
            }
            append("</border>")
        }
        format.protection?.let { p ->
            append("<protection")
            p.locked?.let { append(""" locked="${onOff(it)}"""") }
            p.hidden?.let { append(""" hidden="${onOff(it)}"""") }
            append("/>")
        }
        append("</dxf>")
    }

    private fun onOff(value: Boolean): String = if (value) "1" else "0"

    /**
     * Reads the `<dxfs>` table; a `<dxf>` this model cannot hold is kept as `null` so `dxfId`s never shift. The
     * `<numFmts>` table is read first because a dxf's `<numFmt>` may state only an id that the table resolves.
     */
    fun parseDxfs(stylesRoot: BoundedXml.Node): List<Format?> {
        val node = stylesRoot.children.firstOrNull { it.name.local == "dxfs" } ?: return emptyList()
        val codes = stylesRoot.children.firstOrNull { it.name.local == "numFmts" }
            ?.children.orEmpty()
            .filter { it.name.local == "numFmt" }
            .mapNotNull { nf -> nf.attr("numFmtId")?.toIntOrNull()?.let { id -> id to nf.attr("formatCode") } }
            .filter { it.second != null }
            .associate { it.first to it.second!! }
        return node.children.filter { it.name.local == "dxf" }.map { parseDxf(it, codes) }
    }

    private fun parseDxf(node: BoundedXml.Node, numFmts: Map<Int, String>): Format? {
        val font = node.children.firstOrNull { it.name.local == "font" }
        val fill = node.children.firstOrNull { it.name.local == "fill" }
        val alignment = node.children.firstOrNull { it.name.local == "alignment" }
        val border = node.children.firstOrNull { it.name.local == "border" }
        val protection = node.children.firstOrNull { it.name.local == "protection" }
        val numFmt = node.children.firstOrNull { it.name.local == "numFmt" }
        fun rgb(raw: String?): String? {
            val value = raw?.trim() ?: return null
            val hex = when (value.length) {
                6 -> value
                8 -> value.substring(2)
                else -> return null
            }
            return if (HEX6.matches(hex)) hex.uppercase() else null
        }
        /** A dxf's decoration flags are written `<b/>` by this writer and `<b val="1"/>`/`<b val="0"/>` by others. */
        fun flag(parent: BoundedXml.Node?, name: String): Boolean {
            val child = parent?.children?.firstOrNull { it.name.local == name } ?: return false
            return readOnOff(child.attr("val")) ?: true
        }
        /**
         * `<u>` is not an `ST_OnOff`: `val` is `ST_UnderlineValues`, where `none` means "no underline" and
         * `single`/`double`/the two accounting forms all mean "there is one". This model holds the fact, not the
         * style, so every non-`none` value collapses to `true` — and `<u/>` with no `val` is `single`.
         */
        fun underlineFlag(): Boolean {
            val child = font?.children?.firstOrNull { it.name.local == "u" } ?: return false
            val raw = child.attr("val")?.trim()?.lowercase() ?: return true
            return raw != "none" && readOnOff(raw) != false
        }
        // A dxf solid fill is written with <fgColor> by Excel and with <bgColor> by other writers (openpyxl does
        // exactly that), so both are accepted; the foreground wins when a file carries both.
        val pattern = fill?.children?.firstOrNull { it.name.local == "patternFill" }
        val patternType = PatternType.of(pattern?.attr("patternType"))
        val fg = rgb(pattern?.children?.firstOrNull { it.name.local == "fgColor" }?.attr("rgb"))
        val bg = rgb(pattern?.children?.firstOrNull { it.name.local == "bgColor" }?.attr("rgb"))
        val hatched = patternType != null && patternType != PatternType.SOLID
        fun edge(side: String): Edge? {
            val child = border?.children?.firstOrNull { it.name.local == side } ?: return null
            val style = BorderStyle.of(child.attr("style")) ?: return null
            return Edge(style, rgb(child.children.firstOrNull { it.name.local == "color" }?.attr("rgb")))
        }
        return runCatching {
            val borderModel = if (border == null) null else Border(
                top = edge("top"),
                right = edge("right"),
                bottom = edge("bottom"),
                left = edge("left"),
                diagonal = edge("diagonal"),
                diagonalUp = readOnOff(border.attr("diagonalUp")) == true,
                diagonalDown = readOnOff(border.attr("diagonalDown")) == true,
            )
            val alignmentModel = if (alignment == null) null else Alignment(
                horizontal = Horizontal.of(alignment.attr("horizontal")),
                vertical = Vertical.of(alignment.attr("vertical")),
                textRotation = alignment.attr("textRotation")?.toIntOrNull(),
                wrapText = readOnOff(alignment.attr("wrapText")),
                indent = alignment.attr("indent")?.toDoubleOrNull()?.toInt(),
                shrinkToFit = readOnOff(alignment.attr("shrinkToFit")),
                readingOrder = alignment.attr("readingOrder")?.toDoubleOrNull()?.toInt(),
            )
            val protectionModel = if (protection == null) null else Protection(
                locked = readOnOff(protection.attr("locked")),
                hidden = readOnOff(protection.attr("hidden")),
            )
            Format(
                bold = flag(font, "b"),
                italic = flag(font, "i"),
                strike = flag(font, "strike"),
                underline = underlineFlag(),
                fontRgb = rgb(font?.children?.firstOrNull { it.name.local == "color" }?.attr("rgb")),
                fillRgb = if (hatched) fg else fg ?: bg,
                fontName = font?.children?.firstOrNull { it.name.local == "name" }?.attr("val"),
                fontSize = font?.children?.firstOrNull { it.name.local == "sz" }?.attr("val")?.toDoubleOrNull(),
                numberFormat = numFmt?.attr("formatCode")
                    ?: numFmt?.attr("numFmtId")?.toIntOrNull()?.let { numFmts[it] },
                fillPattern = if (hatched) patternType else null,
                fillBgRgb = if (hatched) bg else null,
                border = borderModel,
                alignment = alignmentModel,
                protection = protectionModel,
            )
        }.getOrNull()
    }

    /**
     * Every `<conditionalFormatting>` of one sheet, in document order. A rule whose fields this model refuses
     * (an unmodelled kind, a missing dxf, a bad rank) is dropped; a block left with no rules is dropped with it.
     */
    fun parse(root: BoundedXml.Node, dxfs: List<Format?>): List<Block> {
        val blocks = ArrayList<Block>()
        for (node in root.children.filter { it.name.local == "conditionalFormatting" }) {
            if (blocks.size >= MAX_BLOCKS) break
            val sqref = node.attr("sqref")?.trim()?.uppercase()?.split(Regex("\\s+"))
                ?.filter { it.isNotEmpty() }?.joinToString(" ") ?: continue
            val rules = ArrayList<Rule>()
            for (child in node.children.filter { it.name.local == "cfRule" }) {
                if (rules.size >= MAX_RULES_PER_BLOCK) break
                rules.add(parseRule(child, dxfs) ?: continue)
            }
            if (rules.isEmpty()) continue
            runCatching { Block(sqref, rules) }.getOrNull()?.let { blocks.add(it) }
        }
        // Priority is keyed across the whole sheet, and a foreign file may still repeat one. Keeping the first
        // block that uses a priority and dropping a later colliding one preserves the rest of the sheet, where
        // letting the invariant throw would lose the sheet entirely.
        val taken = HashSet<Int>()
        val kept = ArrayList<Block>(blocks.size)
        for (block in blocks) {
            if (block.rules.none { it.priority in taken }) {
                kept.add(block)
                for (rule in block.rules) taken.add(rule.priority)
            }
        }
        return kept
    }

    private fun parseRule(node: BoundedXml.Node, dxfs: List<Format?>): Rule? {
        val kind = Kind.of(node.attr("type") ?: "") ?: return null
        val priority = node.attr("priority")?.toIntOrNull() ?: return null
        val format = node.attr("dxfId")?.toIntOrNull()?.let { dxfs.getOrNull(it) }
        val formulas = node.children.filter { it.name.local == "formula" }.map { it.text }
            .let { list -> if (kind.isText) emptyList() else list }
        val operator = node.attr("operator")?.let { Operator.of(it) }
        val rank = node.attr("rank")?.toIntOrNull()
        val stopIfTrue = when (node.attr("stopIfTrue")?.lowercase()) {
            null -> null
            "1", "true" -> true
            else -> false
        }
        val text = node.attr("text")
        val timePeriod = node.attr("timePeriod")?.let { TimePeriod.of(it) }
        val aboveAverage = boolOr(node.attr("aboveAverage"))
        val equalAverage = boolOr(node.attr("equalAverage"))
        val stdDev = node.attr("stdDev")?.toIntOrNull()
        var percent = when (node.attr("percent")?.lowercase()) {
            null -> null
            "1", "true" -> true
            else -> false
        }
        val bottom = when (node.attr("bottom")?.lowercase()) {
            null -> null
            "1", "true" -> true
            else -> false
        }
        val thresholds = ArrayList<Threshold>()
        val colors = ArrayList<String>()
        node.children.firstOrNull { it.name.local == "colorScale" }?.let { scale ->
            for (cfvo in scale.children.filter { it.name.local == "cfvo" }) {
                val thresholdKind = ThresholdKind.of(cfvo.attr("type") ?: "") ?: return null
                thresholds.add(
                    runCatching { Threshold(thresholdKind, cfvo.attr("val")?.toDoubleOrNull()) }.getOrNull()
                        ?: return null,
                )
            }
            for (colour in scale.children.filter { it.name.local == "color" }) {
                colors.add(colour.attr("rgb")?.trim()?.uppercase()?.takeLast(6) ?: return null)
            }
        }
        node.children.firstOrNull { it.name.local == "dataBar" }?.let { bar ->
            for (cfvo in bar.children.filter { it.name.local == "cfvo" }) {
                val thresholdKind = ThresholdKind.of(cfvo.attr("type") ?: "") ?: return null
                thresholds.add(
                    runCatching { Threshold(thresholdKind, cfvo.attr("val")?.toDoubleOrNull()) }.getOrNull()
                        ?: return null,
                )
            }
            for (colour in coloursOf(bar)) colors.add(colour)
        }
        var iconSet: IconSet? = null
        var showValue: Boolean? = null
        var reverse: Boolean? = null
        node.children.firstOrNull { it.name.local == "iconSet" }?.let { icons ->
            for (cfvo in icons.children.filter { it.name.local == "cfvo" }) {
                val thresholdKind = ThresholdKind.of(cfvo.attr("type") ?: "") ?: return null
                thresholds.add(
                    runCatching { Threshold(thresholdKind, cfvo.attr("val")?.toDoubleOrNull()) }.getOrNull()
                        ?: return null,
                )
            }
            iconSet = IconSet.of(icons.attr("iconSet") ?: IconSet.DEFAULT.token) ?: return null
            showValue = boolOr(icons.attr("showValue"))
            reverse = boolOr(icons.attr("reverse"))
            // The icon set's own `percent` wins: on that kind the attribute lives on the child, not on cfRule.
            percent = boolOr(icons.attr("percent")) ?: percent
        }
        // Excel writes one <cfvo> per icon, the first pinned at the range minimum; this model stores the boundary
        // set only, so a leading no-op boundary is dropped. A leading boundary that is *not* the minimum cannot be
        // dropped without changing which icon a value receives, so such a rule is refused instead of guessed at.
        iconSet?.let { set ->
            if (thresholds.size == set.iconCount) {
                val first = thresholds.first()
                val atMinimum = first.kind == ThresholdKind.MIN ||
                    ((first.kind == ThresholdKind.PERCENT || first.kind == ThresholdKind.PERCENTILE) &&
                        first.value == 0.0)
                if (!atMinimum) return null
                thresholds.removeAt(0)
            }
        }
        return runCatching {
            Rule(
                kind = kind,
                priority = priority,
                stopIfTrue = stopIfTrue,
                operator = operator,
                formulas = formulas,
                rank = rank,
                percent = percent,
                bottom = bottom,
                thresholds = thresholds,
                colors = colors,
                format = format,
                // A text rule's formula is redundant with type/operator/text under this model's reading, and
                // echoing back a formula this writer did not derive would be a claim about its spelling.
                text = if (kind.isText) text else null,
                aboveAverage = aboveAverage,
                equalAverage = equalAverage,
                stdDev = stdDev,
                timePeriod = if (kind == Kind.TIME_PERIOD) timePeriod else null,
                iconSet = if (kind == Kind.ICON_SET) iconSet else null,
                showValue = showValue,
                reverse = reverse,
            )
        }.getOrNull()
    }

    /** ST_OnOff: `1`/`true`/`0`/`false`, case-insensitive; anything else is not a boolean and reads as absent. */
    private fun boolOr(raw: String?): Boolean? = when (raw?.lowercase()) {
        null -> null
        "1", "true" -> true
        "0", "false" -> false
        else -> null
    }

    /** `<color>` children of a `dataBar` are handled the same way as in a colour scale. */
    private fun coloursOf(node: BoundedXml.Node): List<String> =
        node.children.filter { it.name.local == "color" }
            .mapNotNull { it.attr("rgb")?.trim()?.uppercase()?.takeLast(6) }

    /**
     * `<conditionalFormatting>` blocks in `CT_Worksheet` order; `internDxf` is called once per distinct format so
     * the caller's `styles.xml` gets a matching `<dxfs>` index.
     */
    fun toXml(blocks: List<Block>, internDxf: (Format) -> Int): String = buildString {
        for (block in blocks) {
            append("""<conditionalFormatting sqref="${esc(block.sqref)}">""")
            for (rule in block.rules) append(ruleXml(rule, internDxf))
            append("</conditionalFormatting>")
        }
    }

    private fun ruleXml(rule: Rule, internDxf: (Format) -> Int): String = buildString {
        append("""<cfRule type="${rule.kind.token}"""")
        rule.format?.let { append(""" dxfId="${internDxf(it)}"""") }
        append(""" priority="${rule.priority}"""")
        rule.stopIfTrue?.let { append(""" stopIfTrue="${if (it) 1 else 0}"""") }
        rule.percent?.let { append(""" percent="${if (it) 1 else 0}"""") }
        rule.bottom?.let { append(""" bottom="${if (it) 1 else 0}"""") }
        rule.operator?.let { append(""" operator="${it.token}"""") }
        if (rule.kind.isText) {
            append(""" operator="${rule.kind.textOperator}"""")
            append(""" text="${esc(rule.text ?: "")}"""")
        }
        rule.aboveAverage?.let { append(""" aboveAverage="${if (it) 1 else 0}"""") }
        rule.equalAverage?.let { append(""" equalAverage="${if (it) 1 else 0}"""") }
        rule.stdDev?.let { append(""" stdDev="$it"""") }
        rule.rank?.let { append(""" rank="$it"""") }
        rule.timePeriod?.let { append(""" timePeriod="${it.token}"""") }
        val hasChildren = rule.formulas.isNotEmpty() || rule.thresholds.isNotEmpty()
        if (!hasChildren) {
            append("/>")
            return@buildString
        }
        append(">")
        for (formula in rule.formulas) append("<formula>${esc(formula)}</formula>")
        if (rule.thresholds.isNotEmpty()) {
            val element = when (rule.kind) {
                Kind.DATA_BAR -> "dataBar"
                Kind.ICON_SET -> "iconSet"
                else -> "colorScale"
            }
            append("<$element")
            if (rule.kind == Kind.ICON_SET) {
                // The set's name is the rule's subject, not a modifier, so it is always written; the three flags
                // are written only when they differ from the schema defaults (showValue true, percent true,
                // reverse false).
                append(""" iconSet="${(rule.iconSet ?: IconSet.DEFAULT).token}"""")
                rule.showValue?.let { append(""" showValue="${if (it) 1 else 0}"""") }
                rule.percent?.let { append(""" percent="${if (it) 1 else 0}"""") }
                rule.reverse?.let { append(""" reverse="${if (it) 1 else 0}"""") }
                append(">")
            } else {
                append(">")
            }
            for (threshold in rule.thresholds) {
                append("""<cfvo type="${threshold.kind.token}"""")
                threshold.value?.let { append(""" val="${number(it)}"""") }
                append("/>")
            }
            for (color in rule.colors) append("""<color rgb="FF${color.uppercase()}"/>""")
            append("</$element>")
        }
        append("</cfRule>")
    }
}
