// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

import kotlin.math.abs

/**
 * Clean-room Dense Linear System $Ax = b$ Partial-Pivoting Solver + $L_\infty$ Condition Estimate
 * for Scientific/Engineering Calculator workbenches.
 */
object UnitMatrixSolverWorkbench {
    const val SCOPE =
        "Pure-JVM partial-pivoting Gaussian elimination Ax=b solver with residual verification and L-infinity norm calculation."

    data class LinearSolveResult(
        val solution: DoubleArray,
        val maxAbsResidual: Double,
        val infinityNormA: Double,
    )

    fun solve(a: Array<DoubleArray>, b: DoubleArray): LinearSolveResult {
        val n = b.size
        require(n in 1..64 && a.size == n && a.all { it.size == n }) { "expected NxN matrix and N-vector (1..64)" }
        val infNorm = a.maxOf { row -> row.sumOf { abs(it) } }
        val aug = Array(n) { r -> DoubleArray(n + 1) { c -> if (c < n) a[r][c] else b[r] } }

        for (col in 0 until n) {
            var piv = col
            for (r in col + 1 until n) if (abs(aug[r][col]) > abs(aug[piv][col])) piv = r
            require(abs(aug[piv][col]) >= 1e-12) { "singular or nearly singular linear system at column $col" }
            if (piv != col) { val tmp = aug[col]; aug[col] = aug[piv]; aug[piv] = tmp }
            val pv = aug[col][col]
            for (c in col..n) aug[col][c] /= pv
            for (r in 0 until n) {
                if (r == col) continue
                val f = aug[r][col]
                if (f != 0.0) for (c in col..n) aug[r][c] -= f * aug[col][c]
            }
        }
        val x = DoubleArray(n) { aug[it][n] }
        var maxRes = 0.0
        for (r in 0 until n) {
            var ax = 0.0
            for (c in 0 until n) ax += a[r][c] * x[c]
            maxRes = maxOf(maxRes, abs(ax - b[r]))
        }
        return LinearSolveResult(x, maxRes, infNorm)
    }
}
