// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

/**
 * **Pointer Safe-Triangle Diagonal Submenu Hover Filter** (classic macOS AppKit `NSMenu` / Amazon
 * mega-menu barycentric corridor algorithm).
 *
 * When a user's cursor moves diagonally from a parent context-menu row `P0` toward an open
 * cascading submenu's top `T1` and bottom `T2` near-edge corners, momentarily crossing sibling
 * parent rows inside triangle `(P0, T1, T2)` within `graceWindowMs = 300 ms` keeps the open
 * submenu alive instead of flickering closed!
 */
object PointerSafeTriangleSubmenuFilter {
    const val SCOPE =
        "Barycentric signed-area pointer safe-triangle submenu hover filter preventing diagonal cursor motion from collapsing open submenus."

    data class PointDp(val x: Double, val y: Double)

    fun shouldKeepSubmenuOpenOnSiblingHover(
        prevPointer: PointDp,
        currentPointer: PointDp,
        submenuNearEdgeX: Double,
        submenuTopY: Double,
        submenuBottomY: Double,
        elapsedMillisSincePrevSample: Long,
        graceWindowMillis: Long = 300L,
    ): Boolean {
        if (elapsedMillisSincePrevSample > graceWindowMillis || submenuBottomY <= submenuTopY) return false
        val tTop = PointDp(submenuNearEdgeX, submenuTopY - 6.0)
        val tBot = PointDp(submenuNearEdgeX, submenuBottomY + 6.0)
        return pointInTriangleInclusive(currentPointer, prevPointer, tTop, tBot)
    }

    fun pointInTriangleInclusive(p: PointDp, a: PointDp, b: PointDp, c: PointDp): Boolean {
        val d1 = crossSign(p, a, b)
        val d2 = crossSign(p, b, c)
        val d3 = crossSign(p, c, a)
        val hasNeg = (d1 < -1e-6) || (d2 < -1e-6) || (d3 < -1e-6)
        val hasPos = (d1 > 1e-6) || (d2 > 1e-6) || (d3 > 1e-6)
        return !(hasNeg && hasPos)
    }

    private fun crossSign(p1: PointDp, p2: PointDp, p3: PointDp): Double =
        (p1.x - p3.x) * (p2.y - p3.y) - (p2.x - p3.x) * (p1.y - p3.y)
}
