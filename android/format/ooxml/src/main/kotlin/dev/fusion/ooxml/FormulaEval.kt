// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sqrt
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Clean-room SpreadsheetML reactive dependency-DAG formula engine over [ColLetters],
 * [SheetRange], and [CellErr] (`ECMA-376 Part 1 §18.17`).
 *
 * Advances `:format:ooxml` beyond `DecimalSheet` (`<=256` arithmetic-only cells, no strings,
 * no booleans, no error propagation, no ranges beyond `SUM`) into a typed spreadsheet kernel:
 * - Values: [CellValue.Empty], [CellValue.Num], [CellValue.Text], [CellValue.Bool], [CellValue.Err].
 * - Operators: `= <> < <= > >=`, `&`, `+ -`, `* /`, `^`, unary `+/-`, postfix `%`.
 * - Cell & rectangular range references (`A1`, `$B$2`, `A1:C10`) with `$` anchoring accepted.
 * - 19 pure functions: `SUM`, `AVERAGE`, `MIN`, `MAX`, `COUNT`, `COUNTA`, `IF` (lazy),
 *   `IFERROR` (lazy), `AND`, `OR`, `NOT`, `ABS`, `ROUND`, `MOD`, `INT`, `LEN`, `CONCAT`,
 *   `INDEX`, `MATCH` (exact `match_type = 0`).
 * - Topological evaluation refusing circular dependencies with an explicit witness cycle, plus
 *   [dirtyDependents] for incremental reactive recalculation when upstream cells change.
 */
object FormulaEval {
    const val SCOPE =
        "Typed single-sheet DAG formula engine (300 pure functions R53 expanding 260 pure functions R52 expanding 215 pure functions expanding 168 pure functions R50, 130 pure functions R49, 95 pure functions R48, 61 pure functions R47, and 35 pure functions R46 baselines: HANKEL/TOEPLITZ/VANDERMONDE/KRONECKER/CONVOLVE1D/PARETOGRID + COMPLEX/IMREAL/IMAGINARY/IMABS/IMARGUMENT/IMCONJUGATE/IMSUM/IMSUB/IMPRODUCT/IMDIV/IMPOWER/IMSQRT/IMEXP/IMLN/IMSIN/IMCOS + COUPDAYS/COUPDAYBS/COUPDAYSNC/COUPNUM/COUPPCD/COUPNCD/DURATION/MDURATION + BETA.DIST/TDIST.2T/TDIST.RT/F.DIST.RT/FISHER/FISHERINV/STANDARDIZE/CONFIDENCE.T/HARMEAN/TRIMMEAN/DEVSQ/AVEDEV/SERIESSUM/MULTINOMIAL + OUTERPRODUCT/CUMSUM/DIFFARRAY/MOVINGAVERAGE/ZSCOREGRID/DIAGONAL + WEIBULL.DIST/HYPGEOM.DIST/NEGBINOM.DIST/LOGNORM.DIST/LOGNORM.INV/GAMMA/GAMMA.DIST/CHISQ.DIST/CHISQ.DIST.RT/BESSELJ/BESSELI/ERF.PRECISE/ERFC.PRECISE + TBILLEQ/TBILLPRICE/TBILLYIELD/DISC/INTRATE/RECEIVED/PRICEDISC/YIELDDISC + PERMUTATIONA/SEC/CSC/COT/SECH/CSCH/COTH/ACOT/ACOTH/OCT2BIN/OCT2HEX/HEX2OCT/BIN2OCT/TEXTBEFORE/TEXTAFTER/ENCODEURL/UNICHAR/UNICODE + EXPAND/TRIMRANGE/GROUPBY/PIVOTBY/LINEST/LOGEST/FLATTEN + NORM.S.DIST/NORM.S.INV/NORM.DIST/POISSON.DIST/BINOM.DIST/EXPON.DIST/CONFIDENCE.NORM/GEOMEAN/SKEW/KURT/RSQ/STEYX/COVARIANCE.P/COVARIANCE.S + SLN/SYD/DDB/MIRR/IPMT/PPMT/CUMIPMT/CUMPRINC/EFFECT/NOMINAL + BASE/DECIMAL/ROMAN/ARABIC/COMBINA/FACTDOUBLE/QUOTIENT/MROUND/SQRTPI/DEGREES/RADIANS/ISOWEEKNUM/ADDRESS/PERCENTOF/ARRAYTOTEXT/VALUETOTEXT + matrix MMULT/MINVERSE/MDETERM/MUNIT + FREQUENCY/TREND/GROWTH + lexical LAMBDA closures + ISOMITTED + HOFs + dynamic array shapers, ranges, CellErr propagation, cycle refusal, dirty-dependent closure); volatile NOW/RAND/INDIRECT and single-sheet 3D refs refused."

    const val MAX_CELLS = 16_384
    const val MAX_RANGE_CELLS = 16_384L
    const val MAX_FORMULA_CHARS = 4_096
    const val MAX_AST_DEPTH = 64
    const val MAX_LAMBDA_CALL_DEPTH = 32

    private val VOLATILE_OR_DYNAMIC = setOf(
        "NOW", "TODAY", "RAND", "RANDBETWEEN", "INDIRECT", "OFFSET", "CELL", "INFO",
    )

    sealed interface CellValue {
        data object Empty : CellValue
        data class Num(val value: Double) : CellValue {
            init {
                require(value.isFinite()) { "non-finite spreadsheet number $value" }
            }
        }
        data class Text(val value: String) : CellValue
        data class Bool(val value: Boolean) : CellValue
        data class Err(val error: CellErr.Err) : CellValue

        fun displayText(): String = when (this) {
            Empty -> ""
            is Num -> formatCanonicalNumber(value)
            is Text -> value
            is Bool -> if (value) "TRUE" else "FALSE"
            is Err -> error.literal
        }
    }

    sealed interface CellInput {
        data object Blank : CellInput
        data class NumberLiteral(val value: Double) : CellInput {
            init {
                require(value.isFinite()) { "non-finite number literal $value" }
            }
        }
        data class TextLiteral(val value: String) : CellInput
        data class BoolLiteral(val value: Boolean) : CellInput
        data class ErrorLiteral(val error: CellErr.Err) : CellInput
        data class Formula(val expression: String) : CellInput {
            val body: String = expression.trim().removePrefix("=").trim()
            init {
                require(body.isNotEmpty() && body.length <= MAX_FORMULA_CHARS) {
                    "formula length outside 1..$MAX_FORMULA_CHARS"
                }
            }
        }
    }

    data class EvaluatedSheet(
        val values: Map<String, CellValue>,
        val evaluationOrder: List<String>,
        val dependencies: Map<String, Set<String>>,
    )

    fun evaluateSheet(cells: Map<String, CellInput>): EvaluatedSheet = evaluateSheet(cells, materializeFormulaResults = true)

    // SpillSheetEngine uses a temporary cell to evaluate a subexpression, not an actual
    // output cell. Preserve the intermediate Empty value in that private probe context.
    internal fun evaluateSheetForSubexpression(cells: Map<String, CellInput>): EvaluatedSheet =
        evaluateSheet(cells, materializeFormulaResults = false)

    private fun evaluateSheet(cells: Map<String, CellInput>, materializeFormulaResults: Boolean): EvaluatedSheet {
        require(cells.size <= MAX_CELLS) { "sheet exceeds $MAX_CELLS populated cells" }
        val canonicalInputs = LinkedHashMap<String, CellInput>(cells.size)
        for ((rawRef, inp) in cells) {
            val norm = canonicalCellRef(rawRef)
            require(norm !in canonicalInputs) { "duplicate cell reference '$rawRef' -> '$norm'" }
            canonicalInputs[norm] = inp
        }

        val parsedAsts = LinkedHashMap<String, Expr>()
        val deps = LinkedHashMap<String, Set<String>>()
        for ((ref, inp) in canonicalInputs) {
            if (inp is CellInput.Formula) {
                val ast = Parser(inp.body).parseFull()
                parsedAsts[ref] = ast
                val d = LinkedHashSet<String>()
                collectDeps(ast, d)
                deps[ref] = d
            } else {
                deps[ref] = emptySet()
            }
        }

        val order = topoSort(canonicalInputs.keys, deps)
        val evaluated = LinkedHashMap<String, CellValue>(canonicalInputs.size)
        for (ref in order) {
            val inp = canonicalInputs[ref]!!
            evaluated[ref] = when (inp) {
                CellInput.Blank -> CellValue.Empty
                is CellInput.NumberLiteral -> CellValue.Num(normalizeNegZero(inp.value))
                is CellInput.TextLiteral -> CellValue.Text(inp.value)
                is CellInput.BoolLiteral -> CellValue.Bool(inp.value)
                is CellInput.ErrorLiteral -> CellValue.Err(inp.error)
                is CellInput.Formula -> {
                    // An empty reference can remain empty *inside* an expression (for example,
                    // a range aggregate ignores an empty source). Once a scalar formula result
                    // is stored in a cell, however, Excel-style evaluation exposes that result as
                    // numeric zero rather than an unoccupied cell. Do not conflate this with a
                    // formula returning explicit empty text, which remains a counted text value.
                    val result = evalExpr(parsedAsts[ref]!!, evaluated)
                    if (materializeFormulaResults && result == CellValue.Empty) CellValue.Num(0.0) else result
                }
            }
        }
        return EvaluatedSheet(evaluated, order, deps)
    }

    /**
     * Returns all transitive downstream formula cells affected by mutating [changedRefs],
     * ordered topologically so an incremental recalculator visits every dependency once.
     */
    fun dirtyDependents(dependencies: Map<String, Set<String>>, changedRefs: Set<String>): List<String> {
        val reverse = HashMap<String, MutableList<String>>()
        for ((cell, upstreams) in dependencies) {
            for (u in upstreams) reverse.getOrPut(u) { ArrayList() }.add(cell)
        }
        val dirty = LinkedHashSet<String>()
        val queue = ArrayDeque<String>()
        for (raw in changedRefs) {
            val norm = canonicalCellRef(raw)
            queue.addLast(norm)
        }
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (down in reverse[cur].orEmpty()) {
                if (dirty.add(down)) queue.addLast(down)
            }
        }
        if (dirty.isEmpty()) return emptyList()
        val subDeps = dirty.associateWith { cell -> dependencies[cell].orEmpty().intersect(dirty) }
        return topoSort(dirty, subDeps)
    }

    fun canonicalCellRef(raw: String): String =
        SheetRange.parseCell(raw.trim().replace("$", "")).toA1()

    fun formatCanonicalNumber(v: Double): String {
        val clean = normalizeNegZero(v)
        val asLong = clean.toLong()
        return if (clean == asLong.toDouble() && abs(clean) < 1e15) {
            asLong.toString()
        } else {
            clean.toString()
        }
    }

    private fun normalizeNegZero(v: Double): Double = if (v == 0.0) 0.0 else v

    // ---- AST & Parser ------------------------------------------------------

    private sealed interface Expr {
        data object OmittedArg : Expr
        data class NumLit(val v: Double) : Expr
        data class StrLit(val v: String) : Expr
        data class BoolLit(val v: Boolean) : Expr
        data class ErrLit(val e: CellErr.Err) : Expr
        data class CellRef(val a1: String) : Expr
        data class SymbolRef(val ident: String) : Expr
        data class RangeRef(val range: SheetRange.Range) : Expr
        data class ArrayLit(val rows: List<List<Expr>>) : Expr
        data class Unary(val op: Char, val arg: Expr) : Expr
        data class Percent(val arg: Expr) : Expr
        data class Binary(val op: String, val left: Expr, val right: Expr) : Expr
        data class Call(val name: String, val args: List<Expr>) : Expr
        data class Invoke(val callee: Expr, val args: List<Expr>) : Expr
    }

    private sealed interface RichBinding {
        data object Omitted : RichBinding
        data class Grid(val rows: List<List<CellValue>>) : RichBinding
        data class Closure(
            val params: List<String>,
            val body: Expr,
            val capturedEnv: Map<String, CellValue>,
            val capturedRich: Map<String, RichBinding>,
        ) : RichBinding
    }

    private class EvalRuntimeState {
        var richScopes: Map<String, RichBinding> = emptyMap()
        var lambdaDepth: Int = 0
    }

    private val runtimeState = ThreadLocal.withInitial { EvalRuntimeState() }

    /**
     * Evaluates an arbitrary spreadsheet formula (including `LET`, `LAMBDA`, `MAP`, `SCAN`,
     * `BYROW`, `BYCOL`, `MAKEARRAY`, `VSTACK`, `HSTACK`, `TAKE`, `DROP`, `TOCOL`, `TOROW`,
     * `CHOOSECOLS`, `CHOOSEROWS`, `WRAPROWS`, `WRAPCOLS`) to its full 2D grid representation
     * for [SpillSheetEngine].
     */
    fun evaluateFormulaAsGrid(formulaExpression: String, env: Map<String, CellValue>): List<List<CellValue>> {
        val body = formulaExpression.trim().removePrefix("=").trim()
        require(body.isNotEmpty() && body.length <= MAX_FORMULA_CHARS) { "formula length outside 1..$MAX_FORMULA_CHARS" }
        val ast = Parser(body).parseFull()
        val rt = runtimeState.get()
        val prevRich = rt.richScopes
        val prevDepth = rt.lambdaDepth
        rt.richScopes = emptyMap()
        rt.lambdaDepth = 0
        return try {
            exprTo2DGrid(ast, env) ?: listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
        } finally {
            rt.richScopes = prevRich
            rt.lambdaDepth = prevDepth
        }
    }

    private fun normalizeFnName(raw: String): String {
        var u = raw.uppercase()
        if (u.startsWith("_XLFN._XLWS.")) u = u.removePrefix("_XLFN._XLWS.")
        else if (u.startsWith("_XLFN.")) u = u.removePrefix("_XLFN.")
        return u
    }

    private fun normalizeVarName(raw: String): String =
        raw.uppercase().removePrefix("_XLPM.")

    private fun exprParamName(e: Expr): String? = when (e) {
        is Expr.SymbolRef -> normalizeVarName(e.ident)
        is Expr.CellRef -> normalizeVarName(e.a1)
        else -> null
    }

    private fun collectDeps(e: Expr, out: MutableSet<String>, boundVars: Set<String> = emptySet()) {
        when (e) {
            is Expr.CellRef -> if (normalizeVarName(e.a1) !in boundVars) out.add(e.a1)
            is Expr.SymbolRef -> {}
            is Expr.RangeRef -> {
                require(e.range.cells <= MAX_RANGE_CELLS) {
                    "range ${e.range.toA1()} spans ${e.range.cells} cells (max $MAX_RANGE_CELLS)"
                }
                for (r in e.range.top.row..e.range.bottom.row) {
                    for (c in e.range.top.col..e.range.bottom.col) {
                        out.add(SheetRange.Cell(c, r).toA1())
                    }
                }
            }
            is Expr.ArrayLit -> e.rows.forEach { row -> row.forEach { collectDeps(it, out, boundVars) } }
            is Expr.Unary -> collectDeps(e.arg, out, boundVars)
            is Expr.Percent -> collectDeps(e.arg, out, boundVars)
            is Expr.Binary -> { collectDeps(e.left, out, boundVars); collectDeps(e.right, out, boundVars) }
            is Expr.Call -> {
                val fnNorm = normalizeFnName(e.name)
                if (fnNorm == "LET" && e.args.size >= 3 && e.args.size % 2 == 1) {
                    val scoped = HashSet(boundVars)
                    var i = 0
                    while (i < e.args.size - 1) {
                        collectDeps(e.args[i + 1], out, scoped)
                        exprParamName(e.args[i])?.let { scoped.add(it) }
                        i += 2
                    }
                    collectDeps(e.args.last(), out, scoped)
                } else if (fnNorm == "LAMBDA" && e.args.size >= 1) {
                    val scoped = HashSet(boundVars)
                    for (i in 0 until e.args.size - 1) {
                        exprParamName(e.args[i])?.let { scoped.add(it) }
                    }
                    collectDeps(e.args.last(), out, scoped)
                } else {
                    e.args.forEach { collectDeps(it, out, boundVars) }
                }
            }
            is Expr.Invoke -> {
                collectDeps(e.callee, out, boundVars)
                e.args.forEach { collectDeps(it, out, boundVars) }
            }
            else -> {}
        }
    }

    private fun topoSort(nodes: Set<String>, deps: Map<String, Set<String>>): List<String> {
        val state = HashMap<String, Int>(nodes.size) // 0=unvisited, 1=visiting, 2=done
        val stack = ArrayList<String>()
        val order = ArrayList<String>(nodes.size)

        fun dfs(u: String) {
            state[u] = 1
            stack.add(u)
            val upstreams = deps[u].orEmpty().sorted()
            for (v in upstreams) {
                if (v !in nodes) continue
                when (state[v] ?: 0) {
                    1 -> {
                        val idx = stack.indexOf(v)
                        val cycle = (stack.subList(idx, stack.size) + v).joinToString(" -> ")
                        throw IllegalArgumentException("circular formula dependency: $cycle")
                    }
                    0 -> dfs(v)
                }
            }
            stack.removeAt(stack.size - 1)
            state[u] = 2
            order.add(u)
        }

        for (n in nodes.sorted()) {
            if ((state[n] ?: 0) == 0) dfs(n)
        }
        return order
    }

    // ---- Evaluation --------------------------------------------------------

    private fun evalExpr(e: Expr, env: Map<String, CellValue>): CellValue = when (e) {
        Expr.OmittedArg -> CellValue.Empty
        is Expr.NumLit -> CellValue.Num(normalizeNegZero(e.v))
        is Expr.StrLit -> CellValue.Text(e.v)
        is Expr.BoolLit -> CellValue.Bool(e.v)
        is Expr.ErrLit -> CellValue.Err(e.e)
        is Expr.CellRef -> {
            val vKey = normalizeVarName(e.a1)
            when (val rb = runtimeState.get().richScopes[vKey]) {
                RichBinding.Omitted -> CellValue.Empty
                is RichBinding.Grid -> if (rb.rows.size == 1 && rb.rows[0].size == 1) rb.rows[0][0] else CellValue.Err(CellErr.Err.VALUE)
                is RichBinding.Closure -> CellValue.Err(CellErr.Err.VALUE)
                null -> env["\u0000LET:$vKey"] ?: env[e.a1] ?: CellValue.Empty
            }
        }
        is Expr.SymbolRef -> {
            val vKey = normalizeVarName(e.ident)
            when (val rb = runtimeState.get().richScopes[vKey]) {
                RichBinding.Omitted -> CellValue.Empty
                is RichBinding.Grid -> if (rb.rows.size == 1 && rb.rows[0].size == 1) rb.rows[0][0] else CellValue.Err(CellErr.Err.VALUE)
                is RichBinding.Closure -> CellValue.Err(CellErr.Err.VALUE)
                null -> env["\u0000LET:$vKey"] ?: CellValue.Err(CellErr.Err.NAME)
            }
        }
        is Expr.RangeRef -> CellValue.Err(CellErr.Err.VALUE) // bare range in scalar context -> #VALUE!
        is Expr.ArrayLit -> if (e.rows.size == 1 && e.rows[0].size == 1) evalExpr(e.rows[0][0], env) else CellValue.Err(CellErr.Err.VALUE)
        is Expr.Unary -> {
            val v = evalScalarAsNum(evalExpr(e.arg, env))
            if (v is CellValue.Err) v else {
                val n = (v as CellValue.Num).value
                CellValue.Num(normalizeNegZero(if (e.op == '-') -n else n))
            }
        }
        is Expr.Percent -> {
            val v = evalScalarAsNum(evalExpr(e.arg, env))
            if (v is CellValue.Err) v else {
                val r = (v as CellValue.Num).value / 100.0
                if (!r.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(r))
            }
        }
        is Expr.Binary -> evalBinary(e.op, evalExpr(e.left, env), evalExpr(e.right, env))
        is Expr.Call -> evalCall(e.name, e.args, env)
        is Expr.Invoke -> evalInvoke(e.callee, e.args, env)
    }

    private fun evalScalarAsNum(v: CellValue): CellValue = when (v) {
        is CellValue.Err -> v
        is CellValue.Num -> v
        CellValue.Empty -> CellValue.Num(0.0)
        is CellValue.Bool -> CellValue.Num(if (v.value) 1.0 else 0.0)
        is CellValue.Text -> v.value.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
            ?.let { CellValue.Num(normalizeNegZero(it)) }
            ?: CellValue.Err(CellErr.Err.VALUE)
    }

    private fun evalAsString(v: CellValue): CellValue = when (v) {
        is CellValue.Err -> v
        else -> CellValue.Text(v.displayText())
    }

    private fun evalAsBool(v: CellValue): CellValue = when (v) {
        is CellValue.Err -> v
        is CellValue.Bool -> v
        is CellValue.Num -> CellValue.Bool(v.value != 0.0)
        CellValue.Empty -> CellValue.Bool(false)
        is CellValue.Text -> when (v.value.trim().uppercase()) {
            "TRUE" -> CellValue.Bool(true)
            "FALSE" -> CellValue.Bool(false)
            else -> CellValue.Err(CellErr.Err.VALUE)
        }
    }

    private fun evalBinary(op: String, left: CellValue, right: CellValue): CellValue {
        if (left is CellValue.Err) return left
        if (right is CellValue.Err) return right
        if (op == "&") {
            val ls = (evalAsString(left) as CellValue.Text).value
            val rs = (evalAsString(right) as CellValue.Text).value
            return CellValue.Text(ls + rs)
        }
        if (op in setOf("=", "<>", "<", "<=", ">", ">=")) {
            val cmp = compareSpreadsheetValues(left, right)
            val ans = when (op) {
                "=" -> cmp == 0
                "<>" -> cmp != 0
                "<" -> cmp < 0
                "<=" -> cmp <= 0
                ">" -> cmp > 0
                ">=" -> cmp >= 0
                else -> error("unreachable")
            }
            return CellValue.Bool(ans)
        }
        val ln = evalScalarAsNum(left)
        if (ln is CellValue.Err) return ln
        val rn = evalScalarAsNum(right)
        if (rn is CellValue.Err) return rn
        val a = (ln as CellValue.Num).value
        val b = (rn as CellValue.Num).value
        val res = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> {
                if (b == 0.0) return CellValue.Err(CellErr.Err.DIV0)
                a / b
            }
            "^" -> a.pow(b)
            else -> error("unknown binary operator '$op'")
        }
        return if (!res.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(res))
    }

    /** ECMA-376 type rank ordering: Number < Text < Boolean (case-insensitive text comparison). */
    private fun compareSpreadsheetValues(a: CellValue, b: CellValue): Int {
        fun normEmpty(v: CellValue, peer: CellValue): CellValue = if (v != CellValue.Empty) v else when (peer) {
            is CellValue.Text -> CellValue.Text("")
            is CellValue.Bool -> CellValue.Bool(false)
            else -> CellValue.Num(0.0)
        }
        val lhs = normEmpty(a, b)
        val rhs = normEmpty(b, a)
        fun rank(v: CellValue): Int = when (v) {
            is CellValue.Num -> 1
            is CellValue.Text -> 2
            is CellValue.Bool -> 3
            else -> 0
        }
        val rl = rank(lhs)
        val rr = rank(rhs)
        if (rl != rr) return rl.compareTo(rr)
        return when (lhs) {
            is CellValue.Num -> lhs.value.compareTo((rhs as CellValue.Num).value)
            is CellValue.Text -> lhs.value.lowercase().compareTo((rhs as CellValue.Text).value.lowercase())
            is CellValue.Bool -> lhs.value.compareTo((rhs as CellValue.Bool).value)
            else -> 0
        }
    }

    private fun flattenArgValues(args: List<Expr>, env: Map<String, CellValue>): List<Pair<CellValue, Boolean>> {
        // Pair(value, fromRangeOrRef) because in Excel SUM("5") coerces direct arg "5" -> 5, while SUM(A1:A2) ignores text/bool cells in ranges!
        val out = ArrayList<Pair<CellValue, Boolean>>()
        for (arg in args) {
            when (arg) {
                is Expr.RangeRef -> {
                    for (r in arg.range.top.row..arg.range.bottom.row) {
                        for (c in arg.range.top.col..arg.range.bottom.col) {
                            val v = env[SheetRange.Cell(c, r).toA1()] ?: CellValue.Empty
                            out.add(v to true)
                        }
                    }
                }
                is Expr.CellRef -> {
                    val vKey = normalizeVarName(arg.a1)
                    val rb = runtimeState.get().richScopes[vKey]
                    if (rb is RichBinding.Grid) {
                        for (row in rb.rows) for (item in row) out.add(item to true)
                    } else {
                        out.add((env["\u0000LET:$vKey"] ?: env[arg.a1] ?: CellValue.Empty) to true)
                    }
                }
                is Expr.SymbolRef -> {
                    val vKey = normalizeVarName(arg.ident)
                    val rb = runtimeState.get().richScopes[vKey]
                    if (rb is RichBinding.Grid) {
                        for (row in rb.rows) for (item in row) out.add(item to true)
                    } else {
                        out.add(evalExpr(arg, env) to false)
                    }
                }
                is Expr.ArrayLit -> {
                    for (row in arg.rows) {
                        for (item in row) out.add(evalExpr(item, env) to true)
                    }
                }
                is Expr.Call -> {
                    val maybeGrid = tryEvalDynamicGridCall(arg, env)
                    if (maybeGrid != null) {
                        for (row in maybeGrid) for (item in row) out.add(item to true)
                    } else {
                        out.add(evalExpr(arg, env) to false)
                    }
                }
                else -> out.add(evalExpr(arg, env) to false)
            }
        }
        return out
    }

    private fun collectNumericAggregate(args: List<Expr>, env: Map<String, CellValue>): Result<List<Double>> {
        if (args.isEmpty()) return Result.failure(CellErrorSignal(CellErr.Err.VALUE))
        val nums = ArrayList<Double>()
        for ((v, fromRef) in flattenArgValues(args, env)) {
            when (v) {
                is CellValue.Err -> return Result.failure(CellErrorSignal(v.error))
                is CellValue.Num -> nums.add(v.value)
                CellValue.Empty -> {}
                is CellValue.Bool -> if (!fromRef) nums.add(if (v.value) 1.0 else 0.0)
                is CellValue.Text -> if (!fromRef) {
                    val parsed = v.value.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
                        ?: return Result.failure(CellErrorSignal(CellErr.Err.VALUE))
                    nums.add(parsed)
                }
            }
        }
        return Result.success(nums)
    }

    private class CellErrorSignal(val err: CellErr.Err) : RuntimeException(null, null, false, false)

    private fun evalCall(rawName: String, args: List<Expr>, env: Map<String, CellValue>): CellValue {
        val name = normalizeFnName(rawName)
        require(name !in VOLATILE_OR_DYNAMIC) {
            "volatile or dynamic-reference function '$name' is refused in deterministic scope"
        }
        return when (name) {
            "SUM" -> {
                val res = collectNumericAggregate(args, env)
                res.fold(
                    onSuccess = { nums ->
                        val s = nums.sum()
                        if (!s.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(s))
                    },
                    onFailure = { CellValue.Err((it as CellErrorSignal).err) },
                )
            }
            "SUBTOTAL" -> {
                if (args.size < 2) return CellValue.Err(CellErr.Err.VALUE)
                val codeVal = evalScalarAsNum(evalExpr(args[0], env))
                if (codeVal is CellValue.Err) return codeVal
                val rawCode = (codeVal as CellValue.Num).value.toInt()
                val baseCode = if (rawCode in 101..111) rawCode - 100 else rawCode
                val rest = args.drop(1)
                when (baseCode) {
                    1 -> evalCall("AVERAGE", rest, env)
                    2 -> evalCall("COUNT", rest, env)
                    3 -> evalCall("COUNTA", rest, env)
                    4 -> evalCall("MAX", rest, env)
                    5 -> evalCall("MIN", rest, env)
                    6 -> {
                        val res = collectNumericAggregate(rest, env)
                        res.fold(
                            onSuccess = { nums ->
                                val p = if (nums.isEmpty()) 0.0 else nums.fold(1.0) { acc, x -> acc * x }
                                if (!p.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(p))
                            },
                            onFailure = { CellValue.Err((it as CellErrorSignal).err) },
                        )
                    }
                    7 -> evalCall("STDEV", rest, env)
                    9 -> evalCall("SUM", rest, env)
                    else -> CellValue.Err(CellErr.Err.VALUE)
                }
            }
            "AVERAGE" -> {
                val res = collectNumericAggregate(args, env)
                res.fold(
                    onSuccess = { nums ->
                        if (nums.isEmpty()) CellValue.Err(CellErr.Err.DIV0)
                        else CellValue.Num(normalizeNegZero(nums.sum() / nums.size))
                    },
                    onFailure = { CellValue.Err((it as CellErrorSignal).err) },
                )
            }
            "MIN" -> {
                val res = collectNumericAggregate(args, env)
                res.fold(
                    onSuccess = { nums -> CellValue.Num(normalizeNegZero(nums.minOrNull() ?: 0.0)) },
                    onFailure = { CellValue.Err((it as CellErrorSignal).err) },
                )
            }
            "MAX" -> {
                val res = collectNumericAggregate(args, env)
                res.fold(
                    onSuccess = { nums -> CellValue.Num(normalizeNegZero(nums.maxOrNull() ?: 0.0)) },
                    onFailure = { CellValue.Err((it as CellErrorSignal).err) },
                )
            }
            "COUNT" -> {
                if (args.isEmpty()) return CellValue.Err(CellErr.Err.VALUE)
                var count = 0
                for ((v, fromRef) in flattenArgValues(args, env)) {
                    when (v) {
                        is CellValue.Num -> count++
                        is CellValue.Bool -> if (!fromRef) count++
                        is CellValue.Text -> if (!fromRef && v.value.trim().toDoubleOrNull()?.isFinite() == true) count++
                        else -> {} // COUNT does NOT error on error cells per ECMA-376!
                    }
                }
                CellValue.Num(count.toDouble())
            }
            "COUNTA" -> {
                if (args.isEmpty()) return CellValue.Err(CellErr.Err.VALUE)
                val count = flattenArgValues(args, env).count { (v, _) -> v != CellValue.Empty }
                CellValue.Num(count.toDouble())
            }
            "IF" -> {
                if (args.size !in 2..3) return CellValue.Err(CellErr.Err.VALUE)
                val cond = evalAsBool(evalExpr(args[0], env))
                if (cond is CellValue.Err) return cond
                if ((cond as CellValue.Bool).value) {
                    val t = evalExpr(args[1], env)
                    if (t == CellValue.Empty) CellValue.Num(0.0) else t
                } else if (args.size == 3) {
                    val f = evalExpr(args[2], env)
                    if (f == CellValue.Empty) CellValue.Num(0.0) else f
                } else {
                    CellValue.Bool(false)
                }
            }
            "IFERROR" -> {
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                val primary = evalExpr(args[0], env)
                if (primary is CellValue.Err) evalExpr(args[1], env) else primary
            }
            "AND", "OR" -> {
                if (args.isEmpty()) return CellValue.Err(CellErr.Err.VALUE)
                val bools = ArrayList<Boolean>()
                for ((v, fromRef) in flattenArgValues(args, env)) {
                    when (v) {
                        is CellValue.Err -> return v
                        is CellValue.Bool -> bools.add(v.value)
                        is CellValue.Num -> bools.add(v.value != 0.0)
                        CellValue.Empty -> {}
                        is CellValue.Text -> if (!fromRef) {
                            val b = evalAsBool(v)
                            if (b is CellValue.Err) return b
                            bools.add((b as CellValue.Bool).value)
                        }
                    }
                }
                if (bools.isEmpty()) CellValue.Err(CellErr.Err.VALUE)
                else CellValue.Bool(if (name == "AND") bools.all { it } else bools.any { it })
            }
            "NOT" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val b = evalAsBool(evalExpr(args[0], env))
                if (b is CellValue.Err) b else CellValue.Bool(!(b as CellValue.Bool).value)
            }
            "ABS" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val n = evalScalarAsNum(evalExpr(args[0], env))
                if (n is CellValue.Err) n else CellValue.Num(abs((n as CellValue.Num).value))
            }
            "INT" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val n = evalScalarAsNum(evalExpr(args[0], env))
                if (n is CellValue.Err) n else CellValue.Num(normalizeNegZero(floor((n as CellValue.Num).value)))
            }
            "ROUND" -> {
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                val x = evalScalarAsNum(evalExpr(args[0], env))
                if (x is CellValue.Err) return x
                val d = evalScalarAsNum(evalExpr(args[1], env))
                if (d is CellValue.Err) return d
                val digits = (d as CellValue.Num).value
                val v = (x as CellValue.Num).value
                // Round the canonical decimal representation of this finite binary64 value,
                // halfway away from zero. Never clamp the requested digits to a different scale
                // or multiply by 10^digits in binary, which can overflow or perturb decimal ties.
                // Every finite double has fewer than 400 integer/fraction decimal positions in
                // its canonical representation; avoid allocating an arbitrary requested scale.
                if (digits >= 400.0) return CellValue.Num(normalizeNegZero(v))
                if (digits <= -400.0) return CellValue.Num(0.0)
                val out = BigDecimal.valueOf(v).setScale(digits.toInt(), RoundingMode.HALF_UP).toDouble()
                if (!out.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(out))
            }
            "MOD" -> {
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                val n = evalScalarAsNum(evalExpr(args[0], env))
                if (n is CellValue.Err) return n
                val d = evalScalarAsNum(evalExpr(args[1], env))
                if (d is CellValue.Err) return d
                val num = (n as CellValue.Num).value
                val div = (d as CellValue.Num).value
                if (div == 0.0) return CellValue.Err(CellErr.Err.DIV0)
                val res = num - div * floor(num / div)
                if (!res.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(res))
            }
            "LEN" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val s = evalAsString(evalExpr(args[0], env))
                if (s is CellValue.Err) s else CellValue.Num((s as CellValue.Text).value.length.toDouble())
            }
            "CONCAT" -> {
                val sb = StringBuilder()
                for ((v, _) in flattenArgValues(args, env)) {
                    if (v is CellValue.Err) return v
                    sb.append(v.displayText())
                }
                CellValue.Text(sb.toString())
            }
            "INDEX" -> {
                if (args.size !in 2..3) return CellValue.Err(CellErr.Err.VALUE)
                val rVal = evalScalarAsNum(evalExpr(args[1], env))
                if (rVal is CellValue.Err) return rVal
                val rowIdx = (rVal as CellValue.Num).value.toInt()
                val colIdx = if (args.size == 3) {
                    val cVal = evalScalarAsNum(evalExpr(args[2], env))
                    if (cVal is CellValue.Err) return cVal
                    (cVal as CellValue.Num).value.toInt()
                } else 1
                when (val first = args[0]) {
                    is Expr.ArrayLit -> {
                        val h = first.rows.size
                        val w = first.rows.firstOrNull()?.size ?: 0
                        if (h == 1 && args.size == 2) {
                            if (rowIdx !in 1..w) return CellValue.Err(CellErr.Err.REF)
                            evalExpr(first.rows[0][rowIdx - 1], env)
                        } else {
                            if (rowIdx !in 1..h || colIdx !in 1..w) return CellValue.Err(CellErr.Err.REF)
                            evalExpr(first.rows[rowIdx - 1][colIdx - 1], env)
                        }
                    }
                    else -> {
                        val range = when (first) {
                            is Expr.RangeRef -> first.range
                            is Expr.CellRef -> {
                                val vKey = normalizeVarName(first.a1)
                                if (runtimeState.get().richScopes[vKey] is RichBinding.Grid) null
                                else SheetRange.parse(first.a1)
                            }
                            else -> null
                        }
                        if (range != null) {
                            if (rowIdx !in 1..range.height || colIdx !in 1..range.width) {
                                return CellValue.Err(CellErr.Err.REF)
                            }
                            val target = SheetRange.Cell(range.top.col + colIdx - 1, range.top.row + rowIdx - 1).toA1()
                            val found = env[target] ?: CellValue.Empty
                            if (found == CellValue.Empty) CellValue.Num(0.0) else found
                        } else {
                            val grid = exprTo2DGrid(first, env) ?: return CellValue.Err(CellErr.Err.VALUE)
                            val h = grid.size
                            val w = grid.firstOrNull()?.size ?: 0
                            if (h == 1 && args.size == 2) {
                                if (rowIdx !in 1..w) return CellValue.Err(CellErr.Err.REF)
                                val found = grid[0][rowIdx - 1]
                                if (found == CellValue.Empty) CellValue.Num(0.0) else found
                            } else {
                                if (rowIdx !in 1..h || colIdx !in 1..w) return CellValue.Err(CellErr.Err.REF)
                                val found = grid[rowIdx - 1][colIdx - 1]
                                if (found == CellValue.Empty) CellValue.Num(0.0) else found
                            }
                        }
                    }
                }
            }
            "MATCH" -> {
                if (args.size !in 2..3) return CellValue.Err(CellErr.Err.VALUE)
                val needle = evalExpr(args[0], env)
                if (needle is CellValue.Err) return needle
                val matchType = if (args.size == 3) {
                    val mVal = evalScalarAsNum(evalExpr(args[2], env))
                    if (mVal is CellValue.Err) return mVal
                    (mVal as CellValue.Num).value.toInt()
                } else 0
                require(matchType == 0) { "MATCH approximate match_type=$matchType is out of scope (only exact match_type=0 supported)" }
                val vec = exprTo1DVector(args[1], env) ?: return CellValue.Err(CellErr.Err.NA)
                for (i in vec.indices) {
                    val cellVal = vec[i]
                    if (cellVal !is CellValue.Err && cellVal != CellValue.Empty && compareSpreadsheetValues(needle, cellVal) == 0) {
                        return CellValue.Num((i + 1).toDouble())
                    }
                }
                CellValue.Err(CellErr.Err.NA)
            }
            "IFNA" -> {
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                val primary = evalExpr(args[0], env)
                if (primary is CellValue.Err && primary.error == CellErr.Err.NA) evalExpr(args[1], env) else primary
            }
            "ISBLANK" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val v = when (val a = args[0]) {
                    is Expr.CellRef -> env[a.a1] ?: CellValue.Empty
                    else -> evalExpr(a, env)
                }
                CellValue.Bool(v == CellValue.Empty)
            }
            "ISNUMBER" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                CellValue.Bool(evalExpr(args[0], env) is CellValue.Num)
            }
            "ISTEXT" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                CellValue.Bool(evalExpr(args[0], env) is CellValue.Text)
            }
            "ISERROR" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                CellValue.Bool(evalExpr(args[0], env) is CellValue.Err)
            }
            "MEDIAN" -> {
                val res = collectNumericAggregate(args, env)
                res.fold(
                    onSuccess = { nums ->
                        if (nums.isEmpty()) CellValue.Err(CellErr.Err.NUM)
                        else {
                            val sorted = nums.sorted()
                            val mid = sorted.size / 2
                            val m = if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
                            CellValue.Num(normalizeNegZero(m))
                        }
                    },
                    onFailure = { CellValue.Err((it as CellErrorSignal).err) },
                )
            }
            "STDEV" -> {
                val res = collectNumericAggregate(args, env)
                res.fold(
                    onSuccess = { nums ->
                        if (nums.size < 2) CellValue.Err(CellErr.Err.DIV0)
                        else {
                            val mean = nums.sum() / nums.size
                            val sq = nums.sumOf { (it - mean) * (it - mean) }
                            CellValue.Num(normalizeNegZero(kotlin.math.sqrt(sq / (nums.size - 1))))
                        }
                    },
                    onFailure = { CellValue.Err((it as CellErrorSignal).err) },
                )
            }
            "TRIM" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val v = evalExpr(args[0], env)
                if (v is CellValue.Err) return v
                CellValue.Text(v.displayText().trim().replace(Regex(" +"), " "))
            }
            "LOWER" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val v = evalExpr(args[0], env)
                if (v is CellValue.Err) return v
                CellValue.Text(v.displayText().lowercase())
            }
            "UPPER" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val v = evalExpr(args[0], env)
                if (v is CellValue.Err) return v
                CellValue.Text(v.displayText().uppercase())
            }
            "LEFT" -> {
                if (args.size !in 1..2) return CellValue.Err(CellErr.Err.VALUE)
                val v = evalExpr(args[0], env)
                if (v is CellValue.Err) return v
                val count = if (args.size == 2) {
                    val n = evalScalarAsNum(evalExpr(args[1], env))
                    if (n is CellValue.Err) return n
                    (n as CellValue.Num).value.toInt()
                } else 1
                if (count < 0) return CellValue.Err(CellErr.Err.VALUE)
                val s = v.displayText()
                CellValue.Text(s.substring(0, count.coerceAtMost(s.length)))
            }
            "RIGHT" -> {
                if (args.size !in 1..2) return CellValue.Err(CellErr.Err.VALUE)
                val v = evalExpr(args[0], env)
                if (v is CellValue.Err) return v
                val count = if (args.size == 2) {
                    val n = evalScalarAsNum(evalExpr(args[1], env))
                    if (n is CellValue.Err) return n
                    (n as CellValue.Num).value.toInt()
                } else 1
                if (count < 0) return CellValue.Err(CellErr.Err.VALUE)
                val s = v.displayText()
                CellValue.Text(s.substring((s.length - count).coerceAtLeast(0)))
            }
            "MID" -> {
                if (args.size != 3) return CellValue.Err(CellErr.Err.VALUE)
                val v = evalExpr(args[0], env)
                if (v is CellValue.Err) return v
                val startNum = evalScalarAsNum(evalExpr(args[1], env))
                if (startNum is CellValue.Err) return startNum
                val lenNum = evalScalarAsNum(evalExpr(args[2], env))
                if (lenNum is CellValue.Err) return lenNum
                val start1 = (startNum as CellValue.Num).value.toInt()
                val count = (lenNum as CellValue.Num).value.toInt()
                if (start1 < 1 || count < 0) return CellValue.Err(CellErr.Err.VALUE)
                val s = v.displayText()
                val idx0 = (start1 - 1).coerceAtMost(s.length)
                val end0 = (idx0 + count).coerceAtMost(s.length)
                CellValue.Text(s.substring(idx0, end0))
            }
            "VLOOKUP" -> {
                if (args.size !in 3..4) return CellValue.Err(CellErr.Err.VALUE)
                val needle = evalExpr(args[0], env)
                if (needle is CellValue.Err) return needle
                val table = when (val t = args[1]) {
                    is Expr.RangeRef -> t.range
                    is Expr.CellRef -> SheetRange.parse(t.a1)
                    else -> return CellValue.Err(CellErr.Err.VALUE)
                }
                val colNum = evalScalarAsNum(evalExpr(args[2], env))
                if (colNum is CellValue.Err) return colNum
                val colIdx = (colNum as CellValue.Num).value.toInt()
                if (colIdx < 1) return CellValue.Err(CellErr.Err.VALUE)
                if (colIdx > table.width) return CellValue.Err(CellErr.Err.REF)
                val approx = if (args.size == 4) {
                    val b = evalExpr(args[3], env)
                    if (b is CellValue.Err) return b
                    when (b) {
                        is CellValue.Bool -> b.value
                        is CellValue.Num -> b.value != 0.0
                        else -> return CellValue.Err(CellErr.Err.VALUE)
                    }
                } else true
                require(!approx) { "VLOOKUP approximate range_lookup=TRUE is out of scope (pass FALSE/0 for exact match)" }
                for (r in table.top.row..table.bottom.row) {
                    val keyCell = env[SheetRange.Cell(table.top.col, r).toA1()] ?: CellValue.Empty
                    if (keyCell !is CellValue.Err && keyCell != CellValue.Empty && compareSpreadsheetValues(needle, keyCell) == 0) {
                        val hit = env[SheetRange.Cell(table.top.col + colIdx - 1, r).toA1()] ?: CellValue.Empty
                        return if (hit == CellValue.Empty) CellValue.Num(0.0) else hit
                    }
                }
                CellValue.Err(CellErr.Err.NA)
            }
            "COUNTIF", "SUMIF" -> {
                val expectedArgs = if (name == "COUNTIF") 2..2 else 2..3
                if (args.size !in expectedArgs) return CellValue.Err(CellErr.Err.VALUE)
                val critRange = when (val r0 = args[0]) {
                    is Expr.RangeRef -> r0.range
                    is Expr.CellRef -> SheetRange.parse(r0.a1)
                    else -> return CellValue.Err(CellErr.Err.VALUE)
                }
                val critRaw = evalExpr(args[1], env)
                if (critRaw is CellValue.Err) return critRaw
                val sumRange = if (name == "SUMIF" && args.size == 3) {
                    when (val r2 = args[2]) {
                        is Expr.RangeRef -> r2.range
                        is Expr.CellRef -> SheetRange.parse(r2.a1)
                        else -> return CellValue.Err(CellErr.Err.VALUE)
                    }
                } else critRange
                if (name == "SUMIF" && (sumRange.width != critRange.width || sumRange.height != critRange.height)) {
                    return CellValue.Err(CellErr.Err.VALUE)
                }
                var count = 0
                var sum = 0.0
                for (dr in 0 until critRange.height) {
                    for (dc in 0 until critRange.width) {
                        val cv = env[SheetRange.Cell(critRange.top.col + dc, critRange.top.row + dr).toA1()] ?: CellValue.Empty
                        if (matchesCriteria(cv, critRaw)) {
                            count++
                            if (name == "SUMIF") {
                                val sv = env[SheetRange.Cell(sumRange.top.col + dc, sumRange.top.row + dr).toA1()] ?: CellValue.Empty
                                if (sv is CellValue.Err) return sv
                                if (sv is CellValue.Num) sum += sv.value
                            }
                        }
                    }
                }
                if (name == "COUNTIF") CellValue.Num(count.toDouble())
                else if (!sum.isFinite()) CellValue.Err(CellErr.Err.NUM)
                else CellValue.Num(normalizeNegZero(sum))
            }
            "AVERAGEIF" -> {
                if (args.size !in 2..3) return CellValue.Err(CellErr.Err.VALUE)
                val critRange = exprToRange(args[0]) ?: return CellValue.Err(CellErr.Err.VALUE)
                val critRaw = evalExpr(args[1], env)
                if (critRaw is CellValue.Err) return critRaw
                val avgRange = if (args.size == 3) exprToRange(args[2]) ?: return CellValue.Err(CellErr.Err.VALUE) else critRange
                if (avgRange.width != critRange.width || avgRange.height != critRange.height) return CellValue.Err(CellErr.Err.VALUE)
                var count = 0
                var sum = 0.0
                for (dr in 0 until critRange.height) {
                    for (dc in 0 until critRange.width) {
                        val cv = env[SheetRange.Cell(critRange.top.col + dc, critRange.top.row + dr).toA1()] ?: CellValue.Empty
                        if (matchesCriteria(cv, critRaw)) {
                            val av = env[SheetRange.Cell(avgRange.top.col + dc, avgRange.top.row + dr).toA1()] ?: CellValue.Empty
                            if (av is CellValue.Err) return av
                            if (av is CellValue.Num) {
                                sum += av.value
                                count++
                            }
                        }
                    }
                }
                if (count == 0) CellValue.Err(CellErr.Err.DIV0)
                else CellValue.Num(normalizeNegZero(sum / count))
            }
            "COUNTIFS", "SUMIFS", "AVERAGEIFS" -> evalMultiCriteriaIfs(name, args, env)
            "XLOOKUP" -> evalXlookup(args, env)
            "XMATCH" -> evalXmatch(args, env)
            "HLOOKUP" -> evalHlookup(args, env)
            "CHOOSE" -> {
                if (args.size < 2) return CellValue.Err(CellErr.Err.VALUE)
                val idxVal = evalScalarAsNum(evalExpr(args[0], env))
                if (idxVal is CellValue.Err) return idxVal
                val idx = (idxVal as CellValue.Num).value.toInt()
                if (idx < 1 || idx >= args.size) CellValue.Err(CellErr.Err.VALUE) else evalExpr(args[idx], env)
            }
            "IFS" -> {
                if (args.size < 2 || args.size % 2 != 0) return CellValue.Err(CellErr.Err.VALUE)
                var i = 0
                while (i < args.size) {
                    val c = evalAsBool(evalExpr(args[i], env))
                    if (c is CellValue.Err) return c
                    if ((c as CellValue.Bool).value) return evalExpr(args[i + 1], env)
                    i += 2
                }
                CellValue.Err(CellErr.Err.NA)
            }
            "SWITCH" -> {
                if (args.size < 3) return CellValue.Err(CellErr.Err.VALUE)
                val target = evalExpr(args[0], env)
                if (target is CellValue.Err) return target
                var i = 1
                while (i + 1 < args.size) {
                    val cand = evalExpr(args[i], env)
                    if (cand is CellValue.Err) return cand
                    if (compareSpreadsheetValues(target, cand) == 0) return evalExpr(args[i + 1], env)
                    i += 2
                }
                if (i < args.size) evalExpr(args[i], env) else CellValue.Err(CellErr.Err.NA)
            }
            "XOR" -> {
                if (args.isEmpty()) return CellValue.Err(CellErr.Err.VALUE)
                var sawBool = false
                var parity = false
                for ((v, fromRef) in flattenArgValues(args, env)) {
                    when (v) {
                        is CellValue.Err -> return v
                        is CellValue.Bool -> { sawBool = true; if (v.value) parity = !parity }
                        is CellValue.Num -> { sawBool = true; if (v.value != 0.0) parity = !parity }
                        CellValue.Empty -> {}
                        is CellValue.Text -> if (!fromRef) return CellValue.Err(CellErr.Err.VALUE)
                    }
                }
                if (!sawBool) CellValue.Err(CellErr.Err.VALUE) else CellValue.Bool(parity)
            }
            "TEXTJOIN" -> {
                if (args.size < 3) return CellValue.Err(CellErr.Err.VALUE)
                val delimVal = evalExpr(args[0], env)
                if (delimVal is CellValue.Err) return delimVal
                val ignoreVal = evalAsBool(evalExpr(args[1], env))
                if (ignoreVal is CellValue.Err) return ignoreVal
                val ignoreEmpty = (ignoreVal as CellValue.Bool).value
                val parts = ArrayList<String>()
                for ((v, _) in flattenArgValues(args.subList(2, args.size), env)) {
                    if (v is CellValue.Err) return v
                    val txt = v.displayText()
                    if (!ignoreEmpty || txt.isNotEmpty()) parts.add(txt)
                }
                CellValue.Text(parts.joinToString(delimVal.displayText()))
            }
            "SUBSTITUTE" -> {
                if (args.size !in 3..4) return CellValue.Err(CellErr.Err.VALUE)
                val srcVal = evalExpr(args[0], env); if (srcVal is CellValue.Err) return srcVal
                val oldVal = evalExpr(args[1], env); if (oldVal is CellValue.Err) return oldVal
                val newVal = evalExpr(args[2], env); if (newVal is CellValue.Err) return newVal
                val text = srcVal.displayText()
                val oldStr = oldVal.displayText()
                val newStr = newVal.displayText()
                if (oldStr.isEmpty()) return CellValue.Text(text)
                if (args.size == 3) return CellValue.Text(text.replace(oldStr, newStr))
                val instNum = evalScalarAsNum(evalExpr(args[3], env))
                if (instNum is CellValue.Err) return instNum
                val targetOcc = (instNum as CellValue.Num).value.toInt()
                if (targetOcc < 1) return CellValue.Err(CellErr.Err.VALUE)
                var occ = 0
                var idx = 0
                val sb = StringBuilder()
                while (idx <= text.length) {
                    val hit = text.indexOf(oldStr, idx)
                    if (hit < 0) {
                        sb.append(text.substring(idx))
                        break
                    }
                    occ++
                    sb.append(text, idx, hit)
                    sb.append(if (occ == targetOcc) newStr else oldStr)
                    idx = hit + oldStr.length
                }
                CellValue.Text(sb.toString())
            }
            "FIND" -> {
                if (args.size !in 2..3) return CellValue.Err(CellErr.Err.VALUE)
                val needle = evalExpr(args[0], env); if (needle is CellValue.Err) return needle
                val hay = evalExpr(args[1], env); if (hay is CellValue.Err) return hay
                val startOne = if (args.size == 3) {
                    val st = evalScalarAsNum(evalExpr(args[2], env))
                    if (st is CellValue.Err) return st
                    (st as CellValue.Num).value.toInt()
                } else 1
                val hayStr = hay.displayText()
                if (startOne < 1 || startOne > hayStr.length + 1) return CellValue.Err(CellErr.Err.VALUE)
                val hit = hayStr.indexOf(needle.displayText(), startOne - 1)
                if (hit < 0) CellValue.Err(CellErr.Err.VALUE) else CellValue.Num((hit + 1).toDouble())
            }
            "PROPER" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val v = evalExpr(args[0], env); if (v is CellValue.Err) return v
                val s = v.displayText()
                val out = buildString(s.length) {
                    var capNext = true
                    for (ch in s) {
                        if (ch.isLetter()) {
                            append(if (capNext) ch.uppercaseChar() else ch.lowercaseChar())
                            capNext = false
                        } else {
                            append(ch)
                            capNext = true
                        }
                    }
                }
                CellValue.Text(out)
            }
            "EXACT" -> {
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                val a = evalExpr(args[0], env); if (a is CellValue.Err) return a
                val b = evalExpr(args[1], env); if (b is CellValue.Err) return b
                CellValue.Bool(a.displayText() == b.displayText())
            }
            "CEILING", "FLOOR" -> {
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                val xVal = evalScalarAsNum(evalExpr(args[0], env)); if (xVal is CellValue.Err) return xVal
                val sVal = evalScalarAsNum(evalExpr(args[1], env)); if (sVal is CellValue.Err) return sVal
                val x = (xVal as CellValue.Num).value
                val sig = (sVal as CellValue.Num).value
                if (sig == 0.0) return if (x == 0.0) CellValue.Num(0.0) else CellValue.Err(CellErr.Err.DIV0)
                if ((x > 0.0 && sig < 0.0) || (x < 0.0 && sig < 0.0 && name == "FLOOR" && false)) {
                    if (x > 0.0 && sig < 0.0) return CellValue.Err(CellErr.Err.NUM)
                }
                val q = x / sig
                val res = (if (name == "CEILING") ceil(q) else floor(q)) * sig
                if (!res.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(res))
            }
            "SIGN" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val xVal = evalScalarAsNum(evalExpr(args[0], env)); if (xVal is CellValue.Err) return xVal
                val v = (xVal as CellValue.Num).value
                CellValue.Num(if (v == 0.0) 0.0 else sign(v))
            }
            "LN", "LOG10", "EXP" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val xVal = evalScalarAsNum(evalExpr(args[0], env)); if (xVal is CellValue.Err) return xVal
                val v = (xVal as CellValue.Num).value
                if (name != "EXP" && v <= 0.0) return CellValue.Err(CellErr.Err.NUM)
                val res = when (name) {
                    "LN" -> ln(v)
                    "LOG10" -> log10(v)
                    else -> exp(v)
                }
                if (!res.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(res))
            }
            "NPV" -> {
                if (args.size < 2) return CellValue.Err(CellErr.Err.VALUE)
                val rVal = evalScalarAsNum(evalExpr(args[0], env)); if (rVal is CellValue.Err) return rVal
                val rate = (rVal as CellValue.Num).value
                if (rate <= -1.0) return CellValue.Err(CellErr.Err.DIV0)
                val flows = collectNumericAggregate(args.subList(1, args.size), env)
                flows.fold(
                    onSuccess = { nums ->
                        var total = 0.0
                        for (i in nums.indices) total += nums[i] / (1.0 + rate).pow(i + 1)
                        if (!total.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(total))
                    },
                    onFailure = { CellValue.Err((it as CellErrorSignal).err) },
                )
            }
            "PMT", "PV", "FV" -> evalFinancialTvm(name, args, env)
            "LET" -> evalLet(args, env)
            "SUMPRODUCT" -> evalSumproduct(args, env)
            "PERCENTILE", "QUARTILE" -> evalPercentileOrQuartile(name, args, env)
            "CORREL", "SLOPE", "INTERCEPT" -> evalLinearStats(name, args, env)
            "IRR" -> evalIrr(args, env)
            "RATE" -> evalRate(args, env)
            "NPER" -> evalNper(args, env)
            "DATE", "YEAR", "MONTH", "DAY", "EDATE", "EOMONTH", "DATEDIF", "WEEKDAY", "NETWORKDAYS" ->
                evalDateFunction(name, args, env)
            "CLEAN", "CODE", "CHAR", "NUMBERVALUE", "REGEXMATCH", "REGEXEXTRACT", "REGEXREPLACE" ->
                evalTextR48Function(name, args, env)
            "ISERR" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val v = evalExpr(args[0], env)
                CellValue.Bool(v is CellValue.Err && v.error != CellErr.Err.NA)
            }
            "ISLOGICAL" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                CellValue.Bool(evalExpr(args[0], env) is CellValue.Bool)
            }
            "EVEN", "ODD", "FACT", "COMBIN", "SQRT", "POWER" -> evalMathR48Function(name, args, env)
            "LAMBDA" -> CellValue.Err(CellErr.Err.VALUE) // Uninvoked bare LAMBDA in a scalar cell -> #VALUE!/#CALC!
            "ISOMITTED" -> evalIsOmitted(args)
            "REDUCE" -> evalReduce(args, env)
            "MAP", "SCAN", "BYROW", "BYCOL", "MAKEARRAY",
            "CHOOSECOLS", "CHOOSEROWS", "TAKE", "DROP", "HSTACK", "VSTACK",
            "TOCOL", "TOROW", "WRAPROWS", "WRAPCOLS" -> {
                val g = evalArrayOrHofGrid(name, args, env)
                if (g.size == 1 && g[0].size == 1) g[0][0] else g.firstOrNull()?.firstOrNull() ?: CellValue.Err(CellErr.Err.VALUE)
            }
            "SUMSQ", "SUMX2MY2", "SUMX2PY2", "SUMXMY2", "HARMEAN", "TRIMMEAN",
            "DEVSQ", "AVEDEV", "LARGE", "SMALL", "FISHER", "FISHERINV",
            "STANDARDIZE", "PERMUT", "PDURATION", "RRI", "DOLLARDE" -> evalR49ScalarFunction(name, args, env)
            "MMULT", "MINVERSE", "MUNIT", "FREQUENCY", "TREND", "GROWTH" -> {
                val g = evalR50MatrixGrid(name, args, env)
                if (g.size == 1 && g[0].size == 1) g[0][0] else g.firstOrNull()?.firstOrNull() ?: CellValue.Err(CellErr.Err.VALUE)
            }
            "MDETERM", "RANK", "RANK.EQ", "RANK.AVG", "PERCENTRANK", "PERCENTRANK.INC", "PERCENTRANK.EXC",
            "BITAND", "BITOR", "BITXOR", "BITLSHIFT", "BITRSHIFT",
            "DEC2BIN", "DEC2HEX", "DEC2OCT", "BIN2DEC", "HEX2DEC", "OCT2DEC", "BIN2HEX", "HEX2BIN",
            "DELTA", "GESTEP", "CONVERT",
            "ERF", "ERFC", "GAMMALN", "SERIESSUM", "MULTINOMIAL", "GCD", "LCM", "WORKDAY", "DOLLARFR" ->
                evalR50ScalarFunction(name, args, env)
            "EXPAND", "TRIMRANGE", "GROUPBY", "PIVOTBY", "LINEST", "LOGEST", "FLATTEN" -> {
                val g = evalR51Grid(name, args, env)
                if (g.size == 1 && g[0].size == 1) g[0][0] else g.firstOrNull()?.firstOrNull() ?: CellValue.Err(CellErr.Err.VALUE)
            }
            "PERCENTOF", "ARRAYTOTEXT", "VALUETOTEXT",
            "GEOMEAN", "SKEW", "KURT", "RSQ", "STEYX", "COVARIANCE.P", "COVARIANCE.S",
            "NORM.S.DIST", "NORM.S.INV", "NORM.DIST", "POISSON.DIST", "BINOM.DIST", "EXPON.DIST", "CONFIDENCE.NORM",
            "SLN", "SYD", "DDB", "MIRR", "IPMT", "PPMT", "CUMIPMT", "CUMPRINC", "EFFECT", "NOMINAL",
            "BASE", "DECIMAL", "ROMAN", "ARABIC", "COMBINA", "FACTDOUBLE", "QUOTIENT", "MROUND",
            "SQRTPI", "DEGREES", "RADIANS", "ISOWEEKNUM", "ADDRESS" ->
                evalR51ScalarFunction(name, args, env)
            "OUTERPRODUCT", "CUMSUM", "DIFFARRAY", "MOVINGAVERAGE", "ZSCOREGRID", "DIAGONAL" -> {
                val g = evalR52Grid(name, args, env)
                if (g.size == 1 && g[0].size == 1) g[0][0] else g.firstOrNull()?.firstOrNull() ?: CellValue.Err(CellErr.Err.VALUE)
            }
            "WEIBULL.DIST", "HYPGEOM.DIST", "NEGBINOM.DIST", "LOGNORM.DIST", "LOGNORM.INV",
            "GAMMA", "GAMMA.DIST", "CHISQ.DIST", "CHISQ.DIST.RT",
            "BESSELJ", "BESSELI", "ERF.PRECISE", "ERFC.PRECISE",
            "TBILLEQ", "TBILLPRICE", "TBILLYIELD", "DISC", "INTRATE", "RECEIVED", "PRICEDISC", "YIELDDISC",
            "PERMUTATIONA", "SEC", "CSC", "COT", "SECH", "CSCH", "COTH", "ACOT", "ACOTH",
            "OCT2BIN", "OCT2HEX", "HEX2OCT", "BIN2OCT",
            "TEXTBEFORE", "TEXTAFTER", "ENCODEURL", "UNICHAR", "UNICODE" ->
                evalR52ScalarFunction(name, args, env)
            "HANKEL", "TOEPLITZ", "VANDERMONDE", "KRONECKER", "CONVOLVE1D", "PARETOGRID" -> {
                val g = evalR53Grid(name, args, env)
                if (g.size == 1 && g[0].size == 1) g[0][0] else g.firstOrNull()?.firstOrNull() ?: CellValue.Err(CellErr.Err.VALUE)
            }
            "COMPLEX", "IMREAL", "IMAGINARY", "IMABS", "IMARGUMENT", "IMCONJUGATE",
            "IMSUM", "IMSUB", "IMPRODUCT", "IMDIV", "IMPOWER", "IMSQRT", "IMEXP", "IMLN", "IMSIN", "IMCOS",
            "COUPDAYS", "COUPDAYBS", "COUPDAYSNC", "COUPNUM", "COUPPCD", "COUPNCD", "DURATION", "MDURATION",
            "BETA.DIST", "TDIST.2T", "TDIST.RT", "F.DIST.RT",
            "FISHER", "FISHERINV", "STANDARDIZE", "CONFIDENCE.T",
            "HARMEAN", "TRIMMEAN", "DEVSQ", "AVEDEV", "SERIESSUM", "MULTINOMIAL" ->
                evalR53ScalarFunction(name, args, env)
            else -> {
                val lexicalClosure = runtimeState.get().richScopes[normalizeVarName(rawName)]
                if (lexicalClosure is RichBinding.Closure) {
                    invokeClosureScalar(lexicalClosure, args, env)
                } else {
                    CellValue.Err(CellErr.Err.NAME)
                }
            }
        }
    }

    private fun exprToRange(e: Expr): SheetRange.Range? = when (e) {
        is Expr.RangeRef -> e.range
        is Expr.CellRef -> SheetRange.parse(e.a1)
        else -> null
    }

    /** Coerces a 1D range, inline 1D array literal `{1,2,3}` / `{1;2;3}` (IronCalc PR #7), or 1-cell ref into a 1D vector. */
    private fun exprTo1DVector(e: Expr, env: Map<String, CellValue>): List<CellValue>? = when (e) {
        is Expr.RangeRef -> {
            val r = e.range
            if (r.width > 1 && r.height > 1) null
            else {
                val out = ArrayList<CellValue>((r.width * r.height))
                for (row in r.top.row..r.bottom.row) {
                    for (col in r.top.col..r.bottom.col) {
                        out.add(env[SheetRange.Cell(col, row).toA1()] ?: CellValue.Empty)
                    }
                }
                out
            }
        }
        is Expr.ArrayLit -> {
            if (e.rows.size == 1) e.rows[0].map { evalExpr(it, env) }
            else if (e.rows.all { it.size == 1 }) e.rows.map { evalExpr(it[0], env) }
            else null
        }
        is Expr.CellRef -> {
            val vKey = normalizeVarName(e.a1)
            val rb = runtimeState.get().richScopes[vKey]
            if (rb is RichBinding.Grid) {
                if (rb.rows.size == 1) rb.rows[0]
                else if (rb.rows.all { it.size == 1 }) rb.rows.map { it[0] }
                else null
            } else {
                listOf(env["\u0000LET:$vKey"] ?: env[e.a1] ?: CellValue.Empty)
            }
        }
        is Expr.SymbolRef -> {
            val vKey = normalizeVarName(e.ident)
            val rb = runtimeState.get().richScopes[vKey]
            if (rb is RichBinding.Grid) {
                if (rb.rows.size == 1) rb.rows[0]
                else if (rb.rows.all { it.size == 1 }) rb.rows.map { it[0] }
                else null
            } else {
                listOf(evalExpr(e, env))
            }
        }
        is Expr.Call -> {
            val g = tryEvalDynamicGridCall(e, env)
            if (g != null) {
                if (g.size == 1) g[0]
                else if (g.all { it.size == 1 }) g.map { it[0] }
                else null
            } else {
                listOf(evalExpr(e, env))
            }
        }
        else -> listOf(evalExpr(e, env))
    }

    private fun evalLet(args: List<Expr>, env: Map<String, CellValue>): CellValue {
        val g = evalLetGrid(args, env)
        return if (g.size == 1 && g[0].size == 1) g[0][0] else g.firstOrNull()?.firstOrNull() ?: CellValue.Err(CellErr.Err.VALUE)
    }

    private fun evalLetGrid(args: List<Expr>, env: Map<String, CellValue>): List<List<CellValue>> {
        if (args.size < 3 || args.size % 2 == 0) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
        val rt = runtimeState.get()
        val prevRich = rt.richScopes
        val scopedCells = HashMap(env)
        val scopedRich = HashMap(prevRich)
        try {
            var i = 0
            while (i < args.size - 1) {
                val pname = exprParamName(args[i]) ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                rt.richScopes = scopedRich
                val boundExpr = args[i + 1]
                val closure = tryBuildLambdaClosure(boundExpr, scopedCells, scopedRich)
                if (closure != null) {
                    scopedRich[pname] = closure
                    scopedCells.remove("\u0000LET:$pname")
                } else {
                    val maybeGrid = tryEvalDynamicGridExpr(boundExpr, scopedCells)
                    if (maybeGrid != null && (maybeGrid.size > 1 || (maybeGrid.firstOrNull()?.size ?: 0) > 1)) {
                        scopedRich[pname] = RichBinding.Grid(maybeGrid)
                        scopedCells.remove("\u0000LET:$pname")
                    } else {
                        val pval = if (maybeGrid != null && maybeGrid.isNotEmpty() && maybeGrid[0].isNotEmpty()) {
                            maybeGrid[0][0]
                        } else {
                            evalExpr(boundExpr, scopedCells)
                        }
                        if (pval is CellValue.Err) return listOf(listOf(pval))
                        scopedRich.remove(pname)
                        scopedCells["\u0000LET:$pname"] = pval
                    }
                }
                i += 2
            }
            rt.richScopes = scopedRich
            return exprTo2DGrid(args.last(), scopedCells) ?: listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
        } finally {
            rt.richScopes = prevRich
        }
    }

    private fun exprTo2DGrid(e: Expr, env: Map<String, CellValue>): List<List<CellValue>>? = when (e) {
        is Expr.RangeRef -> {
            val r = e.range
            (r.top.row..r.bottom.row).map { row ->
                (r.top.col..r.bottom.col).map { col -> env[SheetRange.Cell(col, row).toA1()] ?: CellValue.Empty }
            }
        }
        is Expr.ArrayLit -> e.rows.map { row -> row.map { evalExpr(it, env) } }
        is Expr.CellRef -> {
            val vKey = normalizeVarName(e.a1)
            val rb = runtimeState.get().richScopes[vKey]
            if (rb is RichBinding.Grid) rb.rows
            else listOf(listOf(env["\u0000LET:$vKey"] ?: env[e.a1] ?: CellValue.Empty))
        }
        is Expr.SymbolRef -> {
            val vKey = normalizeVarName(e.ident)
            val rb = runtimeState.get().richScopes[vKey]
            if (rb is RichBinding.Grid) rb.rows
            else listOf(listOf(evalExpr(e, env)))
        }
        is Expr.Call -> tryEvalDynamicGridCall(e, env) ?: listOf(listOf(evalExpr(e, env)))
        is Expr.Invoke -> evalInvokeGrid(e.callee, e.args, env)
        else -> listOf(listOf(evalExpr(e, env)))
    }

    private fun tryEvalDynamicGridExpr(e: Expr, env: Map<String, CellValue>): List<List<CellValue>>? = when (e) {
        is Expr.RangeRef, is Expr.ArrayLit -> exprTo2DGrid(e, env)
        is Expr.Call -> tryEvalDynamicGridCall(e, env)
        is Expr.Invoke -> evalInvokeGrid(e.callee, e.args, env)
        else -> null
    }

    private fun tryEvalDynamicGridCall(call: Expr.Call, env: Map<String, CellValue>): List<List<CellValue>>? {
        val name = normalizeFnName(call.name)
        return when (name) {
            "LET" -> evalLetGrid(call.args, env)
            "MAP", "SCAN", "BYROW", "BYCOL", "MAKEARRAY",
            "CHOOSECOLS", "CHOOSEROWS", "TAKE", "DROP", "HSTACK", "VSTACK",
            "TOCOL", "TOROW", "WRAPROWS", "WRAPCOLS" -> evalArrayOrHofGrid(name, call.args, env)
            "MMULT", "MINVERSE", "MUNIT", "FREQUENCY", "TREND", "GROWTH" -> evalR50MatrixGrid(name, call.args, env)
            "EXPAND", "TRIMRANGE", "GROUPBY", "PIVOTBY", "LINEST", "LOGEST", "FLATTEN" -> evalR51Grid(name, call.args, env)
            "OUTERPRODUCT", "CUMSUM", "DIFFARRAY", "MOVINGAVERAGE", "ZSCOREGRID", "DIAGONAL" -> evalR52Grid(name, call.args, env)
            "HANKEL", "TOEPLITZ", "VANDERMONDE", "KRONECKER", "CONVOLVE1D", "PARETOGRID" -> evalR53Grid(name, call.args, env)
            "SEQUENCE", "TRANSPOSE" -> evalEmbeddedSequenceOrTranspose(name, call.args, env)
            else -> {
                val lexicalClosure = runtimeState.get().richScopes[normalizeVarName(call.name)]
                if (lexicalClosure is RichBinding.Closure) invokeClosureGrid(lexicalClosure, call.args, env)
                else null
            }
        }
    }

    private fun evalEmbeddedSequenceOrTranspose(name: String, args: List<Expr>, env: Map<String, CellValue>): List<List<CellValue>> {
        if (name == "TRANSPOSE") {
            if (args.size != 1) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
            val src = exprTo2DGrid(args[0], env) ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
            val h = src.size; val w = src.firstOrNull()?.size ?: 0
            if (h == 0 || w == 0) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
            return List(w) { c -> List(h) { r -> src[r][c] } }
        }
        // SEQUENCE(rows, [cols], [start], [step])
        if (args.size !in 1..4) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
        val rV = evalScalarAsNum(evalExpr(args[0], env)); if (rV is CellValue.Err) return listOf(listOf(rV))
        val rows = (rV as CellValue.Num).value.toInt()
        val cols = if (args.size >= 2 && args[1] != Expr.OmittedArg) {
            val cV = evalScalarAsNum(evalExpr(args[1], env)); if (cV is CellValue.Err) return listOf(listOf(cV))
            (cV as CellValue.Num).value.toInt()
        } else 1
        val start = if (args.size >= 3 && args[2] != Expr.OmittedArg) {
            val sV = evalScalarAsNum(evalExpr(args[2], env)); if (sV is CellValue.Err) return listOf(listOf(sV))
            (sV as CellValue.Num).value
        } else 1.0
        val step = if (args.size >= 4 && args[3] != Expr.OmittedArg) {
            val stV = evalScalarAsNum(evalExpr(args[3], env)); if (stV is CellValue.Err) return listOf(listOf(stV))
            (stV as CellValue.Num).value
        } else 1.0
        if (rows < 1 || cols < 1 || rows.toLong() * cols > 4_096L) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
        var cur = start
        return List(rows) { List(cols) { val out = CellValue.Num(normalizeNegZero(cur)); cur += step; out } }
    }

    private fun tryBuildLambdaClosure(
        e: Expr,
        env: Map<String, CellValue>,
        rich: Map<String, RichBinding>,
    ): RichBinding.Closure? {
        if (e is Expr.Call && normalizeFnName(e.name) == "LAMBDA") {
            require(e.args.size >= 1) { "LAMBDA requires at least a body expression" }
            val params = ArrayList<String>(e.args.size - 1)
            for (i in 0 until e.args.size - 1) {
                val pn = exprParamName(e.args[i])
                    ?: throw IllegalArgumentException("invalid LAMBDA parameter #${i + 1}")
                require(pn !in params) { "duplicate LAMBDA parameter '$pn'" }
                params.add(pn)
            }
            return RichBinding.Closure(params, e.args.last(), HashMap(env), HashMap(rich))
        }
        if (e is Expr.Call) {
            val boundClosure = rich[normalizeVarName(e.name)] as? RichBinding.Closure ?: return null
            if (e.args.size > boundClosure.params.size) return null
            val rt = runtimeState.get()
            if (rt.lambdaDepth >= MAX_LAMBDA_CALL_DEPTH) return null
            val prevRich = rt.richScopes
            val callCells = HashMap(boundClosure.capturedEnv)
            val callRich = HashMap(boundClosure.capturedRich)
            rt.lambdaDepth++
            return try {
                bindClosureArgs(boundClosure, e.args, env, rich, callCells, callRich)
                tryBuildLambdaClosure(boundClosure.body, callCells, callRich)
            } finally {
                rt.lambdaDepth--
                rt.richScopes = prevRich
            }
        }
        if (e is Expr.SymbolRef) return rich[normalizeVarName(e.ident)] as? RichBinding.Closure
        if (e is Expr.CellRef) return rich[normalizeVarName(e.a1)] as? RichBinding.Closure
        if (e is Expr.Invoke) {
            val outer = tryBuildLambdaClosure(e.callee, env, rich) ?: return null
            // Support curried LAMBDA returning another LAMBDA: `LAMBDA(a, LAMBDA(b, a + b))(10)`!
            if (e.args.size > outer.params.size) return null
            val rt = runtimeState.get()
            if (rt.lambdaDepth >= MAX_LAMBDA_CALL_DEPTH) return null
            val prevRich = rt.richScopes
            val callCells = HashMap(outer.capturedEnv)
            val callRich = HashMap(outer.capturedRich)
            rt.lambdaDepth++
            return try {
                bindClosureArgs(outer, e.args, env, rich, callCells, callRich)
                tryBuildLambdaClosure(outer.body, callCells, callRich)
            } finally {
                rt.lambdaDepth--
                rt.richScopes = prevRich
            }
        }
        return null
    }

    private fun bindClosureArgs(
        closure: RichBinding.Closure,
        callArgs: List<Expr>,
        callerEnv: Map<String, CellValue>,
        callerRich: Map<String, RichBinding>,
        targetCells: MutableMap<String, CellValue>,
        targetRich: MutableMap<String, RichBinding>,
    ) {
        val rt = runtimeState.get()
        for ((idx, pname) in closure.params.withIndex()) {
            val argExpr = callArgs.getOrNull(idx) ?: Expr.OmittedArg
            if (argExpr == Expr.OmittedArg) {
                targetRich[pname] = RichBinding.Omitted
                targetCells.remove("\u0000LET:$pname")
                continue
            }
            rt.richScopes = callerRich
            val subClosure = tryBuildLambdaClosure(argExpr, callerEnv, callerRich)
            if (subClosure != null) {
                targetRich[pname] = subClosure
                targetCells.remove("\u0000LET:$pname")
                continue
            }
            val maybeGrid = tryEvalDynamicGridExpr(argExpr, callerEnv)
            if (maybeGrid != null && (maybeGrid.size > 1 || (maybeGrid.firstOrNull()?.size ?: 0) > 1)) {
                targetRich[pname] = RichBinding.Grid(maybeGrid)
                targetCells.remove("\u0000LET:$pname")
            } else {
                val sv = if (maybeGrid != null && maybeGrid.isNotEmpty() && maybeGrid[0].isNotEmpty()) {
                    maybeGrid[0][0]
                } else {
                    evalExpr(argExpr, callerEnv)
                }
                targetRich.remove(pname)
                targetCells["\u0000LET:$pname"] = sv
            }
        }
    }

    private fun evalInvoke(callee: Expr, args: List<Expr>, env: Map<String, CellValue>): CellValue {
        val g = evalInvokeGrid(callee, args, env)
        return if (g.size == 1 && g[0].size == 1) g[0][0] else g.firstOrNull()?.firstOrNull() ?: CellValue.Err(CellErr.Err.VALUE)
    }

    private fun evalInvokeGrid(callee: Expr, args: List<Expr>, env: Map<String, CellValue>): List<List<CellValue>> {
        val rt = runtimeState.get()
        val closure = tryBuildLambdaClosure(callee, env, rt.richScopes)
            ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
        return invokeClosureGrid(closure, args, env)
    }

    private fun invokeClosureScalar(
        closure: RichBinding.Closure,
        args: List<Expr>,
        callerEnv: Map<String, CellValue>,
    ): CellValue {
        val g = invokeClosureGrid(closure, args, callerEnv)
        return if (g.size == 1 && g[0].size == 1) g[0][0] else g.firstOrNull()?.firstOrNull() ?: CellValue.Err(CellErr.Err.VALUE)
    }

    private fun invokeClosureGrid(
        closure: RichBinding.Closure,
        args: List<Expr>,
        callerEnv: Map<String, CellValue>,
    ): List<List<CellValue>> {
        if (args.size > closure.params.size) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
        val rt = runtimeState.get()
        if (rt.lambdaDepth >= MAX_LAMBDA_CALL_DEPTH) {
            return listOf(listOf(CellValue.Err(CellErr.Err.NUM)))
        }
        val prevRich = rt.richScopes
        val callCells = HashMap(closure.capturedEnv)
        val callRich = HashMap(closure.capturedRich)
        bindClosureArgs(closure, args, callerEnv, prevRich, callCells, callRich)
        rt.richScopes = callRich
        rt.lambdaDepth++
        return try {
            exprTo2DGrid(closure.body, callCells) ?: listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
        } finally {
            rt.lambdaDepth--
            rt.richScopes = prevRich
        }
    }

    private fun applyClosureValues(
        closure: RichBinding.Closure,
        boundValues: List<Any>, // CellValue or List<List<CellValue>>
    ): List<List<CellValue>> {
        if (boundValues.size > closure.params.size) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
        val rt = runtimeState.get()
        if (rt.lambdaDepth >= MAX_LAMBDA_CALL_DEPTH) return listOf(listOf(CellValue.Err(CellErr.Err.NUM)))
        val prevRich = rt.richScopes
        val callCells = HashMap(closure.capturedEnv)
        val callRich = HashMap(closure.capturedRich)
        for ((idx, pname) in closure.params.withIndex()) {
            val bv = boundValues.getOrNull(idx)
            when (bv) {
                null -> {
                    callRich[pname] = RichBinding.Omitted
                    callCells.remove("\u0000LET:$pname")
                }
                is CellValue -> {
                    callRich.remove(pname)
                    callCells["\u0000LET:$pname"] = bv
                }
                is List<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    callRich[pname] = RichBinding.Grid(bv as List<List<CellValue>>)
                    callCells.remove("\u0000LET:$pname")
                }
            }
        }
        rt.richScopes = callRich
        rt.lambdaDepth++
        return try {
            exprTo2DGrid(closure.body, callCells) ?: listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
        } finally {
            rt.lambdaDepth--
            rt.richScopes = prevRich
        }
    }

    private fun evalIsOmitted(args: List<Expr>): CellValue {
        if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
        val a = args[0]
        if (a == Expr.OmittedArg) return CellValue.Bool(true)
        val pname = exprParamName(a) ?: return CellValue.Bool(false)
        return CellValue.Bool(runtimeState.get().richScopes[pname] === RichBinding.Omitted)
    }

    private fun evalReduce(args: List<Expr>, env: Map<String, CellValue>): CellValue {
        val g = evalReduceOrScanGrid("REDUCE", args, env)
        return if (g.size == 1 && g[0].size == 1) g[0][0] else g.firstOrNull()?.firstOrNull() ?: CellValue.Err(CellErr.Err.VALUE)
    }

    private fun evalReduceOrScanGrid(name: String, args: List<Expr>, env: Map<String, CellValue>): List<List<CellValue>> {
        if (args.size !in 2..3) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
        val hasInit = args.size == 3 && args[0] != Expr.OmittedArg
        val arrExpr = if (args.size == 3) args[1] else args[0]
        val fnExpr = if (args.size == 3) args[2] else args[1]
        val closure = tryBuildLambdaClosure(fnExpr, env, runtimeState.get().richScopes)
            ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
        if (closure.params.size < 2) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
        val src = exprTo2DGrid(arrExpr, env) ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
        val h = src.size; val w = src.firstOrNull()?.size ?: 0
        if (h == 0 || w == 0) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))

        var accGrid: List<List<CellValue>>? = if (hasInit) {
            exprTo2DGrid(args[0], env) ?: listOf(listOf(CellValue.Empty))
        } else null

        if (name == "REDUCE") {
            for (r in 0 until h) {
                for (c in 0 until w) {
                    val elem = src[r][c]
                    if (accGrid == null) {
                        accGrid = listOf(listOf(elem))
                    } else {
                        val accArg: Any = if (accGrid.size == 1 && accGrid[0].size == 1) accGrid[0][0] else accGrid
                        accGrid = applyClosureValues(closure, listOf(accArg, elem))
                    }
                }
            }
            return accGrid ?: listOf(listOf(CellValue.Empty))
        } else {
            // SCAN emits an h x w grid of running accumulator scalars!
            var accScalar: CellValue = if (accGrid != null) accGrid[0][0] else CellValue.Empty
            var first = !hasInit
            return List(h) { r ->
                List(w) { c ->
                    val elem = src[r][c]
                    accScalar = if (first) {
                        first = false
                        elem
                    } else {
                        applyClosureValues(closure, listOf(accScalar, elem))[0][0]
                    }
                    accScalar
                }
            }
        }
    }

    private fun evalArrayOrHofGrid(name: String, args: List<Expr>, env: Map<String, CellValue>): List<List<CellValue>> {
        val rt = runtimeState.get()
        return when (name) {
            "SCAN" -> evalReduceOrScanGrid("SCAN", args, env)
            "MAP" -> {
                if (args.size < 2) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val closure = tryBuildLambdaClosure(args.last(), env, rt.richScopes)
                    ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val arrs = args.dropLast(1).map {
                    exprTo2DGrid(it, env) ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                }
                if (closure.params.size != arrs.size) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val h = arrs[0].size; val w = arrs[0].firstOrNull()?.size ?: 0
                if (h == 0 || w == 0 || arrs.any { it.size != h || it[0].size != w }) {
                    return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                }
                List(h) { r ->
                    List(w) { c ->
                        val cellArgs = arrs.map { it[r][c] }
                        applyClosureValues(closure, cellArgs)[0][0]
                    }
                }
            }
            "BYROW" -> {
                if (args.size != 2) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val src = exprTo2DGrid(args[0], env) ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val closure = tryBuildLambdaClosure(args[1], env, rt.richScopes)
                    ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                if (closure.params.size != 1) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                List(src.size) { r ->
                    val rowSlice = listOf(src[r])
                    val res = applyClosureValues(closure, listOf(rowSlice))
                    // Excel BYROW rule: each LAMBDA application must return a 1x1 scalar (else #VALUE!/#CALC!)
                    val sv = if (res.size == 1 && res[0].size == 1) res[0][0] else CellValue.Err(CellErr.Err.VALUE)
                    listOf(sv)
                }
            }
            "BYCOL" -> {
                if (args.size != 2) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val src = exprTo2DGrid(args[0], env) ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val closure = tryBuildLambdaClosure(args[1], env, rt.richScopes)
                    ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                if (closure.params.size != 1) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val h = src.size; val w = src.firstOrNull()?.size ?: 0
                listOf(
                    List(w) { c ->
                        val colSlice = List(h) { r -> listOf(src[r][c]) }
                        val res = applyClosureValues(closure, listOf(colSlice))
                        if (res.size == 1 && res[0].size == 1) res[0][0] else CellValue.Err(CellErr.Err.VALUE)
                    },
                )
            }
            "MAKEARRAY" -> {
                if (args.size != 3) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val rV = evalScalarAsNum(evalExpr(args[0], env)); if (rV is CellValue.Err) return listOf(listOf(rV))
                val cV = evalScalarAsNum(evalExpr(args[1], env)); if (cV is CellValue.Err) return listOf(listOf(cV))
                val rows = (rV as CellValue.Num).value.toInt()
                val cols = (cV as CellValue.Num).value.toInt()
                if (rows < 1 || cols < 1 || rows.toLong() * cols > 4_096L) {
                    return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                }
                val closure = tryBuildLambdaClosure(args[2], env, rt.richScopes)
                    ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                if (closure.params.size != 2) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                List(rows) { r ->
                    List(cols) { c ->
                        applyClosureValues(
                            closure,
                            listOf(CellValue.Num((r + 1).toDouble()), CellValue.Num((c + 1).toDouble())),
                        )[0][0]
                    }
                }
            }
            "VSTACK" -> {
                if (args.isEmpty()) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val grids = args.map { exprTo2DGrid(it, env) ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE))) }
                val maxW = grids.maxOf { it.firstOrNull()?.size ?: 0 }.coerceAtLeast(1)
                val out = ArrayList<List<CellValue>>()
                for (g in grids) {
                    for (row in g) {
                        out.add(List(maxW) { c -> if (c < row.size) row[c] else CellValue.Err(CellErr.Err.NA) })
                    }
                }
                out
            }
            "HSTACK" -> {
                if (args.isEmpty()) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val grids = args.map { exprTo2DGrid(it, env) ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE))) }
                val maxH = grids.maxOf { it.size }.coerceAtLeast(1)
                List(maxH) { r ->
                    val rowOut = ArrayList<CellValue>()
                    for (g in grids) {
                        val gw = g.firstOrNull()?.size ?: 1
                        if (r < g.size) rowOut.addAll(g[r])
                        else repeat(gw) { rowOut.add(CellValue.Err(CellErr.Err.NA)) }
                    }
                    rowOut
                }
            }
            "TAKE", "DROP" -> {
                if (args.size !in 2..3) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val src = exprTo2DGrid(args[0], env) ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val h = src.size; val w = src.firstOrNull()?.size ?: 0
                if (h == 0 || w == 0) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                // Per Excel 365 & rustsheet #1608: an omitted row/col count keeps EVERY row/col!
                val rowArgOmitted = args[1] == Expr.OmittedArg
                val colArgOmitted = args.size < 3 || args[2] == Expr.OmittedArg
                val rCnt = if (rowArgOmitted) null else {
                    val v = evalScalarAsNum(evalExpr(args[1], env)); if (v is CellValue.Err) return listOf(listOf(v))
                    (v as CellValue.Num).value.toInt()
                }
                val cCnt = if (colArgOmitted) null else {
                    val v = evalScalarAsNum(evalExpr(args[2], env)); if (v is CellValue.Err) return listOf(listOf(v))
                    (v as CellValue.Num).value.toInt()
                }
                val rowIndices = sliceIndices(h, rCnt, isTake = name == "TAKE")
                val colIndices = sliceIndices(w, cCnt, isTake = name == "TAKE")
                if (rowIndices.isEmpty() || colIndices.isEmpty()) {
                    return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                }
                rowIndices.map { r -> colIndices.map { c -> src[r][c] } }
            }
            "CHOOSECOLS", "CHOOSEROWS" -> {
                if (args.size < 2) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val src = exprTo2DGrid(args[0], env) ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val h = src.size; val w = src.firstOrNull()?.size ?: 0
                val limit = if (name == "CHOOSECOLS") w else h
                val picked = ArrayList<Int>()
                for (i in 1 until args.size) {
                    val vec = exprTo1DVector(args[i], env) ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                    for (item in vec) {
                        val nv = evalScalarAsNum(item); if (nv is CellValue.Err) return listOf(listOf(nv))
                        val rawIdx = (nv as CellValue.Num).value.toInt()
                        if (rawIdx == 0 || abs(rawIdx) > limit) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                        picked.add(if (rawIdx > 0) rawIdx - 1 else limit + rawIdx)
                    }
                }
                if (picked.isEmpty()) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                if (name == "CHOOSECOLS") src.map { row -> picked.map { c -> row[c] } }
                else picked.map { r -> src[r] }
            }
            "TOCOL", "TOROW" -> {
                if (args.size !in 1..3) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val src = exprTo2DGrid(args[0], env) ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val ignore = if (args.size >= 2 && args[1] != Expr.OmittedArg) {
                    val v = evalScalarAsNum(evalExpr(args[1], env)); if (v is CellValue.Err) return listOf(listOf(v))
                    (v as CellValue.Num).value.toInt()
                } else 0 // 0=keep all, 1=ignore blanks, 2=ignore errors, 3=ignore blanks & errors
                val scanByCol = if (args.size >= 3 && args[2] != Expr.OmittedArg) {
                    val b = evalAsBool(evalExpr(args[2], env)); if (b is CellValue.Err) return listOf(listOf(b))
                    (b as CellValue.Bool).value
                } else false
                val h = src.size; val w = src.firstOrNull()?.size ?: 0
                val flat = ArrayList<CellValue>(h * w)
                if (scanByCol) {
                    for (c in 0 until w) for (r in 0 until h) flat.add(src[r][c])
                } else {
                    for (r in 0 until h) for (c in 0 until w) flat.add(src[r][c])
                }
                val filtered = flat.filter { v ->
                    val dropBlank = (ignore == 1 || ignore == 3) && (v == CellValue.Empty || (v is CellValue.Text && v.value.isEmpty()))
                    val dropErr = (ignore == 2 || ignore == 3) && (v is CellValue.Err)
                    (!dropBlank) && (!dropErr)
                }
                if (filtered.isEmpty()) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                if (name == "TOCOL") filtered.map { listOf(it) } else listOf(filtered)
            }
            "WRAPROWS", "WRAPCOLS" -> {
                if (args.size !in 2..3) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val vec = exprTo1DVector(args[0], env) ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val cntV = evalScalarAsNum(evalExpr(args[1], env)); if (cntV is CellValue.Err) return listOf(listOf(cntV))
                val wrapCount = (cntV as CellValue.Num).value.toInt()
                if (wrapCount < 1 || vec.isEmpty()) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val pad = if (args.size == 3 && args[2] != Expr.OmittedArg) evalExpr(args[2], env) else CellValue.Err(CellErr.Err.NA)
                val chunks = (vec.size + wrapCount - 1) / wrapCount
                if (name == "WRAPROWS") {
                    List(chunks) { r ->
                        List(wrapCount) { c -> val idx = r * wrapCount + c; if (idx < vec.size) vec[idx] else pad }
                    }
                } else {
                    List(wrapCount) { r ->
                        List(chunks) { c -> val idx = c * wrapCount + r; if (idx < vec.size) vec[idx] else pad }
                    }
                }
            }
            else -> listOf(listOf(CellValue.Err(CellErr.Err.NAME)))
        }
    }

    private fun sliceIndices(total: Int, countOrNull: Int?, isTake: Boolean): List<Int> {
        if (countOrNull == null) return (0 until total).toList()
        if (isTake) {
            if (countOrNull == 0) return emptyList()
            return if (countOrNull > 0) {
                (0 until minOf(total, countOrNull)).toList()
            } else {
                val k = minOf(total, -countOrNull)
                ((total - k) until total).toList()
            }
        } else {
            if (countOrNull == 0) return (0 until total).toList()
            return if (countOrNull > 0) {
                if (countOrNull >= total) emptyList() else (countOrNull until total).toList()
            } else {
                val dropTail = -countOrNull
                if (dropTail >= total) emptyList() else (0 until (total - dropTail)).toList()
            }
        }
    }

    private fun evalR49ScalarFunction(name: String, args: List<Expr>, env: Map<String, CellValue>): CellValue = when (name) {
        "SUMSQ" -> {
            collectNumericAggregate(args, env).fold(
                onSuccess = { nums ->
                    val s = nums.sumOf { it * it }
                    if (!s.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(s))
                },
                onFailure = { CellValue.Err((it as CellErrorSignal).err) },
            )
        }
        "SUMX2MY2", "SUMX2PY2", "SUMXMY2" -> {
            if (args.size != 2) CellValue.Err(CellErr.Err.VALUE)
            else {
                val x = exprTo1DVector(args[0], env)
                val y = exprTo1DVector(args[1], env)
                if (x == null || y == null || x.size != y.size || x.isEmpty()) CellValue.Err(CellErr.Err.NA)
                else {
                    var sum = 0.0
                    var err: CellValue.Err? = null
                    for (i in x.indices) {
                        val xi = x[i]; val yi = y[i]
                        if (xi is CellValue.Err) { err = xi; break }
                        if (yi is CellValue.Err) { err = yi; break }
                        if (xi is CellValue.Num && yi is CellValue.Num) {
                            sum += when (name) {
                                "SUMX2MY2" -> xi.value * xi.value - yi.value * yi.value
                                "SUMX2PY2" -> xi.value * xi.value + yi.value * yi.value
                                else -> (xi.value - yi.value).pow(2.0)
                            }
                        }
                    }
                    err ?: if (!sum.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(sum))
                }
            }
        }
        "HARMEAN" -> {
            collectNumericAggregate(args, env).fold(
                onSuccess = { nums ->
                    if (nums.isEmpty() || nums.any { it <= 0.0 }) CellValue.Err(CellErr.Err.NUM)
                    else CellValue.Num(normalizeNegZero(nums.size / nums.sumOf { 1.0 / it }))
                },
                onFailure = { CellValue.Err((it as CellErrorSignal).err) },
            )
        }
        "TRIMMEAN" -> {
            if (args.size != 2) CellValue.Err(CellErr.Err.VALUE)
            else collectNumericAggregate(listOf(args[0]), env).fold(
                onSuccess = { nums ->
                    val pv = evalScalarAsNum(evalExpr(args[1], env))
                    if (pv is CellValue.Err) pv
                    else {
                        val pct = (pv as CellValue.Num).value
                        if (nums.isEmpty() || pct < 0.0 || pct >= 1.0) CellValue.Err(CellErr.Err.NUM)
                        else {
                            val sorted = nums.sorted()
                            val cutEachSide = floor((nums.size * pct) / 2.0).toInt()
                            val trimmed = sorted.subList(cutEachSide, sorted.size - cutEachSide)
                            if (trimmed.isEmpty()) CellValue.Err(CellErr.Err.NUM)
                            else CellValue.Num(normalizeNegZero(trimmed.sum() / trimmed.size))
                        }
                    }
                },
                onFailure = { CellValue.Err((it as CellErrorSignal).err) },
            )
        }
        "DEVSQ", "AVEDEV" -> {
            collectNumericAggregate(args, env).fold(
                onSuccess = { nums ->
                    if (nums.isEmpty()) CellValue.Err(CellErr.Err.NUM)
                    else {
                        val mean = nums.sum() / nums.size
                        val res = if (name == "DEVSQ") nums.sumOf { (it - mean) * (it - mean) }
                        else nums.sumOf { abs(it - mean) } / nums.size
                        CellValue.Num(normalizeNegZero(res))
                    }
                },
                onFailure = { CellValue.Err((it as CellErrorSignal).err) },
            )
        }
        "LARGE", "SMALL" -> {
            if (args.size != 2) CellValue.Err(CellErr.Err.VALUE)
            else collectNumericAggregate(listOf(args[0]), env).fold(
                onSuccess = { nums ->
                    val kv = evalScalarAsNum(evalExpr(args[1], env))
                    if (kv is CellValue.Err) kv
                    else {
                        val k = (kv as CellValue.Num).value.toInt()
                        if (k !in 1..nums.size) CellValue.Err(CellErr.Err.NUM)
                        else {
                            val sorted = nums.sorted()
                            val pick = if (name == "SMALL") sorted[k - 1] else sorted[sorted.size - k]
                            CellValue.Num(normalizeNegZero(pick))
                        }
                    }
                },
                onFailure = { CellValue.Err((it as CellErrorSignal).err) },
            )
        }
        "FISHER", "FISHERINV" -> {
            if (args.size != 1) CellValue.Err(CellErr.Err.VALUE)
            else {
                val xv = evalScalarAsNum(evalExpr(args[0], env))
                if (xv is CellValue.Err) xv
                else {
                    val x = (xv as CellValue.Num).value
                    if (name == "FISHER") {
                        if (x <= -1.0 || x >= 1.0) CellValue.Err(CellErr.Err.NUM)
                        else CellValue.Num(normalizeNegZero(0.5 * ln((1.0 + x) / (1.0 - x))))
                    } else {
                        val e2y = exp(2.0 * x)
                        val r = if (!e2y.isFinite()) 1.0 else (e2y - 1.0) / (e2y + 1.0)
                        CellValue.Num(normalizeNegZero(r))
                    }
                }
            }
        }
        "STANDARDIZE" -> {
            if (args.size != 3) CellValue.Err(CellErr.Err.VALUE)
            else {
                val xv = evalScalarAsNum(evalExpr(args[0], env))
                val mv = evalScalarAsNum(evalExpr(args[1], env))
                val sv = evalScalarAsNum(evalExpr(args[2], env))
                when {
                    xv is CellValue.Err -> xv
                    mv is CellValue.Err -> mv
                    sv is CellValue.Err -> sv
                    (sv as CellValue.Num).value <= 0.0 -> CellValue.Err(CellErr.Err.NUM)
                    else -> CellValue.Num(normalizeNegZero(((xv as CellValue.Num).value - (mv as CellValue.Num).value) / sv.value))
                }
            }
        }
        "PERMUT" -> {
            if (args.size != 2) CellValue.Err(CellErr.Err.VALUE)
            else {
                val nv = evalScalarAsNum(evalExpr(args[0], env))
                val kv = evalScalarAsNum(evalExpr(args[1], env))
                when {
                    nv is CellValue.Err -> nv
                    kv is CellValue.Err -> kv
                    else -> {
                        val n = floor((nv as CellValue.Num).value).toLong()
                        val k = floor((kv as CellValue.Num).value).toLong()
                        if (n < 0 || k < 0 || k > n || k > 170) CellValue.Err(CellErr.Err.NUM)
                        else {
                            var acc = 1.0
                            for (i in 0 until k) acc *= (n - i).toDouble()
                            if (!acc.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(acc))
                        }
                    }
                }
            }
        }
        "PDURATION", "RRI" -> {
            if (args.size != 3) CellValue.Err(CellErr.Err.VALUE)
            else {
                val a0 = evalScalarAsNum(evalExpr(args[0], env))
                val a1 = evalScalarAsNum(evalExpr(args[1], env))
                val a2 = evalScalarAsNum(evalExpr(args[2], env))
                when {
                    a0 is CellValue.Err -> a0
                    a1 is CellValue.Err -> a1
                    a2 is CellValue.Err -> a2
                    else -> {
                        val x0 = (a0 as CellValue.Num).value
                        val x1 = (a1 as CellValue.Num).value
                        val x2 = (a2 as CellValue.Num).value
                        if (x0 <= 0.0 || x1 <= 0.0 || x2 <= 0.0) CellValue.Err(CellErr.Err.NUM)
                        else {
                            val res = if (name == "PDURATION") (ln(x2) - ln(x1)) / ln(1.0 + x0)
                            else (x2 / x1).pow(1.0 / x0) - 1.0
                            if (!res.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(res))
                        }
                    }
                }
            }
        }
        else -> { // DOLLARDE(fractional_dollar, fraction)
            if (args.size != 2) CellValue.Err(CellErr.Err.VALUE)
            else {
                val dv = evalScalarAsNum(evalExpr(args[0], env))
                val fv = evalScalarAsNum(evalExpr(args[1], env))
                when {
                    dv is CellValue.Err -> dv
                    fv is CellValue.Err -> fv
                    else -> {
                        val fd = (dv as CellValue.Num).value
                        val frac = floor((fv as CellValue.Num).value).toInt()
                        if (frac < 0) CellValue.Err(CellErr.Err.NUM)
                        else if (frac == 0) CellValue.Err(CellErr.Err.DIV0)
                        else {
                            val signMul = if (fd < 0.0) -1.0 else 1.0
                            val absFd = abs(fd)
                            val whole = floor(absFd)
                            val rem = absFd - whole
                            val scale = 10.0.pow(ceil(log10((frac + 1).toDouble()).coerceAtLeast(1.0)))
                            val res = signMul * (whole + (rem * scale) / frac.toDouble())
                            CellValue.Num(normalizeNegZero(res))
                        }
                    }
                }
            }
        }
    }

    private fun evalR50MatrixGrid(name: String, args: List<Expr>, env: Map<String, CellValue>): List<List<CellValue>> {
        fun gridNums(e: Expr): Pair<Array<DoubleArray>?, CellValue.Err?> {
            val g = exprTo2DGrid(e, env) ?: return null to CellValue.Err(CellErr.Err.VALUE)
            val h = g.size; val w = g.firstOrNull()?.size ?: 0
            if (h == 0 || w == 0 || g.any { it.size != w }) return null to CellValue.Err(CellErr.Err.VALUE)
            val m = Array(h) { DoubleArray(w) }
            for (r in 0 until h) {
                for (c in 0 until w) {
                    when (val cv = g[r][c]) {
                        is CellValue.Err -> return null to cv
                        is CellValue.Num -> m[r][c] = cv.value
                        else -> return null to CellValue.Err(CellErr.Err.VALUE)
                    }
                }
            }
            return m to null
        }
        return when (name) {
            "MUNIT" -> {
                if (args.size != 1) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val nv = evalScalarAsNum(evalExpr(args[0], env))
                if (nv is CellValue.Err) return listOf(listOf(nv))
                val n = floor((nv as CellValue.Num).value).toInt()
                if (n !in 1..64) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                List(n) { r -> List(n) { c -> CellValue.Num(if (r == c) 1.0 else 0.0) } }
            }
            "MMULT" -> {
                if (args.size != 2) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val (a, ea) = gridNums(args[0]); if (ea != null || a == null) return listOf(listOf(ea!!))
                val (b, eb) = gridNums(args[1]); if (eb != null || b == null) return listOf(listOf(eb!!))
                val m = a.size; val k = a[0].size; val kb = b.size; val n = b[0].size
                if (k != kb) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                List(m) { r ->
                    List(n) { c ->
                        var s = 0.0
                        for (t in 0 until k) s += a[r][t] * b[t][c]
                        if (!s.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(s))
                    }
                }
            }
            "MINVERSE" -> {
                if (args.size != 1) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val (a, ea) = gridNums(args[0]); if (ea != null || a == null) return listOf(listOf(ea!!))
                val n = a.size
                if (n != a[0].size || n > 64) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val aug = Array(n) { r ->
                    DoubleArray(2 * n) { c ->
                        if (c < n) a[r][c] else if (c - n == r) 1.0 else 0.0
                    }
                }
                for (col in 0 until n) {
                    var piv = col
                    for (r in col + 1 until n) if (abs(aug[r][col]) > abs(aug[piv][col])) piv = r
                    if (abs(aug[piv][col]) < 1e-12) return listOf(listOf(CellValue.Err(CellErr.Err.NUM)))
                    if (piv != col) { val tmp = aug[col]; aug[col] = aug[piv]; aug[piv] = tmp }
                    val diag = aug[col][col]
                    for (c in 0 until 2 * n) aug[col][c] /= diag
                    for (r in 0 until n) {
                        if (r == col) continue
                        val f = aug[r][col]
                        if (f != 0.0) for (c in 0 until 2 * n) aug[r][c] -= f * aug[col][c]
                    }
                }
                List(n) { r ->
                    List(n) { c ->
                        val v = aug[r][n + c]
                        if (!v.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(v))
                    }
                }
            }
            "FREQUENCY" -> {
                if (args.size != 2) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val dataRes = collectNumericAggregate(listOf(args[0]), env)
                val binsRes = collectNumericAggregate(listOf(args[1]), env)
                dataRes.fold(
                    onSuccess = { data ->
                        binsRes.fold(
                            onSuccess = { bins ->
                                val indexedBins = bins.withIndex().sortedBy { it.value }
                                val counts = IntArray(bins.size + 1)
                                for (x in data) {
                                    var placed = false
                                    for ((origIdx, upper) in indexedBins) {
                                        if (x <= upper) { counts[origIdx]++; placed = true; break }
                                    }
                                    if (!placed) counts[bins.size]++
                                }
                                counts.map { listOf(CellValue.Num(it.toDouble())) }
                            },
                            onFailure = { listOf(listOf(CellValue.Err((it as CellErrorSignal).err))) },
                        )
                    },
                    onFailure = { listOf(listOf(CellValue.Err((it as CellErrorSignal).err))) },
                )
            }
            "TREND", "GROWTH" -> {
                if (args.isEmpty() || args.size > 4) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val yGrid = exprTo2DGrid(args[0], env) ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val yFlat = yGrid.flatten()
                val ys = ArrayList<Double>(yFlat.size)
                for (cv in yFlat) {
                    if (cv is CellValue.Err) return listOf(listOf(cv))
                    if (cv !is CellValue.Num) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                    if (name == "GROWTH" && cv.value <= 0.0) return listOf(listOf(CellValue.Err(CellErr.Err.NUM)))
                    ys.add(if (name == "GROWTH") ln(cv.value) else cv.value)
                }
                if (ys.isEmpty()) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                val xs = if (args.size >= 2 && args[1] != Expr.OmittedArg) {
                    val xg = exprTo2DGrid(args[1], env) ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                    val xf = xg.flatten()
                    if (xf.size != ys.size) return listOf(listOf(CellValue.Err(CellErr.Err.REF)))
                    xf.map {
                        if (it is CellValue.Err) return listOf(listOf(it))
                        if (it !is CellValue.Num) return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                        it.value
                    }
                } else List(ys.size) { (it + 1).toDouble() }
                val newXGrid = if (args.size >= 3 && args[2] != Expr.OmittedArg) {
                    exprTo2DGrid(args[2], env) ?: return listOf(listOf(CellValue.Err(CellErr.Err.VALUE)))
                } else if (args.size >= 2 && args[1] != Expr.OmittedArg) {
                    exprTo2DGrid(args[1], env)!!
                } else {
                    yGrid.mapIndexed { r, row -> List(row.size) { c -> CellValue.Num((r * row.size + c + 1).toDouble()) } }
                }
                val hasConst = if (args.size >= 4 && args[3] != Expr.OmittedArg) {
                    val bv = evalAsBool(evalExpr(args[3], env))
                    if (bv is CellValue.Err) return listOf(listOf(bv))
                    (bv as CellValue.Bool).value
                } else true
                val (slope, intercept) = if (hasConst) {
                    val mx = xs.average(); val my = ys.average()
                    var sxx = 0.0; var sxy = 0.0
                    for (i in xs.indices) { val dx = xs[i] - mx; sxx += dx * dx; sxy += dx * (ys[i] - my) }
                    val b1 = if (sxx == 0.0) 0.0 else sxy / sxx
                    b1 to (my - b1 * mx)
                } else {
                    var sxx = 0.0; var sxy = 0.0
                    for (i in xs.indices) { sxx += xs[i] * xs[i]; sxy += xs[i] * ys[i] }
                    if (sxx == 0.0) return listOf(listOf(CellValue.Err(CellErr.Err.DIV0)))
                    (sxy / sxx) to 0.0
                }
                newXGrid.map { row ->
                    row.map { cell ->
                        when (cell) {
                            is CellValue.Err -> cell
                            is CellValue.Num -> {
                                val lin = intercept + slope * cell.value
                                val pred = if (name == "GROWTH") exp(lin) else lin
                                if (!pred.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(pred))
                            }
                            else -> CellValue.Err(CellErr.Err.VALUE)
                        }
                    }
                }
            }
            else -> listOf(listOf(CellValue.Err(CellErr.Err.NAME)))
        }
    }

    private fun erfApprox(x: Double): Double {
        if (x == 0.0) return 0.0
        val sgn = if (x < 0.0) -1.0 else 1.0
        val ax = abs(x)
        // Abramowitz & Stegun 7.1.26 (max error <= 1.5e-7)
        val t = 1.0 / (1.0 + 0.3275911 * ax)
        val poly = (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t
        return sgn * (1.0 - poly * exp(-ax * ax))
    }

    private fun lnGammaLanczos(z: Double): Double {
        // Lanczos g=7, n=9 Chebyshev coefficients (double precision ~15 digits)
        val p = doubleArrayOf(
            0.99999999999980993,
            676.5203681218851,
            -1259.1392167224028,
            771.32342877765313,
            -176.61502916214059,
            12.507343278686905,
            -0.13857109526572012,
            9.9843695780195716e-6,
            1.5056327351493116e-7,
        )
        var x = p[0]
        val zm1 = z - 1.0
        for (i in 1 until p.size) x += p[i] / (zm1 + i.toDouble())
        val t = zm1 + 7.5
        return 0.5 * ln(2.0 * Math.PI) + (zm1 + 0.5) * ln(t) - t + ln(x)
    }

    private tailrec fun gcdLong(a: Long, b: Long): Long = if (b == 0L) abs(a) else gcdLong(b, a % b)

    private fun evalR50ScalarFunction(name: String, args: List<Expr>, env: Map<String, CellValue>): CellValue = when (name) {
        "MDETERM" -> {
            if (args.size != 1) CellValue.Err(CellErr.Err.VALUE)
            else {
                val g = exprTo2DGrid(args[0], env)
                if (g == null || g.isEmpty() || g.size != (g.firstOrNull()?.size ?: 0) || g.size > 64) {
                    CellValue.Err(CellErr.Err.VALUE)
                } else {
                    val n = g.size
                    val m = Array(n) { DoubleArray(n) }
                    var err: CellValue.Err? = null
                    outer@ for (r in 0 until n) {
                        for (c in 0 until n) {
                            when (val cv = g[r][c]) {
                                is CellValue.Err -> { err = cv; break@outer }
                                is CellValue.Num -> m[r][c] = cv.value
                                else -> { err = CellValue.Err(CellErr.Err.VALUE); break@outer }
                            }
                        }
                    }
                    if (err != null) err
                    else {
                        var signFlip = 1.0
                        var det = 1.0
                        for (col in 0 until n) {
                            var piv = col
                            for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[piv][col])) piv = r
                            if (abs(m[piv][col]) < 1e-15) { det = 0.0; break }
                            if (piv != col) { val tmp = m[col]; m[col] = m[piv]; m[piv] = tmp; signFlip = -signFlip }
                            val pv = m[col][col]
                            det *= pv
                            for (r in col + 1 until n) {
                                val f = m[r][col] / pv
                                for (c in col + 1 until n) m[r][c] -= f * m[col][c]
                            }
                        }
                        val out = det * signFlip
                        if (!out.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(out))
                    }
                }
            }
        }
        "RANK", "RANK.EQ", "RANK.AVG" -> {
            if (args.size !in 2..3) CellValue.Err(CellErr.Err.VALUE)
            else {
                val xv = evalScalarAsNum(evalExpr(args[0], env))
                if (xv is CellValue.Err) xv
                else {
                    val order = if (args.size == 3 && args[2] != Expr.OmittedArg) {
                        val ov = evalScalarAsNum(evalExpr(args[2], env))
                        if (ov is CellValue.Err) return ov
                        (ov as CellValue.Num).value.toInt()
                    } else 0
                    collectNumericAggregate(listOf(args[1]), env).fold(
                        onSuccess = { nums ->
                            val target = (xv as CellValue.Num).value
                            val sorted = if (order == 0) nums.sortedDescending() else nums.sorted()
                            val first = sorted.indexOfFirst { it == target }
                            if (first < 0) CellValue.Err(CellErr.Err.NA)
                            else if (name == "RANK.AVG") {
                                val last = sorted.indexOfLast { it == target }
                                CellValue.Num(((first + 1) + (last + 1)) / 2.0)
                            } else {
                                CellValue.Num((first + 1).toDouble())
                            }
                        },
                        onFailure = { CellValue.Err((it as CellErrorSignal).err) },
                    )
                }
            }
        }
        "PERCENTRANK", "PERCENTRANK.INC", "PERCENTRANK.EXC" -> {
            if (args.size !in 2..3) CellValue.Err(CellErr.Err.VALUE)
            else {
                val xv = evalScalarAsNum(evalExpr(args[1], env))
                if (xv is CellValue.Err) xv
                else {
                    val sig = if (args.size == 3 && args[2] != Expr.OmittedArg) {
                        val sv = evalScalarAsNum(evalExpr(args[2], env))
                        if (sv is CellValue.Err) return sv
                        floor((sv as CellValue.Num).value).toInt()
                    } else 3
                    if (sig < 1 || sig > 15) CellValue.Err(CellErr.Err.NUM)
                    else collectNumericAggregate(listOf(args[0]), env).fold(
                        onSuccess = { nums ->
                            if (nums.isEmpty()) return CellValue.Err(CellErr.Err.NUM)
                            val s = nums.sorted()
                            val x = (xv as CellValue.Num).value
                            if (x < s.first() || x > s.last()) return CellValue.Err(CellErr.Err.NA)
                            val raw = if (name == "PERCENTRANK.EXC") {
                                val n = s.size
                                val exact = s.indexOfFirst { it == x }
                                if (exact >= 0) (exact + 1).toDouble() / (n + 1).toDouble()
                                else {
                                    val lo = s.indexOfLast { it < x }
                                    val frac = (x - s[lo]) / (s[lo + 1] - s[lo])
                                    (lo + 1 + frac) / (n + 1).toDouble()
                                }
                            } else {
                                if (s.size == 1) 1.0
                                else {
                                    val exact = s.indexOfFirst { it == x }
                                    if (exact >= 0) exact.toDouble() / (s.size - 1).toDouble()
                                    else {
                                        val lo = s.indexOfLast { it < x }
                                        val frac = (x - s[lo]) / (s[lo + 1] - s[lo])
                                        (lo + frac) / (s.size - 1).toDouble()
                                    }
                                }
                            }
                            val scale = 10.0.pow(sig)
                            CellValue.Num(normalizeNegZero(floor(raw * scale + 1e-12) / scale))
                        },
                        onFailure = { CellValue.Err((it as CellErrorSignal).err) },
                    )
                }
            }
        }
        "BITAND", "BITOR", "BITXOR", "BITLSHIFT", "BITRSHIFT" -> {
            if (args.size != 2) CellValue.Err(CellErr.Err.VALUE)
            else {
                val av = evalScalarAsNum(evalExpr(args[0], env))
                val bv = evalScalarAsNum(evalExpr(args[1], env))
                when {
                    av is CellValue.Err -> av
                    bv is CellValue.Err -> bv
                    else -> {
                        val max48 = (1L shl 48) - 1L
                        val aRaw = (av as CellValue.Num).value
                        val bRaw = (bv as CellValue.Num).value
                        if (aRaw < 0.0 || aRaw > max48.toDouble() || floor(aRaw) != aRaw) CellValue.Err(CellErr.Err.NUM)
                        else {
                            val a = aRaw.toLong()
                            if (name == "BITLSHIFT" || name == "BITRSHIFT") {
                                val sh = floor(bRaw).toInt()
                                if (abs(sh) > 53) CellValue.Err(CellErr.Err.NUM)
                                else {
                                    val eff = if (name == "BITLSHIFT") sh else -sh
                                    val res = if (eff >= 0) a shl eff else a ushr (-eff)
                                    if (res > max48 || res < 0L) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(res.toDouble())
                                }
                            } else {
                                if (bRaw < 0.0 || bRaw > max48.toDouble() || floor(bRaw) != bRaw) CellValue.Err(CellErr.Err.NUM)
                                else {
                                    val b = bRaw.toLong()
                                    val res = when (name) {
                                        "BITAND" -> a and b
                                        "BITOR" -> a or b
                                        else -> a xor b
                                    }
                                    CellValue.Num(res.toDouble())
                                }
                            }
                        }
                    }
                }
            }
        }
        "DEC2BIN", "DEC2HEX", "DEC2OCT" -> {
            if (args.size !in 1..2) CellValue.Err(CellErr.Err.VALUE)
            else {
                val nv = evalScalarAsNum(evalExpr(args[0], env))
                if (nv is CellValue.Err) nv
                else {
                    val places = if (args.size == 2 && args[1] != Expr.OmittedArg) {
                        val pv = evalScalarAsNum(evalExpr(args[1], env))
                        if (pv is CellValue.Err) return pv
                        floor((pv as CellValue.Num).value).toInt()
                    } else null
                    val (radix, widthBits) = when (name) {
                        "DEC2BIN" -> 2 to 10 // -512..511
                        "DEC2OCT" -> 8 to 30 // -536870912..536870911
                        else -> 16 to 40 // -549755813888..549755813887
                    }
                    val lo = -(1L shl (widthBits - 1))
                    val hi = (1L shl (widthBits - 1)) - 1L
                    val v = floor((nv as CellValue.Num).value).toLong()
                    if (v !in lo..hi || (places != null && (places < 1 || places > 10))) CellValue.Err(CellErr.Err.NUM)
                    else if (v < 0L) {
                        val twos = (1L shl widthBits) + v
                        CellValue.Text(twos.toString(radix).uppercase())
                    } else {
                        val raw = v.toString(radix).uppercase()
                        if (places != null && raw.length > places) CellValue.Err(CellErr.Err.NUM)
                        else CellValue.Text(if (places != null) raw.padStart(places, '0') else raw)
                    }
                }
            }
        }
        "BIN2DEC", "HEX2DEC", "OCT2DEC", "BIN2HEX", "HEX2BIN" -> {
            if (args.size !in 1..2) CellValue.Err(CellErr.Err.VALUE)
            else {
                val sv = evalExpr(args[0], env)
                if (sv is CellValue.Err) sv
                else {
                    val places = if (args.size == 2 && args[1] != Expr.OmittedArg) {
                        val pv = evalScalarAsNum(evalExpr(args[1], env))
                        if (pv is CellValue.Err) return pv
                        floor((pv as CellValue.Num).value).toInt()
                    } else null
                    val str = sv.displayText().trim().uppercase()
                    val (radix, maxDigits, widthBits) = when {
                        name.startsWith("BIN") -> Triple(2, 10, 10)
                        name.startsWith("OCT") -> Triple(8, 10, 30)
                        else -> Triple(16, 10, 40)
                    }
                    val parsed = if (str.isEmpty() || str.length > maxDigits) null else str.toLongOrNull(radix)
                    if (parsed == null) CellValue.Err(CellErr.Err.NUM)
                    else {
                        val signed = if (parsed >= (1L shl (widthBits - 1))) parsed - (1L shl widthBits) else parsed
                        when (name) {
                            "BIN2DEC", "HEX2DEC", "OCT2DEC" -> CellValue.Num(signed.toDouble())
                            "BIN2HEX" -> {
                                if (signed < 0L) CellValue.Text(((1L shl 40) + signed).toString(16).uppercase())
                                else {
                                    val raw = signed.toString(16).uppercase()
                                    if (places != null && (places < 1 || places > 10 || raw.length > places)) CellValue.Err(CellErr.Err.NUM)
                                    else CellValue.Text(if (places != null) raw.padStart(places, '0') else raw)
                                }
                            }
                            else -> { // HEX2BIN
                                if (signed !in -512L..511L) CellValue.Err(CellErr.Err.NUM)
                                else if (signed < 0L) CellValue.Text(((1L shl 10) + signed).toString(2))
                                else {
                                    val raw = signed.toString(2)
                                    if (places != null && (places < 1 || places > 10 || raw.length > places)) CellValue.Err(CellErr.Err.NUM)
                                    else CellValue.Text(if (places != null) raw.padStart(places, '0') else raw)
                                }
                            }
                        }
                    }
                }
            }
        }
        "DELTA", "GESTEP" -> {
            if (args.size !in 1..2) CellValue.Err(CellErr.Err.VALUE)
            else {
                val av = evalScalarAsNum(evalExpr(args[0], env))
                if (av is CellValue.Err) av
                else {
                    val b = if (args.size == 2 && args[1] != Expr.OmittedArg) {
                        val bv = evalScalarAsNum(evalExpr(args[1], env))
                        if (bv is CellValue.Err) return bv
                        (bv as CellValue.Num).value
                    } else 0.0
                    val a = (av as CellValue.Num).value
                    val hit = if (name == "DELTA") a == b else a >= b
                    CellValue.Num(if (hit) 1.0 else 0.0)
                }
            }
        }
        "CONVERT" -> {
            if (args.size != 3) CellValue.Err(CellErr.Err.VALUE)
            else {
                val xv = evalScalarAsNum(evalExpr(args[0], env))
                val fu = evalExpr(args[1], env)
                val tu = evalExpr(args[2], env)
                when {
                    xv is CellValue.Err -> xv
                    fu is CellValue.Err -> fu
                    tu is CellValue.Err -> tu
                    else -> convertEngineeringUnit((xv as CellValue.Num).value, fu.displayText().trim(), tu.displayText().trim())
                }
            }
        }
        "ERF", "ERFC" -> {
            if (name == "ERFC" && args.size != 1) CellValue.Err(CellErr.Err.VALUE)
            else if (name == "ERF" && args.size !in 1..2) CellValue.Err(CellErr.Err.VALUE)
            else {
                val a0 = evalScalarAsNum(evalExpr(args[0], env))
                if (a0 is CellValue.Err) a0
                else {
                    val x0 = (a0 as CellValue.Num).value
                    if (name == "ERFC") CellValue.Num(normalizeNegZero(1.0 - erfApprox(x0)))
                    else if (args.size == 1) CellValue.Num(normalizeNegZero(erfApprox(x0)))
                    else {
                        val a1 = evalScalarAsNum(evalExpr(args[1], env))
                        if (a1 is CellValue.Err) a1
                        else CellValue.Num(normalizeNegZero(erfApprox((a1 as CellValue.Num).value) - erfApprox(x0)))
                    }
                }
            }
        }
        "GAMMALN" -> {
            if (args.size != 1) CellValue.Err(CellErr.Err.VALUE)
            else {
                val xv = evalScalarAsNum(evalExpr(args[0], env))
                if (xv is CellValue.Err) xv
                else {
                    val x = (xv as CellValue.Num).value
                    if (x <= 0.0) CellValue.Err(CellErr.Err.NUM)
                    else {
                        val g = lnGammaLanczos(x)
                        if (!g.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(g))
                    }
                }
            }
        }
        "SERIESSUM" -> {
            if (args.size != 4) CellValue.Err(CellErr.Err.VALUE)
            else {
                val xv = evalScalarAsNum(evalExpr(args[0], env))
                val nv = evalScalarAsNum(evalExpr(args[1], env))
                val mv = evalScalarAsNum(evalExpr(args[2], env))
                when {
                    xv is CellValue.Err -> xv
                    nv is CellValue.Err -> nv
                    mv is CellValue.Err -> mv
                    else -> collectNumericAggregate(listOf(args[3]), env).fold(
                        onSuccess = { coeffs ->
                            if (coeffs.isEmpty()) CellValue.Err(CellErr.Err.NA)
                            else {
                                val x = (xv as CellValue.Num).value
                                val n0 = (nv as CellValue.Num).value
                                val m = (mv as CellValue.Num).value
                                var acc = 0.0
                                for ((i, a) in coeffs.withIndex()) {
                                    val expVal = n0 + i * m
                                    if (x == 0.0 && expVal < 0.0) return CellValue.Err(CellErr.Err.NUM)
                                    acc += a * x.pow(expVal)
                                }
                                if (!acc.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(acc))
                            }
                        },
                        onFailure = { CellValue.Err((it as CellErrorSignal).err) },
                    )
                }
            }
        }
        "MULTINOMIAL", "GCD", "LCM" -> {
            if (args.isEmpty()) CellValue.Err(CellErr.Err.VALUE)
            else collectNumericAggregate(args, env).fold(
                onSuccess = { nums ->
                    if (nums.isEmpty() || nums.any { it < 0.0 }) return CellValue.Err(CellErr.Err.NUM)
                    when (name) {
                        "MULTINOMIAL" -> {
                            val ints = nums.map { floor(it).toInt() }
                            val total = ints.sum()
                            if (total > 170) CellValue.Err(CellErr.Err.NUM)
                            else {
                                var logRes = lnGammaLanczos(total + 1.0)
                                for (v in ints) logRes -= lnGammaLanczos(v + 1.0)
                                CellValue.Num(normalizeNegZero(round(exp(logRes))))
                            }
                        }
                        "GCD" -> {
                            var g = 0L
                            for (d in nums) g = gcdLong(g, floor(d).toLong())
                            CellValue.Num(g.toDouble())
                        }
                        else -> {
                            val longs = nums.map { floor(it).toLong() }
                            if (longs.any { it == 0L }) CellValue.Num(0.0)
                            else {
                                var l = longs[0]
                                for (i in 1 until longs.size) {
                                    val g = gcdLong(l, longs[i])
                                    l = (l / g) * longs[i]
                                }
                                CellValue.Num(l.toDouble())
                            }
                        }
                    }
                },
                onFailure = { CellValue.Err((it as CellErrorSignal).err) },
            )
        }
        "WORKDAY" -> {
            if (args.size !in 2..3) CellValue.Err(CellErr.Err.VALUE)
            else {
                val sv = evalScalarAsNum(evalExpr(args[0], env))
                val dv = evalScalarAsNum(evalExpr(args[1], env))
                when {
                    sv is CellValue.Err -> sv
                    dv is CellValue.Err -> dv
                    else -> {
                        val holidays = if (args.size == 3 && args[2] != Expr.OmittedArg) {
                            collectNumericAggregate(listOf(args[2]), env).fold(
                                onSuccess = { hs -> hs.map { floor(it).toLong() }.toSet() },
                                onFailure = { return CellValue.Err((it as CellErrorSignal).err) },
                            )
                        } else emptySet()
                        val epoch1900 = LocalDate.of(1899, 12, 30)
                        var curSerial = floor((sv as CellValue.Num).value).toLong()
                        var rem = floor((dv as CellValue.Num).value).toInt()
                        val step = if (rem >= 0) 1L else -1L
                        rem = abs(rem)
                        while (rem > 0) {
                            curSerial += step
                            val dow = epoch1900.plusDays(curSerial).dayOfWeek.value // 1=Mon..7=Sun
                            if (dow in 1..5 && curSerial !in holidays) rem--
                        }
                        CellValue.Num(curSerial.toDouble())
                    }
                }
            }
        }
        else -> { // DOLLARFR(decimal_dollar, fraction)
            if (args.size != 2) CellValue.Err(CellErr.Err.VALUE)
            else {
                val dv = evalScalarAsNum(evalExpr(args[0], env))
                val fv = evalScalarAsNum(evalExpr(args[1], env))
                when {
                    dv is CellValue.Err -> dv
                    fv is CellValue.Err -> fv
                    else -> {
                        val dd = (dv as CellValue.Num).value
                        val frac = floor((fv as CellValue.Num).value).toInt()
                        if (frac < 0) CellValue.Err(CellErr.Err.NUM)
                        else if (frac == 0) CellValue.Err(CellErr.Err.DIV0)
                        else {
                            val signMul = if (dd < 0.0) -1.0 else 1.0
                            val absDd = abs(dd)
                            val whole = floor(absDd)
                            val rem = absDd - whole
                            val scale = 10.0.pow(ceil(log10((frac + 1).toDouble()).coerceAtLeast(1.0)))
                            CellValue.Num(normalizeNegZero(signMul * (whole + (rem * frac.toDouble()) / scale)))
                        }
                    }
                }
            }
        }
    }

    private fun convertEngineeringUnit(v: Double, fromU: String, toU: String): CellValue {
        if (fromU in setOf("C", "cel", "F", "fah", "K", "kel") || toU in setOf("C", "cel", "F", "fah", "K", "kel")) {
            val kelvin = when (fromU) {
                "C", "cel" -> v + 273.15
                "F", "fah" -> (v - 32.0) * 5.0 / 9.0 + 273.15
                "K", "kel" -> v
                else -> return CellValue.Err(CellErr.Err.NA)
            }
            val out = when (toU) {
                "C", "cel" -> kelvin - 273.15
                "F", "fah" -> (kelvin - 273.15) * 9.0 / 5.0 + 32.0
                "K", "kel" -> kelvin
                else -> return CellValue.Err(CellErr.Err.NA)
            }
            return CellValue.Num(normalizeNegZero(out))
        }
        data class UnitDef(val dim: String, val toBase: Double)
        val units = mapOf(
            "m" to UnitDef("L", 1.0), "km" to UnitDef("L", 1000.0), "cm" to UnitDef("L", 0.01), "mm" to UnitDef("L", 0.001),
            "in" to UnitDef("L", 0.0254), "ft" to UnitDef("L", 0.3048), "yd" to UnitDef("L", 0.9144), "mi" to UnitDef("L", 1609.344),
            "Nmi" to UnitDef("L", 1852.0),
            "g" to UnitDef("M", 1.0), "kg" to UnitDef("M", 1000.0), "mg" to UnitDef("M", 0.001),
            "lbm" to UnitDef("M", 453.59237), "ozm" to UnitDef("M", 28.349523125),
            "sec" to UnitDef("T", 1.0), "s" to UnitDef("T", 1.0), "mn" to UnitDef("T", 60.0),
            "hr" to UnitDef("T", 3600.0), "day" to UnitDef("T", 86400.0),
            "byte" to UnitDef("D", 1.0), "bit" to UnitDef("D", 0.125), "kbyte" to UnitDef("D", 1024.0), "Mbyte" to UnitDef("D", 1048576.0),
            "Pa" to UnitDef("P", 1.0), "atm" to UnitDef("P", 101325.0),
            "J" to UnitDef("E", 1.0), "cal" to UnitDef("E", 4.1868), "Wh" to UnitDef("E", 3600.0),
        )
        val f = units[fromU] ?: return CellValue.Err(CellErr.Err.NA)
        val t = units[toU] ?: return CellValue.Err(CellErr.Err.NA)
        if (f.dim != t.dim) return CellValue.Err(CellErr.Err.NA)
        val out = v * f.toBase / t.toBase
        return if (!out.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(out))
    }

    // --- R51 7 Dynamic-Array/Grouping/Regression Grid Functions + 40 Statistical/Financial/Base/Lookup Functions ---

    private fun evalR51Grid(name: String, args: List<Expr>, env: Map<String, CellValue>): List<List<CellValue>> {
        fun gridErr(e: CellErr.Err): List<List<CellValue>> = listOf(listOf(CellValue.Err(e)))
        return when (name) {
            "FLATTEN" -> {
                if (args.isEmpty()) return gridErr(CellErr.Err.VALUE)
                val out = ArrayList<List<CellValue>>()
                for (a in args) {
                    val g = exprTo2DGrid(a, env) ?: return gridErr(CellErr.Err.VALUE)
                    for (r in g) for (c in r) out.add(listOf(c))
                }
                if (out.isEmpty()) gridErr(CellErr.Err.VALUE) else out
            }
            "EXPAND" -> {
                if (args.size !in 2..4) return gridErr(CellErr.Err.VALUE)
                val src = exprTo2DGrid(args[0], env) ?: return gridErr(CellErr.Err.VALUE)
                val h = src.size; val w = src.firstOrNull()?.size ?: 0
                val rows = if (args[1] == Expr.OmittedArg) h else {
                    val rv = evalScalarAsNum(evalExpr(args[1], env)); if (rv is CellValue.Err) return listOf(listOf(rv))
                    floor((rv as CellValue.Num).value).toInt()
                }
                val cols = if (args.size < 3 || args[2] == Expr.OmittedArg) w else {
                    val cv = evalScalarAsNum(evalExpr(args[2], env)); if (cv is CellValue.Err) return listOf(listOf(cv))
                    floor((cv as CellValue.Num).value).toInt()
                }
                if (rows < h || cols < w || rows.toLong() * cols > 4_096L) return gridErr(CellErr.Err.VALUE)
                val pad = if (args.size == 4 && args[3] != Expr.OmittedArg) evalExpr(args[3], env) else CellValue.Err(CellErr.Err.NA)
                List(rows) { r ->
                    List(cols) { c -> if (r < h && c < w) src[r][c] else pad }
                }
            }
            "TRIMRANGE" -> {
                if (args.size !in 1..3) return gridErr(CellErr.Err.VALUE)
                val src = exprTo2DGrid(args[0], env) ?: return gridErr(CellErr.Err.VALUE)
                val trimRows = if (args.size >= 2 && args[1] != Expr.OmittedArg) {
                    val tv = evalScalarAsNum(evalExpr(args[1], env)); if (tv is CellValue.Err) return listOf(listOf(tv))
                    floor((tv as CellValue.Num).value).toInt()
                } else 3 // 0=none, 1=leading, 2=trailing, 3=both
                val trimCols = if (args.size >= 3 && args[2] != Expr.OmittedArg) {
                    val tv = evalScalarAsNum(evalExpr(args[2], env)); if (tv is CellValue.Err) return listOf(listOf(tv))
                    floor((tv as CellValue.Num).value).toInt()
                } else 3
                if (trimRows !in 0..3 || trimCols !in 0..3) return gridErr(CellErr.Err.VALUE)
                fun isBlankCell(v: CellValue): Boolean = v == CellValue.Empty || (v is CellValue.Text && v.value.isEmpty())
                var r0 = 0; var r1 = src.lastIndex
                if (trimRows == 1 || trimRows == 3) while (r0 <= r1 && src[r0].all(::isBlankCell)) r0++
                if (trimRows == 2 || trimRows == 3) while (r1 >= r0 && src[r1].all(::isBlankCell)) r1--
                if (r0 > r1) return gridErr(CellErr.Err.VALUE)
                val w = src[0].size
                var c0 = 0; var c1 = w - 1
                if (trimCols == 1 || trimCols == 3) while (c0 <= c1 && (r0..r1).all { isBlankCell(src[it][c0]) }) c0++
                if (trimCols == 2 || trimCols == 3) while (c1 >= c0 && (r0..r1).all { isBlankCell(src[it][c1]) }) c1--
                if (c0 > c1) return gridErr(CellErr.Err.VALUE)
                (r0..r1).map { r -> src[r].subList(c0, c1 + 1) }
            }
            "GROUPBY" -> {
                if (args.size !in 3..4) return gridErr(CellErr.Err.VALUE)
                val rowFields = exprTo2DGrid(args[0], env) ?: return gridErr(CellErr.Err.VALUE)
                val vals = exprTo2DGrid(args[1], env) ?: return gridErr(CellErr.Err.VALUE)
                if (rowFields.size != vals.size || rowFields.isEmpty() || vals[0].isEmpty()) return gridErr(CellErr.Err.VALUE)
                val aggName = extractAggKeyword(args[2], env) ?: return gridErr(CellErr.Err.VALUE)
                val includeTotal = if (args.size == 4 && args[3] != Expr.OmittedArg) {
                    val tv = evalScalarAsNum(evalExpr(args[3], env)); if (tv is CellValue.Err) return listOf(listOf(tv))
                    floor((tv as CellValue.Num).value).toInt() != 0
                } else true
                val buckets = LinkedHashMap<String, Pair<List<CellValue>, ArrayList<List<CellValue>>>>()
                for (r in rowFields.indices) {
                    val keyRow = rowFields[r]
                    val keyStr = keyRow.joinToString("\u0001") { it.displayText().uppercase() }
                    buckets.getOrPut(keyStr) { keyRow to ArrayList() }.second.add(vals[r])
                }
                val valCols = vals[0].size
                val out = ArrayList<List<CellValue>>()
                for ((_, pair) in buckets) {
                    val aggCells = List(valCols) { c -> applyGroupByAgg(aggName, pair.second.map { it[c] }) }
                    out.add(pair.first + aggCells)
                }
                if (includeTotal) {
                    val labelRow = List(rowFields[0].size) { idx -> if (idx == 0) CellValue.Text("Total") else CellValue.Empty }
                    val totCells = List(valCols) { c -> applyGroupByAgg(aggName, vals.map { it[c] }) }
                    out.add(labelRow + totCells)
                }
                out
            }
            "PIVOTBY" -> {
                if (args.size != 4) return gridErr(CellErr.Err.VALUE)
                val rFields = exprTo2DGrid(args[0], env) ?: return gridErr(CellErr.Err.VALUE)
                val cFields = exprTo2DGrid(args[1], env) ?: return gridErr(CellErr.Err.VALUE)
                val vals = exprTo2DGrid(args[2], env) ?: return gridErr(CellErr.Err.VALUE)
                if (rFields.size != cFields.size || rFields.size != vals.size || rFields.isEmpty()) return gridErr(CellErr.Err.VALUE)
                val aggName = extractAggKeyword(args[3], env) ?: return gridErr(CellErr.Err.VALUE)
                val rowKeys = LinkedHashMap<String, CellValue>()
                val colKeys = LinkedHashMap<String, CellValue>()
                val cellBuckets = HashMap<Pair<String, String>, ArrayList<CellValue>>()
                for (i in rFields.indices) {
                    val rv = rFields[i][0]; val cv = cFields[i][0]
                    val rk = rv.displayText(); val ck = cv.displayText()
                    rowKeys.putIfAbsent(rk, rv)
                    colKeys.putIfAbsent(ck, cv)
                    cellBuckets.getOrPut(rk to ck) { ArrayList() }.add(vals[i][0])
                }
                val out = ArrayList<List<CellValue>>()
                out.add(listOf(CellValue.Empty) + colKeys.values.toList())
                for ((rk, rLabel) in rowKeys) {
                    val rowCells = ArrayList<CellValue>()
                    rowCells.add(rLabel)
                    for (ck in colKeys.keys) {
                        val items = cellBuckets[rk to ck]
                        rowCells.add(if (items.isNullOrEmpty()) CellValue.Num(0.0) else applyGroupByAgg(aggName, items))
                    }
                    out.add(rowCells)
                }
                out
            }
            else -> { // LINEST(known_y, [known_x], [const], [stats]) / LOGEST
                if (args.isEmpty() || args.size > 4) return gridErr(CellErr.Err.VALUE)
                val ysRaw = exprTo1DVector(args[0], env)?.mapNotNull { (it as? CellValue.Num)?.value } ?: return gridErr(CellErr.Err.VALUE)
                val n = ysRaw.size
                if (n < 2) return gridErr(CellErr.Err.VALUE)
                val xs = if (args.size >= 2 && args[1] != Expr.OmittedArg) {
                    exprTo1DVector(args[1], env)?.mapNotNull { (it as? CellValue.Num)?.value } ?: return gridErr(CellErr.Err.VALUE)
                } else List(n) { (it + 1).toDouble() }
                if (xs.size != n) return gridErr(CellErr.Err.VALUE)
                val stats = if (args.size == 4 && args[3] != Expr.OmittedArg) {
                    val sv = evalAsBool(evalExpr(args[3], env)); if (sv is CellValue.Err) return listOf(listOf(sv))
                    (sv as CellValue.Bool).value
                } else false
                val ys = if (name == "LOGEST") {
                    if (ysRaw.any { it <= 0.0 }) return gridErr(CellErr.Err.NUM)
                    ysRaw.map { ln(it) }
                } else ysRaw
                val mx = xs.average(); val my = ys.average()
                val sxx = xs.sumOf { (it - mx) * (it - mx) }
                if (sxx == 0.0) return gridErr(CellErr.Err.DIV0)
                val sxy = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
                val syy = ys.sumOf { (it - my) * (it - my) }
                val slope = sxy / sxx
                val intercept = my - slope * mx
                val outM = if (name == "LOGEST") exp(slope) else slope
                val outB = if (name == "LOGEST") exp(intercept) else intercept
                if (!stats) {
                    listOf(listOf(CellValue.Num(normalizeNegZero(outM)), CellValue.Num(normalizeNegZero(outB))))
                } else {
                    val sse = (syy - slope * sxy).coerceAtLeast(0.0)
                    val r2 = if (syy == 0.0) 1.0 else (1.0 - sse / syy).coerceIn(0.0, 1.0)
                    val seY = if (n > 2) sqrt(sse / (n - 2)) else 0.0
                    val seM = if (n > 2) seY / sqrt(sxx) else 0.0
                    val seB = if (n > 2) seY * sqrt(1.0 / n + (mx * mx) / sxx) else 0.0
                    listOf(
                        listOf(CellValue.Num(normalizeNegZero(outM)), CellValue.Num(normalizeNegZero(outB))),
                        listOf(CellValue.Num(normalizeNegZero(seM)), CellValue.Num(normalizeNegZero(seB))),
                        listOf(CellValue.Num(normalizeNegZero(r2)), CellValue.Num(normalizeNegZero(seY))),
                    )
                }
            }
        }
    }

    private fun extractAggKeyword(e: Expr, env: Map<String, CellValue>): String? = when (e) {
        is Expr.SymbolRef -> normalizeFnName(e.ident).takeIf { it in setOf("SUM", "AVERAGE", "MIN", "MAX", "COUNT", "COUNTA") }
        else -> evalExpr(e, env).displayText().trim().uppercase().takeIf { it in setOf("SUM", "AVERAGE", "MIN", "MAX", "COUNT", "COUNTA") }
    }

    private fun applyGroupByAgg(agg: String, items: List<CellValue>): CellValue {
        for (it in items) if (it is CellValue.Err) return it
        val nums = items.mapNotNull { (it as? CellValue.Num)?.value }
        return when (agg) {
            "SUM" -> CellValue.Num(normalizeNegZero(nums.sum()))
            "AVERAGE" -> if (nums.isEmpty()) CellValue.Err(CellErr.Err.DIV0) else CellValue.Num(normalizeNegZero(nums.average()))
            "MIN" -> CellValue.Num(if (nums.isEmpty()) 0.0 else nums.min())
            "MAX" -> CellValue.Num(if (nums.isEmpty()) 0.0 else nums.max())
            "COUNT" -> CellValue.Num(nums.size.toDouble())
            else -> CellValue.Num(items.count { it != CellValue.Empty }.toDouble())
        }
    }

    private fun evalR51ScalarFunction(name: String, args: List<Expr>, env: Map<String, CellValue>): CellValue {
        return when (name) {
        "PERCENTOF" -> {
            if (args.size != 2) CellValue.Err(CellErr.Err.VALUE)
            else collectNumericAggregate(listOf(args[0]), env).fold(
                onSuccess = { sub ->
                    collectNumericAggregate(listOf(args[1]), env).fold(
                        onSuccess = { all ->
                            val tot = all.sum()
                            if (tot == 0.0) CellValue.Err(CellErr.Err.DIV0)
                            else CellValue.Num(normalizeNegZero(sub.sum() / tot))
                        },
                        onFailure = { CellValue.Err((it as CellErrorSignal).err) },
                    )
                },
                onFailure = { CellValue.Err((it as CellErrorSignal).err) },
            )
        }
        "VALUETOTEXT" -> {
            if (args.size !in 1..2) CellValue.Err(CellErr.Err.VALUE)
            else {
                val fmt = if (args.size == 2 && args[1] != Expr.OmittedArg) {
                    val fv = evalScalarAsNum(evalExpr(args[1], env)); if (fv is CellValue.Err) return fv
                    floor((fv as CellValue.Num).value).toInt()
                } else 0
                val v = evalExpr(args[0], env)
                if (fmt !in 0..1) CellValue.Err(CellErr.Err.VALUE)
                else if (fmt == 1 && v is CellValue.Text) CellValue.Text("\"" + v.value.replace("\"", "\"\"") + "\"")
                else CellValue.Text(v.displayText())
            }
        }
        "ARRAYTOTEXT" -> {
            if (args.size !in 1..2) CellValue.Err(CellErr.Err.VALUE)
            else {
                val g = exprTo2DGrid(args[0], env) ?: return CellValue.Err(CellErr.Err.VALUE)
                val fmt = if (args.size == 2 && args[1] != Expr.OmittedArg) {
                    val fv = evalScalarAsNum(evalExpr(args[1], env)); if (fv is CellValue.Err) return fv
                    floor((fv as CellValue.Num).value).toInt()
                } else 0
                if (fmt == 0) CellValue.Text(g.flatten().joinToString(", ") { it.displayText() })
                else if (fmt == 1) {
                    val body = g.joinToString(";") { row ->
                        row.joinToString(",") { v ->
                            if (v is CellValue.Text) "\"" + v.value.replace("\"", "\"\"") + "\"" else v.displayText()
                        }
                    }
                    CellValue.Text("{$body}")
                } else CellValue.Err(CellErr.Err.VALUE)
            }
        }
        "GEOMEAN", "SKEW", "KURT" -> {
            collectNumericAggregate(args, env).fold(
                onSuccess = { nums ->
                    when (name) {
                        "GEOMEAN" -> {
                            if (nums.isEmpty() || nums.any { it <= 0.0 }) CellValue.Err(CellErr.Err.NUM)
                            else CellValue.Num(normalizeNegZero(exp(nums.sumOf { ln(it) } / nums.size)))
                        }
                        "SKEW" -> {
                            val n = nums.size
                            if (n < 3) CellValue.Err(CellErr.Err.DIV0)
                            else {
                                val mean = nums.average()
                                val s = sqrt(nums.sumOf { (it - mean) * (it - mean) } / (n - 1))
                                if (s == 0.0) CellValue.Err(CellErr.Err.DIV0)
                                else {
                                    val m3 = nums.sumOf { ((it - mean) / s).pow(3.0) }
                                    CellValue.Num(normalizeNegZero((n.toDouble() / ((n - 1).toDouble() * (n - 2).toDouble())) * m3))
                                }
                            }
                        }
                        else -> { // KURT
                            val n = nums.size
                            if (n < 4) CellValue.Err(CellErr.Err.DIV0)
                            else {
                                val mean = nums.average()
                                val s = sqrt(nums.sumOf { (it - mean) * (it - mean) } / (n - 1))
                                if (s == 0.0) CellValue.Err(CellErr.Err.DIV0)
                                else {
                                    val m4 = nums.sumOf { ((it - mean) / s).pow(4.0) }
                                    val nd = n.toDouble()
                                    val k = (nd * (nd + 1.0) / ((nd - 1.0) * (nd - 2.0) * (nd - 3.0))) * m4 -
                                        (3.0 * (nd - 1.0) * (nd - 1.0)) / ((nd - 2.0) * (nd - 3.0))
                                    CellValue.Num(normalizeNegZero(k))
                                }
                            }
                        }
                    }
                },
                onFailure = { CellValue.Err((it as CellErrorSignal).err) },
            )
        }
        "RSQ", "STEYX", "COVARIANCE.P", "COVARIANCE.S" -> {
            if (args.size != 2) CellValue.Err(CellErr.Err.VALUE)
            else {
                val vy = exprTo1DVector(args[0], env)
                val vx = exprTo1DVector(args[1], env)
                if (vy == null || vx == null || vy.size != vx.size || vy.isEmpty()) CellValue.Err(CellErr.Err.NA)
                else {
                    val ys = ArrayList<Double>(); val xs = ArrayList<Double>()
                    for (i in vy.indices) {
                        val y = vy[i]; val x = vx[i]
                        if (y is CellValue.Err) return y
                        if (x is CellValue.Err) return x
                        if (y is CellValue.Num && x is CellValue.Num) { ys.add(y.value); xs.add(x.value) }
                    }
                    val n = xs.size
                    when (name) {
                        "COVARIANCE.P" -> if (n < 1) CellValue.Err(CellErr.Err.DIV0) else {
                            val mx = xs.average(); val my = ys.average()
                            CellValue.Num(normalizeNegZero(xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / n))
                        }
                        "COVARIANCE.S" -> if (n < 2) CellValue.Err(CellErr.Err.DIV0) else {
                            val mx = xs.average(); val my = ys.average()
                            CellValue.Num(normalizeNegZero(xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / (n - 1)))
                        }
                        "RSQ" -> if (n < 2) CellValue.Err(CellErr.Err.DIV0) else {
                            val mx = xs.average(); val my = ys.average()
                            val sxx = xs.sumOf { (it - mx) * (it - mx) }
                            val syy = ys.sumOf { (it - my) * (it - my) }
                            if (sxx == 0.0 || syy == 0.0) CellValue.Err(CellErr.Err.DIV0)
                            else {
                                val sxy = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
                                CellValue.Num(normalizeNegZero((sxy * sxy) / (sxx * syy)))
                            }
                        }
                        else -> if (n < 3) CellValue.Err(CellErr.Err.DIV0) else { // STEYX
                            val mx = xs.average(); val my = ys.average()
                            val sxx = xs.sumOf { (it - mx) * (it - mx) }
                            val syy = ys.sumOf { (it - my) * (it - my) }
                            if (sxx == 0.0) CellValue.Err(CellErr.Err.DIV0)
                            else {
                                val sxy = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
                                val sse = (syy - (sxy * sxy) / sxx).coerceAtLeast(0.0)
                                CellValue.Num(normalizeNegZero(sqrt(sse / (n - 2))))
                            }
                        }
                    }
                }
            }
        }
        "NORM.S.DIST" -> {
            if (args.size != 2) CellValue.Err(CellErr.Err.VALUE)
            else {
                val zv = evalScalarAsNum(evalExpr(args[0], env)); if (zv is CellValue.Err) return zv
                val cv = evalAsBool(evalExpr(args[1], env)); if (cv is CellValue.Err) return cv
                val z = (zv as CellValue.Num).value
                val cum = (cv as CellValue.Bool).value
                val res = if (cum) 0.5 * (1.0 + erfApprox(z / sqrt(2.0))) else exp(-0.5 * z * z) / sqrt(2.0 * Math.PI)
                CellValue.Num(normalizeNegZero(res))
            }
        }
        "NORM.S.INV" -> {
            if (args.size != 1) CellValue.Err(CellErr.Err.VALUE)
            else {
                val pv = evalScalarAsNum(evalExpr(args[0], env)); if (pv is CellValue.Err) return pv
                val p = (pv as CellValue.Num).value
                if (p <= 0.0 || p >= 1.0) CellValue.Err(CellErr.Err.NUM)
                else CellValue.Num(normalizeNegZero(normSInvAcklam(p)))
            }
        }
        "NORM.DIST" -> {
            if (args.size != 4) CellValue.Err(CellErr.Err.VALUE)
            else {
                val xv = evalScalarAsNum(evalExpr(args[0], env)); if (xv is CellValue.Err) return xv
                val mv = evalScalarAsNum(evalExpr(args[1], env)); if (mv is CellValue.Err) return mv
                val sv = evalScalarAsNum(evalExpr(args[2], env)); if (sv is CellValue.Err) return sv
                val cv = evalAsBool(evalExpr(args[3], env)); if (cv is CellValue.Err) return cv
                val x = (xv as CellValue.Num).value; val m = (mv as CellValue.Num).value; val sd = (sv as CellValue.Num).value
                if (sd <= 0.0) CellValue.Err(CellErr.Err.NUM)
                else {
                    val z = (x - m) / sd
                    val res = if ((cv as CellValue.Bool).value) 0.5 * (1.0 + erfApprox(z / sqrt(2.0)))
                    else exp(-0.5 * z * z) / (sd * sqrt(2.0 * Math.PI))
                    CellValue.Num(normalizeNegZero(res))
                }
            }
        }
        "POISSON.DIST" -> {
            if (args.size != 3) CellValue.Err(CellErr.Err.VALUE)
            else {
                val kv = evalScalarAsNum(evalExpr(args[0], env)); if (kv is CellValue.Err) return kv
                val mv = evalScalarAsNum(evalExpr(args[1], env)); if (mv is CellValue.Err) return mv
                val cv = evalAsBool(evalExpr(args[2], env)); if (cv is CellValue.Err) return cv
                val k = floor((kv as CellValue.Num).value).toInt(); val lam = (mv as CellValue.Num).value
                if (k < 0 || lam < 0.0) CellValue.Err(CellErr.Err.NUM)
                else if (lam == 0.0) CellValue.Num(if (k == 0 || (cv as CellValue.Bool).value) 1.0 else 0.0)
                else {
                    fun pmf(j: Int): Double = exp(j * ln(lam) - lam - lnGammaLanczos(j + 1.0))
                    val res = if ((cv as CellValue.Bool).value) (0..k).sumOf(::pmf) else pmf(k)
                    CellValue.Num(normalizeNegZero(res))
                }
            }
        }
        "BINOM.DIST" -> {
            if (args.size != 4) CellValue.Err(CellErr.Err.VALUE)
            else {
                val sv = evalScalarAsNum(evalExpr(args[0], env)); if (sv is CellValue.Err) return sv
                val nv = evalScalarAsNum(evalExpr(args[1], env)); if (nv is CellValue.Err) return nv
                val pv = evalScalarAsNum(evalExpr(args[2], env)); if (pv is CellValue.Err) return pv
                val cv = evalAsBool(evalExpr(args[3], env)); if (cv is CellValue.Err) return cv
                val s = floor((sv as CellValue.Num).value).toInt(); val n = floor((nv as CellValue.Num).value).toInt()
                val p = (pv as CellValue.Num).value
                if (s < 0 || n < 0 || s > n || p < 0.0 || p > 1.0) CellValue.Err(CellErr.Err.NUM)
                else {
                    fun pmf(k: Int): Double {
                        if (p == 0.0) return if (k == 0) 1.0 else 0.0
                        if (p == 1.0) return if (k == n) 1.0 else 0.0
                        val lc = lnGammaLanczos(n + 1.0) - lnGammaLanczos(k + 1.0) - lnGammaLanczos(n - k + 1.0)
                        return exp(lc + k * ln(p) + (n - k) * ln(1.0 - p))
                    }
                    val res = if ((cv as CellValue.Bool).value) (0..s).sumOf(::pmf) else pmf(s)
                    CellValue.Num(normalizeNegZero(res))
                }
            }
        }
        "EXPON.DIST" -> {
            if (args.size != 3) CellValue.Err(CellErr.Err.VALUE)
            else {
                val xv = evalScalarAsNum(evalExpr(args[0], env)); if (xv is CellValue.Err) return xv
                val lv = evalScalarAsNum(evalExpr(args[1], env)); if (lv is CellValue.Err) return lv
                val cv = evalAsBool(evalExpr(args[2], env)); if (cv is CellValue.Err) return cv
                val x = (xv as CellValue.Num).value; val lam = (lv as CellValue.Num).value
                if (x < 0.0 || lam <= 0.0) CellValue.Err(CellErr.Err.NUM)
                else {
                    val res = if ((cv as CellValue.Bool).value) 1.0 - exp(-lam * x) else lam * exp(-lam * x)
                    CellValue.Num(normalizeNegZero(res))
                }
            }
        }
        "CONFIDENCE.NORM" -> {
            if (args.size != 3) CellValue.Err(CellErr.Err.VALUE)
            else {
                val av = evalScalarAsNum(evalExpr(args[0], env)); if (av is CellValue.Err) return av
                val sv = evalScalarAsNum(evalExpr(args[1], env)); if (sv is CellValue.Err) return sv
                val nv = evalScalarAsNum(evalExpr(args[2], env)); if (nv is CellValue.Err) return nv
                val alpha = (av as CellValue.Num).value; val sd = (sv as CellValue.Num).value; val sz = floor((nv as CellValue.Num).value)
                if (alpha <= 0.0 || alpha >= 1.0 || sd <= 0.0 || sz < 1.0) CellValue.Err(CellErr.Err.NUM)
                else CellValue.Num(normalizeNegZero(normSInvAcklam(1.0 - alpha / 2.0) * sd / sqrt(sz)))
            }
        }
        "SLN", "SYD", "DDB" -> evalDepreciation(name, args, env)
        "MIRR", "IPMT", "PPMT", "CUMIPMT", "CUMPRINC", "EFFECT", "NOMINAL" -> evalR51Financial(name, args, env)
        "BASE" -> {
            if (args.size !in 2..3) CellValue.Err(CellErr.Err.VALUE)
            else {
                val nv = evalScalarAsNum(evalExpr(args[0], env)); if (nv is CellValue.Err) return nv
                val rv = evalScalarAsNum(evalExpr(args[1], env)); if (rv is CellValue.Err) return rv
                val num = floor((nv as CellValue.Num).value).toLong()
                val rad = floor((rv as CellValue.Num).value).toInt()
                val minLen = if (args.size == 3 && args[2] != Expr.OmittedArg) {
                    val mv = evalScalarAsNum(evalExpr(args[2], env)); if (mv is CellValue.Err) return mv
                    floor((mv as CellValue.Num).value).toInt()
                } else 0
                if (num < 0L || rad !in 2..36 || minLen < 0 || minLen > 255) CellValue.Err(CellErr.Err.NUM)
                else CellValue.Text(num.toString(rad).uppercase().padStart(minLen, '0'))
            }
        }
        "DECIMAL" -> {
            if (args.size != 2) CellValue.Err(CellErr.Err.VALUE)
            else {
                val tv = evalExpr(args[0], env); if (tv is CellValue.Err) return tv
                val rv = evalScalarAsNum(evalExpr(args[1], env)); if (rv is CellValue.Err) return rv
                val rad = floor((rv as CellValue.Num).value).toInt()
                val raw = tv.displayText().trim()
                val parsed = if (rad !in 2..36 || raw.isEmpty()) null else raw.toLongOrNull(rad)
                if (parsed == null) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(parsed.toDouble())
            }
        }
        "ROMAN" -> {
            if (args.size !in 1..2) CellValue.Err(CellErr.Err.VALUE)
            else {
                val nv = evalScalarAsNum(evalExpr(args[0], env)); if (nv is CellValue.Err) return nv
                var n = floor((nv as CellValue.Num).value).toInt()
                if (n < 0 || n > 3999) CellValue.Err(CellErr.Err.VALUE)
                else if (n == 0) CellValue.Text("")
                else {
                    val vals = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
                    val syms = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
                    val sb = StringBuilder()
                    for (i in vals.indices) while (n >= vals[i]) { sb.append(syms[i]); n -= vals[i] }
                    CellValue.Text(sb.toString())
                }
            }
        }
        "ARABIC" -> {
            if (args.size != 1) CellValue.Err(CellErr.Err.VALUE)
            else {
                val tv = evalExpr(args[0], env); if (tv is CellValue.Err) return tv
                val s0 = tv.displayText().trim().uppercase()
                if (s0.isEmpty()) CellValue.Num(0.0)
                else {
                    val neg = s0.startsWith("-")
                    val s = if (neg) s0.substring(1).trim() else s0
                    val map = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
                    if (s.isEmpty() || s.any { it !in map }) CellValue.Err(CellErr.Err.VALUE)
                    else {
                        var sum = 0
                        for (i in s.indices) {
                            val cur = map.getValue(s[i])
                            val next = if (i + 1 < s.length) map.getValue(s[i + 1]) else 0
                            if (cur < next) sum -= cur else sum += cur
                        }
                        CellValue.Num((if (neg) -sum else sum).toDouble())
                    }
                }
            }
        }
        "COMBINA" -> {
            if (args.size != 2) CellValue.Err(CellErr.Err.VALUE)
            else {
                val nv = evalScalarAsNum(evalExpr(args[0], env)); if (nv is CellValue.Err) return nv
                val kv = evalScalarAsNum(evalExpr(args[1], env)); if (kv is CellValue.Err) return kv
                val n = floor((nv as CellValue.Num).value).toInt(); val k = floor((kv as CellValue.Num).value).toInt()
                if (n < 0 || k < 0 || (n == 0 && k > 0)) CellValue.Err(CellErr.Err.NUM)
                else if (k == 0) CellValue.Num(1.0)
                else {
                    val top = n + k - 1
                    val lc = lnGammaLanczos(top + 1.0) - lnGammaLanczos(k + 1.0) - lnGammaLanczos(top - k + 1.0)
                    CellValue.Num(normalizeNegZero(round(exp(lc))))
                }
            }
        }
        "FACTDOUBLE" -> {
            if (args.size != 1) CellValue.Err(CellErr.Err.VALUE)
            else {
                val nv = evalScalarAsNum(evalExpr(args[0], env)); if (nv is CellValue.Err) return nv
                var k = floor((nv as CellValue.Num).value).toInt()
                if (k < -1 || k > 300) CellValue.Err(CellErr.Err.NUM)
                else {
                    var p = 1.0
                    while (k > 1) { p *= k; k -= 2 }
                    if (!p.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(p)
                }
            }
        }
        "QUOTIENT", "MROUND" -> {
            if (args.size != 2) CellValue.Err(CellErr.Err.VALUE)
            else {
                val av = evalScalarAsNum(evalExpr(args[0], env)); if (av is CellValue.Err) return av
                val bv = evalScalarAsNum(evalExpr(args[1], env)); if (bv is CellValue.Err) return bv
                val a = (av as CellValue.Num).value; val b = (bv as CellValue.Num).value
                if (name == "QUOTIENT") {
                    if (b == 0.0) CellValue.Err(CellErr.Err.DIV0)
                    else { val q = a / b; CellValue.Num(normalizeNegZero(if (q >= 0.0) floor(q) else ceil(q))) }
                } else {
                    if (b == 0.0) CellValue.Num(0.0)
                    else if ((a > 0.0 && b < 0.0) || (a < 0.0 && b > 0.0)) CellValue.Err(CellErr.Err.NUM)
                    else CellValue.Num(normalizeNegZero(round(a / b) * b))
                }
            }
        }
        "SQRTPI", "DEGREES", "RADIANS" -> {
            if (args.size != 1) CellValue.Err(CellErr.Err.VALUE)
            else {
                val xv = evalScalarAsNum(evalExpr(args[0], env)); if (xv is CellValue.Err) return xv
                val x = (xv as CellValue.Num).value
                when (name) {
                    "SQRTPI" -> if (x < 0.0) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(sqrt(x * Math.PI)))
                    "DEGREES" -> CellValue.Num(normalizeNegZero(x * 180.0 / Math.PI))
                    else -> CellValue.Num(normalizeNegZero(x * Math.PI / 180.0))
                }
            }
        }
        "ISOWEEKNUM" -> {
            if (args.size != 1) CellValue.Err(CellErr.Err.VALUE)
            else {
                val dv = evalScalarAsNum(evalExpr(args[0], env)); if (dv is CellValue.Err) return dv
                val serial = floor((dv as CellValue.Num).value).toLong()
                if (serial < 0L) CellValue.Err(CellErr.Err.NUM)
                else {
                    val d = LocalDate.of(1899, 12, 30).plusDays(serial)
                    val isoWeek = d.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
                    CellValue.Num(isoWeek.toDouble())
                }
            }
        }
        else -> { // ADDRESS(row_num, column_num, [abs_num], [a1], [sheet_text])
            if (args.size !in 2..5) CellValue.Err(CellErr.Err.VALUE)
            else {
                val rv = evalScalarAsNum(evalExpr(args[0], env)); if (rv is CellValue.Err) return rv
                val cv = evalScalarAsNum(evalExpr(args[1], env)); if (cv is CellValue.Err) return cv
                val row = floor((rv as CellValue.Num).value).toInt()
                val col = floor((cv as CellValue.Num).value).toInt()
                val absNum = if (args.size >= 3 && args[2] != Expr.OmittedArg) {
                    val av = evalScalarAsNum(evalExpr(args[2], env)); if (av is CellValue.Err) return av
                    floor((av as CellValue.Num).value).toInt()
                } else 1
                val a1Mode = if (args.size >= 4 && args[3] != Expr.OmittedArg) {
                    val bv = evalAsBool(evalExpr(args[3], env)); if (bv is CellValue.Err) return bv
                    (bv as CellValue.Bool).value
                } else true
                val sheet = if (args.size == 5 && args[4] != Expr.OmittedArg) evalExpr(args[4], env).displayText() else ""
                if (row < 1 || col < 1 || col > 16384 || absNum !in 1..4) CellValue.Err(CellErr.Err.VALUE)
                else {
                    val ref = if (a1Mode) {
                        val colL = ColLetters.toLetters(col)
                        when (absNum) {
                            1 -> "\$$colL\$$row"
                            2 -> "$colL\$$row"
                            3 -> "\$$colL$row"
                            else -> "$colL$row"
                        }
                    } else {
                        val rPart = if (absNum == 1 || absNum == 2) "R$row" else "R[$row]"
                        val cPart = if (absNum == 1 || absNum == 3) "C$col" else "C[$col]"
                        "$rPart$cPart"
                    }
                    val prefix = if (sheet.isEmpty()) "" else if (sheet.any { !it.isLetterOrDigit() && it != '_' }) "'${sheet.replace("'", "''")}'!" else "$sheet!"
                    CellValue.Text(prefix + ref)
                }
            }
        }
        }
    }

    private fun evalDepreciation(name: String, args: List<Expr>, env: Map<String, CellValue>): CellValue {
        if (name == "SLN" && args.size != 3) return CellValue.Err(CellErr.Err.VALUE)
        if (name == "SYD" && args.size != 4) return CellValue.Err(CellErr.Err.VALUE)
        if (name == "DDB" && args.size !in 4..5) return CellValue.Err(CellErr.Err.VALUE)
        val nums = args.map { a ->
            if (a == Expr.OmittedArg) 2.0 else {
                val v = evalScalarAsNum(evalExpr(a, env)); if (v is CellValue.Err) return v
                (v as CellValue.Num).value
            }
        }
        val cost = nums[0]; val salvage = nums[1]; val life = nums[2]
        if (cost < 0.0 || salvage < 0.0 || life <= 0.0) return CellValue.Err(CellErr.Err.NUM)
        return when (name) {
            "SLN" -> CellValue.Num(normalizeNegZero((cost - salvage) / life))
            "SYD" -> {
                val per = nums[3]
                if (per <= 0.0 || per > life) CellValue.Err(CellErr.Err.NUM)
                else CellValue.Num(normalizeNegZero(((cost - salvage) * (life - per + 1.0) * 2.0) / (life * (life + 1.0))))
            }
            else -> { // DDB
                val per = floor(nums[3]).toInt()
                val factor = if (nums.size == 5) nums[4] else 2.0
                if (per < 1 || per > life || factor <= 0.0) return CellValue.Err(CellErr.Err.NUM)
                var book = cost
                var dep = 0.0
                for (p in 1..per) {
                    dep = minOf(book * (factor / life), (book - salvage).coerceAtLeast(0.0))
                    book -= dep
                }
                CellValue.Num(normalizeNegZero(dep))
            }
        }
    }

    private fun evalR51Financial(name: String, args: List<Expr>, env: Map<String, CellValue>): CellValue {
        return when (name) {
            "EFFECT", "NOMINAL" -> {
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                val rv = evalScalarAsNum(evalExpr(args[0], env)); if (rv is CellValue.Err) return rv
                val nv = evalScalarAsNum(evalExpr(args[1], env)); if (nv is CellValue.Err) return nv
                val r = (rv as CellValue.Num).value; val n = floor((nv as CellValue.Num).value)
                if (r <= 0.0 || n < 1.0) CellValue.Err(CellErr.Err.NUM)
                else if (name == "EFFECT") CellValue.Num(normalizeNegZero((1.0 + r / n).pow(n) - 1.0))
                else CellValue.Num(normalizeNegZero(n * ((1.0 + r).pow(1.0 / n) - 1.0)))
            }
            "MIRR" -> {
                if (args.size != 3) return CellValue.Err(CellErr.Err.VALUE)
                val cfs = collectNumericAggregate(listOf(args[0]), env).getOrElse { return CellValue.Err((it as CellErrorSignal).err) }
                val fv = evalScalarAsNum(evalExpr(args[1], env)); if (fv is CellValue.Err) return fv
                val rv = evalScalarAsNum(evalExpr(args[2], env)); if (rv is CellValue.Err) return rv
                val fr = (fv as CellValue.Num).value; val rr = (rv as CellValue.Num).value
                if (cfs.size < 2 || cfs.none { it > 0.0 } || cfs.none { it < 0.0 } || fr <= -1.0 || rr <= -1.0) {
                    return CellValue.Err(CellErr.Err.DIV0)
                }
                val n = cfs.size
                var posFv = 0.0; var negPv = 0.0
                for (i in cfs.indices) {
                    if (cfs[i] > 0.0) posFv += cfs[i] * (1.0 + rr).pow((n - 1 - i).toDouble())
                    else if (cfs[i] < 0.0) negPv += cfs[i] / (1.0 + fr).pow(i.toDouble())
                }
                CellValue.Num(normalizeNegZero((-posFv / negPv).pow(1.0 / (n - 1).toDouble()) - 1.0))
            }
            "IPMT", "PPMT" -> {
                if (args.size !in 4..6) return CellValue.Err(CellErr.Err.VALUE)
                val nums = args.map { a ->
                    if (a == Expr.OmittedArg) 0.0 else {
                        val v = evalScalarAsNum(evalExpr(a, env)); if (v is CellValue.Err) return v
                        (v as CellValue.Num).value
                    }
                }
                val rate = nums[0]; val per = nums[1]; val nper = nums[2]; val pv = nums[3]
                val fv = nums.getOrElse(4) { 0.0 }; val type = if (nums.getOrElse(5) { 0.0 } != 0.0) 1 else 0
                if (nper <= 0.0 || per < 1.0 || per > nper) return CellValue.Err(CellErr.Err.NUM)
                val pmt = pmtHelper(rate, nper, pv, fv, type)
                val ipmt = ipmtHelper(rate, per, pmt, pv, type)
                CellValue.Num(normalizeNegZero(if (name == "IPMT") ipmt else pmt - ipmt))
            }
            else -> { // CUMIPMT / CUMPRINC
                if (args.size != 6) return CellValue.Err(CellErr.Err.VALUE)
                val nums = args.map { a ->
                    val v = evalScalarAsNum(evalExpr(a, env)); if (v is CellValue.Err) return v
                    (v as CellValue.Num).value
                }
                val rate = nums[0]; val nper = nums[1]; val pv = nums[2]
                val sPer = ceil(nums[3]).toInt(); val ePer = floor(nums[4]).toInt(); val type = nums[5].toInt()
                if (rate <= 0.0 || nper <= 0.0 || pv <= 0.0 || sPer < 1 || ePer < sPer || ePer > nper || type !in 0..1) {
                    return CellValue.Err(CellErr.Err.NUM)
                }
                val pmt = pmtHelper(rate, nper, pv, 0.0, type)
                var sum = 0.0
                for (p in sPer..ePer) {
                    val ip = ipmtHelper(rate, p.toDouble(), pmt, pv, type)
                    sum += if (name == "CUMIPMT") ip else (pmt - ip)
                }
                CellValue.Num(normalizeNegZero(sum))
            }
        }
    }

    private fun pmtHelper(rate: Double, nper: Double, pv: Double, fv: Double, type: Int): Double {
        if (rate == 0.0) return -(pv + fv) / nper
        val f = (1.0 + rate).pow(nper)
        return -rate * (pv * f + fv) / ((1.0 + rate * type) * (f - 1.0))
    }

    private fun ipmtHelper(rate: Double, per: Double, pmt: Double, pv: Double, type: Int): Double {
        if (type == 1 && per == 1.0) return 0.0
        val k = if (type == 1) per - 2.0 else per - 1.0
        val fvPrev = if (rate == 0.0) -(pv + pmt * k) else {
            val f = (1.0 + rate).pow(k)
            -(pv * f + pmt * (1.0 + rate * type) * (f - 1.0) / rate)
        }
        val ip = fvPrev * rate
        return if (type == 1) ip / (1.0 + rate) else ip
    }

    /** Peter J. Acklam inverse standard normal CDF rational approximation (max relative error < 1.15e-9). */
    private fun normSInvAcklam(p: Double): Double {
        val a1 = -39.69683028665376; val a2 = 220.9460984245205; val a3 = -275.9285104469687
        val a4 = 138.3577518672690; val a5 = -30.66479806614716; val a6 = 2.506628277459239
        val b1 = -54.47609879822406; val b2 = 161.5858368580409; val b3 = -155.6989798598866
        val b4 = 66.80131188771972; val b5 = -13.28068155288572
        val c1 = -0.007784894002430293; val c2 = -0.3223964580411365; val c3 = -2.400758277161838
        val c4 = -2.549732539343734; val c5 = 4.374664141464968; val c6 = 2.938163982698783
        val d1 = 0.007784695709041462; val d2 = 0.3224671290700398; val d3 = 2.445134137142996; val d4 = 3.754408661907416
        val pLow = 0.02425; val pHigh = 1.0 - pLow
        return when {
            p < pLow -> {
                val q = sqrt(-2.0 * ln(p))
                (((((c1 * q + c2) * q + c3) * q + c4) * q + c5) * q + c6) /
                    ((((d1 * q + d2) * q + d3) * q + d4) * q + 1.0)
            }
            p <= pHigh -> {
                val q = p - 0.5; val r = q * q
                (((((a1 * r + a2) * r + a3) * r + a4) * r + a5) * r + a6) * q /
                    (((((b1 * r + b2) * r + b3) * r + b4) * r + b5) * r + 1.0)
            }
            else -> {
                val q = sqrt(-2.0 * ln(1.0 - p))
                -(((((c1 * q + c2) * q + c3) * q + c4) * q + c5) * q + c6) /
                    ((((d1 * q + d2) * q + d3) * q + d4) * q + 1.0)
            }
        }
    }

    private fun evalSumproduct(args: List<Expr>, env: Map<String, CellValue>): CellValue {
        if (args.isEmpty()) return CellValue.Err(CellErr.Err.VALUE)
        val grids = args.map { exprTo2DGrid(it, env) ?: return CellValue.Err(CellErr.Err.VALUE) }
        val h = grids[0].size
        val w = grids[0].firstOrNull()?.size ?: 0
        if (h == 0 || w == 0 || grids.any { it.size != h || it.any { row -> row.size != w } }) {
            return CellValue.Err(CellErr.Err.VALUE)
        }
        var total = 0.0
        for (r in 0 until h) {
            for (c in 0 until w) {
                var prod = 1.0
                for (g in grids) {
                    val cv = g[r][c]
                    if (cv is CellValue.Err) return cv
                    val nv = if (cv is CellValue.Num) cv.value else 0.0
                    prod *= nv
                }
                total += prod
            }
        }
        return if (!total.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(total))
    }

    private fun evalPercentileOrQuartile(name: String, args: List<Expr>, env: Map<String, CellValue>): CellValue {
        if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
        val numsRes = collectNumericAggregate(listOf(args[0]), env)
        val kRaw = evalScalarAsNum(evalExpr(args[1], env))
        if (kRaw is CellValue.Err) return kRaw
        val k = if (name == "QUARTILE") {
            val q = (kRaw as CellValue.Num).value.toInt()
            if (q !in 0..4) return CellValue.Err(CellErr.Err.NUM)
            q * 0.25
        } else {
            val p = (kRaw as CellValue.Num).value
            if (p < 0.0 || p > 1.0) return CellValue.Err(CellErr.Err.NUM)
            p
        }
        return numsRes.fold(
            onSuccess = { nums ->
                if (nums.isEmpty()) return CellValue.Err(CellErr.Err.NUM)
                val sorted = nums.sorted()
                if (sorted.size == 1) return CellValue.Num(sorted[0])
                val pos = k * (sorted.size - 1)
                val lo = floor(pos).toInt().coerceIn(0, sorted.lastIndex)
                val hi = ceil(pos).toInt().coerceIn(0, sorted.lastIndex)
                val frac = pos - lo
                CellValue.Num(normalizeNegZero(sorted[lo] + frac * (sorted[hi] - sorted[lo])))
            },
            onFailure = { CellValue.Err((it as CellErrorSignal).err) },
        )
    }

    private fun evalLinearStats(name: String, args: List<Expr>, env: Map<String, CellValue>): CellValue {
        if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
        val yVec = exprTo1DVector(args[0], env) ?: return CellValue.Err(CellErr.Err.VALUE)
        val xVec = exprTo1DVector(args[1], env) ?: return CellValue.Err(CellErr.Err.VALUE)
        if (yVec.size != xVec.size) return CellValue.Err(CellErr.Err.NA)
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        for (i in yVec.indices) {
            val y = yVec[i]; val x = xVec[i]
            if (y is CellValue.Err) return y
            if (x is CellValue.Err) return x
            if (y is CellValue.Num && x is CellValue.Num) {
                ys.add(y.value); xs.add(x.value)
            }
        }
        if (xs.size < 2) return CellValue.Err(CellErr.Err.DIV0)
        val mx = xs.sum() / xs.size
        val my = ys.sum() / ys.size
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (i in xs.indices) {
            val dx = xs[i] - mx; val dy = ys[i] - my
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy
        }
        if (sxx == 0.0 || (name == "CORREL" && syy == 0.0)) return CellValue.Err(CellErr.Err.DIV0)
        val res = when (name) {
            "CORREL" -> sxy / sqrt(sxx * syy)
            "SLOPE" -> sxy / sxx
            else -> my - (sxy / sxx) * mx
        }
        return if (!res.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(res))
    }

    private fun evalIrr(args: List<Expr>, env: Map<String, CellValue>): CellValue {
        if (args.size !in 1..2) return CellValue.Err(CellErr.Err.VALUE)
        val guess = if (args.size == 2) {
            val g = evalScalarAsNum(evalExpr(args[1], env)); if (g is CellValue.Err) return g
            (g as CellValue.Num).value
        } else 0.1
        val flowsRes = collectNumericAggregate(listOf(args[0]), env)
        return flowsRes.fold(
            onSuccess = { flows ->
                if (flows.size < 2 || flows.none { it > 0.0 } || flows.none { it < 0.0 }) return CellValue.Err(CellErr.Err.NUM)
                var r = guess.coerceAtLeast(-0.99)
                repeat(80) {
                    var npv = 0.0
                    var dnpv = 0.0
                    for (t in flows.indices) {
                        val denom = (1.0 + r).pow(t.toDouble())
                        npv += flows[t] / denom
                        if (t > 0) dnpv -= t * flows[t] / ((1.0 + r).pow((t + 1).toDouble()))
                    }
                    if (abs(npv) < 1e-10) return CellValue.Num(normalizeNegZero(r))
                    if (abs(dnpv) < 1e-15) return CellValue.Err(CellErr.Err.NUM)
                    val next = r - npv / dnpv
                    if (!next.isFinite() || next <= -1.0) r = (r - 1.0) / 2.0 else r = next
                }
                CellValue.Err(CellErr.Err.NUM)
            },
            onFailure = { CellValue.Err((it as CellErrorSignal).err) },
        )
    }

    private fun evalRate(args: List<Expr>, env: Map<String, CellValue>): CellValue {
        if (args.size !in 3..6) return CellValue.Err(CellErr.Err.VALUE)
        val nums = DoubleArray(6)
        for (i in args.indices) {
            val v = evalScalarAsNum(evalExpr(args[i], env)); if (v is CellValue.Err) return v
            nums[i] = (v as CellValue.Num).value
        }
        val nper = nums[0]; val pmt = nums[1]; val pv = nums[2]
        val fv = if (args.size >= 4) nums[3] else 0.0
        val type = if (args.size >= 5 && nums[4] != 0.0) 1.0 else 0.0
        var r = if (args.size >= 6) nums[5] else 0.1
        if (nper <= 0.0) return CellValue.Err(CellErr.Err.NUM)
        repeat(80) {
            val f = if (abs(r) < 1e-12) pv + pmt * nper + fv else {
                val p = (1.0 + r).pow(nper)
                pv * p + pmt * (1.0 + r * type) * (p - 1.0) / r + fv
            }
            if (abs(f) < 1e-9) return CellValue.Num(normalizeNegZero(r))
            val eps = 1e-7
            val r2 = r + eps
            val p2 = (1.0 + r2).pow(nper)
            val f2 = pv * p2 + pmt * (1.0 + r2 * type) * (p2 - 1.0) / r2 + fv
            val df = (f2 - f) / eps
            if (abs(df) < 1e-15) return CellValue.Err(CellErr.Err.NUM)
            val next = r - f / df
            if (!next.isFinite() || next <= -1.0) return CellValue.Err(CellErr.Err.NUM)
            r = next
        }
        return CellValue.Err(CellErr.Err.NUM)
    }

    private fun evalNper(args: List<Expr>, env: Map<String, CellValue>): CellValue {
        if (args.size !in 3..5) return CellValue.Err(CellErr.Err.VALUE)
        val nums = DoubleArray(5)
        for (i in args.indices) {
            val v = evalScalarAsNum(evalExpr(args[i], env)); if (v is CellValue.Err) return v
            nums[i] = (v as CellValue.Num).value
        }
        val rate = nums[0]; val pmt = nums[1]; val pv = nums[2]
        val fv = if (args.size >= 4) nums[3] else 0.0
        val type = if (args.size >= 5 && nums[4] != 0.0) 1.0 else 0.0
        if (abs(rate) < 1e-12) {
            if (pmt == 0.0) return CellValue.Err(CellErr.Err.DIV0)
            return CellValue.Num(normalizeNegZero(-(pv + fv) / pmt))
        }
        val adjPmt = pmt * (1.0 + rate * type)
        val num = adjPmt - fv * rate
        val den = pv * rate + adjPmt
        if (den == 0.0 || num / den <= 0.0 || 1.0 + rate <= 0.0) return CellValue.Err(CellErr.Err.NUM)
        val res = ln(num / den) / ln(1.0 + rate)
        return if (!res.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(res))
    }

    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    private fun serialToLocalDate(serial: Double): LocalDate? {
        val days = floor(serial).toLong()
        if (days < 61L || days > 2_958_465L) return null // post-1900-03-01 deterministic civil window
        return EXCEL_EPOCH.plusDays(days)
    }

    private fun localDateToSerial(d: LocalDate): Double =
        ChronoUnit.DAYS.between(EXCEL_EPOCH, d).toDouble()

    private fun evalDateFunction(name: String, args: List<Expr>, env: Map<String, CellValue>): CellValue {
        return when (name) {
            "DATE" -> {
                if (args.size != 3) return CellValue.Err(CellErr.Err.VALUE)
                val yv = evalScalarAsNum(evalExpr(args[0], env)); if (yv is CellValue.Err) return yv
                val mv = evalScalarAsNum(evalExpr(args[1], env)); if (mv is CellValue.Err) return mv
                val dv = evalScalarAsNum(evalExpr(args[2], env)); if (dv is CellValue.Err) return dv
                var y = (yv as CellValue.Num).value.toInt()
                if (y in 0..1899) y += 1900
                val m = (mv as CellValue.Num).value.toLong()
                val d = (dv as CellValue.Num).value.toLong()
                if (y !in 1900..9999) return CellValue.Err(CellErr.Err.NUM)
                val date = LocalDate.of(y, 1, 1).plusMonths(m - 1).plusDays(d - 1)
                CellValue.Num(localDateToSerial(date))
            }
            "YEAR", "MONTH", "DAY" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val sv = evalScalarAsNum(evalExpr(args[0], env)); if (sv is CellValue.Err) return sv
                val d = serialToLocalDate((sv as CellValue.Num).value) ?: return CellValue.Err(CellErr.Err.NUM)
                val part = when (name) { "YEAR" -> d.year; "MONTH" -> d.monthValue; else -> d.dayOfMonth }
                CellValue.Num(part.toDouble())
            }
            "EDATE", "EOMONTH" -> {
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                val sv = evalScalarAsNum(evalExpr(args[0], env)); if (sv is CellValue.Err) return sv
                val mv = evalScalarAsNum(evalExpr(args[1], env)); if (mv is CellValue.Err) return mv
                val start = serialToLocalDate((sv as CellValue.Num).value) ?: return CellValue.Err(CellErr.Err.NUM)
                val shifted = start.plusMonths((mv as CellValue.Num).value.toLong())
                val target = if (name == "EOMONTH") shifted.withDayOfMonth(shifted.lengthOfMonth()) else shifted
                CellValue.Num(localDateToSerial(target))
            }
            "DATEDIF" -> {
                if (args.size != 3) return CellValue.Err(CellErr.Err.VALUE)
                val s1 = evalScalarAsNum(evalExpr(args[0], env)); if (s1 is CellValue.Err) return s1
                val s2 = evalScalarAsNum(evalExpr(args[1], env)); if (s2 is CellValue.Err) return s2
                val u = evalExpr(args[2], env); if (u is CellValue.Err) return u
                val d1 = serialToLocalDate((s1 as CellValue.Num).value) ?: return CellValue.Err(CellErr.Err.NUM)
                val d2 = serialToLocalDate((s2 as CellValue.Num).value) ?: return CellValue.Err(CellErr.Err.NUM)
                if (d1.isAfter(d2)) return CellValue.Err(CellErr.Err.NUM)
                val diff = when (u.displayText().trim().uppercase()) {
                    "D" -> ChronoUnit.DAYS.between(d1, d2)
                    "M" -> ChronoUnit.MONTHS.between(d1, d2)
                    "Y" -> ChronoUnit.YEARS.between(d1, d2)
                    else -> return CellValue.Err(CellErr.Err.NUM)
                }
                CellValue.Num(diff.toDouble())
            }
            "WEEKDAY" -> {
                if (args.size !in 1..2) return CellValue.Err(CellErr.Err.VALUE)
                val sv = evalScalarAsNum(evalExpr(args[0], env)); if (sv is CellValue.Err) return sv
                val rtype = if (args.size == 2) {
                    val rv = evalScalarAsNum(evalExpr(args[1], env)); if (rv is CellValue.Err) return rv
                    (rv as CellValue.Num).value.toInt()
                } else 1
                val d = serialToLocalDate((sv as CellValue.Num).value) ?: return CellValue.Err(CellErr.Err.NUM)
                val iso = d.dayOfWeek.value // 1=Mon..7=Sun
                val out = when (rtype) {
                    1 -> if (iso == 7) 1 else iso + 1
                    2 -> iso
                    3 -> iso - 1
                    else -> return CellValue.Err(CellErr.Err.NUM)
                }
                CellValue.Num(out.toDouble())
            }
            else -> { // NETWORKDAYS
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                val s1 = evalScalarAsNum(evalExpr(args[0], env)); if (s1 is CellValue.Err) return s1
                val s2 = evalScalarAsNum(evalExpr(args[1], env)); if (s2 is CellValue.Err) return s2
                val d1 = serialToLocalDate((s1 as CellValue.Num).value) ?: return CellValue.Err(CellErr.Err.NUM)
                val d2 = serialToLocalDate((s2 as CellValue.Num).value) ?: return CellValue.Err(CellErr.Err.NUM)
                val signDir = if (d2.isBefore(d1)) -1 else 1
                var lo = if (signDir > 0) d1 else d2
                val hi = if (signDir > 0) d2 else d1
                var biz = 0L
                while (!lo.isAfter(hi)) {
                    if (lo.dayOfWeek.value in 1..5) biz++
                    lo = lo.plusDays(1)
                }
                CellValue.Num((biz * signDir).toDouble())
            }
        }
    }

    private fun evalTextR48Function(name: String, args: List<Expr>, env: Map<String, CellValue>): CellValue {
        return when (name) {
            "CLEAN" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val v = evalExpr(args[0], env); if (v is CellValue.Err) return v
                CellValue.Text(v.displayText().filter { it.code >= 32 })
            }
            "CODE" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val v = evalExpr(args[0], env); if (v is CellValue.Err) return v
                val s = v.displayText()
                if (s.isEmpty()) CellValue.Err(CellErr.Err.VALUE) else CellValue.Num(s[0].code.toDouble())
            }
            "CHAR" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val nv = evalScalarAsNum(evalExpr(args[0], env)); if (nv is CellValue.Err) return nv
                val cp = (nv as CellValue.Num).value.toInt()
                if (cp !in 1..65535) CellValue.Err(CellErr.Err.VALUE) else CellValue.Text(cp.toChar().toString())
            }
            "NUMBERVALUE" -> {
                if (args.size !in 1..3) return CellValue.Err(CellErr.Err.VALUE)
                val tv = evalExpr(args[0], env); if (tv is CellValue.Err) return tv
                val decSep = if (args.size >= 2) {
                    val dv = evalExpr(args[1], env); if (dv is CellValue.Err) return dv
                    dv.displayText().firstOrNull()?.toString() ?: return CellValue.Err(CellErr.Err.VALUE)
                } else "."
                val grpSep = if (args.size >= 3) {
                    val gv = evalExpr(args[2], env); if (gv is CellValue.Err) return gv
                    gv.displayText().firstOrNull()?.toString() ?: ""
                } else ","
                if (decSep == grpSep) return CellValue.Err(CellErr.Err.VALUE)
                var raw = tv.displayText().trim().replace(" ", "")
                if (grpSep.isNotEmpty()) raw = raw.replace(grpSep, "")
                raw = raw.replace(decSep, ".")
                var pctScale = 1.0
                while (raw.endsWith("%")) { raw = raw.dropLast(1); pctScale *= 0.01 }
                val parsed = raw.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return CellValue.Err(CellErr.Err.VALUE)
                CellValue.Num(normalizeNegZero(parsed * pctScale))
            }
            "REGEXMATCH" -> {
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                val tv = evalExpr(args[0], env); if (tv is CellValue.Err) return tv
                val pv = evalExpr(args[1], env); if (pv is CellValue.Err) return pv
                val rx = runCatching { Regex(pv.displayText()) }.getOrElse { return CellValue.Err(CellErr.Err.VALUE) }
                CellValue.Bool(rx.containsMatchIn(tv.displayText()))
            }
            "REGEXEXTRACT" -> {
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                val tv = evalExpr(args[0], env); if (tv is CellValue.Err) return tv
                val pv = evalExpr(args[1], env); if (pv is CellValue.Err) return pv
                val rx = runCatching { Regex(pv.displayText()) }.getOrElse { return CellValue.Err(CellErr.Err.VALUE) }
                val m = rx.find(tv.displayText()) ?: return CellValue.Err(CellErr.Err.NA)
                val hit = if (m.groupValues.size > 1) m.groupValues[1] else m.value
                CellValue.Text(hit)
            }
            else -> { // REGEXREPLACE
                if (args.size != 3) return CellValue.Err(CellErr.Err.VALUE)
                val tv = evalExpr(args[0], env); if (tv is CellValue.Err) return tv
                val pv = evalExpr(args[1], env); if (pv is CellValue.Err) return pv
                val rv = evalExpr(args[2], env); if (rv is CellValue.Err) return rv
                val rx = runCatching { Regex(pv.displayText()) }.getOrElse { return CellValue.Err(CellErr.Err.VALUE) }
                CellValue.Text(rx.replace(tv.displayText(), rv.displayText()))
            }
        }
    }

    private fun evalMathR48Function(name: String, args: List<Expr>, env: Map<String, CellValue>): CellValue {
        return when (name) {
            "EVEN", "ODD" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val xv = evalScalarAsNum(evalExpr(args[0], env)); if (xv is CellValue.Err) return xv
                val x = (xv as CellValue.Num).value
                if (x == 0.0) return CellValue.Num(if (name == "EVEN") 0.0 else 1.0)
                val s = if (x < 0.0) -1.0 else 1.0
                var k = ceil(abs(x)).toLong()
                if (name == "EVEN" && k % 2L != 0L) k++
                if (name == "ODD" && k % 2L == 0L) k++
                CellValue.Num(normalizeNegZero(s * k.toDouble()))
            }
            "FACT" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val nv = evalScalarAsNum(evalExpr(args[0], env)); if (nv is CellValue.Err) return nv
                val n = floor((nv as CellValue.Num).value).toInt()
                if (n < 0 || n > 170) return CellValue.Err(CellErr.Err.NUM)
                var acc = 1.0
                for (i in 2..n) acc *= i.toDouble()
                CellValue.Num(acc)
            }
            "COMBIN" -> {
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                val nv = evalScalarAsNum(evalExpr(args[0], env)); if (nv is CellValue.Err) return nv
                val kv = evalScalarAsNum(evalExpr(args[1], env)); if (kv is CellValue.Err) return kv
                val n = floor((nv as CellValue.Num).value).toInt()
                var k = floor((kv as CellValue.Num).value).toInt()
                if (n < 0 || k < 0 || k > n) return CellValue.Err(CellErr.Err.NUM)
                if (k > n - k) k = n - k
                var acc = 1.0
                for (i in 1..k) acc = acc * (n - k + i) / i
                if (!acc.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(round(acc))
            }
            "SQRT" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                val xv = evalScalarAsNum(evalExpr(args[0], env)); if (xv is CellValue.Err) return xv
                val x = (xv as CellValue.Num).value
                if (x < 0.0) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(sqrt(x)))
            }
            else -> { // POWER
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                val bv = evalScalarAsNum(evalExpr(args[0], env)); if (bv is CellValue.Err) return bv
                val ev = evalScalarAsNum(evalExpr(args[1], env)); if (ev is CellValue.Err) return ev
                val res = (bv as CellValue.Num).value.pow((ev as CellValue.Num).value)
                if (!res.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(res))
            }
        }
    }

    private fun evalMultiCriteriaIfs(name: String, args: List<Expr>, env: Map<String, CellValue>): CellValue {
        val hasTarget = name != "COUNTIFS"
        val minArgs = if (hasTarget) 3 else 2
        if (args.size < minArgs || (args.size - if (hasTarget) 1 else 0) % 2 != 0) {
            return CellValue.Err(CellErr.Err.VALUE)
        }
        val targetRange = if (hasTarget) exprToRange(args[0]) ?: return CellValue.Err(CellErr.Err.VALUE) else null
        val startIdx = if (hasTarget) 1 else 0
        val pairs = ArrayList<Pair<SheetRange.Range, CellValue>>()
        var i = startIdx
        while (i < args.size) {
            val rng = exprToRange(args[i]) ?: return CellValue.Err(CellErr.Err.VALUE)
            val crit = evalExpr(args[i + 1], env)
            if (crit is CellValue.Err) return crit
            pairs.add(rng to crit)
            i += 2
        }
        val baseW = targetRange?.width ?: pairs[0].first.width
        val baseH = targetRange?.height ?: pairs[0].first.height
        if (pairs.any { it.first.width != baseW || it.first.height != baseH }) {
            return CellValue.Err(CellErr.Err.VALUE)
        }
        var count = 0
        var sum = 0.0
        for (dr in 0 until baseH) {
            for (dc in 0 until baseW) {
                var allMatch = true
                for ((rng, crit) in pairs) {
                    val cv = env[SheetRange.Cell(rng.top.col + dc, rng.top.row + dr).toA1()] ?: CellValue.Empty
                    if (!matchesCriteria(cv, crit)) {
                        allMatch = false
                        break
                    }
                }
                if (allMatch) {
                    if (!hasTarget) {
                        count++
                    } else {
                        val tv = env[SheetRange.Cell(targetRange!!.top.col + dc, targetRange.top.row + dr).toA1()] ?: CellValue.Empty
                        if (tv is CellValue.Err) return tv
                        if (tv is CellValue.Num) {
                            sum += tv.value
                            count++
                        }
                    }
                }
            }
        }
        return when (name) {
            "COUNTIFS" -> CellValue.Num(count.toDouble())
            "SUMIFS" -> if (!sum.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(sum))
            else -> if (count == 0) CellValue.Err(CellErr.Err.DIV0) else CellValue.Num(normalizeNegZero(sum / count))
        }
    }

    private fun findVectorMatchIndex(
        needle: CellValue,
        vec: List<CellValue>,
        matchMode: Int,
        searchMode: Int,
    ): Int? {
        val indices = if (searchMode == -1) vec.indices.reversed() else vec.indices
        for (idx in indices) {
            val cand = vec[idx]
            if (cand is CellValue.Err || cand == CellValue.Empty) continue
            if (compareSpreadsheetValues(needle, cand) == 0) return idx
        }
        if (matchMode == 0) return null
        var bestIdx: Int? = null
        var bestVal: CellValue? = null
        for (idx in indices) {
            val cand = vec[idx]
            if (cand is CellValue.Err || cand == CellValue.Empty) continue
            if (cand::class != needle::class) continue
            val cmp = compareSpreadsheetValues(cand, needle)
            if (matchMode == -1 && cmp < 0) {
                if (bestVal == null || compareSpreadsheetValues(cand, bestVal) > 0) {
                    bestVal = cand; bestIdx = idx
                }
            } else if (matchMode == 1 && cmp > 0) {
                if (bestVal == null || compareSpreadsheetValues(cand, bestVal) < 0) {
                    bestVal = cand; bestIdx = idx
                }
            }
        }
        return bestIdx
    }

    private fun evalXlookup(args: List<Expr>, env: Map<String, CellValue>): CellValue {
        if (args.size !in 3..6) return CellValue.Err(CellErr.Err.VALUE)
        val needle = evalExpr(args[0], env)
        if (needle is CellValue.Err) return needle
        val lookupVec = exprTo1DVector(args[1], env) ?: return CellValue.Err(CellErr.Err.VALUE)
        val returnVec = exprTo1DVector(args[2], env) ?: return CellValue.Err(CellErr.Err.VALUE)
        if (lookupVec.size != returnVec.size) return CellValue.Err(CellErr.Err.VALUE)
        val matchMode = if (args.size >= 5) {
            val m = evalScalarAsNum(evalExpr(args[4], env)); if (m is CellValue.Err) return m
            (m as CellValue.Num).value.toInt()
        } else 0
        val searchMode = if (args.size >= 6) {
            val s = evalScalarAsNum(evalExpr(args[5], env)); if (s is CellValue.Err) return s
            (s as CellValue.Num).value.toInt()
        } else 1
        if (matchMode !in setOf(0, -1, 1) || searchMode !in setOf(1, -1)) return CellValue.Err(CellErr.Err.VALUE)
        val hitIdx = findVectorMatchIndex(needle, lookupVec, matchMode, searchMode)
        if (hitIdx == null) {
            return if (args.size >= 4) evalExpr(args[3], env) else CellValue.Err(CellErr.Err.NA)
        }
        val found = returnVec[hitIdx]
        return if (found == CellValue.Empty) CellValue.Num(0.0) else found
    }

    private fun evalXmatch(args: List<Expr>, env: Map<String, CellValue>): CellValue {
        if (args.size !in 2..4) return CellValue.Err(CellErr.Err.VALUE)
        val needle = evalExpr(args[0], env)
        if (needle is CellValue.Err) return needle
        val lookupVec = exprTo1DVector(args[1], env) ?: return CellValue.Err(CellErr.Err.VALUE)
        val matchMode = if (args.size >= 3) {
            val m = evalScalarAsNum(evalExpr(args[2], env)); if (m is CellValue.Err) return m
            (m as CellValue.Num).value.toInt()
        } else 0
        val searchMode = if (args.size >= 4) {
            val s = evalScalarAsNum(evalExpr(args[3], env)); if (s is CellValue.Err) return s
            (s as CellValue.Num).value.toInt()
        } else 1
        if (matchMode !in setOf(0, -1, 1) || searchMode !in setOf(1, -1)) return CellValue.Err(CellErr.Err.VALUE)
        val hitIdx = findVectorMatchIndex(needle, lookupVec, matchMode, searchMode)
        return if (hitIdx == null) CellValue.Err(CellErr.Err.NA) else CellValue.Num((hitIdx + 1).toDouble())
    }

    private fun evalHlookup(args: List<Expr>, env: Map<String, CellValue>): CellValue {
        if (args.size !in 3..4) return CellValue.Err(CellErr.Err.VALUE)
        val needle = evalExpr(args[0], env)
        if (needle is CellValue.Err) return needle
        val table = exprToRange(args[1]) ?: return CellValue.Err(CellErr.Err.VALUE)
        val rowNum = evalScalarAsNum(evalExpr(args[2], env))
        if (rowNum is CellValue.Err) return rowNum
        val rowIdx = (rowNum as CellValue.Num).value.toInt()
        if (rowIdx < 1) return CellValue.Err(CellErr.Err.VALUE)
        if (rowIdx > table.height) return CellValue.Err(CellErr.Err.REF)
        val approx = if (args.size == 4) {
            val b = evalAsBool(evalExpr(args[3], env))
            if (b is CellValue.Err) return b
            (b as CellValue.Bool).value
        } else true
        require(!approx) { "HLOOKUP approximate range_lookup=TRUE is out of scope (pass FALSE/0 for exact match)" }
        for (c in table.top.col..table.bottom.col) {
            val keyCell = env[SheetRange.Cell(c, table.top.row).toA1()] ?: CellValue.Empty
            if (keyCell !is CellValue.Err && keyCell != CellValue.Empty && compareSpreadsheetValues(needle, keyCell) == 0) {
                val hit = env[SheetRange.Cell(c, table.top.row + rowIdx - 1).toA1()] ?: CellValue.Empty
                return if (hit == CellValue.Empty) CellValue.Num(0.0) else hit
            }
        }
        return CellValue.Err(CellErr.Err.NA)
    }

    private fun evalFinancialTvm(name: String, args: List<Expr>, env: Map<String, CellValue>): CellValue {
        if (args.size !in 3..5) return CellValue.Err(CellErr.Err.VALUE)
        val nums = DoubleArray(5)
        for (i in args.indices) {
            val v = evalScalarAsNum(evalExpr(args[i], env))
            if (v is CellValue.Err) return v
            nums[i] = (v as CellValue.Num).value
        }
        val rate = nums[0]
        val nper = nums[1]
        val a2 = nums[2]
        val a3 = if (args.size >= 4) nums[3] else 0.0
        val type = if (args.size >= 5) (if (nums[4] != 0.0) 1.0 else 0.0) else 0.0
        if (nper <= 0.0 && name == "PMT") return CellValue.Err(CellErr.Err.NUM)
        val res = when (name) {
            "PMT" -> {
                val pv = a2; val fv = a3
                if (abs(rate) < 1e-12) -(pv + fv) / nper
                else {
                    val f = (1.0 + rate).pow(nper)
                    -rate * (pv * f + fv) / ((1.0 + rate * type) * (f - 1.0))
                }
            }
            "FV" -> {
                val pmt = a2; val pv = a3
                if (abs(rate) < 1e-12) -(pv + pmt * nper)
                else {
                    val f = (1.0 + rate).pow(nper)
                    -(pv * f + pmt * (1.0 + rate * type) * (f - 1.0) / rate)
                }
            }
            else -> { // PV
                val pmt = a2; val fv = a3
                if (abs(rate) < 1e-12) -(fv + pmt * nper)
                else {
                    val f = (1.0 + rate).pow(nper)
                    -(fv + pmt * (1.0 + rate * type) * (f - 1.0) / rate) / f
                }
            }
        }
        return if (!res.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(res))
    }

    private fun matchesCriteria(cell: CellValue, crit: CellValue): Boolean {
        if (cell is CellValue.Err) return false
        if (crit is CellValue.Num || crit is CellValue.Bool) {
            return cell != CellValue.Empty && compareSpreadsheetValues(cell, crit) == 0
        }
        val spec = crit.displayText()
        val opLen = when {
            spec.startsWith(">=") || spec.startsWith("<=") || spec.startsWith("<>") -> 2
            spec.startsWith(">") || spec.startsWith("<") || spec.startsWith("=") -> 1
            else -> 0
        }
        val op = if (opLen > 0) spec.substring(0, opLen) else "="
        val rhsText = spec.substring(opLen)
        if (rhsText.isEmpty() && op == "=") return cell == CellValue.Empty || (cell is CellValue.Text && cell.value.isEmpty())
        val rhsNum = rhsText.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
        if (rhsNum != null && cell is CellValue.Num) {
            val cmp = cell.value.compareTo(rhsNum)
            return when (op) {
                "=" -> cmp == 0
                "<>" -> cmp != 0
                ">" -> cmp > 0
                ">=" -> cmp >= 0
                "<" -> cmp < 0
                "<=" -> cmp <= 0
                else -> false
            }
        }
        if (op in setOf(">", ">=", "<", "<=") && rhsNum != null) return false
        val cellStr = if (cell == CellValue.Empty) "" else cell.displayText()
        val cmpStr = cellStr.lowercase().compareTo(rhsText.lowercase())
        return when (op) {
            "=" -> cmpStr == 0
            "<>" -> cmpStr != 0
            ">" -> cmpStr > 0
            ">=" -> cmpStr >= 0
            "<" -> cmpStr < 0
            "<=" -> cmpStr <= 0
            else -> false
        }
    }

    // ---- Tokenizer & Recursive Descent Parser ------------------------------

    private class Parser(private val src: String) {
        private var pos = 0
        private var depth = 0

        fun parseFull(): Expr {
            val expr = parseComparison()
            skipWs()
            require(pos == src.length) { "unexpected trailing formula text at $pos: '${src.substring(pos)}'" }
            return expr
        }

        private fun enter(): Int {
            depth++
            require(depth <= MAX_AST_DEPTH) { "formula nesting exceeds $MAX_AST_DEPTH" }
            return depth
        }
        private fun leave() { depth-- }

        private fun skipWs() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun parseComparison(): Expr {
            enter()
            try {
                var left = parseConcat()
                while (true) {
                    skipWs()
                    val op = when {
                        src.startsWith("<>", pos) -> "<>"
                        src.startsWith("<=", pos) -> "<="
                        src.startsWith(">=", pos) -> ">="
                        pos < src.length && (src[pos] == '=' || src[pos] == '<' || src[pos] == '>') -> src[pos].toString()
                        else -> break
                    }
                    pos += op.length
                    left = Expr.Binary(op, left, parseConcat())
                }
                return left
            } finally {
                leave()
            }
        }

        private fun parseConcat(): Expr {
            var left = parseAddSub()
            while (true) {
                skipWs()
                if (pos < src.length && src[pos] == '&') {
                    pos++
                    left = Expr.Binary("&", left, parseAddSub())
                } else break
            }
            return left
        }

        private fun parseAddSub(): Expr {
            var left = parseMulDiv()
            while (true) {
                skipWs()
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) {
                    val op = src[pos++].toString()
                    left = Expr.Binary(op, left, parseMulDiv())
                } else break
            }
            return left
        }

        private fun parseMulDiv(): Expr {
            var left = parsePow()
            while (true) {
                skipWs()
                if (pos < src.length && (src[pos] == '*' || src[pos] == '/')) {
                    val op = src[pos++].toString()
                    left = Expr.Binary(op, left, parsePow())
                } else break
            }
            return left
        }

        private fun parsePow(): Expr {
            var left = parseUnary()
            while (true) {
                skipWs()
                if (pos < src.length && src[pos] == '^') {
                    pos++
                    left = Expr.Binary("^", left, parseUnary())
                } else break
            }
            return left
        }

        private fun parseUnary(): Expr {
            skipWs()
            if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) {
                val op = src[pos++]
                enter()
                try {
                    return Expr.Unary(op, parseUnary())
                } finally {
                    leave()
                }
            }
            var atom = parseAtom()
            while (true) {
                skipWs()
                if (pos < src.length && src[pos] == '%') {
                    pos++
                    atom = Expr.Percent(atom)
                } else if (pos < src.length && src[pos] == '(') {
                    atom = Expr.Invoke(atom, parseArgList("invocation"))
                } else break
            }
            return atom
        }

        private fun parseAtom(): Expr {
            skipWs()
            require(pos < src.length) { "unexpected end of formula" }
            val c = src[pos]
            return when {
                c == '(' -> {
                    pos++
                    enter()
                    val inner = try { parseComparison() } finally { leave() }
                    skipWs()
                    require(pos < src.length && src[pos] == ')') { "missing ')' at $pos" }
                    pos++
                    inner
                }
                c == '"' -> parseString()
                c == '{' -> parseArrayLiteral()
                c == '#' -> parseErrorLiteral()
                c.isDigit() || c == '.' -> parseNumber()
                c.isLetter() || c == '$' || c == '_' -> parseIdentOrRef()
                else -> throw IllegalArgumentException("unexpected character '$c' at $pos")
            }
        }

        private fun parseArrayLiteral(): Expr {
            pos++ // '{'
            enter()
            try {
                val rows = ArrayList<List<Expr>>()
                var curRow = ArrayList<Expr>()
                skipWs()
                require(pos < src.length && src[pos] != '}') { "empty array literal '{}' refused" }
                while (true) {
                    curRow.add(parseComparison())
                    skipWs()
                    require(pos < src.length) { "unterminated array literal" }
                    when (src[pos]) {
                        ',' -> {
                            pos++
                            skipWs()
                        }
                        ';' -> {
                            rows.add(curRow)
                            curRow = ArrayList()
                            pos++
                            skipWs()
                        }
                        '}' -> {
                            rows.add(curRow)
                            pos++
                            val w = rows[0].size
                            require(w > 0 && rows.all { it.size == w }) {
                                "ragged array literal rows (${rows.map { it.size }}) refused"
                            }
                            return Expr.ArrayLit(rows)
                        }
                        else -> throw IllegalArgumentException("unexpected '${src[pos]}' inside array literal at $pos")
                    }
                }
            } finally {
                leave()
            }
        }

        private fun parseString(): Expr {
            pos++ // skip opening '"'
            val sb = StringBuilder()
            while (pos < src.length) {
                if (src[pos] == '"') {
                    if (pos + 1 < src.length && src[pos + 1] == '"') {
                        sb.append('"')
                        pos += 2
                    } else {
                        pos++
                        return Expr.StrLit(sb.toString())
                    }
                } else {
                    sb.append(src[pos++])
                }
            }
            throw IllegalArgumentException("unterminated string literal")
        }

        private fun parseErrorLiteral(): Expr {
            val start = pos
            pos++ // '#'
            while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '/')) pos++
            if (pos < src.length && (src[pos] == '!' || src[pos] == '?')) pos++
            val token = src.substring(start, pos).uppercase()
            return Expr.ErrLit(CellErr.parse(token))
        }

        private fun parseNumber(): Expr {
            val start = pos
            while (pos < src.length && src[pos].isDigit()) pos++
            if (pos < src.length && src[pos] == '.') {
                pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                val expStart = pos
                while (pos < src.length && src[pos].isDigit()) pos++
                require(pos > expStart) { "malformed exponent at $start" }
            }
            val v = src.substring(start, pos).toDoubleOrNull()
                ?: throw IllegalArgumentException("malformed numeric literal '${src.substring(start, pos)}'")
            require(v.isFinite()) { "numeric literal overflow" }
            return Expr.NumLit(v)
        }

        private fun parseArgList(label: String): List<Expr> {
            pos++ // '('
            val args = ArrayList<Expr>()
            skipWs()
            if (pos < src.length && src[pos] != ')') {
                while (true) {
                    skipWs()
                    if (pos < src.length && (src[pos] == ',' || src[pos] == ')')) {
                        args.add(Expr.OmittedArg)
                    } else {
                        args.add(parseComparison())
                    }
                    skipWs()
                    if (pos < src.length && src[pos] == ',') {
                        pos++
                        continue
                    }
                    break
                }
            }
            require(pos < src.length && src[pos] == ')') { "unclosed function call '$label('" }
            pos++
            return args
        }

        private fun parseIdentOrRef(): Expr {
            val start = pos
            while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] in "\$_.:!")) pos++
            val raw = src.substring(start, pos)
            require('!' !in raw) { "multi-sheet reference '$raw' is out of scope" }
            skipWs()
            if (pos < src.length && src[pos] == '(') {
                return Expr.Call(raw, parseArgList(raw))
            }
            val upper = raw.uppercase()
            if (upper == "TRUE") return Expr.BoolLit(true)
            if (upper == "FALSE") return Expr.BoolLit(false)
            val stripped = upper.replace("$", "")
            return if (':' in stripped) {
                Expr.RangeRef(SheetRange.parse(stripped))
            } else {
                val cell = if (!upper.startsWith("_XLPM.") && '_' !in stripped && '.' !in stripped) {
                    runCatching { SheetRange.parseCell(stripped).toA1() }.getOrNull()
                } else null
                if (cell != null) Expr.CellRef(cell) else Expr.SymbolRef(upper)
            }
        }
    }

    private fun lanczosLnGamma(z: Double): Double {
        val g = 7.0
        val p = doubleArrayOf(
            0.99999999999980993, 676.5203681218851, -1259.1392167224028,
            771.32342877765313, -176.61502916214059, 12.507343278686905,
            -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7,
        )
        if (z < 0.5) return ln(Math.PI / kotlin.math.sin(Math.PI * z)) - lanczosLnGamma(1.0 - z)
        val zm1 = z - 1.0
        var x = p[0]
        for (i in 1 until p.size) x += p[i] / (zm1 + i.toDouble())
        val t = zm1 + g + 0.5
        return 0.5 * ln(2.0 * Math.PI) + (zm1 + 0.5) * ln(t) - t + ln(x)
    }

    private fun regGammaLowerP(a: Double, x: Double): Double {
        if (x <= 0.0) return 0.0
        if (x < a + 1.0) {
            var ap = a
            var sum = 1.0 / a
            var del = sum
            repeat(200) {
                ap += 1.0
                del *= x / ap
                sum += del
                if (abs(del) < abs(sum) * 1e-14) return sum * exp(-x + a * ln(x) - lanczosLnGamma(a))
            }
            return sum * exp(-x + a * ln(x) - lanczosLnGamma(a))
        } else {
            var b = x + 1.0 - a
            var c = 1.0 / 1e-30
            var d = 1.0 / b
            var h = d
            for (i in 1..200) {
                val an = -i * (i - a)
                b += 2.0
                d = an * d + b
                if (abs(d) < 1e-30) d = 1e-30
                c = b + an / c
                if (abs(c) < 1e-30) c = 1e-30
                d = 1.0 / d
                val del = d * c
                h *= del
                if (abs(del - 1.0) < 1e-14) break
            }
            val q = exp(-x + a * ln(x) - lanczosLnGamma(a)) * h
            return (1.0 - q).coerceIn(0.0, 1.0)
        }
    }

    private fun besselSeries(x: Double, n: Int, modified: Boolean): Double {
        val half = x / 2.0
        var term = 1.0
        for (k in 1..n) term *= half / k.toDouble()
        var sum = term
        val halfSq = half * half
        for (m in 1..80) {
            val factor = halfSq / (m.toDouble() * (m + n).toDouble())
            term *= if (modified) factor else -factor
            sum += term
            if (abs(term) <= abs(sum) * 1e-15) break
        }
        return sum
    }

    private fun evalR52Grid(name: String, args: List<Expr>, env: Map<String, CellValue>): List<List<CellValue>> {
        fun errGrid(e: CellErr.Err = CellErr.Err.VALUE) = listOf(listOf(CellValue.Err(e)))
        return when (name) {
            "OUTERPRODUCT" -> {
                if (args.size != 2) return errGrid()
                val gA = exprTo2DGrid(args[0], env) ?: return errGrid()
                val gB = exprTo2DGrid(args[1], env) ?: return errGrid()
                val vecA = gA.flatten()
                val vecB = gB.flatten()
                if (vecA.isEmpty() || vecB.isEmpty()) return errGrid()
                vecA.map { a ->
                    vecB.map { b ->
                        if (a is CellValue.Err) a
                        else if (b is CellValue.Err) b
                        else if (a is CellValue.Num && b is CellValue.Num) CellValue.Num(normalizeNegZero(a.value * b.value))
                        else CellValue.Err(CellErr.Err.VALUE)
                    }
                }
            }
            "CUMSUM" -> {
                if (args.size != 1) return errGrid()
                val g = exprTo2DGrid(args[0], env) ?: return errGrid()
                var acc = 0.0
                g.map { row ->
                    row.map { cell ->
                        when (cell) {
                            is CellValue.Err -> cell
                            is CellValue.Num -> { acc += cell.value; CellValue.Num(normalizeNegZero(acc)) }
                            else -> CellValue.Num(normalizeNegZero(acc))
                        }
                    }
                }
            }
            "DIFFARRAY" -> {
                if (args.size != 1) return errGrid()
                val flat = (exprTo2DGrid(args[0], env) ?: return errGrid()).flatten()
                if (flat.size < 2) return errGrid()
                (1 until flat.size).map { idx ->
                    val cur = flat[idx]; val prev = flat[idx - 1]
                    val v = if (cur is CellValue.Err) cur
                    else if (prev is CellValue.Err) prev
                    else if (cur is CellValue.Num && prev is CellValue.Num) CellValue.Num(normalizeNegZero(cur.value - prev.value))
                    else CellValue.Err(CellErr.Err.VALUE)
                    listOf(v)
                }
            }
            "MOVINGAVERAGE" -> {
                if (args.size != 2) return errGrid()
                val flat = (exprTo2DGrid(args[0], env) ?: return errGrid()).flatten()
                val winV = evalScalarAsNum(evalExpr(args[1], env)); if (winV is CellValue.Err) return listOf(listOf(winV))
                val win = (winV as CellValue.Num).value.toInt()
                if (win < 1 || win > flat.size) return errGrid(CellErr.Err.NUM)
                (0..(flat.size - win)).map { start ->
                    var s = 0.0
                    var err: CellValue.Err? = null
                    for (k in start until start + win) {
                        val c = flat[k]
                        if (c is CellValue.Err) { err = c; break }
                        if (c !is CellValue.Num) { err = CellValue.Err(CellErr.Err.VALUE); break }
                        s += c.value
                    }
                    listOf(err ?: CellValue.Num(normalizeNegZero(s / win.toDouble())))
                }
            }
            "ZSCOREGRID" -> {
                if (args.size != 1) return errGrid()
                val g = exprTo2DGrid(args[0], env) ?: return errGrid()
                val nums = ArrayList<Double>()
                for (row in g) for (c in row) {
                    if (c is CellValue.Err) return listOf(listOf(c))
                    if (c is CellValue.Num) nums.add(c.value)
                }
                if (nums.size < 2) return errGrid(CellErr.Err.DIV0)
                val mean = nums.sum() / nums.size.toDouble()
                val variance = nums.sumOf { (it - mean) * (it - mean) } / nums.size.toDouble()
                val sd = sqrt(variance)
                if (sd == 0.0) return errGrid(CellErr.Err.DIV0)
                g.map { row ->
                    row.map { c ->
                        if (c is CellValue.Num) CellValue.Num(normalizeNegZero((c.value - mean) / sd))
                        else CellValue.Err(CellErr.Err.VALUE)
                    }
                }
            }
            "DIAGONAL" -> {
                if (args.size != 1) return errGrid()
                val g = exprTo2DGrid(args[0], env) ?: return errGrid()
                val n = minOf(g.size, g.firstOrNull()?.size ?: 0)
                if (n == 0) return errGrid()
                (0 until n).map { i -> listOf(g[i][i]) }
            }
            else -> errGrid(CellErr.Err.NAME)
        }
    }

    private fun evalR52ScalarFunction(name: String, args: List<Expr>, env: Map<String, CellValue>): CellValue {
        fun numArg(idx: Int): Double? {
            if (idx !in args.indices) return null
            val v = evalScalarAsNum(evalExpr(args[idx], env))
            return (v as? CellValue.Num)?.value
        }
        fun errIfAny(count: Int): CellValue.Err? {
            for (i in 0 until minOf(count, args.size)) {
                val r = evalExpr(args[i], env)
                if (r is CellValue.Err) return r
            }
            return null
        }
        return when (name) {
            "WEIBULL.DIST" -> {
                if (args.size != 4) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(4)?.let { return it }
                val x = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                val alpha = numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE)
                val beta = numArg(2) ?: return CellValue.Err(CellErr.Err.VALUE)
                val cum = (evalAsBool(evalExpr(args[3], env)) as? CellValue.Bool)?.value ?: return CellValue.Err(CellErr.Err.VALUE)
                if (x < 0.0 || alpha <= 0.0 || beta <= 0.0) return CellValue.Err(CellErr.Err.NUM)
                val res = if (cum) 1.0 - exp(-(x / beta).pow(alpha))
                else (alpha / beta.pow(alpha)) * x.pow(alpha - 1.0) * exp(-(x / beta).pow(alpha))
                CellValue.Num(normalizeNegZero(res))
            }
            "LOGNORM.DIST" -> {
                if (args.size != 4) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(4)?.let { return it }
                val x = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                val mean = numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE)
                val sd = numArg(2) ?: return CellValue.Err(CellErr.Err.VALUE)
                val cum = (evalAsBool(evalExpr(args[3], env)) as? CellValue.Bool)?.value ?: return CellValue.Err(CellErr.Err.VALUE)
                if (x <= 0.0 || sd <= 0.0) return CellValue.Err(CellErr.Err.NUM)
                val z = (ln(x) - mean) / sd
                val res = if (cum) 0.5 * (1.0 + erfApprox(z / sqrt(2.0)))
                else exp(-0.5 * z * z) / (x * sd * sqrt(2.0 * Math.PI))
                CellValue.Num(normalizeNegZero(res))
            }
            "LOGNORM.INV" -> {
                if (args.size != 3) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(3)?.let { return it }
                val p = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                val mean = numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE)
                val sd = numArg(2) ?: return CellValue.Err(CellErr.Err.VALUE)
                if (p <= 0.0 || p >= 1.0 || sd <= 0.0) return CellValue.Err(CellErr.Err.NUM)
                CellValue.Num(normalizeNegZero(exp(mean + sd * normSInvAcklam(p))))
            }
            "GAMMA" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(1)?.let { return it }
                val x = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                if (x <= 0.0 && floor(x) == x) return CellValue.Err(CellErr.Err.NUM)
                val v = if (x > 0.0) exp(lanczosLnGamma(x)) else Math.PI / (kotlin.math.sin(Math.PI * x) * exp(lanczosLnGamma(1.0 - x)))
                if (!v.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(v))
            }
            "GAMMA.DIST" -> {
                if (args.size != 4) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(4)?.let { return it }
                val x = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                val alpha = numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE)
                val beta = numArg(2) ?: return CellValue.Err(CellErr.Err.VALUE)
                val cum = (evalAsBool(evalExpr(args[3], env)) as? CellValue.Bool)?.value ?: return CellValue.Err(CellErr.Err.VALUE)
                if (x < 0.0 || alpha <= 0.0 || beta <= 0.0) return CellValue.Err(CellErr.Err.NUM)
                val res = if (cum) regGammaLowerP(alpha, x / beta)
                else if (x == 0.0) 0.0 else exp((alpha - 1.0) * ln(x) - (x / beta) - alpha * ln(beta) - lanczosLnGamma(alpha))
                CellValue.Num(normalizeNegZero(res))
            }
            "CHISQ.DIST" -> {
                if (args.size != 3) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(3)?.let { return it }
                val x = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                val df = floor(numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE))
                val cum = (evalAsBool(evalExpr(args[2], env)) as? CellValue.Bool)?.value ?: return CellValue.Err(CellErr.Err.VALUE)
                if (x < 0.0 || df < 1.0) return CellValue.Err(CellErr.Err.NUM)
                val halfK = df / 2.0
                val res = if (cum) regGammaLowerP(halfK, x / 2.0)
                else if (x == 0.0) 0.0 else exp((halfK - 1.0) * ln(x) - x / 2.0 - halfK * ln(2.0) - lanczosLnGamma(halfK))
                CellValue.Num(normalizeNegZero(res))
            }
            "CHISQ.DIST.RT" -> {
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(2)?.let { return it }
                val x = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                val df = floor(numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE))
                if (x < 0.0 || df < 1.0) return CellValue.Err(CellErr.Err.NUM)
                CellValue.Num(normalizeNegZero((1.0 - regGammaLowerP(df / 2.0, x / 2.0)).coerceIn(0.0, 1.0)))
            }
            "HYPGEOM.DIST" -> {
                if (args.size != 5) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(5)?.let { return it }
                val s = (numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)).toInt()
                val nSample = (numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE)).toInt()
                val mSucc = (numArg(2) ?: return CellValue.Err(CellErr.Err.VALUE)).toInt()
                val nPop = (numArg(3) ?: return CellValue.Err(CellErr.Err.VALUE)).toInt()
                val cum = (evalAsBool(evalExpr(args[4], env)) as? CellValue.Bool)?.value ?: return CellValue.Err(CellErr.Err.VALUE)
                if (s < 0 || nSample < 0 || mSucc < 0 || nPop <= 0 || nSample > nPop || mSucc > nPop) return CellValue.Err(CellErr.Err.NUM)
                fun lnChoose(n: Int, k: Int): Double =
                    if (k < 0 || k > n) Double.NEGATIVE_INFINITY
                    else lanczosLnGamma(n + 1.0) - lanczosLnGamma(k + 1.0) - lanczosLnGamma(n - k + 1.0)
                fun pmf(k: Int): Double {
                    val lp = lnChoose(mSucc, k) + lnChoose(nPop - mSucc, nSample - k) - lnChoose(nPop, nSample)
                    return if (lp.isInfinite() && lp < 0) 0.0 else exp(lp)
                }
                val res = if (cum) (0..s).sumOf { pmf(it) }.coerceIn(0.0, 1.0) else pmf(s)
                CellValue.Num(normalizeNegZero(res))
            }
            "NEGBINOM.DIST" -> {
                if (args.size != 4) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(4)?.let { return it }
                val f = (numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)).toInt()
                val r = (numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE)).toInt()
                val p = numArg(2) ?: return CellValue.Err(CellErr.Err.VALUE)
                val cum = (evalAsBool(evalExpr(args[3], env)) as? CellValue.Bool)?.value ?: return CellValue.Err(CellErr.Err.VALUE)
                if (f < 0 || r < 1 || p <= 0.0 || p >= 1.0) return CellValue.Err(CellErr.Err.NUM)
                fun pmf(k: Int): Double {
                    val l = lanczosLnGamma(k + r.toDouble()) - lanczosLnGamma(k + 1.0) - lanczosLnGamma(r.toDouble()) +
                        r * ln(p) + k * ln(1.0 - p)
                    return exp(l)
                }
                val res = if (cum) (0..f).sumOf { pmf(it) }.coerceIn(0.0, 1.0) else pmf(f)
                CellValue.Num(normalizeNegZero(res))
            }
            "BESSELJ", "BESSELI" -> {
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(2)?.let { return it }
                val x = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                val n = (numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE)).toInt()
                if (n < 0) return CellValue.Err(CellErr.Err.NUM)
                CellValue.Num(normalizeNegZero(besselSeries(x, n, modified = (name == "BESSELI"))))
            }
            "ERF.PRECISE" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(1)?.let { return it }
                val x = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                val signX = if (x < 0.0) -1.0 else 1.0
                CellValue.Num(normalizeNegZero(signX * regGammaLowerP(0.5, x * x)))
            }
            "ERFC.PRECISE" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(1)?.let { return it }
                val x = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                val erfVal = (if (x < 0.0) -1.0 else 1.0) * regGammaLowerP(0.5, x * x)
                CellValue.Num(normalizeNegZero(1.0 - erfVal))
            }
            "TBILLEQ", "TBILLPRICE", "TBILLYIELD" -> {
                if (args.size != 3) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(3)?.let { return it }
                val settle = (numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)).toLong()
                val mat = (numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE)).toLong()
                val third = numArg(2) ?: return CellValue.Err(CellErr.Err.VALUE)
                val dsm = (mat - settle).toDouble()
                if (dsm <= 0.0 || dsm > 365.0 || third <= 0.0) return CellValue.Err(CellErr.Err.NUM)
                val v = when (name) {
                    "TBILLEQ" -> (365.0 * third) / (360.0 - third * dsm)
                    "TBILLPRICE" -> 100.0 * (1.0 - (third * dsm) / 360.0)
                    else -> ((100.0 - third) / third) * (360.0 / dsm)
                }
                if (!v.isFinite() || v <= 0.0) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(v))
            }
            "DISC", "INTRATE", "RECEIVED", "PRICEDISC", "YIELDDISC" -> {
                if (args.size !in 4..5) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(args.size)?.let { return it }
                val settle = (numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)).toLong()
                val mat = (numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE)).toLong()
                val a3 = numArg(2) ?: return CellValue.Err(CellErr.Err.VALUE)
                val a4 = numArg(3) ?: return CellValue.Err(CellErr.Err.VALUE)
                val basis = if (args.size == 5) (numArg(4) ?: return CellValue.Err(CellErr.Err.VALUE)).toInt() else 0
                if (basis !in 0..4 || mat <= settle || a3 <= 0.0 || a4 <= 0.0) return CellValue.Err(CellErr.Err.NUM)
                val yearDays = if (basis == 1 || basis == 3) 365.0 else 360.0
                val dsm = (mat - settle).toDouble()
                val frac = dsm / yearDays
                val v = when (name) {
                    "DISC" -> ((a4 - a3) / a4) / frac
                    "INTRATE" -> ((a4 - a3) / a3) / frac
                    "RECEIVED" -> a3 / (1.0 - a4 * frac)
                    "PRICEDISC" -> a4 * (1.0 - a3 * frac)
                    else -> ((a4 - a3) / a3) / frac
                }
                if (!v.isFinite() || v <= 0.0) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(v))
            }
            "PERMUTATIONA" -> {
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(2)?.let { return it }
                val n = floor(numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE))
                val k = floor(numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE))
                if (n < 0.0 || k < 0.0 || (n == 0.0 && k > 0.0)) return CellValue.Err(CellErr.Err.NUM)
                val v = if (k == 0.0) 1.0 else n.pow(k)
                if (!v.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(v))
            }
            "SEC", "CSC", "COT", "SECH", "CSCH", "COTH", "ACOT", "ACOTH" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(1)?.let { return it }
                val x = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                val v = when (name) {
                    "SEC" -> { val c = kotlin.math.cos(x); if (abs(c) < 1e-15) return CellValue.Err(CellErr.Err.DIV0); 1.0 / c }
                    "CSC" -> { val s = kotlin.math.sin(x); if (abs(s) < 1e-15) return CellValue.Err(CellErr.Err.DIV0); 1.0 / s }
                    "COT" -> { val t = kotlin.math.tan(x); if (abs(t) < 1e-15) return CellValue.Err(CellErr.Err.DIV0); 1.0 / t }
                    "SECH" -> 1.0 / kotlin.math.cosh(x)
                    "CSCH" -> { if (x == 0.0) return CellValue.Err(CellErr.Err.DIV0); 1.0 / kotlin.math.sinh(x) }
                    "COTH" -> { if (x == 0.0) return CellValue.Err(CellErr.Err.DIV0); kotlin.math.cosh(x) / kotlin.math.sinh(x) }
                    "ACOT" -> (Math.PI / 2.0) - kotlin.math.atan(x)
                    else -> { if (abs(x) <= 1.0) return CellValue.Err(CellErr.Err.NUM); 0.5 * ln((x + 1.0) / (x - 1.0)) }
                }
                if (!v.isFinite()) CellValue.Err(CellErr.Err.NUM) else CellValue.Num(normalizeNegZero(v))
            }
            "OCT2BIN", "OCT2HEX", "HEX2OCT", "BIN2OCT" -> {
                if (args.size !in 1..2) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(args.size)?.let { return it }
                val raw = evalExpr(args[0], env).displayText().trim().uppercase()
                val places = if (args.size == 2) (numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE)).toInt() else 0
                if (places < 0 || places > 10) return CellValue.Err(CellErr.Err.NUM)
                val (srcRadix, dstRadix) = when (name) {
                    "OCT2BIN" -> 8 to 2
                    "OCT2HEX" -> 8 to 16
                    "HEX2OCT" -> 16 to 8
                    else -> 2 to 8
                }
                val parsed = raw.toLongOrNull(srcRadix) ?: return CellValue.Err(CellErr.Err.NUM)
                val formatted = parsed.toString(dstRadix).uppercase()
                if (places > 0 && formatted.length > places) return CellValue.Err(CellErr.Err.NUM)
                CellValue.Text(if (places > 0) formatted.padStart(places, '0') else formatted)
            }
            "TEXTBEFORE", "TEXTAFTER" -> {
                if (args.size !in 2..3) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(args.size)?.let { return it }
                val text = evalExpr(args[0], env).displayText()
                val delim = evalExpr(args[1], env).displayText()
                val inst = if (args.size == 3) (numArg(2) ?: return CellValue.Err(CellErr.Err.VALUE)).toInt() else 1
                if (delim.isEmpty() || inst == 0) return CellValue.Err(CellErr.Err.VALUE)
                val idx = if (inst > 0) {
                    var pos = -1; var from = 0
                    repeat(inst) {
                        val hit = text.indexOf(delim, from)
                        if (hit < 0) return CellValue.Err(CellErr.Err.NA)
                        pos = hit; from = hit + delim.length
                    }
                    pos
                } else {
                    var pos = -1; var from = text.length
                    repeat(-inst) {
                        val hit = text.lastIndexOf(delim, from - 1)
                        if (hit < 0) return CellValue.Err(CellErr.Err.NA)
                        pos = hit; from = hit
                    }
                    pos
                }
                CellValue.Text(if (name == "TEXTBEFORE") text.substring(0, idx) else text.substring(idx + delim.length))
            }
            "ENCODEURL" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(1)?.let { return it }
                val raw = evalExpr(args[0], env).displayText()
                val sb = StringBuilder()
                for (b in raw.toByteArray(Charsets.UTF_8)) {
                    val u = b.toInt() and 0xFF
                    val ch = u.toChar()
                    if ((ch in 'A'..'Z') || (ch in 'a'..'z') || (ch in '0'..'9') || ch in "-_.~") sb.append(ch)
                    else sb.append('%').append(u.toString(16).uppercase().padStart(2, '0'))
                }
                CellValue.Text(sb.toString())
            }
            "UNICHAR" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(1)?.let { return it }
                val cp = (numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)).toInt()
                if (cp < 1 || cp > 0x10FFFF || cp in 0xD800..0xDFFF) return CellValue.Err(CellErr.Err.VALUE)
                CellValue.Text(String(Character.toChars(cp)))
            }
            "UNICODE" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(1)?.let { return it }
                val s = evalExpr(args[0], env).displayText()
                if (s.isEmpty()) return CellValue.Err(CellErr.Err.VALUE)
                CellValue.Num(s.codePointAt(0).toDouble())
            }
            else -> CellValue.Err(CellErr.Err.NAME)
        }
    }

    private data class ComplexVal(val re: Double, val im: Double, val suffix: Char = 'i')

    private fun parseComplexText(raw: String): ComplexVal? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val suf = if (s.endsWith('j')) 'j' else 'i'
        if (!s.endsWith('i') && !s.endsWith('j')) {
            val reOnly = s.toDoubleOrNull() ?: return null
            return ComplexVal(reOnly, 0.0, 'i')
        }
        val core = s.dropLast(1).trim()
        if (core.isEmpty() || core == "+") return ComplexVal(0.0, 1.0, suf)
        if (core == "-") return ComplexVal(0.0, -1.0, suf)
        // Find split index of '+' or '-' not at index 0 and not immediately after 'e'/'E'
        var splitAt = -1
        for (i in 1 until core.length) {
            val ch = core[i]
            val prev = core[i - 1]
            if ((ch == '+' || ch == '-') && prev != 'e' && prev != 'E') {
                splitAt = i
            }
        }
        if (splitAt < 0) {
            val imOnly = core.toDoubleOrNull() ?: return null
            return ComplexVal(0.0, imOnly, suf)
        }
        val rePart = core.substring(0, splitAt).trim().toDoubleOrNull() ?: return null
        val imStr = core.substring(splitAt).trim()
        val imPart = when (imStr) {
            "+" -> 1.0
            "-" -> -1.0
            else -> imStr.toDoubleOrNull() ?: return null
        }
        return ComplexVal(rePart, imPart, suf)
    }

    private fun formatComplex(re0: Double, im0: Double, suffix: Char = 'i'): String {
        val re = if (abs(re0) < 1e-12) 0.0 else normalizeNegZero(re0)
        val im = if (abs(im0) < 1e-12) 0.0 else normalizeNegZero(im0)
        fun fmtNum(d: Double): String {
            val r = round(d)
            return if (abs(d - r) < 1e-11 && abs(r) < 1e15) r.toLong().toString() else d.toString()
        }
        if (im == 0.0) return fmtNum(re)
        val imStr = when (im) {
            1.0 -> "$suffix"
            -1.0 -> "-$suffix"
            else -> "${fmtNum(im)}$suffix"
        }
        if (re == 0.0) return imStr
        return if (im > 0.0) "${fmtNum(re)}+$imStr" else "${fmtNum(re)}$imStr"
    }

    private fun incompleteBetaContinuedFraction(a: Double, b: Double, x: Double): Double {
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0
        val lnBeta = lnGammaLanczos(a) + lnGammaLanczos(b) - lnGammaLanczos(a + b)
        val front = exp(a * ln(x) + b * ln(1.0 - x) - lnBeta)
        if (x < (a + 1.0) / (a + b + 2.0)) {
            return front * betacfLentz(a, b, x) / a
        }
        return 1.0 - front * betacfLentz(b, a, 1.0 - x) / b
    }

    private fun betacfLentz(a: Double, b: Double, x: Double): Double {
        val fpMin = 1e-30
        val qab = a + b
        val qap = a + 1.0
        val qam = a - 1.0
        var c = 1.0
        var d = 1.0 - qab * x / qap
        if (abs(d) < fpMin) d = fpMin
        d = 1.0 / d
        var h = d
        for (m in 1..200) {
            val m2 = 2 * m
            var aa = m * (b - m) * x / ((qam + m2) * (a + m2))
            d = 1.0 + aa * d; if (abs(d) < fpMin) d = fpMin
            c = 1.0 + aa / c; if (abs(c) < fpMin) c = fpMin
            d = 1.0 / d; h *= d * c
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2))
            d = 1.0 + aa * d; if (abs(d) < fpMin) d = fpMin
            c = 1.0 + aa / c; if (abs(c) < fpMin) c = fpMin
            d = 1.0 / d
            val del = d * c
            h *= del
            if (abs(del - 1.0) < 1e-13) break
        }
        return h
    }

    private fun evalR53Grid(name: String, args: List<Expr>, env: Map<String, CellValue>): List<List<CellValue>> {
        fun errGrid(e: CellErr.Err = CellErr.Err.VALUE) = listOf(listOf(CellValue.Err(e)))
        fun flatNums(e: Expr): List<Double>? {
            val g = exprTo2DGrid(e, env) ?: return null
            val out = ArrayList<Double>()
            for (r in g) for (c in r) {
                if (c !is CellValue.Num) return null
                out.add(c.value)
            }
            return if (out.isEmpty()) null else out
        }
        return when (name) {
            "HANKEL" -> {
                if (args.size !in 1..2) return errGrid()
                val firstCol = flatNums(args[0]) ?: return errGrid()
                val lastRow = if (args.size == 2) (flatNums(args[1]) ?: return errGrid()) else List(firstCol.size) { if (it == 0) firstCol.last() else 0.0 }
                val n = firstCol.size; val m = lastRow.size
                List(n) { i ->
                    List(m) { j ->
                        val k = i + j
                        val v = if (k < n) firstCol[k] else lastRow[k - n + 1]
                        CellValue.Num(normalizeNegZero(v))
                    }
                }
            }
            "TOEPLITZ" -> {
                if (args.size !in 1..2) return errGrid()
                val col = flatNums(args[0]) ?: return errGrid()
                val row = if (args.size == 2) (flatNums(args[1]) ?: return errGrid()) else col
                List(col.size) { i ->
                    List(row.size) { j ->
                        val v = if (i >= j) col[i - j] else row[j - i]
                        CellValue.Num(normalizeNegZero(v))
                    }
                }
            }
            "VANDERMONDE" -> {
                if (args.size != 2) return errGrid()
                val xs = flatNums(args[0]) ?: return errGrid()
                val degV = evalScalarAsNum(evalExpr(args[1], env))
                if (degV !is CellValue.Num) return errGrid()
                val cols = degV.value.toInt()
                if (cols < 1 || cols > 32) return errGrid(CellErr.Err.NUM)
                xs.map { x -> List(cols) { p -> CellValue.Num(normalizeNegZero(x.pow(p.toDouble()))) } }
            }
            "KRONECKER" -> {
                if (args.size != 2) return errGrid()
                val a = exprTo2DGrid(args[0], env) ?: return errGrid()
                val b = exprTo2DGrid(args[1], env) ?: return errGrid()
                val ah = a.size; val aw = a.firstOrNull()?.size ?: 0
                val bh = b.size; val bw = b.firstOrNull()?.size ?: 0
                if (ah == 0 || aw == 0 || bh == 0 || bw == 0 || ah.toLong() * bh * aw * bw > MAX_RANGE_CELLS) return errGrid(CellErr.Err.NUM)
                List(ah * bh) { r ->
                    val ai = r / bh; val bi = r % bh
                    List(aw * bw) { c ->
                        val aj = c / bw; val bj = c % bw
                        val ca = a[ai][aj]; val cb = b[bi][bj]
                        if (ca is CellValue.Err) ca
                        else if (cb is CellValue.Err) cb
                        else if (ca is CellValue.Num && cb is CellValue.Num) CellValue.Num(normalizeNegZero(ca.value * cb.value))
                        else CellValue.Err(CellErr.Err.VALUE)
                    }
                }
            }
            "CONVOLVE1D" -> {
                if (args.size != 2) return errGrid()
                val sig = flatNums(args[0]) ?: return errGrid()
                val ker = flatNums(args[1]) ?: return errGrid()
                val outLen = sig.size + ker.size - 1
                List(outLen) { n ->
                    var acc = 0.0
                    for (k in ker.indices) {
                        val si = n - k
                        if (si in sig.indices) acc += sig[si] * ker[k]
                    }
                    listOf(CellValue.Num(normalizeNegZero(acc)))
                }
            }
            "PARETOGRID" -> {
                if (args.size != 1) return errGrid()
                val vals = flatNums(args[0]) ?: return errGrid()
                if (vals.any { it < 0.0 }) return errGrid(CellErr.Err.NUM)
                val total = vals.sum()
                if (total <= 0.0) return errGrid(CellErr.Err.DIV0)
                val sorted = vals.sortedDescending()
                var cum = 0.0
                sorted.map { v ->
                    cum += v
                    listOf(
                        CellValue.Num(normalizeNegZero(v)),
                        CellValue.Num(normalizeNegZero(v / total)),
                        CellValue.Num(normalizeNegZero(cum / total)),
                    )
                }
            }
            else -> errGrid(CellErr.Err.NAME)
        }
    }

    private fun evalR53ScalarFunction(name: String, args: List<Expr>, env: Map<String, CellValue>): CellValue {
        fun errIfAny(limit: Int = args.size): CellValue.Err? {
            for (i in 0 until limit.coerceAtMost(args.size)) {
                val v = evalExpr(args[i], env)
                if (v is CellValue.Err) return v
            }
            return null
        }
        fun numArg(idx: Int): Double? {
            val v = evalScalarAsNum(evalExpr(args[idx], env))
            return (v as? CellValue.Num)?.value
        }
        fun collectAllNums(): List<Double>? {
            val out = ArrayList<Double>()
            for (a in args) {
                val g = exprTo2DGrid(a, env)
                if (g != null) {
                    for (r in g) for (c in r) {
                        if (c is CellValue.Err) return null
                        if (c is CellValue.Num) out.add(c.value)
                    }
                } else {
                    val v = evalScalarAsNum(evalExpr(a, env))
                    if (v !is CellValue.Num) return null
                    out.add(v.value)
                }
            }
            return out
        }
        return when (name) {
            "COMPLEX" -> {
                if (args.size !in 2..3) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny()?.let { return it }
                val re = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                val im = numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE)
                val sufStr = if (args.size == 3) evalExpr(args[2], env).displayText().trim() else "i"
                if (sufStr != "i" && sufStr != "j") return CellValue.Err(CellErr.Err.VALUE)
                CellValue.Text(formatComplex(re, im, sufStr[0]))
            }
            "IMREAL", "IMAGINARY", "IMABS", "IMARGUMENT", "IMCONJUGATE", "IMSQRT", "IMEXP", "IMLN", "IMSIN", "IMCOS" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(1)?.let { return it }
                val z = parseComplexText(evalExpr(args[0], env).displayText()) ?: return CellValue.Err(CellErr.Err.NUM)
                when (name) {
                    "IMREAL" -> CellValue.Num(normalizeNegZero(z.re))
                    "IMAGINARY" -> CellValue.Num(normalizeNegZero(z.im))
                    "IMABS" -> CellValue.Num(normalizeNegZero(kotlin.math.hypot(z.re, z.im)))
                    "IMARGUMENT" -> {
                        if (z.re == 0.0 && z.im == 0.0) CellValue.Err(CellErr.Err.DIV0)
                        else CellValue.Num(normalizeNegZero(kotlin.math.atan2(z.im, z.re)))
                    }
                    "IMCONJUGATE" -> CellValue.Text(formatComplex(z.re, -z.im, z.suffix))
                    "IMSQRT" -> {
                        val r = kotlin.math.hypot(z.re, z.im)
                        val sr = sqrt((r + z.re) / 2.0)
                        val si = (if (z.im < 0.0) -1.0 else 1.0) * sqrt(((r - z.re).coerceAtLeast(0.0)) / 2.0)
                        CellValue.Text(formatComplex(sr, si, z.suffix))
                    }
                    "IMEXP" -> {
                        val er = exp(z.re)
                        CellValue.Text(formatComplex(er * kotlin.math.cos(z.im), er * kotlin.math.sin(z.im), z.suffix))
                    }
                    "IMLN" -> {
                        val r = kotlin.math.hypot(z.re, z.im)
                        if (r == 0.0) CellValue.Err(CellErr.Err.NUM)
                        else CellValue.Text(formatComplex(ln(r), kotlin.math.atan2(z.im, z.re), z.suffix))
                    }
                    "IMSIN" -> CellValue.Text(formatComplex(kotlin.math.sin(z.re) * kotlin.math.cosh(z.im), kotlin.math.cos(z.re) * kotlin.math.sinh(z.im), z.suffix))
                    else -> CellValue.Text(formatComplex(kotlin.math.cos(z.re) * kotlin.math.cosh(z.im), -kotlin.math.sin(z.re) * kotlin.math.sinh(z.im), z.suffix))
                }
            }
            "IMSUM", "IMPRODUCT" -> {
                if (args.isEmpty()) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny()?.let { return it }
                var accRe = if (name == "IMSUM") 0.0 else 1.0
                var accIm = 0.0
                var suf = 'i'
                for (a in args) {
                    val cells = exprTo2DGrid(a, env)?.flatten() ?: listOf(evalExpr(a, env))
                    for (c in cells) {
                        if (c is CellValue.Err) return c
                        val z = parseComplexText(c.displayText()) ?: return CellValue.Err(CellErr.Err.NUM)
                        if (z.suffix == 'j') suf = 'j'
                        if (name == "IMSUM") {
                            accRe += z.re; accIm += z.im
                        } else {
                            val nr = accRe * z.re - accIm * z.im
                            val ni = accRe * z.im + accIm * z.re
                            accRe = nr; accIm = ni
                        }
                    }
                }
                CellValue.Text(formatComplex(accRe, accIm, suf))
            }
            "IMSUB", "IMDIV", "IMPOWER" -> {
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(2)?.let { return it }
                val a = parseComplexText(evalExpr(args[0], env).displayText()) ?: return CellValue.Err(CellErr.Err.NUM)
                if (name == "IMPOWER") {
                    val p = numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE)
                    val r = kotlin.math.hypot(a.re, a.im)
                    if (r == 0.0 && p <= 0.0) return CellValue.Err(CellErr.Err.NUM)
                    val rp = r.pow(p)
                    val theta = kotlin.math.atan2(a.im, a.re) * p
                    return CellValue.Text(formatComplex(rp * kotlin.math.cos(theta), rp * kotlin.math.sin(theta), a.suffix))
                }
                val b = parseComplexText(evalExpr(args[1], env).displayText()) ?: return CellValue.Err(CellErr.Err.NUM)
                val suf = if (a.suffix == 'j' || b.suffix == 'j') 'j' else 'i'
                if (name == "IMSUB") CellValue.Text(formatComplex(a.re - b.re, a.im - b.im, suf))
                else {
                    val den = b.re * b.re + b.im * b.im
                    if (den == 0.0) CellValue.Err(CellErr.Err.NUM)
                    else CellValue.Text(formatComplex((a.re * b.re + a.im * b.im) / den, (a.im * b.re - a.re * b.im) / den, suf))
                }
            }
            "COUPDAYS", "COUPDAYBS", "COUPDAYSNC", "COUPNUM", "COUPPCD", "COUPNCD" -> {
                if (args.size !in 3..4) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny()?.let { return it }
                val settle = numArg(0)?.toLong() ?: return CellValue.Err(CellErr.Err.VALUE)
                val mature = numArg(1)?.toLong() ?: return CellValue.Err(CellErr.Err.VALUE)
                val freq = numArg(2)?.toInt() ?: return CellValue.Err(CellErr.Err.VALUE)
                val basis = if (args.size == 4) (numArg(3)?.toInt() ?: return CellValue.Err(CellErr.Err.VALUE)) else 0
                if (settle >= mature || freq !in setOf(1, 2, 4) || basis !in 0..4) return CellValue.Err(CellErr.Err.NUM)
                val epoch = LocalDate.of(1899, 12, 30)
                val sDate = epoch.plusDays(settle)
                val mDate = epoch.plusDays(mature)
                val stepMonths = (12 / freq).toLong()
                var k = 0L
                var ncd: LocalDate
                while (k <= 10_000L) {
                    val prev = mDate.minusMonths(stepMonths * (k + 1))
                    if (!prev.isAfter(sDate)) {
                        val pcd = prev
                        ncd = mDate.minusMonths(stepMonths * k)
                        val coupNum = (k + 1).toDouble()
                        val daysInPeriod = if (basis == 0 || basis == 4) (360.0 / freq) else ChronoUnit.DAYS.between(pcd, ncd).toDouble()
                        val dayBs = if (basis == 0 || basis == 4) {
                            ((sDate.year - pcd.year) * 360 + (sDate.monthValue - pcd.monthValue) * 30 + (sDate.dayOfMonth.coerceAtMost(30) - pcd.dayOfMonth.coerceAtMost(30))).toDouble().coerceAtLeast(0.0)
                        } else ChronoUnit.DAYS.between(pcd, sDate).toDouble()
                        val dayNc = (daysInPeriod - dayBs).coerceAtLeast(0.0)
                        return when (name) {
                            "COUPDAYS" -> CellValue.Num(daysInPeriod)
                            "COUPDAYBS" -> CellValue.Num(dayBs)
                            "COUPDAYSNC" -> CellValue.Num(dayNc)
                            "COUPNUM" -> CellValue.Num(coupNum)
                            "COUPPCD" -> CellValue.Num(ChronoUnit.DAYS.between(epoch, pcd).toDouble())
                            else -> CellValue.Num(ChronoUnit.DAYS.between(epoch, ncd).toDouble())
                        }
                    }
                    k++
                }
                CellValue.Err(CellErr.Err.NUM)
            }
            "DURATION", "MDURATION" -> {
                if (args.size !in 5..6) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny()?.let { return it }
                val settle = numArg(0)?.toLong() ?: return CellValue.Err(CellErr.Err.VALUE)
                val mature = numArg(1)?.toLong() ?: return CellValue.Err(CellErr.Err.VALUE)
                val coupon = numArg(2) ?: return CellValue.Err(CellErr.Err.VALUE)
                val yld = numArg(3) ?: return CellValue.Err(CellErr.Err.VALUE)
                val freq = numArg(4)?.toInt() ?: return CellValue.Err(CellErr.Err.VALUE)
                if (settle >= mature || coupon < 0.0 || yld < 0.0 || freq !in setOf(1, 2, 4)) return CellValue.Err(CellErr.Err.NUM)
                val years = (mature - settle).toDouble() / 365.0
                val n = kotlin.math.max(1, kotlin.math.ceil(years * freq).toInt())
                val c = 100.0 * coupon / freq
                val r = yld / freq
                var pvSum = 0.0
                var weightedSum = 0.0
                for (i in 1..n) {
                    val tYears = i.toDouble() / freq
                    val cf = if (i == n) c + 100.0 else c
                    val pv = cf / (1.0 + r).pow(i.toDouble())
                    pvSum += pv
                    weightedSum += tYears * pv
                }
                val macDur = weightedSum / pvSum
                CellValue.Num(normalizeNegZero(if (name == "DURATION") macDur else macDur / (1.0 + r)))
            }
            "BETA.DIST" -> {
                if (args.size !in 4..6) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny()?.let { return it }
                val x = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                val alpha = numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE)
                val beta = numArg(2) ?: return CellValue.Err(CellErr.Err.VALUE)
                val cum = evalExpr(args[3], env).let { (it as? CellValue.Bool)?.value ?: ((it as? CellValue.Num)?.value != 0.0) }
                val aBound = if (args.size >= 5) (numArg(4) ?: return CellValue.Err(CellErr.Err.VALUE)) else 0.0
                val bBound = if (args.size == 6) (numArg(5) ?: return CellValue.Err(CellErr.Err.VALUE)) else 1.0
                if (alpha <= 0.0 || beta <= 0.0 || x < aBound || x > bBound || aBound >= bBound) return CellValue.Err(CellErr.Err.NUM)
                val u = (x - aBound) / (bBound - aBound)
                val res = if (cum) incompleteBetaContinuedFraction(alpha, beta, u) else {
                    if (u <= 0.0 || u >= 1.0) 0.0
                    else exp((alpha - 1.0) * ln(u) + (beta - 1.0) * ln(1.0 - u) - (lnGammaLanczos(alpha) + lnGammaLanczos(beta) - lnGammaLanczos(alpha + beta))) / (bBound - aBound)
                }
                CellValue.Num(normalizeNegZero(res))
            }
            "TDIST.2T", "TDIST.RT" -> {
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(2)?.let { return it }
                val x = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                val df = floor(numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE))
                if (df < 1.0 || (name == "TDIST.2T" && x < 0.0)) return CellValue.Err(CellErr.Err.NUM)
                val ib = incompleteBetaContinuedFraction(df / 2.0, 0.5, df / (df + x * x))
                val p = if (name == "TDIST.2T") ib else if (x >= 0.0) 0.5 * ib else 1.0 - 0.5 * ib
                CellValue.Num(normalizeNegZero(p))
            }
            "F.DIST.RT" -> {
                if (args.size != 3) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(3)?.let { return it }
                val x = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                val d1 = floor(numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE))
                val d2 = floor(numArg(2) ?: return CellValue.Err(CellErr.Err.VALUE))
                if (x < 0.0 || d1 < 1.0 || d2 < 1.0) return CellValue.Err(CellErr.Err.NUM)
                val u = (d1 * x) / (d1 * x + d2)
                CellValue.Num(normalizeNegZero(1.0 - incompleteBetaContinuedFraction(d1 / 2.0, d2 / 2.0, u)))
            }
            "FISHER" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(1)?.let { return it }
                val x = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                if (x <= -1.0 || x >= 1.0) return CellValue.Err(CellErr.Err.NUM)
                CellValue.Num(normalizeNegZero(0.5 * ln((1.0 + x) / (1.0 - x))))
            }
            "FISHERINV" -> {
                if (args.size != 1) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(1)?.let { return it }
                val y = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                CellValue.Num(normalizeNegZero(kotlin.math.tanh(y)))
            }
            "STANDARDIZE" -> {
                if (args.size != 3) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(3)?.let { return it }
                val x = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                val mean = numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE)
                val sd = numArg(2) ?: return CellValue.Err(CellErr.Err.VALUE)
                if (sd <= 0.0) return CellValue.Err(CellErr.Err.NUM)
                CellValue.Num(normalizeNegZero((x - mean) / sd))
            }
            "CONFIDENCE.T" -> {
                if (args.size != 3) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(3)?.let { return it }
                val alpha = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                val sd = numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE)
                val n = floor(numArg(2) ?: return CellValue.Err(CellErr.Err.VALUE))
                if (alpha <= 0.0 || alpha >= 1.0 || sd <= 0.0 || n < 1.0) return CellValue.Err(CellErr.Err.NUM)
                if (n == 1.0) return CellValue.Err(CellErr.Err.DIV0)
                val df = n - 1.0
                // Binary search t > 0 such that TDIST.2T(t, df) == alpha
                var lo = 0.0; var hi = 1e6
                repeat(80) {
                    val mid = 0.5 * (lo + hi)
                    val tail2 = incompleteBetaContinuedFraction(df / 2.0, 0.5, df / (df + mid * mid))
                    if (tail2 > alpha) lo = mid else hi = mid
                }
                val tCrit = 0.5 * (lo + hi)
                CellValue.Num(normalizeNegZero(tCrit * sd / sqrt(n)))
            }
            "HARMEAN", "DEVSQ", "AVEDEV", "MULTINOMIAL" -> {
                if (args.isEmpty()) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny()?.let { return it }
                val nums = collectAllNums() ?: return CellValue.Err(CellErr.Err.VALUE)
                if (nums.isEmpty()) return CellValue.Err(CellErr.Err.NUM)
                when (name) {
                    "HARMEAN" -> {
                        if (nums.any { it <= 0.0 }) return CellValue.Err(CellErr.Err.NUM)
                        CellValue.Num(normalizeNegZero(nums.size.toDouble() / nums.sumOf { 1.0 / it }))
                    }
                    "DEVSQ" -> {
                        val m = nums.average()
                        CellValue.Num(normalizeNegZero(nums.sumOf { (it - m) * (it - m) }))
                    }
                    "AVEDEV" -> {
                        val m = nums.average()
                        CellValue.Num(normalizeNegZero(nums.sumOf { abs(it - m) } / nums.size.toDouble()))
                    }
                    else -> {
                        if (nums.any { it < 0.0 }) return CellValue.Err(CellErr.Err.NUM)
                        val ints = nums.map { floor(it).toInt() }
                        val sumN = ints.sum()
                        var lnRes = lnGammaLanczos(sumN + 1.0)
                        for (v in ints) lnRes -= lnGammaLanczos(v + 1.0)
                        CellValue.Num(normalizeNegZero(round(exp(lnRes))))
                    }
                }
            }
            "TRIMMEAN" -> {
                if (args.size != 2) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(2)?.let { return it }
                val g = exprTo2DGrid(args[0], env) ?: return CellValue.Err(CellErr.Err.VALUE)
                val pct = numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE)
                if (pct < 0.0 || pct >= 1.0) return CellValue.Err(CellErr.Err.NUM)
                val vals = g.flatten().mapNotNull { (it as? CellValue.Num)?.value }.sorted()
                if (vals.isEmpty()) return CellValue.Err(CellErr.Err.NUM)
                val dropPerSide = floor(vals.size * pct / 2.0).toInt()
                val kept = vals.subList(dropPerSide, vals.size - dropPerSide)
                if (kept.isEmpty()) return CellValue.Err(CellErr.Err.NUM)
                CellValue.Num(normalizeNegZero(kept.average()))
            }
            "SERIESSUM" -> {
                if (args.size != 4) return CellValue.Err(CellErr.Err.VALUE)
                errIfAny(4)?.let { return it }
                val x = numArg(0) ?: return CellValue.Err(CellErr.Err.VALUE)
                val n0 = numArg(1) ?: return CellValue.Err(CellErr.Err.VALUE)
                val step = numArg(2) ?: return CellValue.Err(CellErr.Err.VALUE)
                val coeffs = (exprTo2DGrid(args[3], env)?.flatten() ?: listOf(evalExpr(args[3], env))).map {
                    (it as? CellValue.Num)?.value ?: return CellValue.Err(CellErr.Err.VALUE)
                }
                var acc = 0.0
                for ((i, a) in coeffs.withIndex()) {
                    acc += a * x.pow(n0 + i * step)
                }
                CellValue.Num(normalizeNegZero(acc))
            }
            else -> CellValue.Err(CellErr.Err.NAME)
        }
    }
}


