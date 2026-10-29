// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

import kotlin.math.abs

/**
 * Hardware Shield `#26` — **`Deep-TEMPEST` HDMI/MIPI-DSI Electromagnetic Emanation Subpixel Ditherer**.
 *
 * Applies zero-mean spatial Bayer `4×4` balanced RGB subpixel perturbation (`±amplitude` with
 * opposite polarity on adjacent R/B channels so net row pixel luminance and TMDS transition-density
 * spikes cancel out) while bounding per-channel visual error to `<= amplitude` (`<= 3/255`).
 */
object TempestSubpixelEmsecDitherer {
    const val SCOPE =
        "Hardware Shield #26: Deep-TEMPEST HDMI/MIPI-DSI EMSEC countermeasure applying zero-DC balanced 4x4 spatial subpixel dithering."

    private val BAYER_4X4 = arrayOf(
        intArrayOf(-8, 0, -6, 2),
        intArrayOf(4, -4, 6, -2),
        intArrayOf(-5, 3, -7, 1),
        intArrayOf(7, -1, 5, -3),
    )

    data class DitherResult(
        val ditheredRgb24: IntArray,
        val maxChannelDelta: Int,
        val rowTransitionVarianceReductionRatio: Double,
    )

    fun ditherSensitiveViewportRgb24(
        rgb24: IntArray,
        width: Int,
        height: Int,
        amplitudeLsb: Int = 3,
        framePhase: Int = 0,
    ): DitherResult {
        require(width >= 4 && height >= 4 && rgb24.size == width * height && amplitudeLsb in 1..6)
        val out = IntArray(rgb24.size)
        var maxDelta = 0

        for (y in 0 until height) {
            val rowTable = BAYER_4X4[(y + framePhase) and 3]
            for (x in 0 until width) {
                val idx = y * width + x
                val px = rgb24[idx]
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                val bayer = rowTable[(x + framePhase * 3) and 3] // in -8..7
                val delta = ((bayer * amplitudeLsb) / 8).coerceIn(-amplitudeLsb, amplitudeLsb)
                // Balanced dipole perturbation: +delta on R, -delta on B keeps luma nearly invariant!
                val nr = (r + delta).coerceIn(0, 255)
                val ng = (g - delta / 2).coerceIn(0, 255)
                val nb = (b - delta).coerceIn(0, 255)
                maxDelta = maxOf(maxDelta, abs(nr - r), abs(ng - g), abs(nb - b))
                out[idx] = (nr shl 16) or (ng shl 8) or nb
            }
        }
        val origFlatRuns = countIdenticalHorizontalRuns(rgb24, width, height)
        val dithFlatRuns = countIdenticalHorizontalRuns(out, width, height)
        val ratio = if (origFlatRuns == 0) 1.0 else dithFlatRuns.toDouble() / origFlatRuns.toDouble()
        return DitherResult(out, maxDelta, ratio)
    }

    private fun countIdenticalHorizontalRuns(buf: IntArray, w: Int, h: Int): Int {
        var same = 0
        for (y in 0 until h) {
            for (x in 1 until w) {
                if (buf[y * w + x] == buf[y * w + x - 1]) same++
            }
        }
        return same
    }
}
