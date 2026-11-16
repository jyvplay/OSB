// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Merged cells: `<mergeCells count>` and `<mergeCell ref>` (ECMA-376 Part 1 §18.3.1.55, `CT_MergeCells`
 * §18.3.2.11) with the geometry rules Excel enforces when it reopens a sheet.
 *
 * Modelled: a list of rectangles, each spanning at least two cells, pairwise disjoint, inside the sheet's
 * 1 048 576 × 16 384 grid. Excel reports a sheet with overlapping merges as damaged and repairs it by dropping
 * one of the two, so the model refuses to hold a set it would not be able to write back unchanged.
 *
 * Not modelled, and not claimed: the `extLst` child, and the interactive rules Excel's UI applies (only the
 * top-left cell of a merged range is editable; the others are blanked). Those are editor behaviours, not
 * record constraints, and writing them is not something a `.xlsx` file states.
 */
object XlsxMerges {

    /** Self-imposed model bound, not a schema bound: bounds the O(n²) disjointness proof and the output size. */
    const val MAX_RANGES = 4096

    const val MAX_ROW = 1_048_576
    const val MAX_COLUMN = 16_384

    /** One merged rectangle, upper case, `A1:B2` form; a single cell is refused because it merges nothing. */
    data class Range(val ref: String) {
        init {
            require(REF.matches(ref)) { "A merged range must be an upper-case A1:B2 reference: $ref" }
            val box = box(ref)
            require(box[2] <= MAX_ROW && box[3] <= MAX_COLUMN) {
                "A merged range must lie inside the $MAX_ROW x $MAX_COLUMN grid: $ref"
            }
            require(box[0] != box[2] || box[1] != box[3]) {
                "A merged range must cover at least two cells: $ref"
            }
        }

        /** `[firstRow, firstColumn, lastRow, lastColumn]`, one-based, for the overlap test. */
        internal val area: IntArray get() = box(ref)
    }

    private val REF = Regex("^[A-Z]{1,3}[1-9][0-9]{0,6}(:[A-Z]{1,3}[1-9][0-9]{0,6})?$")
    private val CELL = Regex("^([A-Z]{1,3})([1-9][0-9]{0,6})$")

    private fun column(label: String): Int {
        var value = 0
        for (ch in label) value = value * 26 + (ch - 'A' + 1)
        return value
    }

    private fun box(ref: String): IntArray {
        val parts = ref.split(':')
        val first = CELL.matchEntire(parts[0]) ?: error("Unreachable: $ref matched REF but not CELL")
        val last = CELL.matchEntire(parts.getOrElse(1) { parts[0] })
            ?: error("Unreachable: $ref matched REF but not CELL")
        return intArrayOf(
            first.groupValues[2].toInt(),
            column(first.groupValues[1]),
            last.groupValues[2].toInt(),
            column(last.groupValues[1]),
        )
    }

    private fun overlap(a: IntArray, b: IntArray): Boolean =
        a[0] <= b[2] && b[0] <= a[2] && a[1] <= b[3] && b[1] <= a[3]

    /**
     * Refuses a set Excel could not keep: two ranges sharing a cell, more than [MAX_RANGES], or a repetition.
     * Called from `StyledSheet`'s initialiser so an unwritable sheet cannot be constructed at all.
     */
    fun requireDisjoint(ranges: List<Range>) {
        require(ranges.size <= MAX_RANGES) { "At most $MAX_RANGES merged ranges are modelled, got ${ranges.size}" }
        val boxes = ranges.map { it.area }
        for (i in boxes.indices) {
            for (j in i + 1 until boxes.size) {
                require(!overlap(boxes[i], boxes[j])) {
                    "Merged ranges overlap: ${ranges[i].ref} and ${ranges[j].ref}"
                }
            }
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

    /** `<mergeCells count="N">…</mergeCells>`; callers only call this with a non-empty, disjoint list. */
    fun toXml(ranges: List<Range>): String {
        requireDisjoint(ranges)
        require(ranges.isNotEmpty()) { "An empty <mergeCells> element is not written" }
        return buildString {
            append("""<mergeCells count="${ranges.size}">""")
            for (range in ranges) append("""<mergeCell ref="${esc(range.ref)}"/>""")
            append("</mergeCells>")
        }
    }

    /**
     * Reads `<mergeCells>`. A range that this model cannot hold — outside the grid, one cell, or one that
     * overlaps a range already read — is dropped instead of being repaired into something the file does not
     * say. Lower case is normalised, as everywhere else in this writer, because the reference is a name and
     * not new information.
     */
    fun parse(root: BoundedXml.Node): List<Range> {
        val node = root.children.firstOrNull { it.name.local == "mergeCells" } ?: return emptyList()
        val ranges = ArrayList<Range>()
        for (child in node.children.filter { it.name.local == "mergeCell" }) {
            val ref = child.attr("ref")?.trim()?.uppercase() ?: continue
            val range = runCatching { Range(ref) }.getOrNull() ?: continue
            if (ranges.size >= MAX_RANGES) break
            if (ranges.any { overlap(it.area, range.area) }) continue
            ranges.add(range)
        }
        return ranges
    }
}
