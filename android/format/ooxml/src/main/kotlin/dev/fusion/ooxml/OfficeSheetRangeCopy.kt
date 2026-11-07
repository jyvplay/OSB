// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Reviewable copy of one local rectangle. It does not access the system clipboard,
 * styles, other sheets or Excel. A missing source cell is either skipped or clears
 * its destination; an explicitly stored empty-text cell is a populated value.
 */
object OfficeSheetRangeCopy {
    const val MAX_POSITIONS = 256

    class Preview internal constructor(
        val original: OfficeSheetDraft.Draft,
        val selected: String,
        val source: String,
        val destination: String,
        val positions: Int,
        val protectExisting: Boolean,
        val skipMissing: Boolean,
        val calculated: OfficeSheetDraft.Calculated,
        val inserted: Int,
        val replaced: Int,
        val cleared: Int,
        val unchanged: Int,
        val skipped: Int,
        val formulasRemoved: Int,
        val formulasWritten: Int,
    ) {
        val changed: Int get() = inserted + replaced + cleared
        fun isCurrent(draft: OfficeSheetDraft.Draft, currentSelected: String): Boolean =
            draft === original && currentSelected == selected
    }

    /** The selected cell is the top-left destination; the original snapshot is never mutated. */
    fun prepare(
        draft: OfficeSheetDraft.Draft,
        rawSelected: String,
        rawSource: String,
        protectExisting: Boolean = true,
        skipMissing: Boolean = false,
    ): Preview {
        val selected = OfficeSheetDraft.address(rawSelected)
        require(rawSource.length <= 32) { "Source range is too long" }
        val pieces = rawSource.split(':')
        require(pieces.size in 1..2) { "Use one source cell or one rectangular source range" }
        val a = OfficeSheetDraft.address(pieces[0]); val b = OfficeSheetDraft.address(pieces.last())
        val left = minOf(a[0], b[0]); val right = maxOf(a[0], b[0])
        val top = minOf(a.substring(1).toInt(), b.substring(1).toInt())
        val bottom = maxOf(a.substring(1).toInt(), b.substring(1).toInt())
        val width = right - left + 1; val height = bottom - top + 1
        val positions = width * height
        require(positions <= MAX_POSITIONS) { "Source range exceeds $MAX_POSITIONS cells" }
        val targetLeft = selected[0]; val targetTop = selected.substring(1).toInt()
        val targetRight = targetLeft + width - 1; val targetBottom = targetTop + height - 1
        require(targetRight <= 'Z' && targetBottom <= OfficeSheetDraft.MAX_ROWS) {
            "Destination would extend outside A1:Z100; no range copied"
        }
        fun rectangle(first: String, last: String) = if (first == last) first else "$first:$last"
        val source = rectangle("$left$top", "$right$bottom")
        val destination = rectangle(selected, "$targetRight$targetBottom")
        val cells = LinkedHashMap(draft.cells)
        var inserted = 0; var replaced = 0; var cleared = 0; var unchanged = 0; var skipped = 0
        var formulasRemoved = 0; var formulasWritten = 0
        for (row in 0 until height) for (column in 0 until width) {
            val from = "${left + column}${top + row}"
            val to = "${targetLeft + column}${targetTop + row}"
            // Deliberately read ONLY the original immutable snapshot for overlap safety.
            val sourceCell = draft.cells[from]
            if (sourceCell == null && skipMissing) { skipped++; continue }
            val next = if (sourceCell?.kind == OfficeSheetDraft.Kind.FORMULA)
                sourceCell.copy(input = OfficeSheetRangeEdit.relocateFormula(sourceCell.input, from, to))
            else sourceCell
            val old = draft.cells[to]
            if (protectExisting && old != null && old != next) {
                throw IllegalArgumentException("Protected existing cell $to would change; no range copied")
            }
            when {
                old == next -> unchanged++
                old == null -> { inserted++; cells[to] = requireNotNull(next) }
                next == null -> { cleared++; cells.remove(to) }
                else -> { replaced++; cells[to] = next }
            }
            if (old?.kind == OfficeSheetDraft.Kind.FORMULA && old != next) formulasRemoved++
            if (next?.kind == OfficeSheetDraft.Kind.FORMULA && old != next) formulasWritten++
        }
        val changed = inserted + replaced + cleared
        val calculated = if (changed == 0) OfficeSheetDraft.calculate(draft) else {
            require(draft.revision < Long.MAX_VALUE) { "Sheet revision exhausted" }
            OfficeSheetDraft.restore(draft.revision + 1, cells)
        }
        return Preview(draft, selected, source, destination, positions, protectExisting, skipMissing,
            calculated, inserted, replaced, cleared, unchanged, skipped, formulasRemoved, formulasWritten)
    }
}
