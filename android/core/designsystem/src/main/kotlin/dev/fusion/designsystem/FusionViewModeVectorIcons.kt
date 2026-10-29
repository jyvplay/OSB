// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import java.util.Locale

/**
 * **100% Original, Clean-Room Mathematical Vector Logos** for Fusion Shell's three universal
 * collection view modes: **`LIST`**, **`GRID`**, and **`COLUMNS`**.
 *
 * Designed from first geometric principles on a canonical `24.0 x 24.0` coordinate system —
 * deliberately distinct from standard Material/SF-Symbols/Lucide/Heroicons stock icons while
 * remaining instantaneously self-explanatory at `16 px`, `24 px`, and `48 px`:
 *
 * 1. **`LIST` ("Fusion Spine-Ledger")**: A continuous left binding-spine (`x = 3.6`, `y = 3.8..20.2`)
 *    bearing three angled chevron-diamond index ticks (`y = 6.5, 12.0, 17.5`) paired with three
 *    asymmetric typographic measure baselines (`x = 8.4..20.6`, `8.4..17.4`, `8.4..19.4`).
 * 2. **`GRID` ("Fusion Aperture-Mosaic")**: Four chamfered card tiles (`dog-ear` folded top-right
 *    corners on each quadrant tile) arranged around a central `1.5`-radius diamond alignment reticle
 *    at `(12.0, 12.0)` — immediately communicating 2D spatial media/document cards.
 * 3. **`COLUMNS` ("Fusion Cascading Miller-Triptych")**: Three distinct vertical pane chambers
 *    (`[2.8..7.8]`, `[9.5..14.5]`, `[16.2..21.2]`) with graduated active-header bands AND two explicit
 *    left-to-right directional drill-down bridges (`7.8 -> 9.5` at `y = 9.2`, `14.5 -> 16.2` at `y = 14.2`)
 *    depicting parent-to-child folder cascade across columns.
 */
object FusionViewModeVectorIcons {
    const val SCOPE =
        "Original from-scratch 24x24 mathematical vector primitives & SVG path serializer for LIST (Spine-Ledger), GRID (Aperture-Mosaic), and COLUMNS (Cascading Miller-Triptych) buttons across Files, Recall, Launcher, and Workbench."

    const val VIEWBOX_SIZE = 24.0

    enum class ViewModeKind(val buttonLabel: String, val designCodename: String) {
        LIST("List", "Fusion Spine-Ledger"),
        GRID("Grid", "Fusion Aperture-Mosaic"),
        COLUMNS("Columns", "Fusion Cascading Miller-Triptych"),
    }

    sealed interface VectorPrimitive {
        data class Segment(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val strokeWidth: Double = 1.8) : VectorPrimitive
        data class ClosedPolygon(val points: List<Pair<Double, Double>>, val filled: Boolean, val strokeWidth: Double = 1.6) : VectorPrimitive
    }

    fun primitivesFor(kind: ViewModeKind): List<VectorPrimitive> = when (kind) {
        ViewModeKind.LIST -> listOf(
            // Continuous vertical reading spine on left
            VectorPrimitive.Segment(3.6, 3.8, 3.6, 20.2, strokeWidth = 1.9),
            // Three diamond index ticks anchored along spine + staggered ruled text baselines
            diamondTick(5.4, 6.5, r = 1.15),
            VectorPrimitive.Segment(8.2, 6.5, 20.6, 6.5, strokeWidth = 1.9),
            diamondTick(5.4, 12.0, r = 1.15),
            VectorPrimitive.Segment(8.2, 12.0, 17.4, 12.0, strokeWidth = 1.9),
            diamondTick(5.4, 17.5, r = 1.15),
            VectorPrimitive.Segment(8.2, 17.5, 19.4, 17.5, strokeWidth = 1.9),
        )

        ViewModeKind.GRID -> listOf(
            // Four dog-ear chamfered card tiles + central diamond registration reticle
            chamferedTile(3.2, 3.2, 9.8, 9.8, chamfer = 1.9),
            chamferedTile(14.2, 3.2, 20.8, 9.8, chamfer = 1.9),
            chamferedTile(3.2, 14.2, 9.8, 20.8, chamfer = 1.9),
            chamferedTile(14.2, 14.2, 20.8, 20.8, chamfer = 1.9),
            diamondTick(12.0, 12.0, r = 1.55),
        )

        ViewModeKind.COLUMNS -> listOf(
            // Three Miller-column chambers (graduated vertical cadence)
            rectOutline(2.8, 4.0, 7.8, 20.0),
            VectorPrimitive.Segment(2.8, 7.0, 7.8, 7.0, strokeWidth = 1.6),   // Col 1 header bar
            VectorPrimitive.Segment(4.0, 9.2, 6.8, 9.2, strokeWidth = 1.6),   // Selected parent item
            // Bridge 1 -> 2
            VectorPrimitive.Segment(7.8, 9.2, 9.5, 9.2, strokeWidth = 1.8),

            rectOutline(9.5, 4.8, 14.5, 20.0),
            VectorPrimitive.Segment(9.5, 7.6, 14.5, 7.6, strokeWidth = 1.6),  // Col 2 header bar
            VectorPrimitive.Segment(10.7, 14.2, 13.5, 14.2, strokeWidth = 1.6), // Selected subfolder item
            // Bridge 2 -> 3
            VectorPrimitive.Segment(14.5, 14.2, 16.2, 14.2, strokeWidth = 1.8),

            rectOutline(16.2, 5.6, 21.2, 20.0),
            VectorPrimitive.Segment(16.2, 8.2, 21.2, 8.2, strokeWidth = 1.6), // Col 3 header bar
            diamondTick(18.7, 14.2, r = 1.05),                                 // Active leaf preview node
        )
    }

    fun toCleanSvgMarkup(kind: ViewModeKind, strokeHex: String = "#5CE1C6"): String {
        val prims = primitivesFor(kind)
        return buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" width=\"24\" height=\"24\" ")
            append("data-fusion-icon=\"").append(kind.name.lowercase()).append("\" aria-label=\"").append(kind.buttonLabel).append("\">")
            for (p in prims) {
                when (p) {
                    is VectorPrimitive.Segment -> append(
                        String.format(
                            Locale.US,
                            "<line x1=\"%.2f\" y1=\"%.2f\" x2=\"%.2f\" y2=\"%.2f\" stroke=\"%s\" stroke-width=\"%.2f\" stroke-linecap=\"round\"/>",
                            p.x1, p.y1, p.x2, p.y2, strokeHex, p.strokeWidth,
                        )
                    )
                    is VectorPrimitive.ClosedPolygon -> {
                        val pts = p.points.joinToString(" ") { (x, y) -> String.format(Locale.US, "%.2f,%.2f", x, y) }
                        val fill = if (p.filled) strokeHex else "none"
                        append(
                            String.format(
                                Locale.US,
                                "<polygon points=\"%s\" fill=\"%s\" stroke=\"%s\" stroke-width=\"%.2f\" stroke-linejoin=\"round\"/>",
                                pts, fill, strokeHex, p.strokeWidth,
                            )
                        )
                    }
                }
            }
            append("</svg>")
        }
    }

    /** Verifies every coordinate lies strictly inside `[1.5, 22.5]` (`>= 1.5px` anti-clipping pad) and icons are pairwise distinct. */
    fun verifyGeometricIntegrity(): Boolean {
        val svgs = ViewModeKind.entries.map { kind ->
            val prims = primitivesFor(kind)
            if (prims.size < 4) return false
            for (p in prims) {
                val coords = when (p) {
                    is VectorPrimitive.Segment -> listOf(p.x1 to p.y1, p.x2 to p.y2)
                    is VectorPrimitive.ClosedPolygon -> p.points
                }
                if (coords.any { (x, y) -> x !in 1.5..22.5 || y !in 1.5..22.5 }) return false
            }
            toCleanSvgMarkup(kind)
        }
        return svgs.distinct().size == 3
    }

    private fun diamondTick(cx: Double, cy: Double, r: Double): VectorPrimitive.ClosedPolygon =
        VectorPrimitive.ClosedPolygon(
            points = listOf(cx to (cy - r), (cx + r) to cy, cx to (cy + r), (cx - r) to cy),
            filled = true,
            strokeWidth = 1.2,
        )

    private fun chamferedTile(x1: Double, y1: Double, x2: Double, y2: Double, chamfer: Double): VectorPrimitive.ClosedPolygon =
        VectorPrimitive.ClosedPolygon(
            points = listOf(x1 to y1, (x2 - chamfer) to y1, x2 to (y1 + chamfer), x2 to y2, x1 to y2),
            filled = false,
            strokeWidth = 1.65,
        )

    private fun rectOutline(x1: Double, y1: Double, x2: Double, y2: Double): VectorPrimitive.ClosedPolygon =
        VectorPrimitive.ClosedPolygon(
            points = listOf(x1 to y1, x2 to y1, x2 to y2, x1 to y2),
            filled = false,
            strokeWidth = 1.6,
        )
}
