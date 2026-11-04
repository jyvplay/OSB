// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Reviewable local rectangle edits. Formula fill is limited to the draft's admitted
 * single-sheet grammar and refuses out-of-grid translations instead of producing #REF!.
 * No styles, multiple sheets or Excel parity. Candidate construction never mutates the
 * original; callers must confirm the exact preview.
 */
object OfficeSheetRangeEdit {
    const val MAX_POSITIONS = 256
    enum class Operation { CLEAR_CONTENTS, FILL_LITERAL, FILL_FORMULA }

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
        val formulasWritten: Int,
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
        val source = when (operation) {
            Operation.CLEAR_CONTENTS -> null
            Operation.FILL_LITERAL -> {
                val cell = requireNotNull(draft.cells[selected]) { "Select a populated literal source cell" }
                require(cell.kind != OfficeSheetDraft.Kind.FORMULA) { "Formula sources cannot be filled as literals" }
                require(cell.input.isNotEmpty()) { "An empty source cannot be filled; use clear instead" }
                cell
            }
            Operation.FILL_FORMULA -> {
                val cell = requireNotNull(draft.cells[selected]) { "Select an applied formula source cell" }
                require(cell.kind == OfficeSheetDraft.Kind.FORMULA) { "Select an applied formula source cell" }
                cell
            }
        }
        val cells = LinkedHashMap(draft.cells)
        var inserted = 0; var replaced = 0; var cleared = 0; var unchanged = 0
        var formulasRemoved = 0; var formulasWritten = 0
        for (row in top..bottom) for (col in left..right) {
            val ref = "$col$row"
            val old = cells[ref]
            val next = if (operation == Operation.FILL_FORMULA) {
                val formula = requireNotNull(source)
                formula.copy(input = relocateFormula(formula.input, selected, ref))
            } else source
            if (operation != Operation.CLEAR_CONTENTS && protectExisting && old != null && old != next) {
                throw IllegalArgumentException("Protected existing cell $ref would change; no range applied")
            }
            when {
                old == next -> unchanged++
                old == null -> { inserted++; cells[ref] = requireNotNull(next) }
                next == null -> { cleared++; cells.remove(ref) }
                else -> { replaced++; cells[ref] = next }
            }
            if (old?.kind == OfficeSheetDraft.Kind.FORMULA && old != next) formulasRemoved++
            if (next?.kind == OfficeSheetDraft.Kind.FORMULA && old != next) formulasWritten++
        }
        val changed = inserted + replaced + cleared
        val calculated = if (changed == 0) OfficeSheetDraft.calculate(draft) else {
            require(draft.revision < Long.MAX_VALUE) { "Sheet revision exhausted" }
            OfficeSheetDraft.restore(draft.revision + 1, cells)
        }
        return Preview(draft, selected, canonical, positions, operation,
            operation != Operation.CLEAR_CONTENTS && protectExisting, calculated,
            inserted, replaced, cleared, unchanged, formulasRemoved, formulasWritten)
    }

    private val referenceToken = Regex("(\\$?)([A-Za-z])(\\$?)(100|[1-9][0-9]?)")
    private val numberToken = Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
    private val identifierToken = Regex("[A-Za-z_][A-Za-z0-9_.]*")

    /**
     * Rewrites only reference tokens in an already admitted formula. String literals and
     * numeric exponents must be consumed as whole tokens before looking for references.
     * OfficeSheetDraft.restore validates the complete translated candidate afterwards.
     */
    private fun relocateFormula(formula: String, from: String, to: String): String {
        val dc = to[0] - from[0]
        val dr = to.substring(1).toInt() - from.substring(1).toInt()
        if (dc == 0 && dr == 0) return formula
        val result = StringBuilder(formula.length)
        var i = 0
        while (i < formula.length) {
            val c = formula[i]
            when {
                c == '"' -> {
                    val start = i++
                    var closed = false
                    while (i < formula.length) {
                        if (formula[i++] == '"') {
                            if (i < formula.length && formula[i] == '"') i++ else { closed = true; break }
                        }
                    }
                    require(closed) { "Unclosed formula string" }
                    result.append(formula, start, i)
                }
                c in '0'..'9' || c == '.' -> {
                    val token = numberToken.matchAt(formula, i)
                        ?: throw IllegalArgumentException("Malformed formula number")
                    result.append(token.value); i = token.range.last + 1
                }
                c == '$' || c in 'A'..'Z' || c in 'a'..'z' || c == '_' -> {
                    val token = referenceToken.matchAt(formula, i)
                    val end = token?.range?.last?.plus(1) ?: i
                    val whole = token != null && (end == formula.length ||
                        !(formula[end].isLetterOrDigit() || formula[end] == '_' || formula[end] == '$' || formula[end] == '.'))
                    if (whole) {
                        val ref = requireNotNull(token)
                        val letter = ref.groupValues[2].single()
                        val oldColumn = letter.uppercaseChar() - 'A' + 1
                        val oldRow = ref.groupValues[4].toInt()
                        val column = oldColumn + if (ref.groupValues[1].isEmpty()) dc else 0
                        val row = oldRow + if (ref.groupValues[3].isEmpty()) dr else 0
                        require(column in 1..OfficeSheetDraft.MAX_COLUMNS && row in 1..OfficeSheetDraft.MAX_ROWS) {
                            "A translated reference for $to is outside A1:Z100; no range applied"
                        }
                        val translatedLetter = (if (letter in 'a'..'z') 'a' else 'A') + column - 1
                        result.append(ref.groupValues[1]).append(translatedLetter)
                            .append(ref.groupValues[3]).append(row)
                        i = end
                    } else {
                        val name = identifierToken.matchAt(formula, i)
                            ?: throw IllegalArgumentException("Invalid formula reference")
                        result.append(name.value); i = name.range.last + 1
                    }
                }
                else -> { result.append(c); i++ }
            }
        }
        return result.toString()
    }
}
