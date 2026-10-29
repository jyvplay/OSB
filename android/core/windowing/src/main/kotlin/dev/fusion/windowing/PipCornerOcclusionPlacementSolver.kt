// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

/**
 * Pure-JVM **Desktop Freeform Window Snap-Edge & Picture-in-Picture (`PiP`) Occlusion Resolver**
 * for `:core:windowing`, placing floating Recall preview / media PiP tiles into the corner with
 * minimum overlap against active editor carats, bottom-right notification stacks, and dock bars!
 */
object PipCornerOcclusionPlacementSolver {
    const val SCOPE =
        "Picture-in-Picture (PiP) & Recall mini-monitor corner placement solver minimizing overlap with bottom-right Undo notification stacks and active focus rects."

    enum class PipCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    data class RectDp(val x: Double, val y: Double, val width: Double, val height: Double) {
        fun intersectionArea(other: RectDp): Double {
            val ix = (minOf(x + width, other.x + other.width) - maxOf(x, other.x)).coerceAtLeast(0.0)
            val iy = (minOf(y + height, other.y + other.height) - maxOf(y, other.y)).coerceAtLeast(0.0)
            return ix * iy
        }
    }

    data class PipPlacement(val corner: PipCorner, val bounds: RectDp, val occludedAreaSqDp: Double)

    fun chooseLeastOccludedCorner(
        viewportWidthDp: Double,
        viewportHeightDp: Double,
        pipWidthDp: Double = 240.0,
        pipHeightDp: Double = 135.0,
        marginDp: Double = 16.0,
        protectedRegions: List<RectDp>,
    ): PipPlacement {
        require(viewportWidthDp >= pipWidthDp + 2 * marginDp && viewportHeightDp >= pipHeightDp + 2 * marginDp)
        val candidates = listOf(
            PipCorner.TOP_RIGHT to RectDp(viewportWidthDp - pipWidthDp - marginDp, marginDp, pipWidthDp, pipHeightDp),
            PipCorner.TOP_LEFT to RectDp(marginDp, marginDp, pipWidthDp, pipHeightDp),
            PipCorner.BOTTOM_LEFT to RectDp(marginDp, viewportHeightDp - pipHeightDp - marginDp, pipWidthDp, pipHeightDp),
            PipCorner.BOTTOM_RIGHT to RectDp(viewportWidthDp - pipWidthDp - marginDp, viewportHeightDp - pipHeightDp - marginDp, pipWidthDp, pipHeightDp),
        )
        return candidates.map { (corner, rect) ->
            val overlap = protectedRegions.sumOf { rect.intersectionArea(it) }
            PipPlacement(corner, rect, overlap)
        }.minBy { it.occludedAreaSqDp }
    }
}
