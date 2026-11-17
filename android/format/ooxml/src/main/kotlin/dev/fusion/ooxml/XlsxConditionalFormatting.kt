// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Conditional formatting: `<conditionalFormatting sqref>` (ECMA-376 Part 1 §18.3.1.18) with its `<cfRule>`
 * children (§18.3.1.10) and the differential formats those rules point at — the `<dxfs>` table in `styles.xml`
 * (§18.8.3) and its `<dxf>` entries (§18.8.3.2). Only the element sections are cited; the complex-type
 * subsections are named, never numbered, because a wrong number is worse than none.
 *
 * Modelled: five rule kinds whose whole content this writer can state — `cellIs`, `expression`, `top10`,
 * `colorScale` and `dataBar` — the `priority` that must be unique per sheet, `stopIfTrue`, the cellIs operators,
 * formula text, thresholds (`cfvo`), colours, and a bounded differential format (bold/italic/strike/underline,
 * font colour and a solid fill).
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
 * Not modelled, and not claimed: `dxf` fonts' families and sizes, number formats, borders, alignment and
 * protection inside a dxf; `iconSet`, `containsText`, `notContainsText`, `beginsWith`, `endsWith`, `timePeriod`,
 * `aboveAverage`, `duplicateValues`, `uniqueValues`, `containsBlanks`, `notContainsBlanks`, `containsErrors`,
 * `notContainsErrors`; `text` and `timePeriod` attributes; `equalAverage`/`stdDev`; `<extLst>`; and the
 * evaluation itself — whether Excel highlights a cell is not something a file can state.
 */
object XlsxConditionalFormatting {

    /** Self-imposed model bounds, not schema bounds. */
    const val MAX_BLOCKS = 1024
    const val MAX_RULES_PER_BLOCK = 64
    const val MAX_FORMULA = 8_192
    const val MAX_PRIORITY = 1_000_000
    const val MAX_SQREF_TOKENS = 64

    /** Rule kinds this model can write completely. */
    enum class Kind(val token: String) {
        CELL_IS("cellIs"),
        EXPRESSION("expression"),
        TOP10("top10"),
        COLOR_SCALE("colorScale"),
        DATA_BAR("dataBar"),
        ;

        /** Kinds whose only visual is a differential format, so a rule of this kind must carry one. */
        val needsFormat: Boolean get() = this == CELL_IS || this == EXPRESSION || this == TOP10

        companion object {
            fun of(token: String): Kind? = values().firstOrNull { it.token == token }
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
     * A bounded `<dxf>`: a font decoration block and/or a solid fill. Both halves are optional, but an empty
     * format is refused because it would point a rule at a differential format that changes nothing.
     */
    data class Format(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val strike: Boolean = false,
        val underline: Boolean = false,
        val fontRgb: String? = null,
        val fillRgb: String? = null,
    ) {
        init {
            require(bold || italic || strike || underline || fontRgb != null || fillRgb != null) {
                "An empty differential format is not written"
            }
            fontRgb?.let { require(HEX6.matches(it)) { "A font colour must be six hex digits: $it" } }
            fillRgb?.let { require(HEX6.matches(it)) { "A fill colour must be six hex digits: $it" } }
        }

        /** The `<font>` half is written only when it says something; the fill half likewise. */
        val hasFont: Boolean get() = bold || italic || strike || underline || fontRgb != null
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
        val percent: Boolean? = null,
        val bottom: Boolean? = null,
        val thresholds: List<Threshold> = emptyList(),
        val colors: List<String> = emptyList(),
        val format: Format? = null,
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
            if (kind.needsFormat) {
                require(format != null) {
                    "A ${kind.token} rule must carry a differential format, or it changes no cell"
                }
            } else {
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

    /** `<dxf>` for one format; the `CT_Dxf` sequence puts `font` before `fill`, which is the order written here. */
    fun dxfToXml(format: Format): String = buildString {
        append("<dxf>")
        if (format.hasFont) {
            append("<font>")
            if (format.bold) append("<b/>")
            if (format.italic) append("<i/>")
            if (format.strike) append("<strike/>")
            if (format.underline) append("<u/>")
            format.fontRgb?.let { append("""<color rgb="FF${it.uppercase()}"/>""") }
            append("</font>")
        }
        format.fillRgb?.let {
            append("""<fill><patternFill patternType="solid">""")
            append("""<fgColor rgb="FF${it.uppercase()}"/><bgColor indexed="64"/>""")
            append("""</patternFill></fill>""")
        }
        append("</dxf>")
    }

    /** Reads the `<dxfs>` table; a `<dxf>` this model cannot hold is kept as `null` so dxfIds stay aligned. */
    fun parseDxfs(stylesRoot: BoundedXml.Node): List<Format?> {
        val node = stylesRoot.children.firstOrNull { it.name.local == "dxfs" } ?: return emptyList()
        return node.children.filter { it.name.local == "dxf" }.map { parseDxf(it) }
    }

    private fun parseDxf(node: BoundedXml.Node): Format? {
        val font = node.children.firstOrNull { it.name.local == "font" }
        val fill = node.children.firstOrNull { it.name.local == "fill" }
        val color = font?.children?.firstOrNull { it.name.local == "color" }?.attr("rgb")
        // A dxf solid fill is written with <fgColor> by Excel and with <bgColor> by other writers (openpyxl does
        // exactly that), so both are accepted; the foreground wins when a file carries both.
        val pattern = fill?.children?.firstOrNull { it.name.local == "patternFill" }
        val fillRgb = pattern?.children?.firstOrNull { it.name.local == "fgColor" }?.attr("rgb")
            ?: pattern?.children?.firstOrNull { it.name.local == "bgColor" }?.attr("rgb")
        fun rgb(raw: String?): String? {
            val value = raw?.trim() ?: return null
            val hex = when (value.length) {
                6 -> value
                8 -> value.substring(2)
                else -> return null
            }
            return if (HEX6.matches(hex)) hex.uppercase() else null
        }
        return runCatching {
            Format(
                bold = font?.children?.any { it.name.local == "b" } == true,
                italic = font?.children?.any { it.name.local == "i" } == true,
                strike = font?.children?.any { it.name.local == "strike" } == true,
                underline = font?.children?.any { it.name.local == "u" } == true,
                fontRgb = rgb(color),
                fillRgb = rgb(fillRgb),
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
        val operator = node.attr("operator")?.let { Operator.of(it) }
        val rank = node.attr("rank")?.toIntOrNull()
        val stopIfTrue = when (node.attr("stopIfTrue")?.lowercase()) {
            null -> null
            "1", "true" -> true
            else -> false
        }
        val percent = when (node.attr("percent")?.lowercase()) {
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
            )
        }.getOrNull()
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
        rule.rank?.let { append(""" rank="$it"""") }
        val hasChildren = rule.formulas.isNotEmpty() || rule.thresholds.isNotEmpty()
        if (!hasChildren) {
            append("/>")
            return@buildString
        }
        append(">")
        for (formula in rule.formulas) append("<formula>${esc(formula)}</formula>")
        if (rule.thresholds.isNotEmpty()) {
            val element = if (rule.kind == Kind.DATA_BAR) "dataBar" else "colorScale"
            append("<$element>")
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
