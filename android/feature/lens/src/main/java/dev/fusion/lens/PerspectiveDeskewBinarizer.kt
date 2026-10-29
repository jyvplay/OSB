// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Clean-room 4-Point Direct Linear Transform (`DLT`) $3\times 3$ Homography Document Deskew Engine
 * + Integral-Image Sauvola Adaptive Document Binarizer (`LN-08`).
 *
 * Maps destination normalized rectangle $(u, v) \in [0, W) \times [0, H)$ onto arbitrary skewed
 * quadrilateral corners in the source frame via exact $8\times 8$ Gaussian-eliminated projective
 * homography $H$, then applies Sauvola adaptive thresholding:
 *   $$T(x, y) = \mu(x, y) \cdot \left[1 + k \left(\frac{\sigma(x, y)}{R} - 1\right)\right]$$
 */
object PerspectiveDeskewBinarizer {
    const val SCOPE =
        "Pure-JVM 8-DOF projective homography document deskew rectifier and integral-image Sauvola adaptive binarizer (LN-08)."

    data class Point2D(val x: Double, val y: Double)

    /**
     * Computes $3\times 3$ homography matrix $H$ (`h[0..8]`, `h[8] = 1.0`) mapping destination
     * corners `(0,0), (dstW-1,0), (dstW-1,dstH-1), (0,dstH-1)` into source quadrilateral `quadSrc`
     * (ordered TL, TR, BR, BL).
     */
    fun computeDstToSrcHomography(dstW: Int, dstH: Int, quadSrc: List<Point2D>): DoubleArray {
        require(dstW >= 4 && dstH >= 4 && quadSrc.size == 4) { "expected 4 quadrilateral corners and dst >= 4x4" }
        val dstPts = listOf(
            Point2D(0.0, 0.0),
            Point2D((dstW - 1).toDouble(), 0.0),
            Point2D((dstW - 1).toDouble(), (dstH - 1).toDouble()),
            Point2D(0.0, (dstH - 1).toDouble()),
        )
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val u = dstPts[i].x; val v = dstPts[i].y
            val x = quadSrc[i].x; val y = quadSrc[i].y
            a[2 * i] = doubleArrayOf(u, v, 1.0, 0.0, 0.0, 0.0, -u * x, -v * x, x)
            a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, u, v, 1.0, -u * y, -v * y, y)
        }
        // Partial-pivoting Gaussian elimination on 8x9 augmented system
        for (col in 0 until 8) {
            var piv = col
            for (r in col + 1 until 8) if (abs(a[r][col]) > abs(a[piv][col])) piv = r
            require(abs(a[piv][col]) > 1e-10) { "degenerate/collinear document quadrilateral" }
            if (piv != col) { val tmp = a[col]; a[col] = a[piv]; a[piv] = tmp }
            val pv = a[col][col]
            for (c in col..8) a[col][c] /= pv
            for (r in 0 until 8) {
                if (r == col) continue
                val f = a[r][col]
                for (c in col..8) a[r][c] -= f * a[col][c]
            }
        }
        return DoubleArray(9) { if (it < 8) a[it][8] else 1.0 }
    }

    fun deskewAndBinarizeSauvola(
        srcLuma: DoubleArray,
        srcW: Int,
        srcH: Int,
        quadSrc: List<Point2D>,
        dstW: Int,
        dstH: Int,
        windowRadius: Int = 5,
        kFactor: Double = 0.20,
    ): Pair<DoubleArray, ByteArray> {
        require(srcLuma.size == srcW * srcH && windowRadius in 1..32) { "invalid source frame or windowRadius" }
        val h = computeDstToSrcHomography(dstW, dstH, quadSrc)
        val rectified = DoubleArray(dstW * dstH)

        fun sampleBilinear(sx: Double, sy: Double): Double {
            val cx = sx.coerceIn(0.0, (srcW - 1).toDouble())
            val cy = sy.coerceIn(0.0, (srcH - 1).toDouble())
            val x0 = floor(cx).toInt(); val x1 = (x0 + 1).coerceAtMost(srcW - 1); val fx = cx - x0
            val y0 = floor(cy).toInt(); val y1 = (y0 + 1).coerceAtMost(srcH - 1); val fy = cy - y0
            val v00 = srcLuma[y0 * srcW + x0]; val v10 = srcLuma[y0 * srcW + x1]
            val v01 = srcLuma[y1 * srcW + x0]; val v11 = srcLuma[y1 * srcW + x1]
            return (v00 * (1 - fx) + v10 * fx) * (1 - fy) + (v01 * (1 - fx) + v11 * fx) * fy
        }

        for (v in 0 until dstH) {
            for (u in 0 until dstW) {
                val denom = h[6] * u + h[7] * v + h[8]
                val sx = (h[0] * u + h[1] * v + h[2]) / denom
                val sy = (h[3] * u + h[4] * v + h[5]) / denom
                rectified[v * dstW + u] = sampleBilinear(sx, sy).coerceIn(0.0, 1.0)
            }
        }

        // Integral images of I and I^2 for O(1) Sauvola adaptive binarization
        val stride = dstW + 1
        val sum = DoubleArray((dstH + 1) * stride)
        val sqSum = DoubleArray((dstH + 1) * stride)
        for (y in 0 until dstH) {
            var rowS = 0.0; var rowSq = 0.0
            for (x in 0 until dstW) {
                val v = rectified[y * dstW + x]
                rowS += v; rowSq += v * v
                sum[(y + 1) * stride + (x + 1)] = sum[y * stride + (x + 1)] + rowS
                sqSum[(y + 1) * stride + (x + 1)] = sqSum[y * stride + (x + 1)] + rowSq
            }
        }
        val bw = ByteArray(dstW * dstH)
        for (y in 0 until dstH) {
            val y0 = (y - windowRadius).coerceAtLeast(0)
            val y1 = (y + windowRadius + 1).coerceAtMost(dstH)
            for (x in 0 until dstW) {
                val x0 = (x - windowRadius).coerceAtLeast(0)
                val x1 = (x + windowRadius + 1).coerceAtMost(dstW)
                val area = ((y1 - y0) * (x1 - x0)).toDouble()
                val s = sum[y1 * stride + x1] - sum[y0 * stride + x1] - sum[y1 * stride + x0] + sum[y0 * stride + x0]
                val sq = sqSum[y1 * stride + x1] - sqSum[y0 * stride + x1] - sqSum[y1 * stride + x0] + sqSum[y0 * stride + x0]
                val mean = s / area
                val variance = (sq / area - mean * mean).coerceAtLeast(0.0)
                val std = sqrt(variance)
                val threshold = mean * (1.0 + kFactor * (std / 0.5 - 1.0))
                bw[y * dstW + x] = if (rectified[y * dstW + x] >= threshold) 1 else 0
            }
        }
        return rectified to bw
    }
}
