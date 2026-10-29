// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

import kotlin.math.abs

/**
 * Clean-room 8-DOF Planar **3x3 Perspective Homography (`DLT` Gaussian Elimination) Solver**
 * mapping 4 tilted camera quad corners `(src[0..3])` to rectified document/whiteboard corners `(dst[0..3])`.
 */
object PerspectiveHomography3x3 {
    const val SCOPE =
        "Clean-room 8-DOF planar 3x3 perspective homography solver (Direct Linear Transform + partial-pivoting Gaussian elimination)."

    data class Point2D(val x: Double, val y: Double)

    class Homography(val h: DoubleArray) {
        init {
            require(h.size == 9 && h.all { it.isFinite() }) { "invalid 3x3 homography array" }
        }

        fun transform(p: Point2D): Point2D {
            val denom = h[6] * p.x + h[7] * p.y + h[8]
            require(abs(denom) >= 1e-12) { "point maps to line at infinity" }
            val u = (h[0] * p.x + h[1] * p.y + h[2]) / denom
            val v = (h[3] * p.x + h[4] * p.y + h[5]) / denom
            return Point2D(u, v)
        }
    }

    fun computeQuadToQuad(src: List<Point2D>, dst: List<Point2D>): Homography {
        require(src.size == 4 && dst.size == 4) { "homography requires 4 source and 4 destination corners" }
        // Solve 8x8 linear system A * [h0..h7]^T = b with h8 = 1.0:
        // u_i = (h0*x + h1*y + h2) / (h6*x + h7*y + 1) => x*h0 + y*h1 + h2 - u*x*h6 - u*y*h7 = u
        // v_i = (h3*x + h4*y + h5) / (h6*x + h7*y + 1) => x*h3 + y*h4 + h5 - v*x*h6 - v*y*h7 = v
        val aug = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val x = src[i].x; val y = src[i].y
            val u = dst[i].x; val v = dst[i].y
            val r0 = 2 * i
            aug[r0][0] = x; aug[r0][1] = y; aug[r0][2] = 1.0
            aug[r0][6] = -u * x; aug[r0][7] = -u * y; aug[r0][8] = u

            val r1 = 2 * i + 1
            aug[r1][3] = x; aug[r1][4] = y; aug[r1][5] = 1.0
            aug[r1][6] = -v * x; aug[r1][7] = -v * y; aug[r1][8] = v
        }

        for (col in 0 until 8) {
            var pivot = col
            for (r in col + 1 until 8) {
                if (abs(aug[r][col]) > abs(aug[pivot][col])) pivot = r
            }
            require(abs(aug[pivot][col]) >= 1e-12) { "degenerate collinear quad corners in homography" }
            if (pivot != col) {
                val tmp = aug[col]; aug[col] = aug[pivot]; aug[pivot] = tmp
            }
            val diag = aug[col][col]
            for (j in col..8) aug[col][j] /= diag
            for (r in 0 until 8) {
                if (r == col) continue
                val f = aug[r][col]
                for (j in col..8) aug[r][j] -= f * aug[col][j]
            }
        }
        val h = DoubleArray(9)
        for (i in 0 until 8) h[i] = aug[i][8]
        h[8] = 1.0
        return Homography(h)
    }
}
