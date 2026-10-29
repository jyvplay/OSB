// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

import kotlin.math.abs

/**
 * Direct Linear Transform (**DLT** `8×8` Gaussian elimination with partial pivoting) **4-Point
 * Projective Homography Document Deskewer** mapping arbitrary skewed camera quadrilateral corners
 * `(x_i, y_i)` onto a flat upright A4/Letter target rectangle `(u_i, v_i)` for Fusion Lens!
 */
object DocumentSkewQuadRectifier {
    const val SCOPE =
        "8-DOF projective homography matrix H (3x3, h33=1) solver via pivoted Gaussian elimination for Lens document quad rectification."

    data class Point2D(val x: Double, val y: Double)

    fun computeHomographyQuadToRect(srcCornersClockwise: List<Point2D>, dstWidth: Double, dstHeight: Double): DoubleArray {
        require(srcCornersClockwise.size == 4 && dstWidth > 0.0 && dstHeight > 0.0)
        val dst = listOf(
            Point2D(0.0, 0.0),
            Point2D(dstWidth, 0.0),
            Point2D(dstWidth, dstHeight),
            Point2D(0.0, dstHeight),
        )
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val x = srcCornersClockwise[i].x; val y = srcCornersClockwise[i].y
            val u = dst[i].x; val v = dst[i].y
            val r0 = 2 * i
            val r1 = 2 * i + 1
            a[r0][0] = x; a[r0][1] = y; a[r0][2] = 1.0
            a[r0][6] = -u * x; a[r0][7] = -u * y; a[r0][8] = u
            a[r1][3] = x; a[r1][4] = y; a[r1][5] = 1.0
            a[r1][6] = -v * x; a[r1][7] = -v * y; a[r1][8] = v
        }

        // Gaussian elimination with partial pivoting on 8x9 augmented matrix
        for (col in 0 until 8) {
            var pivot = col
            for (r in (col + 1) until 8) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
            require(abs(a[pivot][col]) > 1e-11) { "Degenerate/collinear document quad corners" }
            if (pivot != col) { val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp }
            val div = a[col][col]
            for (j in col..8) a[col][j] /= div
            for (r in 0 until 8) {
                if (r == col) continue
                val factor = a[r][col]
                for (j in col..8) a[r][j] -= factor * a[col][j]
            }
        }
        return doubleArrayOf(a[0][8], a[1][8], a[2][8], a[3][8], a[4][8], a[5][8], a[6][8], a[7][8], 1.0)
    }

    fun projectPoint(h3x3: DoubleArray, p: Point2D): Point2D {
        require(h3x3.size == 9)
        val w = h3x3[6] * p.x + h3x3[7] * p.y + h3x3[8]
        require(abs(w) > 1e-12)
        val u = (h3x3[0] * p.x + h3x3[1] * p.y + h3x3[2]) / w
        val v = (h3x3[3] * p.x + h3x3[4] * p.y + h3x3[5]) / w
        return Point2D(u, v)
    }
}
