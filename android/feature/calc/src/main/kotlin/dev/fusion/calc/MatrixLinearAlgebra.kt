// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

import kotlin.math.abs

/**
 * Clean-room Dense **Matrix Linear Algebra Engine** (`MMULT`, `TRANSPOSE`, LU partial-pivoting
 * `MDETERM`, Gauss-Jordan `MINVERSE`, and `solveLinearSystem(A, b)`).
 */
object MatrixLinearAlgebra {
    const val SCOPE =
        "Clean-room dense matrix linear algebra kernel (MMULT, TRANSPOSE, partial-pivoting LU MDETERM, Gauss-Jordan MINVERSE, and Ax=b solver)."

    const val MAX_DIM = 128

    data class Matrix(val rows: Int, val cols: Int, private val data: DoubleArray) {
        init {
            require(rows in 1..MAX_DIM && cols in 1..MAX_DIM && data.size == rows * cols) {
                "invalid Matrix dimensions ${rows}x$cols"
            }
            require(data.all { it.isFinite() }) { "Matrix contains non-finite entry" }
        }

        operator fun get(r: Int, c: Int): Double = data[r * cols + c]
        fun toRows(): List<List<Double>> = List(rows) { r -> List(cols) { c -> get(r, c) } }

        companion object {
            fun of(grid: List<List<Double>>): Matrix {
                require(grid.isNotEmpty() && grid[0].isNotEmpty()) { "empty matrix" }
                val r = grid.size; val c = grid[0].size
                require(grid.all { it.size == c }) { "ragged matrix rows" }
                val flat = DoubleArray(r * c)
                for (i in 0 until r) for (j in 0 until c) flat[i * c + j] = grid[i][j]
                return Matrix(r, c, flat)
            }

            fun identity(n: Int): Matrix {
                val flat = DoubleArray(n * n)
                for (i in 0 until n) flat[i * n + i] = 1.0
                return Matrix(n, n, flat)
            }
        }
    }

    fun transpose(m: Matrix): Matrix {
        val out = DoubleArray(m.cols * m.rows)
        for (r in 0 until m.rows) for (c in 0 until m.cols) out[c * m.rows + r] = m[r, c]
        return Matrix(m.cols, m.rows, out)
    }

    fun multiply(a: Matrix, b: Matrix): Matrix {
        require(a.cols == b.rows) { "MMULT dimension mismatch: ${a.rows}x${a.cols} * ${b.rows}x${b.cols}" }
        val out = DoubleArray(a.rows * b.cols)
        for (i in 0 until a.rows) {
            for (k in 0 until a.cols) {
                val aik = a[i, k]
                for (j in 0 until b.cols) {
                    out[i * b.cols + j] += aik * b[k, j]
                }
            }
        }
        return Matrix(a.rows, b.cols, out)
    }

    fun determinant(m: Matrix): Double {
        require(m.rows == m.cols) { "MDETERM requires square matrix, got ${m.rows}x${m.cols}" }
        val n = m.rows
        val lu = Array(n) { r -> DoubleArray(n) { c -> m[r, c] } }
        var sign = 1.0
        for (k in 0 until n) {
            var pivot = k
            var maxAbs = abs(lu[k][k])
            for (i in k + 1 until n) {
                val v = abs(lu[i][k])
                if (v > maxAbs) { maxAbs = v; pivot = i }
            }
            if (maxAbs < 1e-14) return 0.0
            if (pivot != k) {
                val tmp = lu[k]; lu[k] = lu[pivot]; lu[pivot] = tmp
                sign = -sign
            }
            val diag = lu[k][k]
            for (i in k + 1 until n) {
                val factor = lu[i][k] / diag
                for (j in k + 1 until n) lu[i][j] -= factor * lu[k][j]
            }
        }
        var det = sign
        for (i in 0 until n) det *= lu[i][i]
        return if (det == 0.0) 0.0 else det
    }

    fun inverse(m: Matrix): Matrix {
        require(m.rows == m.cols) { "MINVERSE requires square matrix" }
        val n = m.rows
        val aug = Array(n) { r ->
            DoubleArray(2 * n) { c -> if (c < n) m[r, c] else if (c - n == r) 1.0 else 0.0 }
        }
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) {
                if (abs(aug[r][col]) > abs(aug[pivot][col])) pivot = r
            }
            require(abs(aug[pivot][col]) >= 1e-12) { "singular matrix cannot be inverted" }
            if (pivot != col) {
                val tmp = aug[col]; aug[col] = aug[pivot]; aug[pivot] = tmp
            }
            val pVal = aug[col][col]
            for (j in 0 until 2 * n) aug[col][j] /= pVal
            for (r in 0 until n) {
                if (r == col) continue
                val factor = aug[r][col]
                for (j in 0 until 2 * n) aug[r][j] -= factor * aug[col][j]
            }
        }
        val flat = DoubleArray(n * n)
        for (r in 0 until n) for (c in 0 until n) flat[r * n + c] = aug[r][n + c]
        return Matrix(n, n, flat)
    }

    fun solve(a: Matrix, rhs: DoubleArray): DoubleArray {
        require(a.rows == a.cols && rhs.size == a.rows) { "solve(A, b) dimension mismatch" }
        val bMat = Matrix(rhs.size, 1, rhs.copyOf())
        val xMat = multiply(inverse(a), bMat)
        return DoubleArray(rhs.size) { xMat[it, 0] }
    }
}
