// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Worksheet outlining: `<sheetPr><outlinePr/></sheetPr>` (`CT_OutlinePr`, ECMA-376 Part 1) and the
 * `outlineLevel`/`collapsed` attributes that `<row>` and `<col>` carry (CT_Row §18.3.1.73,
 * CT_Col §18.3.1.16).
 *
 * Three things here are real constraints rather than taste:
 *
 * - **`outlinePr` sits between `tabColor` and `pageSetUpPr`.** CT_SheetPr is a sequence, and Excel repairs a
 *   sheet part whose children are out of order; two producers were fixing exactly this in 2026
 *   (rust_xlsxwriter #184, exceljs #1348). [outlinePrXml] returns a fragment and the caller that owns
 *   `<sheetPr>` places it, so the order is asserted once, in one place.
 * - **Levels are 1..7.** Excel's UI stops nesting there, and a level outside it is written by nothing and
 *   honoured by nothing; level 0 is spelled by *omitting* the attribute, so a declared 0 is refused.
 * - **A `collapsed` marker with no group hides nothing.** `collapsed="1"` means "the group beside me is
 *   collapsed"; with no grouped row or column in the sheet it is a dangling flag, so it is refused.
 *
 * Not modelled, and not claimed: what the UI does when a group is clicked, whether Excel's `+`/`-` buttons are
 * drawn, or how a collapse interacts with a print area. Grouping does **not** imply hiding: a collapsed group's
 * members are `hidden="1"` in the file, and [Levels] deliberately keeps that separate from `collapsed` — the
 * writer states both or neither, exactly as the file must.
 */
object XlsxOutline {

    /** Excel's nesting ceiling: `outlineLevel` is 0..7 in the schema, and 0 is "not in a group". */
    const val MAX_LEVEL = 7

    /**
     * `outlinePr`. The schema defaults are `summaryBelow = true`, `summaryRight = true`,
     * `applyStyles = false`; a settings object equal to those writes **no element at all**, so a sheet that
     * never mentions outlining keeps its pre-R99 bytes.
     */
    data class Settings(
        val summaryBelow: Boolean = true,
        val summaryRight: Boolean = true,
        val applyStyles: Boolean = false,
    ) {
        /** True when writing this would add nothing but a redundant element. */
        val isDefault: Boolean get() = summaryBelow && summaryRight && !applyStyles
    }

    /**
     * The grouping of one sheet: which rows and columns sit at which depth, and which of them carry the
     * `collapsed` marker. Rows and columns are one-based, as everywhere else in this model.
     */
    data class Levels(
        val rows: Map<Int, Int> = emptyMap(),
        val cols: Map<Int, Int> = emptyMap(),
        val collapsedRows: Set<Int> = emptySet(),
        val collapsedCols: Set<Int> = emptySet(),
    ) {
        init {
            rows.forEach { (row, level) ->
                require(row in 1..1_048_576) { "Outline row $row outside 1..1048576" }
                require(level in 1..MAX_LEVEL) { "Outline level $level outside 1..$MAX_LEVEL (0 is 'not grouped')" }
            }
            cols.forEach { (col, level) ->
                require(col in 1..16_384) { "Outline column $col outside 1..16384" }
                require(level in 1..MAX_LEVEL) { "Outline level $level outside 1..$MAX_LEVEL (0 is 'not grouped')" }
            }
            for (row in collapsedRows) {
                require(row in 1..1_048_576) { "Collapsed row $row outside 1..1048576" }
            }
            for (col in collapsedCols) {
                require(col in 1..16_384) { "Collapsed column $col outside 1..16384" }
            }
            require(collapsedRows.isEmpty() && collapsedCols.isEmpty() || rows.isNotEmpty() || cols.isNotEmpty()) {
                "A collapsed marker with no grouped row or column hides nothing"
            }
        }

        val isEmpty: Boolean get() = rows.isEmpty() && cols.isEmpty() && collapsedRows.isEmpty() && collapsedCols.isEmpty()

        /** The row indexes this model touches, so the caller emits exactly those `<row>` elements. */
        val rowIndexes: Set<Int> get() = rows.keys + collapsedRows

        /** The column indexes this model touches, so the caller emits exactly those `<col>` elements. */
        val columnIndexes: Set<Int> get() = cols.keys + collapsedCols
    }

    /** `<outlinePr/>` or null when [settings] is the schema default, in CT_SheetPr's child position. */
    fun outlinePrXml(settings: Settings?): String? {
        if (settings == null || settings.isDefault) return null
        return buildString {
            append("<outlinePr")
            if (!settings.summaryBelow) append(""" summaryBelow="0"""")
            if (!settings.summaryRight) append(""" summaryRight="0"""")
            if (settings.applyStyles) append(""" applyStyles="1"""")
            append("/>")
        }
    }

    /** The `outlineLevel`/`collapsed` attributes for one `<row>`; empty when the row is not grouped. */
    fun rowAttributes(levels: Levels, row: Int): String = buildString {
        levels.rows[row]?.let { append(""" outlineLevel="$it"""") }
        if (row in levels.collapsedRows) append(""" collapsed="1"""")
    }

    /** The `outlineLevel`/`collapsed` attributes for one `<col>`; empty when the column is not grouped. */
    fun colAttributes(levels: Levels, col: Int): String = buildString {
        levels.cols[col]?.let { append(""" outlineLevel="$it"""") }
        if (col in levels.collapsedCols) append(""" collapsed="1"""")
    }

    /**
     * Reads `outlinePr` from a `sheetPr` node. A value this model cannot represent (an unknown attribute, a
     * non-boolean) falls back to the schema default rather than aborting the sheet: a print or outline record is
     * not worth refusing a document over.
     */
    fun parseSettings(sheetPr: BoundedXml.Node?): Settings? {
        val node = sheetPr?.children?.firstOrNull { it.name.local == "outlinePr" } ?: return null
        val settings = Settings(
            summaryBelow = flagOr(node.attr("summaryBelow"), true),
            summaryRight = flagOr(node.attr("summaryRight"), true),
            applyStyles = flagOr(node.attr("applyStyles"), false),
        )
        return if (settings.isDefault) null else settings
    }

    /**
     * Reads grouping from `<row>` and `<col>`. A `<col min max>` that spans a range is expanded to every column
     * in it: Excel writes one element per group, and keeping only `min` would collapse the group to its first
     * column. The span is capped at [MAX_COL_SPAN] so a hostile `min="1" max="16384"` cannot allocate the grid;
     * a wider span is read as its first column, which is the same repair this reader applies elsewhere.
     */
    fun parseLevels(
        rows: List<BoundedXml.Node>,
        cols: List<BoundedXml.Node>,
        isOn: (String?) -> Boolean,
    ): Levels? {
        val rowLevels = linkedMapOf<Int, Int>()
        val colLevels = linkedMapOf<Int, Int>()
        val collapsedRows = linkedSetOf<Int>()
        val collapsedCols = linkedSetOf<Int>()
        for (row in rows) {
            val index = row.attr("r")?.toIntOrNull()?.takeIf { it in 1..1_048_576 } ?: continue
            row.attr("outlineLevel")?.toIntOrNull()?.takeIf { it in 1..MAX_LEVEL }?.let { rowLevels[index] = it }
            if (isOn(row.attr("collapsed"))) collapsedRows.add(index)
        }
        for (col in cols) {
            val min = col.attr("min")?.toIntOrNull()?.takeIf { it in 1..16_384 } ?: continue
            val max = col.attr("max")?.toIntOrNull()?.takeIf { it in min..16_384 } ?: min
            val level = col.attr("outlineLevel")?.toIntOrNull()?.takeIf { it in 1..MAX_LEVEL }
            val collapsed = isOn(col.attr("collapsed"))
            if (level == null && !collapsed) continue
            val last = if (max - min >= MAX_COL_SPAN) min else max
            for (index in min..last) {
                level?.let { colLevels[index] = it }
                if (collapsed) collapsedCols.add(index)
            }
        }
        val levels = Levels(rowLevels, colLevels, collapsedRows, collapsedCols)
        return if (levels.isEmpty) null else levels
    }

    /** Widest `<col min max>` span this reader expands; Excel's own groups are far narrower. */
    const val MAX_COL_SPAN = 1_024

    private fun flagOr(value: String?, fallback: Boolean): Boolean = when (value) {
        null -> fallback
        "1", "true" -> true
        "0", "false" -> false
        else -> fallback
    }
}
