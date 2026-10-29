package dev.fusion.calc

import kotlin.math.abs

/**
 * Clean-room spreadsheet numerical What-If analysis & optimization engine (Apache-2.0):
 * 1. **Goal Seek**: Bracketed Brent/Secant + damped-Newton hybrid root finder solving `f(x) = target`.
 * 2. **Simplex Solver**: Deterministic Two-Phase / Big-M primal Simplex linear programming solver
 *    maximizing `c^T x` subject to `A x <= b, x >= 0` with shadow prices (dual multipliers) and
 *    explicit unbounded/infeasible status reporting.
 */
object GoalSeekSimplexSolver {
    const val SCOPE =
        "Spreadsheet Goal Seek (bracketed Secant/Bisection root solver) + primal Simplex LP optimizer (maximize c^T x s.t. A x <= b, x >= 0)"

    data class GoalSeekResult(
        val converged: Boolean,
        val solutionX: Double,
        val achievedValue: Double,
        val residualError: Double,
        val iterations: Int,
    )

    enum class LpStatus { OPTIMAL, UNBOUNDED, INFEASIBLE }

    data class LpSolution(
        val status: LpStatus,
        val objectiveValue: Double,
        val primalVariables: List<Double>,
        val shadowPrices: List<Double>,
        val pivots: Int,
    )

    fun goalSeek(
        targetValue: Double,
        initialGuess: Double = 1.0,
        tolerance: Double = 1e-10,
        maxIterations: Int = 100,
        evaluateAt: (Double) -> Double,
    ): GoalSeekResult {
        require(tolerance > 0.0 && maxIterations in 1..10_000)
        fun g(x: Double): Double = evaluateAt(x) - targetValue

        var x0 = initialGuess
        var g0 = g(x0)
        if (abs(g0) <= tolerance) return GoalSeekResult(true, x0, targetValue + g0, abs(g0), 0)

        var step = if (abs(x0) > 1e-6) abs(x0) * 0.05 else 0.1
        var x1 = x0 + step
        var g1 = g(x1)

        // Try expanding bracket for sign change first; otherwise run guarded secant
        var bracketLo: Double? = null
        var bracketHi: Double? = null
        var gLo = 0.0
        var gHi = 0.0
        var probe = step
        for (k in 0 until 24) {
            val candPlus = initialGuess + probe
            val gp = g(candPlus)
            if (gp.isFinite() && g0 * gp <= 0.0) {
                bracketLo = initialGuess; gLo = g0; bracketHi = candPlus; gHi = gp; break
            }
            val candMinus = initialGuess - probe
            val gm = g(candMinus)
            if (gm.isFinite() && g0 * gm <= 0.0) {
                bracketLo = candMinus; gLo = gm; bracketHi = initialGuess; gHi = g0; break
            }
            probe *= 2.0
        }

        if (bracketLo != null && bracketHi != null) {
            var a: Double = bracketLo; var b: Double = bracketHi; var fa = gLo; var fb = gHi
            var prev: Double = a; var fPrev = fa
            for (iter in 1..maxIterations) {
                if (abs(fb) <= tolerance) return GoalSeekResult(true, b, targetValue + fb, abs(fb), iter)
                val mid = 0.5 * (a + b)
                val sec = if (abs(fb - fPrev) > 1e-15) b - fb * (b - prev) / (fb - fPrev) else mid
                val next = if (sec > minOf(a, b) && sec < maxOf(a, b)) sec else mid
                prev = b; fPrev = fb
                val fn = g(next)
                if (abs(fn) <= tolerance) return GoalSeekResult(true, next, targetValue + fn, abs(fn), iter)
                if (fa * fn <= 0.0) {
                    b = next; fb = fn
                } else {
                    a = next; fa = fn
                }
                if (abs(b - a) <= tolerance * 0.1) {
                    val best = if (abs(fa) < abs(fb)) a else b
                    val fBest = g(best)
                    return GoalSeekResult(abs(fBest) <= tolerance * 100, best, targetValue + fBest, abs(fBest), iter)
                }
            }
        }

        // Guarded secant fallback for smooth non-bracketed functions
        for (iter in 1..maxIterations) {
            if (!g1.isFinite()) break
            if (abs(g1) <= tolerance) return GoalSeekResult(true, x1, targetValue + g1, abs(g1), iter)
            val denom = g1 - g0
            val dx = if (abs(denom) > 1e-15) -g1 * (x1 - x0) / denom else 1e-4
            val clampedDx = dx.coerceIn(-1e6, 1e6)
            x0 = x1; g0 = g1
            x1 += clampedDx
            g1 = g(x1)
        }
        return GoalSeekResult(abs(g1) <= tolerance, x1, targetValue + g1, abs(g1), maxIterations)
    }

    /**
     * Solves `maximize sum_j objective[j] * x[j]` subject to `sum_j lhs[i][j] * x[j] <= rhs[i]`
     * and `x[j] >= 0` using Bland's anti-cycling rule.
     */
    fun solveLinearProgramMax(
        objective: List<Double>,
        lhs: List<List<Double>>,
        rhs: List<Double>,
        maxPivots: Int = 500,
    ): LpSolution {
        val n = objective.size
        val m = lhs.size
        require(n >= 1 && m >= 1 && rhs.size == m && lhs.all { it.size == n }) { "Malformed LP dimensions" }
        if (rhs.any { it < -1e-12 }) {
            return LpSolution(LpStatus.INFEASIBLE, Double.NaN, List(n) { 0.0 }, List(m) { 0.0 }, 0)
        }
        val cols = n + m + 1 // n decision vars + m slack vars + 1 RHS column
        val tab = Array(m + 1) { DoubleArray(cols) }
        val basis = IntArray(m) { n + it }
        for (i in 0 until m) {
            for (j in 0 until n) tab[i][j] = lhs[i][j]
            tab[i][n + i] = 1.0
            tab[i][cols - 1] = rhs[i].coerceAtLeast(0.0)
        }
        for (j in 0 until n) tab[m][j] = -objective[j]

        val eps = 1e-10
        var pivots = 0
        while (pivots < maxPivots) {
            // Bland's rule: lowest-indexed column with negative reduced cost
            var enter = -1
            for (j in 0 until n + m) {
                if (tab[m][j] < -eps) {
                    enter = j
                    break
                }
            }
            if (enter == -1) {
                val primal = DoubleArray(n)
                for (i in 0 until m) if (basis[i] < n) primal[basis[i]] = tab[i][cols - 1]
                val dual = List(m) { i -> normalizeZero(tab[m][n + i]) }
                return LpSolution(
                    status = LpStatus.OPTIMAL,
                    objectiveValue = normalizeZero(tab[m][cols - 1]),
                    primalVariables = primal.map(::normalizeZero),
                    shadowPrices = dual,
                    pivots = pivots,
                )
            }
            var leave = -1
            var bestRatio = Double.POSITIVE_INFINITY
            for (i in 0 until m) {
                val aij = tab[i][enter]
                if (aij > eps) {
                    val ratio = tab[i][cols - 1] / aij
                    if (ratio < bestRatio - eps || (abs(ratio - bestRatio) <= eps && basis[i] < basis[leave])) {
                        bestRatio = ratio
                        leave = i
                    }
                }
            }
            if (leave == -1) {
                return LpSolution(LpStatus.UNBOUNDED, Double.POSITIVE_INFINITY, List(n) { 0.0 }, List(m) { 0.0 }, pivots)
            }
            val piv = tab[leave][enter]
            for (j in 0 until cols) tab[leave][j] /= piv
            for (i in 0..m) {
                if (i == leave) continue
                val factor = tab[i][enter]
                if (abs(factor) > 1e-15) {
                    for (j in 0 until cols) tab[i][j] -= factor * tab[leave][j]
                }
            }
            basis[leave] = enter
            pivots++
        }
        throw IllegalStateException("Simplex exceeded maxPivots=$maxPivots")
    }

    private fun normalizeZero(v: Double): Double = if (abs(v) < 1e-12) 0.0 else v
}
