// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

/**
 * R28 EXECUTED host kernel. Edge-snap solver for free windows: pointer position
 * inside the work area selects a zone (corners beat edges), and [apply] maps the
 * zone to an exact target rect. Pure geometry; the window manager keeps authority
 * over whether a snap happens.
 */
object SnapZones {
    const val MAX_EXTENT = 32767
    enum class Zone { NONE, LEFT_HALF, RIGHT_HALF, TOP_HALF, BOTTOM_HALF, FULL, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    data class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
        init {
            require(w in 1..MAX_EXTENT && h in 1..MAX_EXTENT) { "Rect must have positive bounded extent" }
            require(x in -MAX_EXTENT..MAX_EXTENT && y in -MAX_EXTENT..MAX_EXTENT) { "Rect origin out of range" }
        }
    }

    /** Zone for a pointer at (px,py). Distances <= [threshold] snap; corners take priority over edges. */
    fun snap(work: Rect, px: Int, py: Int, threshold: Int): Zone {
        require(threshold in 1..512) { "threshold must be 1..512" }
        require(px in work.x..work.x + work.w && py in work.y..work.y + work.h) { "Pointer must be inside the work area" }
        val dl = px - work.x
        val dr = work.x + work.w - px
        val dt = py - work.y
        val db = work.y + work.h - py
        val nearL = dl <= threshold
        val nearR = dr <= threshold
        val nearT = dt <= threshold
        val nearB = db <= threshold
        return when {
            nearL && nearT -> Zone.TOP_LEFT
            nearR && nearT -> Zone.TOP_RIGHT
            nearL && nearB -> Zone.BOTTOM_LEFT
            nearR && nearB -> Zone.BOTTOM_RIGHT
            nearL -> Zone.LEFT_HALF
            nearR -> Zone.RIGHT_HALF
            nearT -> Zone.TOP_HALF
            nearB -> Zone.BOTTOM_HALF
            else -> Zone.NONE
        }
    }

    /**
     * Target rect for [zone]. [fullZone] reserves the top strip (title/shade rows) for
     * FULL so a snapped window never covers the work-area origin chrome; halves and
     * quarters tile exactly. Refuses when the target would violate [minW]/[minH].
     */
    fun apply(work: Rect, zone: Zone, minW: Int, minH: Int, fullZoneTopStrip: Int = 0): Rect {
        require(minW in 1..MAX_EXTENT && minH in 1..MAX_EXTENT) { "Minimum size out of range" }
        require(fullZoneTopStrip in 0..work.h / 2) { "Top strip must fit inside the work area" }
        val hw = work.w / 2
        val hh = work.h / 2
        val target = when (zone) {
            Zone.NONE -> Rect(work.x, work.y, work.w, work.h)
            Zone.FULL -> Rect(work.x, work.y + fullZoneTopStrip, work.w, work.h - fullZoneTopStrip)
            Zone.LEFT_HALF -> Rect(work.x, work.y, hw, work.h)
            Zone.RIGHT_HALF -> Rect(work.x + work.w - hw, work.y, hw, work.h)
            Zone.TOP_HALF -> Rect(work.x, work.y, work.w, hh)
            Zone.BOTTOM_HALF -> Rect(work.x, work.y + work.h - hh, work.w, hh)
            Zone.TOP_LEFT -> Rect(work.x, work.y, hw, hh)
            Zone.TOP_RIGHT -> Rect(work.x + work.w - hw, work.y, hw, hh)
            Zone.BOTTOM_LEFT -> Rect(work.x, work.y + work.h - hh, hw, hh)
            Zone.BOTTOM_RIGHT -> Rect(work.x + work.w - hw, work.y + work.h - hh, hw, hh)
        }
        require(target.w >= minW && target.h >= minH) { "Snap target violates the minimum window size" }
        return target
    }
}
