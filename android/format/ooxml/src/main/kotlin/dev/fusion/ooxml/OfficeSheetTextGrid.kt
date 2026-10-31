// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Explicit text-only rectangular grid paste, not a general CSV/XLSX importer.
 * Tabs separate fields and CR, LF or CRLF separate records outside quotes.
 * A field may be quoted in full; inside quotes a doubled quote is one quote and
 * tabs/line endings are literal. Other quote positions are refused. One final
 * record separator is ignored. Empty fields clear cells; nonempty fields are
 * always TEXT, never implicitly parsed as numbers or formulas.
 */
object OfficeSheetTextGrid {
    const val MAX_INPUT_BYTES = 64 * 1024
    const val MAX_FIELDS = OfficeSheetDraft.MAX_CELLS

    /** A proposed immutable result, bound to the exact original draft instance. */
    class Preview internal constructor(
        val original: OfficeSheetDraft.Draft,
        val anchor: String,
        val calculated: OfficeSheetDraft.Calculated,
        val rows: Int,
        val columns: Int,
        val inserted: Int,
        val replaced: Int,
        val cleared: Int,
        val unchanged: Int,
    ) {
        val changed: Int get() = inserted + replaced + cleared
        fun isCurrent(draft: OfficeSheetDraft.Draft, selected: String): Boolean =
            draft === original && selected == anchor
    }

    fun prepare(draft: OfficeSheetDraft.Draft, rawAnchor: String, raw: String): Preview {
        val anchor = OfficeSheetDraft.address(rawAnchor)
        require(raw.isNotEmpty()) { "Enter a text grid before previewing" }
        // For valid UTF-8, UTF-16 code units cannot exceed the number of bytes.
        require(raw.length <= MAX_INPUT_BYTES) { "Text grid exceeds 64 KiB" }
        OfficeSheetDraft.validateXml(raw)
        require(OfficeSheetDraft.utf8(raw).size <= MAX_INPUT_BYTES) { "Text grid exceeds 64 KiB UTF-8" }
        val records = parse(raw)
        val rowCount = records.size
        val columnCount = records.first().size
        val startColumn = anchor[0] - 'A'
        val startRow = anchor.substring(1).toInt()
        require(startColumn + columnCount <= OfficeSheetDraft.MAX_COLUMNS &&
            startRow + rowCount - 1 <= OfficeSheetDraft.MAX_ROWS) { "Text grid extends beyond A1–Z100" }

        val cells = LinkedHashMap(draft.cells)
        var inserted = 0
        var replaced = 0
        var cleared = 0
        var unchanged = 0
        for ((dy, record) in records.withIndex()) for ((dx, field) in record.withIndex()) {
            val ref = "${('A'.code + startColumn + dx).toChar()}${startRow + dy}"
            val old = cells[ref]
            val next = if (field.isEmpty()) null else OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.TEXT, field)
            when {
                old == next -> unchanged++
                old == null -> { inserted++; cells[ref] = next!! }
                next == null -> { cleared++; cells.remove(ref) }
                else -> { replaced++; cells[ref] = next }
            }
        }
        val changes = inserted + replaced + cleared
        val calculated = if (changes == 0) OfficeSheetDraft.calculate(draft) else {
            require(draft.revision < Long.MAX_VALUE) { "Sheet revision exhausted" }
            OfficeSheetDraft.restore(draft.revision + 1, cells)
        }
        return Preview(draft, anchor, calculated, rowCount, columnCount, inserted, replaced, cleared, unchanged)
    }

    private enum class Mode { START, UNQUOTED, QUOTED, AFTER_QUOTE }

    private fun parse(raw: String): List<List<String>> {
        val records = ArrayList<List<String>>()
        var record = ArrayList<String>()
        val field = StringBuilder()
        var mode = Mode.START
        var totalFields = 0
        var endedRecord = false
        var i = 0
        fun finishField() {
            require(field.length <= OfficeSheetDraft.MAX_CELL_BYTES) { "A cell exceeds 4,096 characters" }
            totalFields++
            require(totalFields <= MAX_FIELDS) { "Text grid exceeds $MAX_FIELDS fields" }
            record.add(field.toString())
            field.setLength(0)
            mode = Mode.START
        }
        fun finishRecord() {
            require(record.isNotEmpty() && record.size <= OfficeSheetDraft.MAX_COLUMNS) { "Too many columns" }
            require(records.isEmpty() || record.size == records.first().size) { "Every row must have the same number of fields" }
            require(records.size < OfficeSheetDraft.MAX_ROWS) { "Too many rows" }
            records.add(record)
            record = ArrayList()
        }
        while (i < raw.length) {
            val c = raw[i]
            if (mode == Mode.QUOTED) {
                if (c == '"') {
                    if (i + 1 < raw.length && raw[i + 1] == '"') { field.append('"'); i++ }
                    else mode = Mode.AFTER_QUOTE
                } else field.append(c)
                endedRecord = false
                i++
                continue
            }
            when (c) {
                '\t' -> { finishField(); endedRecord = false }
                '\r', '\n' -> {
                    finishField(); finishRecord(); endedRecord = true
                    if (c == '\r' && i + 1 < raw.length && raw[i + 1] == '\n') i++
                }
                '"' -> {
                    require(mode == Mode.START) { "Quote must enclose an entire field" }
                    mode = Mode.QUOTED
                    endedRecord = false
                }
                else -> {
                    require(mode != Mode.AFTER_QUOTE) { "Unexpected character after closing quote" }
                    field.append(c)
                    mode = Mode.UNQUOTED
                    endedRecord = false
                }
            }
            i++
        }
        require(mode != Mode.QUOTED) { "Unclosed quoted field" }
        if (!endedRecord) { finishField(); finishRecord() }
        check(records.isNotEmpty())
        return records
    }
}
