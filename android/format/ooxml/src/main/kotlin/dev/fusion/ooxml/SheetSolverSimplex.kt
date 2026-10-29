// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import kotlin.math.abs

/**
 * Clean-room Spreadsheet What-If Optimization Engine:
 * 1. Two-Phase Tableau Simplex Linear Programming solver (`maximize c^T x` subject to `A x <= b, x >= 0`
 *    or mixed `<= / >= / =` constraints via Phase-I artificial variables with Bland's anti-cycling tie-break!).
 * 2. Bisection/Secant hybrid univariate `goalSeek(f(x) == target)` solver.
 */
object SheetSolverSimplex {
    const val EPS = 1e-9
    const val MAX_PIVOTS = 2_048

    enum class Relation { LE, GE, EQ }
    enum class Status { OPTIMAL, INFEASIBLE, UNBOUNDED, ITERATION_LIMIT }

    data class LinearConstraint(
        val coeffs: DoubleArray,
        val relation: Relation,
        val rhs: Double,
    )

    data class LpSolution(
        val status: Status,
        val objectiveValue: Double,
        val primalVariables: List<Double>,
        val pivotsUsed: Int,
    )

    data class GoalSeekResult(
        val converged: Boolean,
        val rootX: Double,
        val achievedValue: Double,
        val iterations: Int,
    )

    fun solveLinearProgram(
        objective: DoubleArray,
        maximize: Boolean = true,
        constraints: List<LinearConstraint>,
    ): LpSolution {
        val n = objective.size
        require(n in 1..128 && constraints.size in 1..256) { "LP dimensions out of bounds" }
        require(objective.all { it.isFinite() }) { "non-finite objective" }
        val m = constraints.size
        for (c in constraints) {
            require(c.coeffs.size == n && c.coeffs.all { it.isFinite() } && c.rhs.isFinite()) {
                "invalid LP constraint"
            }
        }

        // Normalize RHS >= 0 by flipping row sign if rhs < 0
        val normA = Array(m) { DoubleArray(n) }
        val normRel = Array(m) { Relation.LE }
        val normB = DoubleArray(m)
        var slackCount = 0
        var artCount = 0
        for (i in 0 until m) {
            val c = constraints[i]
            if (c.rhs < -EPS) {
                for (j in 0 until n) normA[i][j] = -c.coeffs[j]
                normB[i] = -c.rhs
                normRel[i] = when (c.relation) {
                    Relation.LE -> Relation.GE
                    Relation.GE -> Relation.LE
                    Relation.EQ -> Relation.EQ
                }
            } else {
                for (j in 0 until n) normA[i][j] = c.coeffs[j]
                normB[i] = c.rhs.coerceAtLeast(0.0)
                normRel[i] = c.relation
            }
            when (normRel[i]) {
                Relation.LE -> slackCount++
                Relation.GE -> { slackCount++; artCount++ }
                Relation.EQ -> artCount++
            }
        }

        val totalVars = n + slackCount + artCount
        // Tableau has m constraint rows + 1 objective row; last col = totalVars is RHS
        val tab = Array(m + 1) { DoubleArray(totalVars + 1) }
        val basis = IntArray(m)
        var nextSlack = n
        var nextArt = n + slackCount
        val artCols = HashSet<Int>()

        for (i in 0 until m) {
            for (j in 0 until n) tab[i][j] = normA[i][j]
            tab[i][totalVars] = normB[i]
            when (normRel[i]) {
                Relation.LE -> {
                    tab[i][nextSlack] = 1.0
                    basis[i] = nextSlack++
                }
                Relation.GE -> {
                    tab[i][nextSlack++] = -1.0
                    tab[i][nextArt] = 1.0
                    artCols.add(nextArt)
                    basis[i] = nextArt++
                }
                Relation.EQ -> {
                    tab[i][nextArt] = 1.0
                    artCols.add(nextArt)
                    basis[i] = nextArt++
                }
            }
        }

        var pivots = 0

        fun runSimplexPhase(colLimit: Int, skipCols: Set<Int>): Status {
            while (pivots < MAX_PIVOTS) {
                // Bland's rule: smallest index column with strictly negative reduced cost in tab[m]
                var enter = -1
                for (j in 0 until colLimit) {
                    if (j in skipCols) continue
                    if (tab[m][j] < -EPS) {
                        enter = j
                        break
                    }
                }
                if (enter < 0) return Status.OPTIMAL

                var leave = -1
                var minRatio = Double.POSITIVE_INFINITY
                for (i in 0 until m) {
                    val aij = tab[i][enter]
                    if (aij > EPS) {
                        val ratio = tab[i][totalVars] / aij
                        if (ratio < minRatio - EPS || (abs(ratio - minRatio) <= EPS && basis[i] < (if (leave >= 0) basis[leave] else Int.MAX_VALUE))) {
                            minRatio = ratio
                            leave = i
                        }
                    }
                }
                if (leave < 0) return Status.UNBOUNDED

                pivot(tab, m, totalVars, leave, enter)
                basis[leave] = enter
                pivots++
            }
            return Status.ITERATION_LIMIT
        }

        // Phase I: minimize sum of artificial variables
        if (artCount > 0) {
            for (j in 0..totalVars) tab[m][j] = 0.0
            for (ac in artCols) tab[m][ac] = 1.0
            for (i in 0 until m) {
                if (basis[i] in artCols) {
                    for (j in 0..totalVars) tab[m][j] -= tab[i][j]
                }
            }
            val p1 = runSimplexPhase(totalVars, emptySet())
            if (p1 != Status.OPTIMAL) return LpSolution(p1, Double.NaN, emptyList(), pivots)
            if (abs(tab[m][totalVars]) > 1e-6) {
                return LpSolution(Status.INFEASIBLE, Double.NaN, emptyList(), pivots)
            }
            // Pivot out any zero-valued artificial variable remaining in basis
            for (i in 0 until m) {
                if (basis[i] in artCols) {
                    val nonArt = (0 until (n + slackCount)).firstOrNull { abs(tab[i][it]) > EPS }
                    if (nonArt != null) {
                        pivot(tab, m, totalVars, i, nonArt)
                        basis[i] = nonArt
                        pivots++
                    }
                }
            }
        }

        // Phase II: maximize (+/- objective)
        for (j in 0..totalVars) tab[m][j] = 0.0
        val sign = if (maximize) -1.0 else 1.0
        for (j in 0 until n) tab[m][j] = sign * objective[j]
        for (i in 0 until m) {
            val bv = basis[i]
            val coeff = tab[m][bv]
            if (abs(coeff) > EPS) {
                for (j in 0..totalVars) tab[m][j] -= coeff * tab[i][j]
            }
        }

        val p2 = runSimplexPhase(n + slackCount, artCols)
        if (p2 != Status.OPTIMAL) return LpSolution(p2, Double.NaN, emptyList(), pivots)

        val x = DoubleArray(n)
        for (i in 0 until m) {
            if (basis[i] in 0 until n) x[basis[i]] = tab[i][totalVars].coerceAtLeast(0.0)
        }
        val rawObj = (0 until n).sumOf { objective[it] * x[it] }
        return LpSolution(Status.OPTIMAL, rawObj, x.toList(), pivots)
    }

    private fun pivot(tab: Array<DoubleArray>, m: Int, rhsCol: Int, row: Int, col: Int) {
        val piv = tab[row][col]
        for (j in 0..rhsCol) tab[row][j] /= piv
        for (i in 0..m) {
            if (i == row) continue
            val factor = tab[i][col]
            if (abs(factor) > 1e-15) {
                for (j in 0..rhsCol) tab[i][j] -= factor * tab[row][j]
            }
        }
    }

    fun goalSeek(
        target: Double,
        initialGuess: Double = 0.0,
        tolerance: Double = 1e-8,
        maxIterations: Int = 100,
        fn: (Double) -> Double,
    ): GoalSeekResult {
        require(target.isFinite() && initialGuess.isFinite() && tolerance > 0.0) { "invalid goalSeek params" }
        var x0 = initialGuess
        var f0 = fn(x0) - target
        if (abs(f0) <= tolerance) return GoalSeekResult(true, x0, f0 + target, 0)

        var x1 = if (abs(x0) > 1e-6) x0 * 1.05 else 1.0
        var f1 = fn(x1) - target
        for (iter in 1..maxIterations) {
            if (abs(f1) <= tolerance) return GoalSeekResult(true, x1, f1 + target, iter)
            val denom = f1 - f0
            val nextX = if (abs(denom) > 1e-14) {
                x1 - f1 * (x1 - x0) / denom
            } else {
                x1 + 0.5
            }
            if (!nextX.isFinite()) return GoalSeekResult(false, x1, f1 + target, iter)
            x0 = x1; f0 = f1
            x1 = nextX; f1 = fn(x1) - target
        }
        return GoalSeekResult(abs(f1) <= tolerance, x1, f1 + target, maxIterations)
    }
}
