package dev.fusion.windowing

/**
 * Clean-room Desktop Freeform Magnetic Edge/Corner Snap & 3-Column Tiling Solver (Apache-2.0).
 */
object SnapTileMagneticLayoutSolver {
    const val SCOPE =
        "Desktop magnetic edge/corner snap-zone classifier + DPI-safe Left/Right/Quadrant/Three-Column tiling solver"

    enum class SnapZone { NONE, MAXIMIZE_TOP, LEFT_HALF, RIGHT_HALF, TOP_LEFT_QUAD, TOP_RIGHT_QUAD, BOTTOM_LEFT_QUAD, BOTTOM_RIGHT_QUAD, CENTER_THIRD }

    data class BoundsPx(val x: Int, val y: Int, val width: Int, val height: Int)

    fun detectSnapZone(
        pointerX: Int,
        pointerY: Int,
        workArea: BoundsPx,
        edgeThresholdPx: Int = 24,
        cornerRadiusPx: Int = 64,
    ): SnapZone {
        val nearLeft = pointerX <= workArea.x + edgeThresholdPx
        val nearRight = pointerX >= workArea.x + workArea.width - edgeThresholdPx
        val nearTop = pointerY <= workArea.y + edgeThresholdPx
        val nearBottom = pointerY >= workArea.y + workArea.height - edgeThresholdPx
        val inTopCornerBand = pointerY <= workArea.y + cornerRadiusPx
        val inBottomCornerBand = pointerY >= workArea.y + workArea.height - cornerRadiusPx
        val inLeftCornerBand = pointerX <= workArea.x + cornerRadiusPx
        val inRightCornerBand = pointerX >= workArea.x + workArea.width - cornerRadiusPx

        return when {
            (nearLeft && inTopCornerBand) || (nearTop && inLeftCornerBand) -> SnapZone.TOP_LEFT_QUAD
            (nearRight && inTopCornerBand) || (nearTop && inRightCornerBand) -> SnapZone.TOP_RIGHT_QUAD
            (nearLeft && inBottomCornerBand) || (nearBottom && inLeftCornerBand) -> SnapZone.BOTTOM_LEFT_QUAD
            (nearRight && inBottomCornerBand) || (nearBottom && inRightCornerBand) -> SnapZone.BOTTOM_RIGHT_QUAD
            nearTop -> SnapZone.MAXIMIZE_TOP
            nearLeft -> SnapZone.LEFT_HALF
            nearRight -> SnapZone.RIGHT_HALF
            else -> SnapZone.NONE
        }
    }

    fun resolveBounds(zone: SnapZone, workArea: BoundsPx, gapPx: Int = 8): BoundsPx {
        val x = workArea.x + gapPx
        val y = workArea.y + gapPx
        val w = (workArea.width - 2 * gapPx).coerceAtLeast(120)
        val h = (workArea.height - 2 * gapPx).coerceAtLeast(120)
        val halfW = (w - gapPx) / 2
        val halfH = (h - gapPx) / 2
        val thirdW = (w - 2 * gapPx) / 3
        return when (zone) {
            SnapZone.MAXIMIZE_TOP, SnapZone.NONE -> BoundsPx(x, y, w, h)
            SnapZone.LEFT_HALF -> BoundsPx(x, y, halfW, h)
            SnapZone.RIGHT_HALF -> BoundsPx(x + halfW + gapPx, y, w - halfW - gapPx, h)
            SnapZone.TOP_LEFT_QUAD -> BoundsPx(x, y, halfW, halfH)
            SnapZone.TOP_RIGHT_QUAD -> BoundsPx(x + halfW + gapPx, y, w - halfW - gapPx, halfH)
            SnapZone.BOTTOM_LEFT_QUAD -> BoundsPx(x, y + halfH + gapPx, halfW, h - halfH - gapPx)
            SnapZone.BOTTOM_RIGHT_QUAD -> BoundsPx(x + halfW + gapPx, y + halfH + gapPx, w - halfW - gapPx, h - halfH - gapPx)
            SnapZone.CENTER_THIRD -> BoundsPx(x + thirdW + gapPx, y, thirdW, h)
        }
    }
}
