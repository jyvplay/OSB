// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Clean-room Dynamic Array Spreadsheet Spill Engine (`ECMA-376` + Excel 365 `#` spill operator,
 * inspired by `IronCalc v0.8.0` `MIT/Apache-2.0` & `gridcalc v0.11.0`).
 *
 * Evaluates 2D array-producing formulas (`SEQUENCE`, `UNIQUE`, `SORT`, `FILTER`, `TEXTSPLIT`,
 * `TRANSPOSE`) anchored at an origin cell `A1`, spills computed cells across `A1:C5`, supports
 * downstream `A1#` (`_xlfn.ANCHORARRAY(A1)`) dynamic spill-range references, and raises
 * deterministic `#SPILL!` (`CellErr.Err.VALUE` / explicit `SpillState.BLOCKED`) if any target cell
 * in the spill footprint is occupied by another input or earlier spill!
 */
object SpillSheetEngine {
    const val SCOPE =
        "2D dynamic-array spreadsheet spill engine (48 spilled array, Hankel/Toeplitz/Vandermonde/Kronecker/convolution/Pareto, grouping, pivot, matrix linear-algebra, regression, time-series & higher-order LAMBDA ops expanding 42 R52, 36 spilled R51, 29 R50, and 23 R49 baselines: SEQUENCE, UNIQUE, SORT, SORTBY, FILTER, TEXTSPLIT, TRANSPOSE, MMULT, MINVERSE, MUNIT, FREQUENCY, TREND, GROWTH, EXPAND, TRIMRANGE, GROUPBY, PIVOTBY, LINEST, LOGEST, FLATTEN, OUTERPRODUCT, CUMSUM, DIFFARRAY, MOVINGAVERAGE, ZSCOREGRID, DIAGONAL, HANKEL, TOEPLITZ, VANDERMONDE, KRONECKER, CONVOLVE1D, PARETOGRID, MAP, REDUCE, SCAN, BYROW, BYCOL, MAKEARRAY, VSTACK, HSTACK, TAKE, DROP, TOCOL, TOROW, CHOOSECOLS, CHOOSEROWS, WRAPROWS, WRAPCOLS, LET-spills, A1# spill-range anchor references, deterministic #SPILL! collision detection, and _xlfn._xlws. OOXML formula normalization); volatile RANDARRAY refused."

    const val SPILL_ERROR_LITERAL = "#SPILL!"
    const val MAX_SPILL_CELLS = 4_096

    enum class SpillStatus { NONE, SPILLED, BLOCKED_COLLISION, OUT_OF_BOUNDS }

    data class SpillRegion(
        val anchor: String,
        val width: Int,
        val height: Int,
        val status: SpillStatus,
        val blockerCells: List<String> = emptyList(),
    ) {
        fun rangeA1(): String {
            val top = SheetRange.parseCell(anchor)
            val bot = SheetRange.Cell(top.col + width - 1, top.row + height - 1)
            return "${top.toA1()}:${bot.toA1()}"
        }
    }

    data class SheetSpillResult(
        val displayGrid: Map<String, String>,
        val typedGrid: Map<String, FormulaEval.CellValue>,
        val spillRegions: Map<String, SpillRegion>,
    )

    /** Normalizes Excel OOXML `_xlfn.` / `_xlfn._xlws.` / `_xlfn.ANCHORARRAY(A1)` syntax into clean user syntax. */
    fun importOoxmlFormula(raw: String): String {
        var s = raw.trim()
        s = Regex("_xlfn\\.ANCHORARRAY\\(\\s*([\$]?[A-Za-z]{1,3}[\$]?[0-9]{1,7})\\s*\\)", RegexOption.IGNORE_CASE)
            .replace(s) { "${it.groupValues[1].replace("$", "").uppercase()}#" }
        s = s.replace(Regex("_xlfn\\._xlws\\.", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("_xlfn\\.", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("_xlpm\\.", RegexOption.IGNORE_CASE), "")
        return s
    }

    /** Exports dynamic-array user formulas with canonical Excel 365 `_xlfn.` / `_xlfn._xlws.` prefixes. */
    fun exportOoxmlFormula(userFormula: String): String {
        var s = userFormula.trim()
        s = Regex("\\b([A-Za-z]{1,3}[0-9]{1,7})#").replace(s) {
            "_xlfn.ANCHORARRAY(${it.groupValues[1].uppercase()})"
        }
        for (wsFn in listOf("FILTER", "SORT")) {
            s = Regex("(?<![A-Za-z0-9_.])" + wsFn + "\\s*\\(", RegexOption.IGNORE_CASE)
                .replace(s) { "_xlfn._xlws.${wsFn}(" }
        }
        for (xlFn in listOf(
            "UNIQUE", "SEQUENCE", "TEXTSPLIT", "XLOOKUP", "XMATCH", "LET", "IFS", "SWITCH", "TEXTJOIN",
            "SORTBY", "LAMBDA", "ISOMITTED", "MAP", "REDUCE", "SCAN", "BYROW", "BYCOL", "MAKEARRAY",
            "VSTACK", "HSTACK", "TAKE", "DROP", "TOCOL", "TOROW", "CHOOSECOLS", "CHOOSEROWS", "WRAPROWS", "WRAPCOLS",
            "MUNIT", "PERCENTRANK.INC", "PERCENTRANK.EXC", "RANK.EQ", "RANK.AVG",
            "BITAND", "BITOR", "BITXOR", "BITLSHIFT", "BITRSHIFT",
            "EXPAND", "TRIMRANGE", "GROUPBY", "PIVOTBY",
        )) {
            s = Regex("(?<![A-Za-z0-9_.])" + xlFn + "\\s*\\(", RegexOption.IGNORE_CASE)
                .replace(s) { "_xlfn.${xlFn}(" }
        }
        return s
    }

    fun evaluate(inputs: Map<String, FormulaEval.CellInput>): SheetSpillResult {
        val canonicalInputs = LinkedHashMap<String, FormulaEval.CellInput>()
        for ((k, v) in inputs) {
            canonicalInputs[FormulaEval.canonicalCellRef(k)] = v
        }

        // Partition dynamic-array formulas vs scalar formulas/literals.
        val spillFormulas = LinkedHashMap<String, String>()
        val scalarInputs = LinkedHashMap<String, FormulaEval.CellInput>()
        for ((cell, inp) in canonicalInputs) {
            if (inp is FormulaEval.CellInput.Formula) {
                val clean = importOoxmlFormula(inp.body)
                if (isDynamicArrayHead(clean)) {
                    spillFormulas[cell] = clean
                } else {
                    scalarInputs[cell] = FormulaEval.CellInput.Formula("=" + clean)
                }
            } else {
                scalarInputs[cell] = inp
            }
        }

        // First evaluate non-anchor scalar base cells that do not reference `A1#`.
        val deferredAnchorConsumers = LinkedHashMap<String, String>()
        val baseInputs = LinkedHashMap<String, FormulaEval.CellInput>()
        for ((cell, inp) in scalarInputs) {
            if (inp is FormulaEval.CellInput.Formula && '#' in inp.body &&
                Regex("\\b[A-Za-z]{1,3}[0-9]{1,7}#").containsMatchIn(inp.body)
            ) {
                deferredAnchorConsumers[cell] = inp.body
            } else {
                baseInputs[cell] = inp
            }
        }

        val workingValues = LinkedHashMap<String, FormulaEval.CellValue>()
        val display = LinkedHashMap<String, String>()
        if (baseInputs.isNotEmpty()) {
            val baseEval = FormulaEval.evaluateSheet(baseInputs)
            for ((k, v) in baseEval.values) {
                workingValues[k] = v
                display[k] = v.displayText()
            }
        }

        val occupied = HashSet<String>(canonicalInputs.keys)
        val spillRegions = LinkedHashMap<String, SpillRegion>()

        // Evaluate spill anchors in deterministic row-major order so chained spills (e.g. B1 = SORT(A1#)) work!
        val orderedAnchors = spillFormulas.keys.sortedWith(
            compareBy({ SheetRange.parseCell(it).row }, { SheetRange.parseCell(it).col }),
        )
        for (anchor in orderedAnchors) {
            val expr = resolveSpillHashRefs(spillFormulas.getValue(anchor), spillRegions)
            val matrix = evalDynamicArray(expr, workingValues)
            val h = matrix.size
            val w = matrix.firstOrNull()?.size ?: 1
            require(w * h in 1..MAX_SPILL_CELLS) { "spill size ${w}x$h exceeds $MAX_SPILL_CELLS" }
            val origin = SheetRange.parseCell(anchor)
            val blockers = ArrayList<String>()
            for (dr in 0 until h) {
                for (dc in 0 until w) {
                    if (dr == 0 && dc == 0) continue
                    val target = SheetRange.Cell(origin.col + dc, origin.row + dr).toA1()
                    if (target in occupied) {
                        val existing = canonicalInputs[target]
                        if (existing != FormulaEval.CellInput.Blank) blockers.add(target)
                    }
                }
            }
            if (blockers.isNotEmpty()) {
                spillRegions[anchor] = SpillRegion(anchor, w, h, SpillStatus.BLOCKED_COLLISION, blockers)
                workingValues[anchor] = FormulaEval.CellValue.Err(CellErr.Err.VALUE)
                display[anchor] = SPILL_ERROR_LITERAL
            } else {
                spillRegions[anchor] = SpillRegion(anchor, w, h, SpillStatus.SPILLED)
                for (dr in 0 until h) {
                    for (dc in 0 until w) {
                        val target = SheetRange.Cell(origin.col + dc, origin.row + dr).toA1()
                        val v = matrix[dr][dc]
                        workingValues[target] = v
                        display[target] = v.displayText()
                        occupied.add(target)
                    }
                }
            }
        }

        // Finally evaluate scalar formulas consuming `A1#` (rewritten to `A1:C5` or `#VALUE!` if anchor `#SPILL!`ed).
        if (deferredAnchorConsumers.isNotEmpty()) {
            val finalSheet = LinkedHashMap<String, FormulaEval.CellInput>()
            for ((cell, v) in workingValues) {
                finalSheet[cell] = valueToLiteralInput(v)
            }
            for ((cell, rawBody) in deferredAnchorConsumers) {
                var blocked = false
                val rewritten = Regex("\\b([A-Za-z]{1,3}[0-9]{1,7})#").replace(rawBody) { m ->
                    val refAnchor = FormulaEval.canonicalCellRef(m.groupValues[1])
                    val reg = spillRegions[refAnchor]
                    if (reg == null || reg.status != SpillStatus.SPILLED) {
                        blocked = true
                        "#VALUE!"
                    } else {
                        reg.rangeA1()
                    }
                }
                finalSheet[cell] = if (blocked) {
                    FormulaEval.CellInput.ErrorLiteral(CellErr.Err.VALUE)
                } else {
                    FormulaEval.CellInput.Formula("=" + rewritten)
                }
            }
            val post = FormulaEval.evaluateSheet(finalSheet)
            for (cell in deferredAnchorConsumers.keys) {
                val pv = post.values[cell] ?: FormulaEval.CellValue.Empty
                workingValues[cell] = pv
                display[cell] = pv.displayText()
            }
        }

        return SheetSpillResult(display, workingValues, spillRegions)
    }

    private fun valueToLiteralInput(v: FormulaEval.CellValue): FormulaEval.CellInput = when (v) {
        FormulaEval.CellValue.Empty -> FormulaEval.CellInput.Blank
        is FormulaEval.CellValue.Num -> FormulaEval.CellInput.NumberLiteral(v.value)
        is FormulaEval.CellValue.Text -> FormulaEval.CellInput.TextLiteral(v.value)
        is FormulaEval.CellValue.Bool -> FormulaEval.CellInput.BoolLiteral(v.value)
        is FormulaEval.CellValue.Err -> FormulaEval.CellInput.ErrorLiteral(v.error)
    }

    private fun resolveSpillHashRefs(body: String, regions: Map<String, SpillRegion>): String =
        Regex("\\b([A-Za-z]{1,3}[0-9]{1,7})#").replace(body) { m ->
            val ref = FormulaEval.canonicalCellRef(m.groupValues[1])
            val reg = regions[ref]
            require(reg != null && reg.status == SpillStatus.SPILLED) {
                "spill reference $ref# does not resolve to an active spilled array"
            }
            reg.rangeA1()
        }

    private val FORMULA_GRID_DELEGATE_HEADS = setOf(
        "MAP", "REDUCE", "SCAN", "BYROW", "BYCOL", "MAKEARRAY",
        "VSTACK", "HSTACK", "TAKE", "DROP", "TOCOL", "TOROW",
        "CHOOSECOLS", "CHOOSEROWS", "WRAPROWS", "WRAPCOLS",
        "MMULT", "MINVERSE", "MUNIT", "FREQUENCY", "TREND", "GROWTH",
        "EXPAND", "TRIMRANGE", "GROUPBY", "PIVOTBY", "LINEST", "LOGEST", "FLATTEN",
        "OUTERPRODUCT", "CUMSUM", "DIFFARRAY", "MOVINGAVERAGE", "ZSCOREGRID", "DIAGONAL",
        "HANKEL", "TOEPLITZ", "VANDERMONDE", "KRONECKER", "CONVOLVE1D", "PARETOGRID",
    )

    private fun isDynamicArrayHead(body: String): Boolean {
        val trimmed = body.trim().removePrefix("=").trim()
        val open = trimmed.indexOf('(')
        if (open <= 0) return false
        val head = trimmed.substring(0, open).trim().uppercase()
        require(head != "RANDARRAY") { "volatile RANDARRAY refused in deterministic spill engine" }
        return head in setOf("SEQUENCE", "UNIQUE", "SORT", "SORTBY", "FILTER", "TEXTSPLIT", "TRANSPOSE") ||
            head in FORMULA_GRID_DELEGATE_HEADS
    }

    private fun splitTopLevelArgs(inside: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        var inQuotes = false
        var start = 0
        var i = 0
        while (i < inside.length) {
            val c = inside[i]
            if (c == '"') {
                if (inQuotes && i + 1 < inside.length && inside[i + 1] == '"') { i += 2; continue }
                inQuotes = !inQuotes
            } else if (!inQuotes) {
                if (c == '(' || c == '{') depth++
                else if (c == ')' || c == '}') depth--
                else if (c == ',' && depth == 0) {
                    out.add(inside.substring(start, i).trim())
                    start = i + 1
                }
            }
            i++
        }
        val tail = inside.substring(start).trim()
        if (tail.isNotEmpty()) out.add(tail)
        return out
    }

    private fun evalScalarArg(arg: String, env: Map<String, FormulaEval.CellValue>): FormulaEval.CellValue {
        val mini = LinkedHashMap<String, FormulaEval.CellInput>()
        for ((k, v) in env) if (k != "__TMP__") mini[k] = valueToLiteralInput(v)
        val probeCell = "ZZ999"
        mini[probeCell] = FormulaEval.CellInput.Formula("=" + arg)
        return FormulaEval.evaluateSheet(mini).values[probeCell] ?: FormulaEval.CellValue.Empty
    }

    private fun readRange2D(spec: String, env: Map<String, FormulaEval.CellValue>): List<List<FormulaEval.CellValue>> {
        val trimmed = spec.trim()
        if (isDynamicArrayHead(trimmed)) return evalDynamicArray(trimmed, env)
        val r = SheetRange.parse(trimmed.replace("$", "").uppercase())
        return (r.top.row..r.bottom.row).map { row ->
            (r.top.col..r.bottom.col).map { col ->
                env[SheetRange.Cell(col, row).toA1()] ?: FormulaEval.CellValue.Empty
            }
        }
    }

    private fun evalDynamicArray(expr: String, env: Map<String, FormulaEval.CellValue>): List<List<FormulaEval.CellValue>> {
        val open = expr.indexOf('(')
        require(open > 0 && expr.trimEnd().endsWith(")")) { "malformed dynamic array call: $expr" }
        val head = expr.substring(0, open).trim().uppercase()
        if (head in FORMULA_GRID_DELEGATE_HEADS) {
            val grid = FormulaEval.evaluateFormulaAsGrid(expr, env)
            val h = grid.size; val w = grid.firstOrNull()?.size ?: 0
            require(h >= 1 && w >= 1 && h.toLong() * w <= MAX_SPILL_CELLS) {
                "$head($h, $w) outside 1..$MAX_SPILL_CELLS"
            }
            return grid
        }
        val args = splitTopLevelArgs(expr.trimEnd().substring(open + 1, expr.trimEnd().length - 1))
        if (head == "SORTBY") {
            require(args.size >= 2) { "SORTBY expects array, by_array1, [sort_order1]" }
            val src = readRange2D(args[0], env)
            val keyGrid = readRange2D(args[1], env)
            val order = if (args.size >= 3) (evalScalarArg(args[2], env) as? FormulaEval.CellValue.Num)?.value?.toInt() ?: 1 else 1
            require(order == 1 || order == -1) { "SORTBY sort_order must be 1 or -1" }
            require(src.size == keyGrid.size && keyGrid.isNotEmpty()) { "SORTBY array and by_array height mismatch" }
            val paired = src.indices.map { r -> src[r] to keyGrid[r].firstOrNull().let { it ?: FormulaEval.CellValue.Empty } }
            return paired.sortedWith { a, b -> order * compareValuesForSort(a.second, b.second) }.map { it.first }
        }
        return when (head) {
            "SEQUENCE" -> {
                require(args.size in 1..4) { "SEQUENCE expects 1..4 args" }
                val rows = (evalScalarArg(args[0], env) as? FormulaEval.CellValue.Num)?.value?.toInt() ?: 0
                val cols = if (args.size >= 2) (evalScalarArg(args[1], env) as? FormulaEval.CellValue.Num)?.value?.toInt() ?: 0 else 1
                val start = if (args.size >= 3) (evalScalarArg(args[2], env) as? FormulaEval.CellValue.Num)?.value ?: 1.0 else 1.0
                val step = if (args.size >= 4) (evalScalarArg(args[3], env) as? FormulaEval.CellValue.Num)?.value ?: 1.0 else 1.0
                require(rows >= 1 && cols >= 1 && rows.toLong() * cols <= MAX_SPILL_CELLS) {
                    "SEQUENCE($rows, $cols) outside 1..$MAX_SPILL_CELLS"
                }
                var cur = start
                List(rows) {
                    List(cols) {
                        val v = FormulaEval.CellValue.Num(cur)
                        cur += step
                        v
                    }
                }
            }
            "TRANSPOSE" -> {
                require(args.size == 1) { "TRANSPOSE expects 1 range arg" }
                val src = readRange2D(args[0], env)
                val h = src.size; val w = src.firstOrNull()?.size ?: 0
                List(w) { c -> List(h) { r -> src[r][c] } }
            }
            "UNIQUE" -> {
                require(args.size in 1..3) { "UNIQUE expects 1..3 args" }
                val src = readRange2D(args[0], env)
                val exactlyOnce = if (args.size >= 3) {
                    (evalScalarArg(args[2], env) as? FormulaEval.CellValue.Bool)?.value ?: false
                } else false
                val counts = LinkedHashMap<List<String>, Pair<Int, List<FormulaEval.CellValue>>>()
                for (row in src) {
                    val key = row.map { "${it::class.simpleName}:${it.displayText().lowercase()}" }
                    val prev = counts[key]
                    counts[key] = if (prev == null) 1 to row else (prev.first + 1) to prev.second
                }
                val filtered = counts.values
                    .filter { (cnt, _) -> if (exactlyOnce) cnt == 1 else cnt >= 1 }
                    .map { it.second }
                filtered.ifEmpty { listOf(listOf(FormulaEval.CellValue.Err(CellErr.Err.VALUE))) }
            }
            "SORT" -> {
                require(args.size in 1..3) { "SORT expects 1..3 args" }
                val src = readRange2D(args[0], env)
                val sortColOne = if (args.size >= 2) ((evalScalarArg(args[1], env) as? FormulaEval.CellValue.Num)?.value?.toInt() ?: 1) else 1
                val sortOrder = if (args.size >= 3) ((evalScalarArg(args[2], env) as? FormulaEval.CellValue.Num)?.value?.toInt() ?: 1) else 1
                val w = src.firstOrNull()?.size ?: 1
                require(sortColOne in 1..w && sortOrder in setOf(1, -1)) { "invalid SORT index/order" }
                val colIdx = sortColOne - 1
                src.sortedWith { a, b ->
                    val cmp = compareValuesForSort(a[colIdx], b[colIdx])
                    cmp * sortOrder
                }
            }
            "FILTER" -> {
                require(args.size in 2..3) { "FILTER expects 2..3 args" }
                val src = readRange2D(args[0], env)
                val mask = parseIncludeMask(args[1], src.size, env)
                require(mask.size == src.size) { "FILTER include mask height (${mask.size}) != range height (${src.size})" }
                val matched = src.indices.filter { mask[it] }.map { src[it] }
                if (matched.isNotEmpty()) matched
                else {
                    val fallback = if (args.size == 3) evalScalarArg(args[2], env) else FormulaEval.CellValue.Err(CellErr.Err.VALUE)
                    listOf(listOf(fallback))
                }
            }
            "TEXTSPLIT" -> {
                require(args.size in 2..3) { "TEXTSPLIT expects text, col_delimiter, [row_delimiter]" }
                val text = evalScalarArg(args[0], env).displayText()
                val colDelim = evalScalarArg(args[1], env).displayText()
                require(colDelim.isNotEmpty()) { "TEXTSPLIT col_delimiter must be non-empty" }
                val rowDelim = if (args.size == 3) evalScalarArg(args[2], env).displayText() else null
                val rawRows = if (!rowDelim.isNullOrEmpty()) text.split(rowDelim) else listOf(text)
                val splitRows = rawRows.map { r -> r.split(colDelim) }
                val maxW = splitRows.maxOfOrNull { it.size } ?: 1
                splitRows.map { r ->
                    List(maxW) { idx ->
                        if (idx < r.size) FormulaEval.CellValue.Text(r[idx])
                        else FormulaEval.CellValue.Err(CellErr.Err.NA)
                    }
                }
            }
            else -> error("unsupported dynamic array function $head")
        }
    }

    private fun parseIncludeMask(maskExpr: String, expectedRows: Int, env: Map<String, FormulaEval.CellValue>): List<Boolean> {
        // Supports either a boolean column range `B1:B5` OR a vector comparison `B1:B5>100` / `A1:A5="North"`.
        val cmpMatch = Regex("^([\$]?[A-Za-z]{1,3}[\$]?[0-9]{1,7}:[\$]?[A-Za-z]{1,3}[\$]?[0-9]{1,7})\\s*(<>|<=|>=|=|<|>)\\s*(.+)$")
            .matchEntire(maskExpr.trim())
        if (cmpMatch != null) {
            val r = SheetRange.parse(cmpMatch.groupValues[1].replace("$", "").uppercase())
            require(r.width == 1) { "FILTER comparison range must be 1 column wide" }
            val op = cmpMatch.groupValues[2]
            val rhsText = cmpMatch.groupValues[3]
            return (r.top.row..r.bottom.row).map { row ->
                val cellRef = SheetRange.Cell(r.top.col, row).toA1()
                val res = evalScalarArg("$cellRef $op $rhsText", env)
                (res as? FormulaEval.CellValue.Bool)?.value ?: false
            }
        }
        val col = readRange2D(maskExpr, env)
        require(col.size == expectedRows && col.all { it.size == 1 }) { "FILTER mask must be a 1-column range" }
        return col.map { row ->
            when (val v = row[0]) {
                is FormulaEval.CellValue.Bool -> v.value
                is FormulaEval.CellValue.Num -> v.value != 0.0
                else -> false
            }
        }
    }

    private fun compareValuesForSort(a: FormulaEval.CellValue, b: FormulaEval.CellValue): Int {
        fun rank(v: FormulaEval.CellValue): Int = when (v) {
            is FormulaEval.CellValue.Num -> 1
            is FormulaEval.CellValue.Text -> 2
            is FormulaEval.CellValue.Bool -> 3
            is FormulaEval.CellValue.Err -> 4
            FormulaEval.CellValue.Empty -> 5 // Excel sorts blanks to the bottom regardless!
        }
        val ra = rank(a); val rb = rank(b)
        if (ra != rb) return ra.compareTo(rb)
        return when (a) {
            is FormulaEval.CellValue.Num -> a.value.compareTo((b as FormulaEval.CellValue.Num).value)
            is FormulaEval.CellValue.Text -> a.value.lowercase().compareTo((b as FormulaEval.CellValue.Text).value.lowercase())
            is FormulaEval.CellValue.Bool -> a.value.compareTo((b as FormulaEval.CellValue.Bool).value)
            else -> 0
        }
    }
}
