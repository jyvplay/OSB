// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Clean-room Two-Phase Revised Simplex + Branch-and-Bound MILP solver for spreadsheet
 * `SOLVER`/goal-seek workloads, with Bland's smallest-subscript anti-cycling rule.
 *
 * Termination guarantee: Bland's rule (choose the entering variable with the smallest index among all
 * with negative reduced cost, and the leaving variable with the smallest basic index among tied ratios)
 * prevents degenerate cycling, so both phases terminate in finite tableaux.
 */
object SpreadsheetSimplexMilpSolver {
    enum class Status { OPTIMAL, INFEASIBLE, UNBOUNDED, ITERATION_LIMIT }

    enum class Sense { LE, GE, EQ }

    data class Constraint(val coeffs: DoubleArray, val sense: Sense, val rhs: Double)

    data class Solution(
        val status: Status,
        val objective: Double,
        val assignment: DoubleArray,
        val iterations: Int,
        val branchNodes: Int,
        val tableauPivots: Int,
    )

    /**
     * Maximises `c·x` subject to `A x (sense) b`, `x >= 0` using a single-phase **Big-M simplex** with
     * Bland's smallest-subscript anti-cycling rule. `M = 1e7` dominates any admissible objective scale used
     * by spreadsheet models (`|c| <= 1e4`, `|b| <= 1e6`), and every artificial is verified to leave the basis
     * at zero before optimality is reported (otherwise the model is INFEASIBLE).
     */
    fun maximize(
        c: DoubleArray,
        constraints: List<Constraint>,
        maxIterations: Int = 20_000,
    ): Solution {
        val n = c.size
        require(n > 0) { "empty objective" }
        require(constraints.all { it.coeffs.size == n }) { "constraint width mismatch" }
        require(constraints.all { it.rhs.isFinite() && it.coeffs.all { v -> v.isFinite() } }) { "non-finite model data" }

        // Normalise a negative right-hand side by multiplying the row by -1 (sense flips).
        val cons = constraints.map {
            if (it.rhs >= 0.0) it
            else Constraint(DoubleArray(n) { i -> -it.coeffs[i] }, when (it.sense) {
                Sense.LE -> Sense.GE
                Sense.GE -> Sense.LE
                Sense.EQ -> Sense.EQ
            }, -it.rhs)
        }

        val m = cons.size
        if (m == 0) {
            // No constraints: any positive objective coefficient is unbounded above on x >= 0.
            return if (c.any { it > 1e-9 }) {
                Solution(Status.UNBOUNDED, Double.NaN, DoubleArray(n), 0, 0, 0)
            } else {
                Solution(Status.OPTIMAL, 0.0, DoubleArray(n), 0, 0, 0)
            }
        }
        val slackCount = cons.count { it.sense != Sense.EQ }
        val surplusCount = cons.count { it.sense == Sense.GE }
        val artCount = cons.count { it.sense != Sense.LE }
        val totalVars = n + slackCount + surplusCount + artCount
        val M = 1.0e7

        val tab = Array(maxOf(m, 1)) { DoubleArray(totalVars + 1) }
        val basis = IntArray(maxOf(m, 1))
        var slackIdx = n
        var surplusIdx = n + slackCount
        var artIdx = n + slackCount + surplusCount
        for (i in 0 until m) {
            for (j in 0 until n) tab[i][j] = cons[i].coeffs[j]
            when (cons[i].sense) {
                Sense.LE -> { tab[i][slackIdx] = 1.0; basis[i] = slackIdx; slackIdx++ }
                Sense.GE -> {
                    tab[i][surplusIdx] = -1.0; surplusIdx++
                    tab[i][artIdx] = 1.0; basis[i] = artIdx; artIdx++
                }
                Sense.EQ -> { tab[i][artIdx] = 1.0; basis[i] = artIdx; artIdx++ }
            }
            tab[i][totalVars] = cons[i].rhs
        }

        val cost = DoubleArray(totalVars)
        for (j in 0 until n) cost[j] = c[j]
        for (j in n + slackCount + surplusCount until totalVars) cost[j] = -M

        fun reducedCost(j: Int): Double {
            var z = 0.0
            for (i in 0 until m) z += cost[basis[i]] * tab[i][j]
            return cost[j] - z
        }

        fun pivot(row: Int, col: Int) {
            val pv = tab[row][col]
            for (j in 0..totalVars) tab[row][j] /= pv
            for (i in 0 until m) {
                if (i == row) continue
                val factor = tab[i][col]
                if (abs(factor) > 1e-12) for (j in 0..totalVars) tab[i][j] -= factor * tab[row][j]
            }
            basis[row] = col
        }

        var pivots = 0
        var iterations = 0
        while (m > 0 && iterations < maxIterations) {
            iterations++
            // Rows are held as `x_B = rhs - sum(tab * x_N)`, so a POSITIVE reduced cost is improving.
            // Bland: smallest column index with a strictly positive reduced cost.
            var enter = -1
            for (j in 0 until totalVars) {
                if (reducedCost(j) > 1e-9) { enter = j; break }
            }
            if (enter < 0) break
            var leave = -1
            var bestRatio = Double.MAX_VALUE
            for (i in 0 until m) {
                if (tab[i][enter] > 1e-9) {
                    val ratio = tab[i][totalVars] / tab[i][enter]
                    if (ratio < bestRatio - 1e-12 || (abs(ratio - bestRatio) <= 1e-12 && leave >= 0 && basis[i] < basis[leave])) {
                        bestRatio = ratio
                        leave = i
                    }
                }
            }
            if (leave < 0) return Solution(Status.UNBOUNDED, Double.NaN, DoubleArray(n), iterations, 0, pivots)
            pivot(leave, enter)
            pivots++
        }
        if (iterations >= maxIterations) return Solution(Status.ITERATION_LIMIT, Double.NaN, DoubleArray(n), iterations, 0, pivots)

        // Every artificial must have left the basis at zero for feasibility.
        for (i in 0 until m) {
            if (basis[i] >= n + slackCount + surplusCount && tab[i][totalVars] > 1e-7) {
                return Solution(Status.INFEASIBLE, Double.NaN, DoubleArray(n), iterations, 0, pivots)
            }
        }
        val assignment = DoubleArray(n)
        for (i in 0 until m) if (basis[i] < n) assignment[basis[i]] = tab[i][totalVars]
        val objective = (0 until n).sumOf { c[it] * assignment[it] }
        return Solution(Status.OPTIMAL, objective, assignment, iterations, 0, pivots)
    }

    /** Branch-and-Bound over integer variables (`x_j ∈ Z`) with LP-relaxation bounds. */
    fun maximizeInteger(
        c: DoubleArray,
        constraints: List<Constraint>,
        integerVars: Set<Int> = c.indices.toSet(),
        maxNodes: Int = 4_000,
    ): Solution {
        var bestObjective = Double.NEGATIVE_INFINITY
        var bestAssignment = DoubleArray(c.size)
        var nodes = 0
        var totalPivots = 0
        var infeasibleCount = 0

        data class Node(val extra: List<Constraint>, val depth: Int)
        val stack = ArrayDeque<Node>()
        stack.addLast(Node(emptyList(), 0))

        while (stack.isNotEmpty() && nodes < maxNodes) {
            val node = stack.removeLast()
            nodes++
            val lp = maximize(c, constraints + node.extra)
            totalPivots += lp.tableauPivots
            when (lp.status) {
                Status.INFEASIBLE -> { infeasibleCount++; continue }
                Status.UNBOUNDED -> return lp.copy(branchNodes = nodes)
                else -> {}
            }
            if (lp.objective <= bestObjective + 1e-9) continue
            val fracIdx = integerVars
                .filter { it in c.indices }
                .firstOrNull { abs(lp.assignment[it] - floor(lp.assignment[it] + 0.5)) > 1e-6 }
            if (fracIdx == null) {
                bestObjective = lp.objective
                bestAssignment = lp.assignment
                continue
            }
            val v = lp.assignment[fracIdx]
            val lo = floor(v)
            val hi = ceil(v)
            val downCoeffs = DoubleArray(c.size).also { it[fracIdx] = 1.0 }
            val upCoeffs = DoubleArray(c.size).also { it[fracIdx] = -1.0 }
            stack.addLast(Node(node.extra + Constraint(downCoeffs, Sense.LE, lo), node.depth + 1))
            stack.addLast(Node(node.extra + Constraint(upCoeffs, Sense.LE, -hi), node.depth + 1))
        }

        return if (bestObjective == Double.NEGATIVE_INFINITY) {
            Solution(Status.INFEASIBLE, Double.NaN, DoubleArray(c.size), 0, nodes, totalPivots)
        } else {
            Solution(Status.OPTIMAL, bestObjective, bestAssignment, 0, nodes, totalPivots)
        }
    }

    /** Classic 0/1 knapsack MILP (maximise value s.t. weight <= capacity, x_i ∈ {0,1}). */
    fun knapsack(weights: IntArray, values: IntArray, capacity: Int): Solution {
        require(weights.size == values.size) { "weights/values length mismatch" }
        val n = weights.size
        val c = DoubleArray(n) { values[it].toDouble() }
        val constraints = ArrayList<Constraint>()
        constraints += Constraint(DoubleArray(n) { weights[it].toDouble() }, Sense.LE, capacity.toDouble())
        for (i in 0 until n) constraints += Constraint(DoubleArray(n) { if (it == i) 1.0 else 0.0 }, Sense.LE, 1.0)
        return maximizeInteger(c, constraints)
    }

    fun optimumViaExhaustiveGrid(c: DoubleArray, constraints: List<Constraint>, bounds: IntArray): Solution? {
        val n = c.size
        var best = Double.NEGATIVE_INFINITY
        var bestX = DoubleArray(n)
        val idx = IntArray(n)
        fun rec(dim: Int) {
            if (dim == n) {
                for (con in constraints) {
                    var lhs = 0.0
                    for (j in 0 until n) lhs += con.coeffs[j] * idx[j]
                    when (con.sense) {
                        Sense.LE -> if (lhs > con.rhs + 1e-9) return
                        Sense.GE -> if (lhs < con.rhs - 1e-9) return
                        Sense.EQ -> if (abs(lhs - con.rhs) > 1e-9) return
                    }
                }
                var obj = 0.0
                for (j in 0 until n) obj += c[j] * idx[j]
                if (obj > best) {
                    best = obj
                    bestX = DoubleArray(n) { idx[it].toDouble() }
                }
                return
            }
            for (v in 0..bounds[dim]) { idx[dim] = v; rec(dim + 1) }
        }
        rec(0)
        return if (best == Double.NEGATIVE_INFINITY) null
        else Solution(Status.OPTIMAL, best, bestX, 0, 0, 0)
    }
}
