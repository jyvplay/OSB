// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

import kotlin.math.floor

/**
 * Clean-room Adobe `.cube` 3D LUT Parser, Trilinear RGB Color Grader, and Rec.709 Luma Histogram /
 * Exposure Zebra Diagnostic Engine (`LN-06`).
 */
object Cube3dLutColorGrader {
    const val SCOPE =
        "Pure-JVM .cube 3D LUT parser, trilinear RGB lattice interpolator, and Rec.709 luminance histogram & highlight-clipping zebra analyzer (LN-06)."

    data class Lut3D(
        val title: String,
        val size: Int,
        val domainMin: DoubleArray,
        val domainMax: DoubleArray,
        val tableRgb: DoubleArray, // size^3 * 3 in R-fastest, G-middle, B-slowest (.cube spec order)
    )

    fun parseCubeLut(cubeText: String): Lut3D {
        var title = "Untitled"
        var size = -1
        var dMin = doubleArrayOf(0.0, 0.0, 0.0)
        var dMax = doubleArrayOf(1.0, 1.0, 1.0)
        val rows = ArrayList<Double>()

        for (raw in cubeText.lines()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("TITLE", ignoreCase = true) ->
                    title = line.substringAfter(' ').trim().trim('"')
                line.startsWith("LUT_3D_SIZE", ignoreCase = true) ->
                    size = line.split(Regex("\\s+")).last().toInt()
                line.startsWith("DOMAIN_MIN", ignoreCase = true) -> {
                    val p = line.split(Regex("\\s+")).drop(1).map { it.toDouble() }
                    require(p.size == 3) { "DOMAIN_MIN requires 3 floats" }
                    dMin = p.toDoubleArray()
                }
                line.startsWith("DOMAIN_MAX", ignoreCase = true) -> {
                    val p = line.split(Regex("\\s+")).drop(1).map { it.toDouble() }
                    require(p.size == 3) { "DOMAIN_MAX requires 3 floats" }
                    dMax = p.toDoubleArray()
                }
                line[0].isDigit() || line[0] == '-' || line[0] == '.' -> {
                    val p = line.split(Regex("\\s+")).map { it.toDouble() }
                    require(p.size == 3) { "expected RGB triplet row in .cube, got '$line'" }
                    rows.addAll(p)
                }
            }
        }
        require(size in 2..65 && rows.size == size * size * size * 3) {
            "expected ${size}x${size}x${size} LUT (${size * size * size} triplets), got ${rows.size / 3}"
        }
        return Lut3D(title, size, dMin, dMax, rows.toDoubleArray())
    }

    fun applyTrilinear(lut: Lut3D, r: Double, g: Double, b: Double): DoubleArray {
        val n = lut.size
        val sr = (((r - lut.domainMin[0]) / (lut.domainMax[0] - lut.domainMin[0])).coerceIn(0.0, 1.0)) * (n - 1)
        val sg = (((g - lut.domainMin[1]) / (lut.domainMax[1] - lut.domainMin[1])).coerceIn(0.0, 1.0)) * (n - 1)
        val sb = (((b - lut.domainMin[2]) / (lut.domainMax[2] - lut.domainMin[2])).coerceIn(0.0, 1.0)) * (n - 1)

        val r0 = floor(sr).toInt().coerceIn(0, n - 1); val r1 = (r0 + 1).coerceAtMost(n - 1); val fr = sr - r0
        val g0 = floor(sg).toInt().coerceIn(0, n - 1); val g1 = (g0 + 1).coerceAtMost(n - 1); val fg = sg - g0
        val b0 = floor(sb).toInt().coerceIn(0, n - 1); val b1 = (b0 + 1).coerceAtMost(n - 1); val fb = sb - b0

        fun fetch(ir: Int, ig: Int, ib: Int, ch: Int): Double =
            lut.tableRgb[((ib * n + ig) * n + ir) * 3 + ch]

        return DoubleArray(3) { ch ->
            val c000 = fetch(r0, g0, b0, ch); val c100 = fetch(r1, g0, b0, ch)
            val c010 = fetch(r0, g1, b0, ch); val c110 = fetch(r1, g1, b0, ch)
            val c001 = fetch(r0, g0, b1, ch); val c101 = fetch(r1, g0, b1, ch)
            val c011 = fetch(r0, g1, b1, ch); val c111 = fetch(r1, g1, b1, ch)
            val c00 = c000 * (1 - fr) + c100 * fr; val c10 = c010 * (1 - fr) + c110 * fr
            val c01 = c001 * (1 - fr) + c101 * fr; val c11 = c011 * (1 - fr) + c111 * fr
            val c0 = c00 * (1 - fg) + c10 * fg
            val c1 = c01 * (1 - fg) + c11 * fg
            (c0 * (1 - fb) + c1 * fb).coerceIn(0.0, 1.0)
        }
    }

    fun computeRec709HistogramAndZebraPct(rgbTriplets: DoubleArray, zebraThreshold: Double = 0.95): Pair<IntArray, Double> {
        require(rgbTriplets.isNotEmpty() && rgbTriplets.size % 3 == 0 && zebraThreshold in 0.5..1.0) {
            "invalid RGB buffer or zebraThreshold"
        }
        val bins = IntArray(16)
        var clipped = 0
        val pxCount = rgbTriplets.size / 3
        for (i in 0 until pxCount) {
            val y = (0.2126 * rgbTriplets[i * 3] + 0.7152 * rgbTriplets[i * 3 + 1] + 0.0722 * rgbTriplets[i * 3 + 2]).coerceIn(0.0, 1.0)
            bins[(y * 15.999).toInt().coerceIn(0, 15)]++
            if (y >= zebraThreshold) clipped++
        }
        return bins to (clipped * 100.0 / pxCount)
    }
}
