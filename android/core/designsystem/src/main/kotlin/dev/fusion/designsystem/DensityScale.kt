// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

/**
 * Bounded density-independent pixel conversion. Converts between density
 * pixels (dp), scale-independent pixels (sp), and pixels (px) given a
 * display density scale and font scale. Rounding is round-half-up (away
 * from zero on ties) and results are clamped to [MAX_PX] to avoid overflow
 * in layout passes.
 *
 * Declared narrowings: densityScale and fontScale must be positive and at
 * most [MAX_SCALE]; input dp/sp values at most [MAX_DIP]; output px at
 * most [MAX_PX]; negative inputs are refused; no platform Canvas or
 * DisplayMetrics is consulted — scales are supplied by the caller.
 */
object DensityScale {
    const val MAX_SCALE = 10.0
    const val MAX_DIP = 100_000.0
    const val MAX_PX = 1_000_000.0

    fun dpToPx(dp: Double, densityScale: Double): Int {
        require(densityScale > 0.0 && densityScale <= MAX_SCALE) {
            "densityScale out of bounds: $densityScale"
        }
        require(dp >= 0.0 && dp <= MAX_DIP) { "dp out of bounds: $dp" }
        return roundHalfUp(dp * densityScale)
    }

    fun pxToDp(px: Int, densityScale: Double): Double {
        require(densityScale > 0.0 && densityScale <= MAX_SCALE) {
            "densityScale out of bounds: $densityScale"
        }
        require(px >= 0 && px <= MAX_PX.toInt()) { "px out of bounds: $px" }
        return px / densityScale
    }

    fun spToPx(sp: Double, fontScale: Double, densityScale: Double): Int {
        require(fontScale > 0.0 && fontScale <= MAX_SCALE) {
            "fontScale out of bounds: $fontScale"
        }
        require(densityScale > 0.0 && densityScale <= MAX_SCALE) {
            "densityScale out of bounds: $densityScale"
        }
        require(sp >= 0.0 && sp <= MAX_DIP) { "sp out of bounds: $sp" }
        return roundHalfUp(sp * fontScale * densityScale)
    }

    fun pxToSp(px: Int, fontScale: Double, densityScale: Double): Double {
        require(fontScale > 0.0 && fontScale <= MAX_SCALE) {
            "fontScale out of bounds: $fontScale"
        }
        require(densityScale > 0.0 && densityScale <= MAX_SCALE) {
            "densityScale out of bounds: $densityScale"
        }
        require(px >= 0 && px <= MAX_PX.toInt()) { "px out of bounds: $px" }
        return px / (fontScale * densityScale)
    }

    private fun roundHalfUp(v: Double): Int {
        val scaled = v + 0.5
        return if (scaled >= 0.0) scaled.toInt() else -((-scaled).toInt())
    }
}
