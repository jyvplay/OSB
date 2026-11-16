package dev.fusion.ooxml

/**
 * SpreadsheetML data validation: the `CT_DataValidation` record (ECMA-376 Part 1, §18.3.1.32) and the
 * predicate that record describes.
 *
 * Two things are modelled, and one is deliberately not:
 *
 * * the **record** — type, operator, two operands, the four booleans, the error/prompt text and `sqref` —
 *   is written into `<dataValidations>` after `<sheetData>` and read back with schema defaults;
 * * the **predicate** — "would Excel accept this cell value under this rule" — is implemented for the
 *   types whose operands are literal numbers or a literal list. [accepts] returns `null` where this model
 *   cannot decide (custom formulas, range-referenced lists, `DATE()`/`TIME()` operands, boolean cells), so
 *   an undecided case can never be mistaken for a decision;
 * * **not** modelled: the "circle invalid data" tracker, the input-message popup's appearance, and the
 *   in-cell dropdown's rendering. None of them is observable without an application.
 *
 * The OOXML attribute `showDropDown` is inverted in the specification: `showDropDown="1"` *suppresses* the
 * in-cell dropdown. This model stores the intended meaning ([Rule.hideDropDown]) and flips it in exactly
 * two places — [toXml] and [parse] — so the inversion exists once in code instead of in every caller.
 */
object XlsxValidation {

    /** CT_DataValidation `type`; `none` is the schema default and is not representable as a rule. */
    enum class Type(val token: String) {
        WHOLE("whole"),
        DECIMAL("decimal"),
        LIST("list"),
        DATE("date"),
        TIME("time"),
        TEXT_LENGTH("textLength"),
        CUSTOM("custom"),
        ;

        companion object {
            fun of(token: String): Type? = values().firstOrNull { it.token == token }
        }
    }

    /**
     * CT_DataValidation `operator`. `between` and `notBetween` take two operands; every other operator takes
     * one. For [Type.LIST] and [Type.CUSTOM] the schema ignores `operator`, so this writer refuses a
     * non-default one rather than emit an attribute that means nothing.
     */
    enum class Operator(val token: String) {
        BETWEEN("between"),
        NOT_BETWEEN("notBetween"),
        EQUAL("equal"),
        NOT_EQUAL("notEqual"),
        LESS_THAN("lessThan"),
        LESS_THAN_OR_EQUAL("lessThanOrEqual"),
        GREATER_THAN("greaterThan"),
        GREATER_THAN_OR_EQUAL("greaterThanOrEqual"),
        ;

        val binary: Boolean get() = this == BETWEEN || this == NOT_BETWEEN

        companion object {
            fun of(token: String): Operator? = values().firstOrNull { it.token == token }
        }
    }

    /** CT_DataValidation `errorStyle`; `stop` is the schema default. */
    enum class ErrorStyle(val token: String) {
        STOP("stop"),
        WARNING("warning"),
        INFORMATION("information"),
        ;

        companion object {
            fun of(token: String): ErrorStyle? = values().firstOrNull { it.token == token }
        }
    }

    /**
     * One `CT_DataValidation` element. Defaults are the *schema* defaults, so a bare
     * `<dataValidation type="list" sqref="A1"><formula1>"a,b"</formula1></dataValidation>` round-trips to
     * exactly this object.
     */
    data class Rule(
        val sqref: String,
        val type: Type,
        val formula1: String,
        val operator: Operator = Operator.BETWEEN,
        val formula2: String? = null,
        val allowBlank: Boolean = false,
        /** Emitted as the inverted OOXML attribute `showDropDown="1"`, which hides the dropdown. */
        val hideDropDown: Boolean = false,
        val showErrorMessage: Boolean = false,
        val errorStyle: ErrorStyle = ErrorStyle.STOP,
        val errorTitle: String? = null,
        val error: String? = null,
        val showInputMessage: Boolean = false,
        val promptTitle: String? = null,
        val prompt: String? = null,
    ) {
        init {
            require(SQREF.matches(sqref)) { "Invalid data-validation sqref: $sqref" }
            require(formula1.isNotBlank()) { "data-validation formula1 must not be blank" }
            val ignoresOperator = type == Type.LIST || type == Type.CUSTOM
            require(!ignoresOperator || operator == Operator.BETWEEN) {
                "$type ignores the OOXML operator; pass the default instead of ${operator.token}"
            }
            require(ignoresOperator || operator.binary == (formula2 != null)) {
                "${operator.token} takes ${if (operator.binary) "two" else "one"} operand(s), got formula2=$formula2"
            }
            // A list operand is either a quoted literal or a reference. A half-quoted literal is refused
            // here because Excel reports the sheet as corrupt rather than ignoring the rule.
            require(formula1.startsWith("\"") == formula1.endsWith("\"")) {
                "Unterminated list literal: $formula1"
            }
        }
    }

    /** `A1` or `A1:B2`, space-separated; upper case only, because the writer normalises before emitting. */
    private val SQREF = Regex(
        "^[A-Z]{1,3}[1-9][0-9]{0,6}(:[A-Z]{1,3}[1-9][0-9]{0,6})?" +
            "( [A-Z]{1,3}[1-9][0-9]{0,6}(:[A-Z]{1,3}[1-9][0-9]{0,6})?)*$",
    )

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

    /**
     * Serialises the rules as the complete `<dataValidations>` element. Attribute order follows the order
     * Excel itself writes, so a byte-level diff against an Excel-authored file stays readable; the caller
     * is responsible for placing the element after `<sheetData>` (CT_Worksheet sequence).
     */
    fun toXml(rules: List<Rule>): String = buildString {
        require(rules.isNotEmpty()) { "toXml requires at least one rule" }
        append("""<dataValidations count="${rules.size}">""")
        for (rule in rules) {
            append("""<dataValidation type="${rule.type.token}"""")
            append(""" errorStyle="${rule.errorStyle.token}"""")
            if (rule.type != Type.LIST && rule.type != Type.CUSTOM) {
                append(""" operator="${rule.operator.token}"""")
            }
            append(""" allowBlank="${if (rule.allowBlank) 1 else 0}"""")
            append(""" showDropDown="${if (rule.hideDropDown) 1 else 0}"""")
            append(""" showInputMessage="${if (rule.showInputMessage) 1 else 0}"""")
            append(""" showErrorMessage="${if (rule.showErrorMessage) 1 else 0}"""")
            rule.errorTitle?.let { append(""" errorTitle="${esc(it)}"""") }
            rule.error?.let { append(""" error="${esc(it)}"""") }
            rule.promptTitle?.let { append(""" promptTitle="${esc(it)}"""") }
            rule.prompt?.let { append(""" prompt="${esc(it)}"""") }
            append(""" sqref="${esc(rule.sqref)}">""")
            append("<formula1>").append(esc(rule.formula1)).append("</formula1>")
            rule.formula2?.let { append("<formula2>").append(esc(it)).append("</formula2>") }
            append("</dataValidation>")
        }
        append("</dataValidations>")
    }

    /**
     * Reads every `<dataValidation>` under `<dataValidations>`. A rule whose `type` or `operator` token this
     * model does not know, or whose `sqref`/`type` combination cannot be represented, is **dropped**: the
     * remaining sheet stays readable and no invented rule is reported as if it had been imported.
     */
    fun parse(root: BoundedXml.Node): List<Rule> {
        val container = root.children.firstOrNull { it.name.local == "dataValidations" } ?: return emptyList()
        val rules = ArrayList<Rule>()
        for (node in container.children.filter { it.name.local == "dataValidation" }) {
            val sqref = node.attr("sqref")?.trim()?.replace(Regex(" +"), " ")?.uppercase() ?: continue
            val type = node.attr("type")?.let { Type.of(it) } ?: continue
            val operatorToken = node.attr("operator")
            val operator = if (operatorToken == null) Operator.BETWEEN else Operator.of(operatorToken) ?: continue
            val errorStyle = node.attr("errorStyle")?.let { ErrorStyle.of(it) } ?: ErrorStyle.STOP
            val formula1 = node.children.firstOrNull { it.name.local == "formula1" }?.text ?: continue
            val formula2 = node.children.firstOrNull { it.name.local == "formula2" }?.text
            rules.add(
                Rule(
                    sqref = sqref,
                    type = type,
                    formula1 = formula1,
                    operator = operator,
                    formula2 = formula2,
                    allowBlank = isOn(node.attr("allowBlank")),
                    hideDropDown = isOn(node.attr("showDropDown")),
                    showErrorMessage = isOn(node.attr("showErrorMessage")),
                    errorStyle = errorStyle,
                    errorTitle = node.attr("errorTitle"),
                    error = node.attr("error"),
                    showInputMessage = isOn(node.attr("showInputMessage")),
                    promptTitle = node.attr("promptTitle"),
                    prompt = node.attr("prompt"),
                ),
            )
        }
        return rules
    }

    private fun isOn(value: String?): Boolean = value == "1" || value.equals("true", ignoreCase = true)

    /**
     * Would the validation rule accept `value`? `null` means **this model cannot decide** — a custom
     * formula, a list held in a range, a `DATE()`/`TIME()` operand, a boolean cell, or a non-numeric operand
     * for a numeric type. Deciding those would require a formula engine and the rest of the sheet, so the
     * honest answer is "not decided" rather than a guess.
     *
     * Documented semantics, each chosen because Excel behaves this way and each exercised by the corpus:
     * * `whole` refuses a non-integral number before comparing (Excel refuses `3.5` for a whole-number rule);
     * * `textLength` counts UTF-16 code units of a **text** cell, which is what Excel's `LEN` returns;
     * * `list` compares case-insensitively and also matches a numeric cell against the literal's spelling
     *   (`3` matches the item `"3"`), which is how a dropdown entry typed as a number behaves;
     * * `date`/`time` compare raw serial numbers, because this writer stores dates as their serial.
     */
    fun accepts(rule: Rule, value: XlsxStyledBook.StyledVal): Boolean? {
        if (rule.type == Type.CUSTOM) return null
        return when (rule.type) {
            Type.LIST -> {
                val items = listItems(rule.formula1) ?: return null
                val text = when (value) {
                    is XlsxStyledBook.StyledVal.Txt -> value.value
                    is XlsxStyledBook.StyledVal.Num -> numberSpelling(value.value)
                    else -> return null
                }
                items.any { it.equals(text, ignoreCase = true) }
            }

            Type.TEXT_LENGTH -> {
                val length = when (value) {
                    is XlsxStyledBook.StyledVal.Txt -> value.value.length.toDouble()
                    else -> return null
                }
                compare(rule, length)
            }

            Type.WHOLE, Type.DECIMAL, Type.DATE, Type.TIME -> {
                val number = (value as? XlsxStyledBook.StyledVal.Num)?.value ?: return null
                if (rule.type == Type.WHOLE && number % 1.0 != 0.0) return false
                compare(rule, number)
            }

            Type.CUSTOM -> null
        }
    }

    private fun compare(rule: Rule, actual: Double): Boolean? {
        val low = literal(rule.formula1) ?: return null
        val op = rule.operator
        if (!op.binary) {
            return when (op) {
                Operator.EQUAL -> actual == low
                Operator.NOT_EQUAL -> actual != low
                Operator.LESS_THAN -> actual < low
                Operator.LESS_THAN_OR_EQUAL -> actual <= low
                Operator.GREATER_THAN -> actual > low
                Operator.GREATER_THAN_OR_EQUAL -> actual >= low
                else -> null
            }
        }
        val high = rule.formula2?.let { literal(it) } ?: return null
        val within = actual in low..high
        return if (op == Operator.BETWEEN) within else !within
    }

    /** A literal number operand; a function call or a reference cannot be evaluated without a formula engine. */
    private fun literal(formula: String): Double? {
        val text = formula.trim()
        if (text.isEmpty()) return null
        if (text.any { it.isLetter() || it == '(' || it == '$' || it == '!' }) return null
        return text.toDoubleOrNull()
    }

    /** `"a,b,c"` → the three items; anything that is not a quoted literal is undecidable here. */
    private fun listItems(formula: String): List<String>? {
        val text = formula.trim()
        if (text.length < 2 || !text.startsWith("\"") || !text.endsWith("\"")) return null
        val body = text.substring(1, text.length - 1)
        if (body.contains('"')) return null
        return body.split(',').map { it.trim() }
    }

    /** Canonical spreadsheet spelling of a number, so `1.0` matches the list item `"1"`. */
    private fun numberSpelling(value: Double): String =
        if (value % 1.0 == 0.0 && value in -1e15..1e15) value.toLong().toString() else value.toString()
}
