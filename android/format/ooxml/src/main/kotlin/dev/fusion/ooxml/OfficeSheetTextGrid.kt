// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.math.BigDecimal

/**
 * Explicit rectangular text-grid paste, not a general CSV/XLSX importer.
 * Tabs separate fields and CR, LF or CRLF separate records outside quotes.
 * A field may be quoted in full; inside quotes a doubled quote is one quote and
 * tabs/line endings are literal. Other quote positions are refused. One final
 * record separator is ignored. Empty fields clear cells. The default mode keeps
 * all other fields as TEXT. Optional decimal mode converts only a restricted,
 * unquoted decimal grammar; no pasted input is ever interpreted as a formula.
 * A separate opt-in transposes the already parsed rectangle before placement;
 * original quotedness, source budgets and destination bounds still apply. Two
 * independent, default-off policies can skip zero-length fields and reject the
 * whole operation if it would change any populated destination cell.
 */
object OfficeSheetTextGrid {
    const val MAX_INPUT_BYTES = 64 * 1024
    const val MAX_FIELDS = OfficeSheetDraft.MAX_CELLS
    enum class Mode { TEXT_ONLY, DECIMALS }
    private val ordinaryDecimal = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]*[1-9])?")

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
        val mode: Mode,
        val numericFields: Int,
        val textFields: Int,
        val emptyFields: Int,
        val transposed: Boolean,
        val skipBlanks: Boolean,
        val protectExisting: Boolean,
        val skippedFields: Int,
    ) {
        val changed: Int get() = inserted + replaced + cleared
        fun isCurrent(draft: OfficeSheetDraft.Draft, selected: String): Boolean =
            draft === original && selected == anchor
    }

    // Preserve the original public entry point and its non-transposing default.
    fun prepare(draft: OfficeSheetDraft.Draft, rawAnchor: String, raw: String, mode: Mode = Mode.TEXT_ONLY): Preview =
        prepare(draft, rawAnchor, raw, mode, transpose = false)

    fun prepare(draft: OfficeSheetDraft.Draft, rawAnchor: String, raw: String, mode: Mode, transpose: Boolean): Preview =
        prepare(draft, rawAnchor, raw, mode, transpose, skipBlanks = false, protectExisting = false)

    fun prepare(
        draft: OfficeSheetDraft.Draft,
        rawAnchor: String,
        raw: String,
        mode: Mode,
        transpose: Boolean,
        skipBlanks: Boolean,
        protectExisting: Boolean,
    ): Preview {
        val anchor = OfficeSheetDraft.address(rawAnchor)
        require(raw.isNotEmpty()) { "Enter a text grid before previewing" }
        // For valid UTF-8, UTF-16 code units cannot exceed the number of bytes.
        require(raw.length <= MAX_INPUT_BYTES) { "Text grid exceeds 64 KiB" }
        OfficeSheetDraft.validateXml(raw)
        require(OfficeSheetDraft.utf8(raw).size <= MAX_INPUT_BYTES) { "Text grid exceeds 64 KiB UTF-8" }
        val parsed = parse(raw)
        // A bounded rectangular transpose retains each field's quotedness for the decimal policy.
        val records = if (transpose) List(parsed.first().size) { x -> List(parsed.size) { y -> parsed[y][x] } } else parsed
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
        var numericFields = 0
        var textFields = 0
        var emptyFields = 0
        var skippedFields = 0
        for ((dy, record) in records.withIndex()) for ((dx, field) in record.withIndex()) {
            val ref = "${('A'.code + startColumn + dx).toChar()}${startRow + dy}"
            val old = cells[ref]
            if (field.text.isEmpty()) {
                emptyFields++
                if (skipBlanks) { skippedFields++; continue }
            }
            val next = when {
                field.text.isEmpty() -> null
                mode == Mode.DECIMALS && !field.quoted && isRoundTripDecimal(field.text) -> {
                    numericFields++; OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.NUMBER, field.text)
                }
                else -> { textFields++; OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.TEXT, field.text) }
            }
            require(!protectExisting || old == null || old == next) {
                "Protected existing cell $ref would change; no grid applied"
            }
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
        return Preview(draft, anchor, calculated, rowCount, columnCount, inserted, replaced, cleared, unchanged,
            mode, numericFields, textFields, emptyFields, transpose, skipBlanks, protectExisting, skippedFields)
    }

    /** Canonical decimal round-trip is not exact representation of a binary64 value. */
    private fun isRoundTripDecimal(text: String): Boolean {
        if (!ordinaryDecimal.matches(text)) return false
        val exact = text.toBigDecimalOrNull() ?: return false
        if (exact.precision() > 15 || (text.startsWith('-') && exact.signum() == 0)) return false
        val binary = text.toDoubleOrNull() ?: return false
        return binary.isFinite() && BigDecimal.valueOf(binary).compareTo(exact) == 0
    }

    private data class Field(val text: String, val quoted: Boolean)
    private enum class ParseMode { START, UNQUOTED, QUOTED, AFTER_QUOTE }

    private fun parse(raw: String): List<List<Field>> {
        val records = ArrayList<List<Field>>()
        var record = ArrayList<Field>()
        val field = StringBuilder()
        var mode = ParseMode.START
        var quoted = false
        var totalFields = 0
        var endedRecord = false
        var i = 0
        fun finishField() {
            require(field.length <= OfficeSheetDraft.MAX_CELL_BYTES) { "A cell exceeds 4,096 characters" }
            totalFields++
            require(totalFields <= MAX_FIELDS) { "Text grid exceeds $MAX_FIELDS fields" }
            record.add(Field(field.toString(), quoted))
            field.setLength(0)
            quoted = false
            mode = ParseMode.START
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
            if (mode == ParseMode.QUOTED) {
                if (c == '"') {
                    if (i + 1 < raw.length && raw[i + 1] == '"') { field.append('"'); i++ }
                    else mode = ParseMode.AFTER_QUOTE
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
                    require(mode == ParseMode.START) { "Quote must enclose an entire field" }
                    mode = ParseMode.QUOTED
                    quoted = true
                    endedRecord = false
                }
                else -> {
                    require(mode != ParseMode.AFTER_QUOTE) { "Unexpected character after closing quote" }
                    field.append(c)
                    mode = ParseMode.UNQUOTED
                    endedRecord = false
                }
            }
            i++
        }
        require(mode != ParseMode.QUOTED) { "Unclosed quoted field" }
        if (!endedRecord) { finishField(); finishRecord() }
        check(records.isNotEmpty())
        return records
    }
}
