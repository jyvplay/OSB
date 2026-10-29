package dev.fusion.capture

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Clean-room Vertical Scrolling-Screenshot Seam Matcher & Linear Feather-Blend Stitcher (Apache-2.0).
 *
 * Finds the exact vertical overlap `overlapRows` between consecutive viewport strips (ignoring
 * fixed top/bottom sticky toolbars!) by minimizing mean absolute row-signature error and blends
 * across the overlap zone without visible horizontal tearing.
 */
object ScrollingStitchSeamMatcher {
    const val SCOPE =
        "Vertical scrolling-capture row-luminance overlap seam matcher + sticky-header exclusion + linear feather stitcher"

    data class StitchResult(
        val width: Int,
        val height: Int,
        val detectedOverlapRows: Int,
        val pixelsGray: IntArray,
    )

    fun stitchVerticalGrayscale(
        topStrip: IntArray,
        topHeight: Int,
        bottomStrip: IntArray,
        bottomHeight: Int,
        width: Int,
        stickyHeaderRows: Int = 0,
        minOverlapRows: Int = 4,
    ): StitchResult {
        require(topStrip.size == topHeight * width && bottomStrip.size == bottomHeight * width)
        val effTopH = topHeight
        val bStart = stickyHeaderRows.coerceIn(0, bottomHeight / 2)
        val effBottomH = bottomHeight - bStart
        val maxOv = minOf(effTopH - stickyHeaderRows - 1, effBottomH - 1)
        require(maxOv >= minOverlapRows) { "Insufficient strip height for overlap search" }

        var bestOverlap = minOverlapRows
        var bestMad = Double.MAX_VALUE
        for (ov in minOverlapRows..maxOv) {
            var diffSum = 0L
            for (r in 0 until ov) {
                val topRowOff = (effTopH - ov + r) * width
                val botRowOff = (bStart + r) * width
                for (x in 0 until width) {
                    diffSum += abs(topStrip[topRowOff + x] - bottomStrip[botRowOff + x])
                }
            }
            val mad = diffSum.toDouble() / (ov * width)
            // Tie-break toward larger verified overlap to avoid periodic false sub-matches
            if (mad < bestMad - 0.25 || (abs(mad - bestMad) <= 0.25 && ov > bestOverlap)) {
                bestMad = mad
                bestOverlap = ov
            }
        }

        val outH = effTopH + (effBottomH - bestOverlap)
        val out = IntArray(outH * width)
        System.arraycopy(topStrip, 0, out, 0, (effTopH - bestOverlap) * width)
        for (r in 0 until bestOverlap) {
            val alpha = if (bestOverlap == 1) 0.5 else r.toDouble() / (bestOverlap - 1)
            val topOff = (effTopH - bestOverlap + r) * width
            val botOff = (bStart + r) * width
            val dstOff = (effTopH - bestOverlap + r) * width
            for (x in 0 until width) {
                out[dstOff + x] = ((1.0 - alpha) * topStrip[topOff + x] + alpha * bottomStrip[botOff + x]).roundToInt()
            }
        }
        val tailRows = effBottomH - bestOverlap
        if (tailRows > 0) {
            System.arraycopy(bottomStrip, (bStart + bestOverlap) * width, out, effTopH * width, tailRows * width)
        }
        return StitchResult(width, outH, bestOverlap, out)
    }
}
