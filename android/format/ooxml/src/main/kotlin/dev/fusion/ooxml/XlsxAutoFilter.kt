package dev.fusion.ooxml

/**
 * AutoFilter: the `<autoFilter>` record (ECMA-376 Part 1, §18.3.1.2 with `CT_FilterColumn` §18.3.2.7,
 * `CT_Filters` §18.3.2.29 and `CT_CustomFilters` §18.3.2.24) and the row predicate the record describes.
 *
 * Modelled: the filtered range, one `filterColumn` per filtered column, the value list (`<filters>`) and the
 * one-or-two custom criteria (`<customFilters>` with their AND/OR join).
 *
 * **Not** modelled, and not claimed: the actual hiding of rows. Excel stores the filter and the *hidden row
 * flags* separately (`<row hidden="1"/>`); this writer never hides a row, so nothing here says a row
 * disappeared. Also not modelled: `top10`, `dynamicFilter`, `colorFilter`, `iconFilter`, `dateGroupItem`.
 *
 * The predicate [matches] decides a documented subset and returns `null` where the answer depends on things
 * this model does not have (blanks, collation order, a non-numeric operand against a number).
 */
object XlsxAutoFilter {

    /** CT_CustomFilterOperator. */
    enum class Operator(val token: String) {
        EQUAL("equal"),
        NOT_EQUAL("notEqual"),
        GREATER_THAN("greaterThan"),
        GREATER_THAN_OR_EQUAL("greaterThanOrEqual"),
        LESS_THAN("lessThan"),
        LESS_THAN_OR_EQUAL("lessThanOrEqual"),
        ;

        val ordering: Boolean get() = this != EQUAL && this != NOT_EQUAL

        companion object {
            fun of(token: String): Operator? = values().firstOrNull { it.token == token }
        }
    }

    /** One `<customFilter operator= val=>`. */
    data class Custom(val operator: Operator, val value: String) {
        init {
            require(value.isNotEmpty()) { "A custom filter operand must not be empty" }
        }
    }

    /**
     * One `<filterColumn colId= …>`. Exactly one of [values] and [custom] is present: a column shows either a
     * checked list or one/two criteria, never both, and the writer refuses the combination instead of writing
     * an element Excel would treat as corrupt.
     */
    data class Column(
        val colId: Int,
        val values: List<String>? = null,
        val custom: List<Custom>? = null,
        /** CT_CustomFilters `and`: `true` joins the two criteria with AND, `false` with OR. */
        val join: Boolean = false,
        /** CT_FilterColumn `hiddenButton`; schema default `false`, so `false` is not written. */
        val hiddenButton: Boolean = false,
        /** CT_FilterColumn `showButton`; schema default `true`, so `true` is not written. */
        val showButton: Boolean = true,
    ) {
        init {
            require(colId >= 0) { "colId must be zero-based and non-negative: $colId" }
            require((values != null) != (custom != null)) {
                "Exactly one of values/custom must be present (colId=$colId)"
            }
            values?.let { list ->
                require(list.isNotEmpty()) { "A <filters> list needs at least one value (colId=$colId)" }
                require(list.all { it.isNotEmpty() }) { "An empty filter value is not admissible (colId=$colId)" }
            }
            custom?.let { list ->
                require(list.size in 1..2) { "A <customFilters> needs one or two criteria, got ${list.size}" }
                require(list.size == 2 || !join) {
                    "The AND join needs two criteria; one criterion has nothing to join (colId=$colId)"
                }
            }
        }
    }

    /** `<autoFilter ref>` plus its column filters; no columns is a legal "filter row, nothing selected yet". */
    data class AutoFilter(val ref: String, val columns: List<Column> = emptyList()) {
        init {
            require(REF.matches(ref)) { "Invalid autoFilter ref: $ref" }
            require(columns.map { it.colId }.toSet().size == columns.size) {
                "Duplicate filterColumn for the same colId is not admissible"
            }
            val width = columnCount(ref)
            columns.forEach { require(it.colId < width) { "colId ${it.colId} outside the ${width}-column range $ref" } }
        }
    }

    private val REF = Regex("^[A-Z]{1,3}[1-9][0-9]{0,6}(:[A-Z]{1,3}[1-9][0-9]{0,6})?$")
    private val CELL = Regex("^([A-Z]{1,3})([1-9][0-9]{0,6})$")

    private fun columnIndex(label: String): Int {
        var column = 0
        for (ch in label) column = column * 26 + (ch - 'A' + 1)
        return column
    }

    /** Rows an applied filter excludes, and rows it cannot decide (which are left visible, not guessed). */
    data class Outcome(val hidden: Set<Int>, val undecided: Set<Int>)

    /**
     * Applies [filter] to a sheet's cells: the header row of the range is never hidden, and a data row is
     * excluded only when some column's predicate is *decidedly* false. A row with an undecidable cell in a
     * column that does not already exclude it is reported in [Outcome.undecided] and stays visible — guessing
     * would hide data the user can still see in Excel.
     *
     * This is what makes the record mean something: `<autoFilter>` alone hides nothing, so the caller feeds
     * [Outcome.hidden] to the writer's per-row `hidden="1"` flag.
     */
    fun evaluate(filter: AutoFilter, cells: List<XlsxStyledBook.StyledCell>): Outcome {
        val parts = filter.ref.split(':')
        val start = CELL.matchEntire(parts[0]) ?: return Outcome(emptySet(), emptySet())
        val firstRow = start.groupValues[2].toInt()
        val firstColumn = columnIndex(start.groupValues[1])
        val lastRow = parts.getOrNull(1)?.let { CELL.matchEntire(it)?.groupValues?.get(2)?.toInt() } ?: firstRow
        if (filter.columns.isEmpty() || lastRow <= firstRow) return Outcome(emptySet(), emptySet())
        val byRef = cells.associateBy { it.ref.uppercase() }
        val hidden = sortedSetOf<Int>()
        val undecided = sortedSetOf<Int>()
        for (row in firstRow + 1..lastRow) {
            var exclude = false
            var unknown = false
            for (column in filter.columns) {
                val ref = label(firstColumn + column.colId) + row
                when (matches(column, byRef[ref]?.value)) {
                    null -> unknown = true
                    true -> Unit
                    false -> exclude = true
                }
            }
            if (exclude) hidden.add(row) else if (unknown) undecided.add(row)
        }
        return Outcome(hidden, undecided)
    }

    private fun label(oneBased: Int): String = buildString {
        var remaining = oneBased
        while (remaining > 0) {
            val digit = (remaining - 1) % 26
            append(('A'.code + digit).toChar())
            remaining = (remaining - 1) / 26
        }
    }.reversed()

    /** Number of columns the range spans: `A1:D10` → 4, `A1` → 1. */
    fun columnCount(ref: String): Int {
        val parts = ref.split(':')
        if (parts.size == 1) return 1
        val start = CELL.matchEntire(parts[0])?.groupValues?.get(1) ?: return 1
        val end = CELL.matchEntire(parts[1])?.groupValues?.get(1) ?: return 1
        return (columnIndex(end) - columnIndex(start) + 1).coerceAtLeast(1)
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

    fun toXml(filter: AutoFilter): String = buildString {
        append("""<autoFilter ref="${esc(filter.ref)}"""")
        if (filter.columns.isEmpty()) {
            append("/>")
            return@buildString
        }
        append(">")
        for (column in filter.columns) {
            append("""<filterColumn colId="${column.colId}"""")
            if (column.hiddenButton) append(""" hiddenButton="1"""")
            if (!column.showButton) append(""" showButton="0"""")
            append(">")
            column.values?.let { values ->
                append("<filters>")
                for (value in values) append("""<filter val="${esc(value)}"/>""")
                append("</filters>")
            }
            column.custom?.let { custom ->
                append("<customFilters")
                if (custom.size == 2 && column.join) append(" and=\"1\"")
                append(">")
                for (criterion in custom) {
                    append("""<customFilter operator="${criterion.operator.token}" val="${esc(criterion.value)}"/>""")
                }
                append("</customFilters>")
            }
            append("</filterColumn>")
        }
        append("</autoFilter>")
    }

    /**
     * Reads `<autoFilter>`. A column whose criterion kind is not modelled (`top10`, `dynamicFilter`,
     * `colorFilter`, `iconFilter`) is dropped together with its column entry, and a `colId` outside the
     * declared range is dropped, rather than being reinterpreted as something this model can write back.
     */
    fun parse(root: BoundedXml.Node): AutoFilter? {
        val node = root.children.firstOrNull { it.name.local == "autoFilter" } ?: return null
        val ref = node.attr("ref")?.trim()?.uppercase() ?: return null
        if (!REF.matches(ref)) return null
        val width = columnCount(ref)
        val columns = ArrayList<Column>()
        for (child in node.children.filter { it.name.local == "filterColumn" }) {
            val colId = child.attr("colId")?.toIntOrNull() ?: continue
            if (colId < 0 || colId >= width) continue
            val filters = child.children.firstOrNull { it.name.local == "filters" }
            val customFilters = child.children.firstOrNull { it.name.local == "customFilters" }
            val hiddenButton = isOn(child.attr("hiddenButton"))
            val showButton = child.attr("showButton")?.let { isOn(it) } ?: true
            if (filters != null) {
                // `blank`/`calendarType` and `<dateGroupItem>` change what the list means; a column that uses
                // them is dropped instead of being read as "exactly these values".
                if (filters.attributes.isNotEmpty()) continue
                if (filters.children.any { it.name.local != "filter" }) continue
                val values = filters.children.filter { it.name.local == "filter" }
                    .mapNotNull { it.attr("val") }
                    .filter { it.isNotEmpty() }
                if (values.isEmpty()) continue
                columns.add(Column(colId, values = values, hiddenButton = hiddenButton, showButton = showButton))
            } else if (customFilters != null) {
                val custom = customFilters.children.filter { it.name.local == "customFilter" }.mapNotNull { item ->
                    val operator = item.attr("operator")?.let { Operator.of(it) } ?: return@mapNotNull null
                    val value = item.attr("val")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    Custom(operator, value)
                }
                if (custom.isEmpty() || custom.size > 2) continue
                val join = isOn(customFilters.attr("and"))
                if (custom.size == 2 || !join) {
                    columns.add(Column(colId, custom = custom, join = join && custom.size == 2,
                        hiddenButton = hiddenButton, showButton = showButton))
                }
            }
        }
        return AutoFilter(ref, columns)
    }

    private fun isOn(value: String?): Boolean = value == "1" || value.equals("true", ignoreCase = true)

    /**
     * Would the row survive this column's filter? `null` means this model cannot decide:
     *
     * * a **blank** cell needs Excel's separate blanks handling, which is not modelled;
     * * an **ordering** comparison against text depends on a collation this model does not have (Excel's own
     *   ordering for text is locale-dependent, so a lexicographic answer here would be a guess);
     * * a **custom operand** that is neither a number nor a pattern this model understands.
     *
     * Text comparisons are case-insensitive, as Excel's are, and `*`/`?` are wildcards in both the checked
     * list and the equality criteria. `~` escaping is **not** modelled, so a literal `*` in a value cannot be
     * expressed; that is a stated loss, not a silent one.
     */
    fun matches(column: Column, value: XlsxStyledBook.StyledVal?): Boolean? {
        if (value == null) return null
        val text = when (value) {
            is XlsxStyledBook.StyledVal.Txt -> value.value
            is XlsxStyledBook.StyledVal.Num -> numberSpelling(value.value)
            else -> return null
        }
        column.values?.let { list -> return list.any { globMatch(it, text) } }
        val criteria = column.custom ?: return null
        val results = criteria.map { criterion -> decide(criterion, value, text) }
        if (results.any { it == null }) return null
        val flags = results.map { it == true }
        return if (criteria.size == 1) flags[0] else if (column.join) flags[0] && flags[1] else flags[0] || flags[1]
    }

    private fun decide(criterion: Custom, value: XlsxStyledBook.StyledVal, text: String): Boolean? {
        val operand = criterion.value
        val number = operand.trim().toDoubleOrNull()
        if (value is XlsxStyledBook.StyledVal.Num) {
            val expected = number ?: return null
            val actual = value.value
            return when (criterion.operator) {
                Operator.GREATER_THAN -> actual > expected
                Operator.GREATER_THAN_OR_EQUAL -> actual >= expected
                Operator.LESS_THAN -> actual < expected
                Operator.LESS_THAN_OR_EQUAL -> actual <= expected
                Operator.EQUAL -> actual == expected
                Operator.NOT_EQUAL -> actual != expected
            }
        }
        if (criterion.operator.ordering) return null
        val hit = globMatch(operand, text)
        return if (criterion.operator == Operator.EQUAL) hit else !hit
    }

    /** `*` matches any run, `?` matches one character, everything else is literal; case-insensitive. */
    private fun globMatch(pattern: String, text: String): Boolean {
        val p = pattern.lowercase()
        val t = text.lowercase()
        var pi = 0
        var ti = 0
        var star = -1
        var resume = 0
        while (ti < t.length) {
            if (pi < p.length && (p[pi] == '?' || p[pi] == t[ti])) {
                pi++
                ti++
            } else if (pi < p.length && p[pi] == '*') {
                star = pi
                resume = ti
                pi++
            } else if (star >= 0) {
                pi = star + 1
                resume++
                ti = resume
            } else {
                return false
            }
        }
        while (pi < p.length && p[pi] == '*') pi++
        return pi == p.length
    }

    /** Canonical spreadsheet spelling of a number, so the checked value `3` matches the number `3`. */
    private fun numberSpelling(value: Double): String =
        if (value % 1.0 == 0.0 && value in -1e15..1e15) value.toLong().toString() else value.toString()
}
