package dev.fusion.lens

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Clean-room 4-Point Perspective Homography (`3x3` DLT Gaussian Elimination) Document Dewarper
 * & Adaptive Integral-Image Sauvola Text Binarizer (`LN-10`, Apache-2.0).
 */
object DocumentDewarpPerspectiveEngine {
    const val SCOPE =
        "8-DOF 4-corner planar homography (3x3 DLT partial-pivoting solver) + bilinear perspective dewarp & Sauvola adaptive binarizer (LN-10)"

    data class Point2D(val x: Double, val y: Double)

    /** Computes `H` (`h[0..8]` with `h[8]=1`) mapping destination `(u, v)` -> source `(x, y)`. */
    fun solveInverseHomography(
        dstWidth: Int,
        dstHeight: Int,
        srcQuadClockwiseFromTopLeft: List<Point2D>,
    ): DoubleArray {
        require(dstWidth >= 4 && dstHeight >= 4 && srcQuadClockwiseFromTopLeft.size == 4)
        val dstPts = listOf(
            Point2D(0.0, 0.0),
            Point2D((dstWidth - 1).toDouble(), 0.0),
            Point2D((dstWidth - 1).toDouble(), (dstHeight - 1).toDouble()),
            Point2D(0.0, (dstHeight - 1).toDouble()),
        )
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val u = dstPts[i].x; val v = dstPts[i].y
            val x = srcQuadClockwiseFromTopLeft[i].x; val y = srcQuadClockwiseFromTopLeft[i].y
            val r0 = 2 * i; val r1 = 2 * i + 1
            a[r0][0] = u; a[r0][1] = v; a[r0][2] = 1.0
            a[r0][6] = -u * x; a[r0][7] = -v * x; a[r0][8] = x
            a[r1][3] = u; a[r1][4] = v; a[r1][5] = 1.0
            a[r1][6] = -u * y; a[r1][7] = -v * y; a[r1][8] = y
        }
        val sol8 = solve8x8Gaussian(a)
        return DoubleArray(9) { if (it < 8) sol8[it] else 1.0 }
    }

    fun dewarpGrayscale(
        srcGray: IntArray,
        srcW: Int,
        srcH: Int,
        quad: List<Point2D>,
        dstW: Int,
        dstH: Int,
    ): IntArray {
        require(srcGray.size == srcW * srcH)
        val h = solveInverseHomography(dstW, dstH, quad)
        val out = IntArray(dstW * dstH)
        for (v in 0 until dstH) {
            for (u in 0 until dstW) {
                val denom = h[6] * u + h[7] * v + h[8]
                val sx = ((h[0] * u + h[1] * v + h[2]) / denom).coerceIn(0.0, (srcW - 1).toDouble())
                val sy = ((h[3] * u + h[4] * v + h[5]) / denom).coerceIn(0.0, (srcH - 1).toDouble())
                val x0 = sx.toInt().coerceIn(0, srcW - 1)
                val y0 = sy.toInt().coerceIn(0, srcH - 1)
                val x1 = (x0 + 1).coerceAtMost(srcW - 1)
                val y1 = (y0 + 1).coerceAtMost(srcH - 1)
                val fx = sx - x0; val fy = sy - y0
                val p00 = srcGray[y0 * srcW + x0]; val p10 = srcGray[y0 * srcW + x1]
                val p01 = srcGray[y1 * srcW + x0]; val p11 = srcGray[y1 * srcW + x1]
                val interp = (1 - fx) * (1 - fy) * p00 + fx * (1 - fy) * p10 + (1 - fx) * fy * p01 + fx * fy * p11
                out[v * dstW + u] = interp.roundToInt().coerceIn(0, 255)
            }
        }
        return out
    }

    /** Sauvola (`T = m * (1 + k * (s / R - 1))`) adaptive local thresholding via O(1) integral images! */
    fun sauvolaBinarize(gray: IntArray, w: Int, h: Int, radius: Int = 7, k: Double = 0.2): IntArray {
        require(gray.size == w * h && w >= 2 && h >= 2)
        val sum = LongArray((w + 1) * (h + 1))
        val sqSum = LongArray((w + 1) * (h + 1))
        for (y in 1..h) {
            var rowS = 0L; var rowSq = 0L
            for (x in 1..w) {
                val p = gray[(y - 1) * w + (x - 1)].toLong()
                rowS += p; rowSq += p * p
                val idx = y * (w + 1) + x
                sum[idx] = sum[(y - 1) * (w + 1) + x] + rowS
                sqSum[idx] = sqSum[(y - 1) * (w + 1) + x] + rowSq
            }
        }
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val y0 = (y - radius).coerceAtLeast(0); val y1 = (y + radius).coerceAtMost(h - 1) + 1
            for (x in 0 until w) {
                val x0 = (x - radius).coerceAtLeast(0); val x1 = (x + radius).coerceAtMost(w - 1) + 1
                val area = (x1 - x0) * (y1 - y0)
                val s = sum[y1 * (w + 1) + x1] - sum[y0 * (w + 1) + x1] - sum[y1 * (w + 1) + x0] + sum[y0 * (w + 1) + x0]
                val sq = sqSum[y1 * (w + 1) + x1] - sqSum[y0 * (w + 1) + x1] - sqSum[y1 * (w + 1) + x0] + sqSum[y0 * (w + 1) + x0]
                val mean = s.toDouble() / area
                val variance = (sq.toDouble() / area - mean * mean).coerceAtLeast(0.0)
                val std = sqrt(variance)
                val threshold = mean * (1.0 + k * (std / 128.0 - 1.0))
                out[y * w + x] = if (gray[y * w + x] >= threshold) 255 else 0
            }
        }
        return out
    }

    private fun solve8x8Gaussian(aug: Array<DoubleArray>): DoubleArray {
        val n = 8
        for (col in 0 until n) {
            var piv = col
            for (r in col + 1 until n) if (abs(aug[r][col]) > abs(aug[piv][col])) piv = r
            require(abs(aug[piv][col]) > 1e-12) { "Degenerate collinear perspective quadrilateral" }
            val tmp = aug[col]; aug[col] = aug[piv]; aug[piv] = tmp
            val div = aug[col][col]
            for (j in col..n) aug[col][j] /= div
            for (r in 0 until n) {
                if (r == col) continue
                val f = aug[r][col]
                for (j in col..n) aug[r][j] -= f * aug[col][j]
            }
        }
        return DoubleArray(n) { aug[it][n] }
    }
}
