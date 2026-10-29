// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

import kotlin.math.hypot

/**
 * Desktop & Foldable **Magnetic Edge/Corner Snap-Zone Router** with Velocity Hysteresis.
 *
 * Maps drag pointer coordinates `(pointerX, pointerY)` and release speed (`velocityDpPerSec`) on a
 * safe work-area rectangle into `MAXIMIZE`, `LEFT_HALF`, `RIGHT_HALF`, `TOP_LEFT_QUARTER`,
 * `TOP_RIGHT_QUARTER`, `BOTTOM_LEFT_QUARTER`, `BOTTOM_RIGHT_QUARTER`, or `FREEFORM` bounds while
 * refusing fast flings above `maxSnapVelocityDpPerSec` from accidentally snapping!
 */
object SnapZoneMagneticRouter {
    const val SCOPE =
        "Desktop/foldable magnetic edge & corner snap-zone router (halves, quarters, top-edge maximize, velocity anti-overshoot guard)."

    enum class Zone {
        FREEFORM,
        MAXIMIZE,
        LEFT_HALF,
        RIGHT_HALF,
        TOP_LEFT_QUARTER,
        TOP_RIGHT_QUARTER,
        BOTTOM_LEFT_QUARTER,
        BOTTOM_RIGHT_QUARTER,
    }

    data class RectDp(val x: Int, val y: Int, val width: Int, val height: Int)
    data class SnapDecision(val zone: Zone, val targetBounds: RectDp?)

    fun evaluate(
        workArea: RectDp,
        pointerX: Int,
        pointerY: Int,
        velocityXDpPerSec: Double = 0.0,
        velocityYDpPerSec: Double = 0.0,
        edgeThresholdDp: Int = 24,
        cornerRadiusDp: Int = 48,
        maxSnapVelocityDpPerSec: Double = 2_400.0,
    ): SnapDecision {
        require(workArea.width >= 320 && workArea.height >= 240) { "workArea below minimum desktop dimensions" }
        val speed = hypot(velocityXDpPerSec, velocityYDpPerSec)
        if (speed > maxSnapVelocityDpPerSec) return SnapDecision(Zone.FREEFORM, null)

        val relX = pointerX - workArea.x
        val relY = pointerY - workArea.y
        val nearLeft = relX <= edgeThresholdDp
        val nearRight = relX >= workArea.width - edgeThresholdDp
        val nearTop = relY <= edgeThresholdDp
        val nearBottom = relY >= workArea.height - edgeThresholdDp

        val inLeftCornerBand = relX <= cornerRadiusDp
        val inRightCornerBand = relX >= workArea.width - cornerRadiusDp
        val inTopCornerBand = relY <= cornerRadiusDp
        val inBottomCornerBand = relY >= workArea.height - cornerRadiusDp

        val halfW = workArea.width / 2
        val rightW = workArea.width - halfW
        val halfH = workArea.height / 2
        val botH = workArea.height - halfH

        return when {
            (nearLeft && inTopCornerBand) || (nearTop && inLeftCornerBand) ->
                SnapDecision(Zone.TOP_LEFT_QUARTER, RectDp(workArea.x, workArea.y, halfW, halfH))
            (nearRight && inTopCornerBand) || (nearTop && inRightCornerBand) ->
                SnapDecision(Zone.TOP_RIGHT_QUARTER, RectDp(workArea.x + halfW, workArea.y, rightW, halfH))
            (nearLeft && inBottomCornerBand) || (nearBottom && inLeftCornerBand) ->
                SnapDecision(Zone.BOTTOM_LEFT_QUARTER, RectDp(workArea.x, workArea.y + halfH, halfW, botH))
            (nearRight && inBottomCornerBand) || (nearBottom && inRightCornerBand) ->
                SnapDecision(Zone.BOTTOM_RIGHT_QUARTER, RectDp(workArea.x + halfW, workArea.y + halfH, rightW, botH))
            nearTop -> SnapDecision(Zone.MAXIMIZE, workArea)
            nearLeft -> SnapDecision(Zone.LEFT_HALF, RectDp(workArea.x, workArea.y, halfW, workArea.height))
            nearRight -> SnapDecision(Zone.RIGHT_HALF, RectDp(workArea.x + halfW, workArea.y, rightW, workArea.height))
            else -> SnapDecision(Zone.FREEFORM, null)
        }
    }
}
