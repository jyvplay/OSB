// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Manual page breaks: `<rowBreaks>` (§18.3.1.84) and `<colBreaks>` (§18.3.1.11) with their `<brk>` children
 * (`CT_Brk` §18.3.2.4).
 *
 * Modelled: the break position (a row or column number, one-based), whether the user placed it, and the
 * optional `min`/`max` span the break covers on the *other* axis. Two things here are real constraints rather
 * than taste: a break may not sit after the last row or column (it would split nothing), and Excel stops
 * honouring breaks past 1026 per sheet, so a longer list is refused instead of written and ignored.
 *
 * Not modelled, and not claimed: `pt` (the footer margin in points), automatic breaks that Excel computes from
 * page size at print time, and the printed result itself. Whether a printer actually starts a new page at
 * `id` is unobservable from the file.
 */
object XlsxBreaks {

    /** Excel's documented per-sheet ceiling; a 1027th break is written by nothing and honoured by nothing. */
    const val MAX_BREAKS = 1026

    const val MAX_ROW = 1_048_576
    const val MAX_COLUMN = 16_384

    /**
     * One `<brk>`. `id` is the row (for `<rowBreaks>`) or column (for `<colBreaks>`) *after* which the page
     * ends; `min`/`max` are the inclusive span on the other axis, written only when the caller declares them
     * (Excel itself often writes neither, and the reader then sees the whole span).
     */
    data class Brk(val id: Int, val manual: Boolean = true, val min: Int? = null, val max: Int? = null) {
        init {
            require(id in 1 until MAX_ROW) { "A break id must be in 1..${MAX_ROW - 1}: $id" }
            if (min != null && max != null) require(min <= max) { "A break span must have min <= max: $min..$max" }
            for ((name, value) in listOf("min" to min, "max" to max)) {
                value?.let { require(it >= 0) { "Break $name may not be negative: $it" } }
            }
        }
    }

    /**
     * A sheet's breaks. `<colBreaks>` ids obey the same 1..1048575 bound as rows because [Brk] is shared, so
     * the column-specific upper bound is enforced here, where the axis is known.
     */
    data class Spec(
        val rows: List<Brk> = emptyList(),
        val cols: List<Brk> = emptyList(),
    ) {
        init {
            require(rows.isNotEmpty() || cols.isNotEmpty()) { "An empty breaks spec is not written" }
            for ((name, list) in listOf("rows" to rows, "cols" to cols)) {
                require(list.size <= MAX_BREAKS) {
                    "At most $MAX_BREAKS $name breaks per sheet, got ${list.size}"
                }
                for (i in list.indices) {
                    if (i > 0) require(list[i].id > list[i - 1].id) {
                        "Break ids must be strictly ascending without duplicates: " +
                            "${list.map { it.id }}"
                    }
                }
            }
            cols.forEach { brk ->
                require(brk.id in 1 until MAX_COLUMN) {
                    "A column break must be in 1..${MAX_COLUMN - 1}: ${brk.id}"
                }
            }
            // A break's span runs along the *other* axis: columns for a row break, rows for a column break.
            requireSpans(rows, MAX_COLUMN, "row")
            requireSpans(cols, MAX_ROW, "column")
        }

        val manualCount: Int get() = rows.count { it.manual } + cols.count { it.manual }
    }

    private fun requireSpans(list: List<Brk>, limit: Int, axis: String) {
        for (brk in list) {
            for ((name, value) in listOf("min" to brk.min, "max" to brk.max)) {
                value?.let {
                    require(it in 0..limit) {
                        "A $axis break span must lie in 0..$limit: $name=$it on break ${brk.id}"
                    }
                }
            }
        }
    }

    private fun container(name: String, breaks: List<Brk>): String = buildString {
        append("""<$name count="${breaks.size}" manualBreakCount="${breaks.count { it.manual }}">""")
        for (brk in breaks) {
            append("""<brk id="${brk.id}"""")
            brk.min?.let { append(""" min="$it"""") }
            brk.max?.let { append(""" max="$it"""") }
            append(""" man="${if (brk.manual) 1 else 0}"/>""")
        }
        append("</$name>")
    }

    /** `<rowBreaks>` then `<colBreaks>` — `CT_Worksheet`'s order, and each container omits nothing it declares. */
    fun toXml(spec: Spec): String = buildString {
        if (spec.rows.isNotEmpty()) append(container("rowBreaks", spec.rows))
        if (spec.cols.isNotEmpty()) append(container("colBreaks", spec.cols))
    }

    /**
     * Reads both containers. Out-of-range positions, repeated ids and spans past the grid are dropped rather
     * than clamped into a break the file did not declare; the list is sorted so a hand-written file with
     * unsorted children reads back in the order this writer emits.
     */
    fun parse(root: BoundedXml.Node): Spec? {
        val rows = readAxis(root, "rowBreaks", idLimit = MAX_ROW, spanLimit = MAX_COLUMN)
        val cols = readAxis(root, "colBreaks", idLimit = MAX_COLUMN, spanLimit = MAX_ROW)
        if (rows.isEmpty() && cols.isEmpty()) return null
        return Spec(rows = rows, cols = cols)
    }

    private fun readAxis(root: BoundedXml.Node, name: String, idLimit: Int, spanLimit: Int): List<Brk> {
        val node = root.children.firstOrNull { it.name.local == name } ?: return emptyList()
        val seen = LinkedHashMap<Int, Brk>()
        for (child in node.children.filter { it.name.local == "brk" }) {
            if (seen.size >= MAX_BREAKS) break
            val id = child.attr("id")?.trim()?.toIntOrNull() ?: continue
            // The id bound is per axis: a row break cannot sit past the last row, a column break past the
            // last column. Filtering here (rather than in Spec) keeps a foreign file from throwing on read.
            if (id !in 1 until idLimit) continue
            if (seen.containsKey(id)) continue
            val manual = when (child.attr("man")?.lowercase()) {
                null -> true
                "1", "true" -> true
                else -> false
            }
            val min = child.attr("min")?.trim()?.toIntOrNull()?.takeIf { it in 0..spanLimit }
            val max = child.attr("max")?.trim()?.toIntOrNull()?.takeIf { it in 0..spanLimit }
            seen[id] = Brk(id = id, manual = manual, min = min, max = max)
        }
        return seen.values.sortedBy { it.id }
    }
}
