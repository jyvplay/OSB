// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.io.ByteArrayOutputStream

/**
 * A bounded values-only UTF-8 comma-separated copy. This is neither an XLSX nor
 * a formula-preserving export. All fields are quoted and internal quotes doubled.
 * Common dangerous *text* prefixes are refused, not prefixed or silently changed.
 * This conservative filter is not a guarantee against every CSV consumer/locale,
 * spreadsheet auto-conversion or save/reopen behavior.
 */
object OfficeSheetCsv {
    const val MAX_OUTPUT_BYTES = 1 * 1024 * 1024
    private const val DANGEROUS = "=+-@＝＋－＠−"

    fun write(draft: OfficeSheetDraft.Draft): ByteArray {
        val values = OfficeSheetDraft.calculate(draft).values
        val maxColumn = draft.cells.keys.maxOfOrNull { it[0] - 'A' + 1 } ?: 1
        val maxRow = draft.cells.keys.maxOfOrNull { it.substring(1).toInt() } ?: 1
        val output = ByteArrayOutputStream()
        fun byte(value: Int) {
            require(output.size() < MAX_OUTPUT_BYTES) { "CSV copy exceeds the output budget" }
            output.write(value)
        }
        for (row in 1..maxRow) {
            for (column in 1..maxColumn) {
                if (column > 1) byte(','.code)
                val ref = "${'A' + column - 1}$row"
                val value = values[ref] ?: FormulaEval.CellValue.Empty
                val text = value.displayText()
                if (value is FormulaEval.CellValue.Text) {
                    require(!dangerousText(text)) { "CSV refused: text at $ref begins with a spreadsheet-formula risk" }
                }
                byte('"'.code)
                for (b in OfficeSheetDraft.utf8(text)) {
                    val unsigned = b.toInt() and 0xff
                    byte(unsigned)
                    if (unsigned == '"'.code) byte(unsigned)
                }
                byte('"'.code)
            }
            byte('\r'.code); byte('\n'.code)
        }
        return output.toByteArray()
    }

    /** No field content is returned or logged by this test. */
    private fun dangerousText(text: String): Boolean {
        for (c in text) {
            if (c == '\t' || c == '\r' || c == '\n') return true
            if (c.isWhitespace() || Character.isSpaceChar(c) || Character.getType(c) == Character.FORMAT.toInt() || c == '\'' || c == '"') continue
            return c in DANGEROUS
        }
        return false
    }
}
