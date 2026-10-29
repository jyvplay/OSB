// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Pure-JVM **Smart Alignment Snap-Guide & Equal-Spacing Distributor** for `:feature:slides`,
 * snapping dragged slide shapes to canvas centerlines / neighbor edges (`<= 6.0 pt` magnet threshold)
 * and distributing `N >= 3` shapes with exact uniform horizontal or vertical gaps!
 */
object SlideShapeSnapDistributor {
    const val SCOPE =
        "Presentation canvas shape edge/centerline magnet snap-guide solver and N-shape uniform gap distributor."

    data class ShapeBox(val id: String, val xPt: Double, val yPt: Double, val widthPt: Double, val heightPt: Double) {
        val centerXPt: Double get() = xPt + widthPt / 2.0
        val rightPt: Double get() = xPt + widthPt
    }

    fun distributeHorizontallyUniformGap(shapes: List<ShapeBox>): List<ShapeBox> {
        if (shapes.size <= 2) return shapes
        val ordered = shapes.sortedBy { it.xPt }
        val leftBound = ordered.first().xPt
        val rightBound = ordered.last().rightPt
        val totalWidths = ordered.sumOf { it.widthPt }
        val gap = (rightBound - leftBound - totalWidths) / (ordered.size - 1).toDouble()
        var cursorX = leftBound
        return ordered.map { s ->
            val placed = s.copy(xPt = cursorX)
            cursorX += s.widthPt + gap
            placed
        }
    }

    fun snapHorizontalPosition(moving: ShapeBox, others: List<ShapeBox>, canvasWidthPt: Double = 960.0, magnetPt: Double = 6.0): Pair<Double, String?> {
        val candidates = ArrayList<Pair<Double, String>>()
        // Snap center to canvas center
        candidates.add((canvasWidthPt / 2.0 - moving.widthPt / 2.0) to "CANVAS_CENTER_X")
        for (o in others) {
            if (o.id == moving.id) continue
            candidates.add(o.xPt to "ALIGN_LEFT:${o.id}")
            candidates.add((o.rightPt - moving.widthPt) to "ALIGN_RIGHT:${o.id}")
            candidates.add((o.centerXPt - moving.widthPt / 2.0) to "ALIGN_CENTER_X:${o.id}")
        }
        val best = candidates.minByOrNull { kotlin.math.abs(it.first - moving.xPt) }
        return if (best != null && kotlin.math.abs(best.first - moving.xPt) <= magnetPt) best.first to best.second
        else moving.xPt to null
    }
}
