// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

/**
 * Bidirectional OASIS **OpenFormula** (`of:=SUM([.A1:.B4]; [.C1])`) <-> ECMA-376 Excel (`=SUM(A1:B4, C1)`)
 * formula syntax translator preserving string literals while converting `[.Sheet.Cell]` brackets
 * and `;` <-> `,` argument separators outside quoted strings.
 */
object OdfSpreadsheetFormulaTranslator {
    const val SCOPE =
        "Bidirectional OASIS ODF 1.3 OpenFormula (of:=SUM([.A1:.B4];1)) <-> ECMA-376 OOXML formula syntax translator."

    fun excelToOpenFormula(excelFormula: String): String {
        val body = excelFormula.trim().removePrefix("=")
        val sb = StringBuilder("of:=")
        var i = 0
        var inQuotes = false
        while (i < body.length) {
            val ch = body[i]
            if (ch == '"') {
                inQuotes = !inQuotes
                sb.append(ch); i++
                continue
            }
            if (!inQuotes) {
                if (ch == ',') {
                    sb.append(';'); i++; continue
                }
                val rangeMatch = CELL_OR_RANGE_RE.matchAt(body, i)
                if (rangeMatch != null && (i + rangeMatch.value.length >= body.length || body[i + rangeMatch.value.length] != '(')) {
                    val tok = rangeMatch.value
                    if (':' in tok) {
                        val (a, b) = tok.split(':', limit = 2)
                        sb.append("[.").append(a).append(":.").append(b).append(']')
                    } else {
                        sb.append("[.").append(tok).append(']')
                    }
                    i += tok.length
                    continue
                }
            }
            sb.append(ch); i++
        }
        return sb.toString()
    }

    fun openFormulaToExcel(openFormula: String): String {
        val stripped = openFormula.trim().removePrefix("of:").removePrefix("=")
        val d = '$'
        val unbracketed = Regex("\\[\\.([\\${d}A-Za-z0-9]+)(?::\\.([\\${d}A-Za-z0-9]+))?\\]").replace(stripped) { m ->
            if (m.groupValues[2].isEmpty()) m.groupValues[1] else "${m.groupValues[1]}:${m.groupValues[2]}"
        }
        val sb = StringBuilder("=")
        var inQuotes = false
        for (ch in unbracketed) {
            if (ch == '"') inQuotes = !inQuotes
            sb.append(if (!inQuotes && ch == ';') ',' else ch)
        }
        return sb.toString()
    }

    private val CELL_OR_RANGE_RE = Regex("\\${'$'}?[A-Z]{1,3}\\${'$'}?[1-9][0-9]{0,6}(?::\\${'$'}?[A-Z]{1,3}\\${'$'}?[1-9][0-9]{0,6})?")
}
