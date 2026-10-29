// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import kotlin.math.roundToInt

/**
 * **Multi-DPI Pixel-Grid Crisp Hinting Engine** (`16 px`, `20 px`, `24 px`, `32 px`, `48 px`) for
 * Fusion's three original from-scratch collection view-mode logos (`LIST` Spine-Ledger, `GRID`
 * Aperture-Mosaic, `COLUMNS` Cascading Miller-Triptych).
 *
 * Aligns 1-px / odd-px stroke centers to `(N + 0.5) px` and even-px stroke centers to `N.0 px` in
 * device pixel space across `1.0x..3.0x` display densities so horizontal/vertical strokes never
 * straddle two device pixel rows with blurry 50%-alpha anti-aliasing!
 */
object ViewModePixelGridCrispHinting {
    const val SCOPE =
        "Multi-DPI (16/20/24/32/48px at 1.0x..3.0x) half-pixel/integer-pixel stroke hinter for Fusion LIST, GRID, and COLUMNS original vector logos."

    val STANDARD_ICON_SIZES_PX = listOf(16, 20, 24, 32, 48)

    data class HintedSegmentPx(
        val x1Px: Double,
        val y1Px: Double,
        val x2Px: Double,
        val y2Px: Double,
        val integerStrokeWidthPx: Int,
        val axisAlignedCrisp: Boolean,
    )

    data class HintedIconSpec(
        val kind: FusionViewModeVectorIcons.ViewModeKind,
        val targetCanvasPx: Int,
        val hintedSegments: List<HintedSegmentPx>,
        val crispAlignmentRatio: Double,
    )

    fun hintIconForCanvasPx(kind: FusionViewModeVectorIcons.ViewModeKind, targetCanvasPx: Int): HintedIconSpec {
        require(targetCanvasPx in 12..128)
        val scale = targetCanvasPx.toDouble() / FusionViewModeVectorIcons.VIEWBOX_SIZE
        val prims = FusionViewModeVectorIcons.primitivesFor(kind)
        val hinted = ArrayList<HintedSegmentPx>()

        for (p in prims) {
            when (p) {
                is FusionViewModeVectorIcons.VectorPrimitive.Segment -> {
                    hinted.add(snapSegment(p.x1 * scale, p.y1 * scale, p.x2 * scale, p.y2 * scale, p.strokeWidth * scale))
                }
                is FusionViewModeVectorIcons.VectorPrimitive.ClosedPolygon -> {
                    val pts = p.points
                    for (i in pts.indices) {
                        val a = pts[i]
                        val b = pts[(i + 1) % pts.size]
                        hinted.add(snapSegment(a.first * scale, a.second * scale, b.first * scale, b.second * scale, p.strokeWidth * scale))
                    }
                }
            }
        }
        val axisAligned = hinted.filter { it.x1Px == it.x2Px || it.y1Px == it.y2Px }
        val crispRatio = if (axisAligned.isEmpty()) 1.0 else axisAligned.count { it.axisAlignedCrisp }.toDouble() / axisAligned.size
        return HintedIconSpec(kind, targetCanvasPx, hinted, crispRatio)
    }

    private fun snapSegment(x1: Double, y1: Double, x2: Double, y2: Double, rawStrokePx: Double): HintedSegmentPx {
        val sw = rawStrokePx.roundToInt().coerceAtLeast(1)
        val oddStroke = (sw % 2 == 1)
        fun snapCoord(v: Double): Double = if (oddStroke) kotlin.math.floor(v) + 0.5 else v.roundToInt().toDouble()

        val isVert = kotlin.math.abs(x1 - x2) < 1e-4
        val isHoriz = kotlin.math.abs(y1 - y2) < 1e-4
        return when {
            isVert -> {
                val sx = snapCoord(x1)
                HintedSegmentPx(sx, y1.roundToInt().toDouble(), sx, y2.roundToInt().toDouble(), sw, true)
            }
            isHoriz -> {
                val sy = snapCoord(y1)
                HintedSegmentPx(x1.roundToInt().toDouble(), sy, x2.roundToInt().toDouble(), sy, sw, true)
            }
            else -> HintedSegmentPx(x1, y1, x2, y2, sw, false)
        }
    }
}
