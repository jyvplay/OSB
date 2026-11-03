// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Reviewable local rectangle edits. No styles, multiple sheets, formula fill, or Excel parity.
 * Candidate construction never mutates the original; callers must confirm the exact preview.
 */
object OfficeSheetRangeEdit {
    const val MAX_POSITIONS = 256
    enum class Operation { CLEAR_CONTENTS, FILL_LITERAL }

    class Preview internal constructor(
        val original: OfficeSheetDraft.Draft,
        val selected: String,
        val range: String,
        val positions: Int,
        val operation: Operation,
        val protectExisting: Boolean,
        val calculated: OfficeSheetDraft.Calculated,
        val inserted: Int,
        val replaced: Int,
        val cleared: Int,
        val unchanged: Int,
        val formulasRemoved: Int,
    ) {
        val changed: Int get() = inserted + replaced + cleared
        fun isCurrent(draft: OfficeSheetDraft.Draft, currentSelected: String): Boolean =
            draft === original && currentSelected == selected
    }

    /** Parse only the existing editor's A1..Z100 grammar; two corners are normalized. */
    fun prepare(
        draft: OfficeSheetDraft.Draft,
        rawSelected: String,
        rawRange: String,
        operation: Operation,
        protectExisting: Boolean = true,
    ): Preview {
        val selected = OfficeSheetDraft.address(rawSelected)
        require(rawRange.length <= 32) { "Range is too long" }
        val pieces = rawRange.split(':')
        require(pieces.size in 1..2) { "Use one cell or one rectangular range" }
        val a = OfficeSheetDraft.address(pieces[0])
        val b = OfficeSheetDraft.address(pieces.last())
        val left = minOf(a[0], b[0]); val right = maxOf(a[0], b[0])
        val top = minOf(a.substring(1).toInt(), b.substring(1).toInt())
        val bottom = maxOf(a.substring(1).toInt(), b.substring(1).toInt())
        val positions = (right - left + 1) * (bottom - top + 1)
        require(positions <= MAX_POSITIONS) { "Range exceeds $MAX_POSITIONS cells" }
        val first = "$left$top"; val last = "$right$bottom"
        val canonical = if (first == last) first else "$first:$last"
        val source = if (operation == Operation.FILL_LITERAL) {
            val cell = requireNotNull(draft.cells[selected]) { "Select a populated literal source cell" }
            require(cell.kind != OfficeSheetDraft.Kind.FORMULA) { "Formula sources cannot be filled" }
            require(cell.input.isNotEmpty()) { "An empty source cannot be filled; use clear instead" }
            cell
        } else null
        val cells = LinkedHashMap(draft.cells)
        var inserted = 0; var replaced = 0; var cleared = 0; var unchanged = 0; var formulasRemoved = 0
        for (row in top..bottom) for (col in left..right) {
            val ref = "$col$row"
            val old = cells[ref]
            val next = source
            if (operation == Operation.FILL_LITERAL && protectExisting && old != null && old != next) {
                throw IllegalArgumentException("Protected existing cell $ref would change; no range applied")
            }
            when {
                old == next -> unchanged++
                old == null -> { inserted++; cells[ref] = requireNotNull(next) }
                next == null -> { cleared++; cells.remove(ref) }
                else -> { replaced++; cells[ref] = next }
            }
            if (old?.kind == OfficeSheetDraft.Kind.FORMULA && old != next) formulasRemoved++
        }
        val changed = inserted + replaced + cleared
        val calculated = if (changed == 0) OfficeSheetDraft.calculate(draft) else {
            require(draft.revision < Long.MAX_VALUE) { "Sheet revision exhausted" }
            OfficeSheetDraft.restore(draft.revision + 1, cells)
        }
        return Preview(draft, selected, canonical, positions, operation,
            operation == Operation.FILL_LITERAL && protectExisting, calculated,
            inserted, replaced, cleared, unchanged, formulasRemoved)
    }
}
