// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Discrete half-stop zoom ladder for the lens surface: step i holds scale
 * 2^(i/2), from 0.25x (i=-4) to 16x (i=8). Steps are exact doubles at even
 * indices; odd indices are the irrational half-stops and must only be
 * compared with tolerance. [nearest] clamps out-of-range scales to the
 * ladder ends and breaks exact ties upward, so pinch math always lands on
 * a defined step.
 */
object ZoomLadder {
    const val MIN = -4
    const val MAX = 8

    fun valid(index: Int) = index in MIN..MAX

    fun scale(index: Int): Double {
        require(valid(index))
        return 2.0.pow(index / 2.0)
    }

    fun zoomIn(index: Int): Int {
        require(valid(index))
        return minOf(MAX, index + 1)
    }

    fun zoomOut(index: Int): Int {
        require(valid(index))
        return maxOf(MIN, index - 1)
    }

    fun nearest(scale: Double): Int {
        require(scale.isFinite() && scale > 0)
        if (scale <= scale(MIN)) return MIN
        if (scale >= scale(MAX)) return MAX
        // Compare in log2 space: exact ties break upward via round-half-up.
        val exact = kotlin.math.log2(scale) * 2
        return exact.roundToInt().coerceIn(MIN, MAX)
    }
}
