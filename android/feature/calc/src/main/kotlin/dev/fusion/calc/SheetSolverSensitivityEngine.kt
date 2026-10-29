// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

import kotlin.math.abs

/**
 * Pure-JVM **What-If Data Table (`1-Variable` & `2-Variable`) and Constrained Simplex / Projected
 * Gradient Sensitivity Solver** for `:feature:sheets`.
 */
object SheetSolverSensitivityEngine {
    const val SCOPE =
        "Spreadsheet 1D/2D What-If Data Table evaluator and box-constrained projected-gradient objective optimizer with finite-difference shadow-price sensitivity."

    data class TwoVarDataTable(
        val rowInputs: List<Double>,
        val colInputs: List<Double>,
        val gridOutputs: List<List<Double>>,
    )

    data class OptimizationResult(
        val optimalDecisionVector: List<Double>,
        val optimalObjectiveValue: Double,
        val activeLowerBounds: List<Int>,
        val activeUpperBounds: List<Int>,
        val marginalShadowGradient: List<Double>,
        val iterations: Int,
    )

    fun evaluateTwoVariableTable(
        rowInputs: List<Double>,
        colInputs: List<Double>,
        formula: (rowVal: Double, colVal: Double) -> Double,
    ): TwoVarDataTable {
        require(rowInputs.isNotEmpty() && colInputs.isNotEmpty())
        val grid = rowInputs.map { r -> colInputs.map { c -> formula(r, c) } }
        return TwoVarDataTable(rowInputs, colInputs, grid)
    }

    fun maximizeBoxConstrained(
        initialGuess: DoubleArray,
        lowerBounds: DoubleArray,
        upperBounds: DoubleArray,
        maxIterations: Int = 400,
        objective: (DoubleArray) -> Double,
    ): OptimizationResult {
        val n = initialGuess.size
        require(n >= 1 && lowerBounds.size == n && upperBounds.size == n)
        val x = DoubleArray(n) { i ->
            require(lowerBounds[i] <= upperBounds[i]) { "Lower bound > upper bound at $i" }
            initialGuess[i].coerceIn(lowerBounds[i], upperBounds[i])
        }
        val eps = 1e-6
        var step = 0.25
        var iter = 0
        while (iter < maxIterations && step > 1e-9) {
            iter++
            val f0 = objective(x)
            val grad = finiteDiffGradient(x, eps, objective)
            val trial = DoubleArray(n) { i -> (x[i] + step * grad[i]).coerceIn(lowerBounds[i], upperBounds[i]) }
            val fTrial = objective(trial)
            if (fTrial > f0 + 1e-10) {
                var moved = 0.0
                for (i in 0 until n) {
                    moved += abs(trial[i] - x[i])
                    x[i] = trial[i]
                }
                if (moved < 1e-9) break
            } else {
                step *= 0.5
            }
        }
        val finalVal = objective(x)
        val finalGrad = finiteDiffGradient(x, eps, objective)
        val activeLo = (0 until n).filter { abs(x[it] - lowerBounds[it]) <= 1e-5 }
        val activeHi = (0 until n).filter { abs(x[it] - upperBounds[it]) <= 1e-5 }
        return OptimizationResult(x.toList(), finalVal, activeLo, activeHi, finalGrad.toList(), iter)
    }

    private fun finiteDiffGradient(x: DoubleArray, eps: Double, f: (DoubleArray) -> Double): DoubleArray {
        val g = DoubleArray(x.size)
        val probe = x.copyOf()
        for (i in x.indices) {
            val orig = probe[i]
            probe[i] = orig + eps
            val fp = f(probe)
            probe[i] = orig - eps
            val fm = f(probe)
            probe[i] = orig
            g[i] = (fp - fm) / (2.0 * eps)
        }
        return g
    }
}
