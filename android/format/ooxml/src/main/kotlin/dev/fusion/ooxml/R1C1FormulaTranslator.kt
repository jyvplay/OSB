package dev.fusion.ooxml

/**
 * Clean-room bi-directional Excel `A1 <-> R1C1` formula coordinate translator (Apache-2.0).
 *
 * Preserves quoted sheet names (`'Q1 Sales'!`), double-quoted string literals (`"R1C1"`),
 * absolute (`$B$3` <-> `R3C2`), row-relative/col-absolute (`$B5` from `C4` <-> `R[1]C2`),
 * row-absolute/col-relative (`D$2` from `C4` <-> `R2C[1]`), and pure relative (`RC[-1]`)
 * cell and range references.
 */
object R1C1FormulaTranslator {
    const val SCOPE =
        "Bi-directional Excel A1 <-> R1C1 formula translator preserving mixed anchors, ranges, sheet prefixes, and string literals"

    private const val MAX_EXCEL_ROWS = 1_048_576
    private const val MAX_EXCEL_COLS = 16_384

    private val A1_TOKEN_RE = Regex("(\\$?)([A-Za-z]{1,3})(\\$?)([1-9][0-9]{0,6})")
    private val R1C1_TOKEN_RE = Regex("R(?:(\\[[+-]?[0-9]+\\])|([1-9][0-9]{0,6}))?C(?:(\\[[+-]?[0-9]+\\])|([1-9][0-9]{0,4}))?")

    fun toR1C1(a1Formula: String, anchorCellA1: String): String {
        val anchor = SheetRange.parseCell(anchorCellA1)
        val anchorR1 = anchor.row
        val anchorC1 = anchor.col
        return rewriteOutsideStringsAndSheetQuotes(a1Formula) { seg ->
            val out = StringBuilder()
            var i = 0
            while (i < seg.length) {
                val m = A1_TOKEN_RE.matchAt(seg, i)
                val prev = if (i > 0) seg[i - 1] else ' '
                val afterIdx = m?.let { it.range.last + 1 } ?: -1
                val next = if (afterIdx in seg.indices) seg[afterIdx] else ' '
                // Do not rewrite function identifiers like LOG10( or SHA256 or A1B2
                if (m != null && !prev.isLetterOrDigit() && prev != '_' && prev != '.' &&
                    next != '(' && !next.isLetterOrDigit() && next != '_'
                ) {
                    val c1 = runCatching { ColLetters.fromLetters(m.groupValues[2].uppercase()) }.getOrElse { -1 }
                    val row1 = m.groupValues[4].toIntOrNull() ?: -1
                    if (c1 in 1..MAX_EXCEL_COLS && row1 in 1..MAX_EXCEL_ROWS) {
                        val colAbs = m.groupValues[1] == "$"
                        val rowAbs = m.groupValues[3] == "$"
                        val rPart = if (rowAbs) "R$row1" else {
                            val dr = row1 - anchorR1
                            if (dr == 0) "R" else "R[$dr]"
                        }
                        val cPart = if (colAbs) "C$c1" else {
                            val dc = c1 - anchorC1
                            if (dc == 0) "C" else "C[$dc]"
                        }
                        out.append(rPart).append(cPart)
                        i = afterIdx
                        continue
                    }
                }
                out.append(seg[i])
                i++
            }
            out.toString()
        }
    }

    fun toA1(r1c1Formula: String, anchorCellA1: String): String {
        val anchor = SheetRange.parseCell(anchorCellA1)
        val anchorR1 = anchor.row
        val anchorC1 = anchor.col
        return rewriteOutsideStringsAndSheetQuotes(r1c1Formula) { seg ->
            val out = StringBuilder()
            var i = 0
            while (i < seg.length) {
                val m = R1C1_TOKEN_RE.matchAt(seg, i)
                val prev = if (i > 0) seg[i - 1] else ' '
                val afterIdx = m?.let { it.range.last + 1 } ?: -1
                val next = if (afterIdx in seg.indices) seg[afterIdx] else ' '
                if (m != null && !prev.isLetterOrDigit() && prev != '_' &&
                    next != '(' && !next.isLetterOrDigit() && next != '_'
                ) {
                    val rRel = m.groupValues[1]
                    val rAbs = m.groupValues[2]
                    val cRel = m.groupValues[3]
                    val cAbs = m.groupValues[4]
                    val targetRow1 = when {
                        rAbs.isNotEmpty() -> rAbs.toInt()
                        rRel.isNotEmpty() -> anchorR1 + rRel.substring(1, rRel.length - 1).toInt()
                        else -> anchorR1
                    }
                    val targetCol1 = when {
                        cAbs.isNotEmpty() -> cAbs.toInt()
                        cRel.isNotEmpty() -> anchorC1 + cRel.substring(1, cRel.length - 1).toInt()
                        else -> anchorC1
                    }
                    require(targetRow1 in 1..MAX_EXCEL_ROWS && targetCol1 in 1..MAX_EXCEL_COLS) {
                        "R1C1 reference '${m.value}' from $anchorCellA1 resolves out of bounds: row=$targetRow1 col=$targetCol1"
                    }
                    val colLetters = ColLetters.toLetters(targetCol1)
                    val colPrefix = if (cAbs.isNotEmpty()) "$" else ""
                    val rowPrefix = if (rAbs.isNotEmpty()) "$" else ""
                    out.append(colPrefix).append(colLetters).append(rowPrefix).append(targetRow1)
                    i = afterIdx
                    continue
                }
                out.append(seg[i])
                i++
            }
            out.toString()
        }
    }

    /** Translates a copied R1C1-invariant formula from [fromAnchorA1] to [toAnchorA1] (Excel Fill-Down / Fill-Right). */
    fun relocateFormula(a1Formula: String, fromAnchorA1: String, toAnchorA1: String): String =
        toA1(toR1C1(a1Formula, fromAnchorA1), toAnchorA1)

    private fun rewriteOutsideStringsAndSheetQuotes(input: String, transform: (String) -> String): String {
        val result = StringBuilder()
        val plain = StringBuilder()
        fun flushPlain() {
            if (plain.isNotEmpty()) {
                result.append(transform(plain.toString()))
                plain.setLength(0)
            }
        }
        var i = 0
        while (i < input.length) {
            when (val ch = input[i]) {
                '"' -> {
                    flushPlain()
                    result.append(ch); i++
                    while (i < input.length) {
                        result.append(input[i])
                        if (input[i] == '"') {
                            if (i + 1 < input.length && input[i + 1] == '"') {
                                result.append('"'); i += 2; continue
                            }
                            i++; break
                        }
                        i++
                    }
                }
                '\'' -> {
                    flushPlain()
                    result.append(ch); i++
                    while (i < input.length) {
                        result.append(input[i])
                        if (input[i] == '\'') {
                            if (i + 1 < input.length && input[i + 1] == '\'') {
                                result.append('\''); i += 2; continue
                            }
                            i++
                            if (i < input.length && input[i] == '!') {
                                result.append('!'); i++
                            }
                            break
                        }
                        i++
                    }
                }
                else -> {
                    // Also preserve unquoted Sheet2! prefix verbatim before '!'
                    if ((ch.isLetter() || ch == '_')) {
                        var j = i + 1
                        while (j < input.length && (input[j].isLetterOrDigit() || input[j] == '_' || input[j] == '.')) j++
                        if (j < input.length && input[j] == '!') {
                            flushPlain()
                            result.append(input, i, j + 1)
                            i = j + 1
                            continue
                        }
                    }
                    plain.append(ch)
                    i++
                }
            }
        }
        flushPlain()
        return result.toString()
    }
}
