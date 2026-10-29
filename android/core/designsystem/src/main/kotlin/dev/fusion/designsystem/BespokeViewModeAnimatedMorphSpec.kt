// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * R54 original from-scratch **morphing** `List` / `Grid` / `Columns` vector iconography spec.
 *
 * All three glyphs share one 24×24 unit grid and are drawn as stroke paths whose *endpoints interpolate*
 * between views, so switching List→Grid→Columns animates geometrically instead of cross-fading. Strokes are
 * half-pixel hinted (`x.0`/`x.5`) so `1dp`/`1.5dp` strokes stay crisp at `1.0x..3.0x` density.
 *
 * Glyph definitions (original, authored here):
 * - **List**: three full-width rows `y = 7, 12, 17` with a leading `3×3` bullet at `x = 4`.
 * - **Grid**: 2×2 tiles as rounded squares inset by `2` with `gap = 3`.
 * - **Columns**: three vertical bars at `x = 5, 11, 17`, `y` from `4` to `20`, rounded caps.
 */
object BespokeViewModeAnimatedMorphSpec {
    enum class ViewMode { LIST, GRID, COLUMNS }

    data class StrokePath(val id: String, val x1: Float, val y1: Float, val x2: Float, val y2: Float, val strokeDp: Float)

    data class IconSpec(val viewMode: ViewMode, val viewBox: Float, val paths: List<StrokePath>) {
        val pathCount: Int get() = paths.size
    }

    const val VIEW_BOX = 24f
    const val STROKE_DP = 1.5f

    val LIST: IconSpec = IconSpec(
        ViewMode.LIST,
        VIEW_BOX,
        listOf(
            StrokePath("bullet-0", 4f, 7f, 4f, 7f, 3f),
            StrokePath("row-0", 9f, 7f, 20f, 7f, STROKE_DP),
            StrokePath("bullet-1", 4f, 12f, 4f, 12f, 3f),
            StrokePath("row-1", 9f, 12f, 20f, 12f, STROKE_DP),
            StrokePath("bullet-2", 4f, 17f, 4f, 17f, 3f),
            StrokePath("row-2", 9f, 17f, 20f, 17f, STROKE_DP),
        ),
    )

    val GRID: IconSpec = IconSpec(
        ViewMode.GRID,
        VIEW_BOX,
        listOf(
            StrokePath("tile-0", 4f, 4f, 10f, 10f, STROKE_DP),
            StrokePath("tile-1", 14f, 4f, 20f, 10f, STROKE_DP),
            StrokePath("tile-2", 4f, 14f, 10f, 20f, STROKE_DP),
            StrokePath("tile-3", 14f, 14f, 20f, 20f, STROKE_DP),
        ),
    )

    val COLUMNS: IconSpec = IconSpec(
        ViewMode.COLUMNS,
        VIEW_BOX,
        listOf(
            StrokePath("col-0", 5f, 4f, 5f, 20f, STROKE_DP),
            StrokePath("col-1", 11f, 4f, 11f, 20f, STROKE_DP),
            StrokePath("col-2", 17f, 4f, 17f, 20f, STROKE_DP),
        ),
    )

    fun specOf(mode: ViewMode): IconSpec = when (mode) {
        ViewMode.LIST -> LIST
        ViewMode.GRID -> GRID
        ViewMode.COLUMNS -> COLUMNS
    }

    /** Pairs the longer path list with the shorter one so every interpolated frame is well-defined. */
    private fun paired(from: IconSpec, to: IconSpec): List<Pair<StrokePath, StrokePath>> {
        val n = max(from.paths.size, to.paths.size)
        return (0 until n).map { i ->
            val a = from.paths[i % from.paths.size]
            val b = to.paths[i % to.paths.size]
            a to b
        }
    }

    fun interpolate(from: ViewMode, to: ViewMode, fraction: Float): IconSpec {
        require(fraction in 0f..1f) { "fraction must be in [0,1], got $fraction" }
        val t = easeInOutCubic(fraction)
        val pairs = paired(specOf(from), specOf(to))
        val paths = pairs.map { (a, b) ->
            StrokePath(
                id = if (t < 0.5f) a.id else b.id,
                x1 = hint(lerp(a.x1, b.x1, t)),
                y1 = hint(lerp(a.y1, b.y1, t)),
                x2 = hint(lerp(a.x2, b.x2, t)),
                y2 = hint(lerp(a.y2, b.y2, t)),
                strokeDp = lerp(a.strokeDp, b.strokeDp, t),
            )
        }
        val stroke = lerp(specOf(from).paths.first().strokeDp, specOf(to).paths.first().strokeDp, t)
        return IconSpec(if (t < 0.5f) from else to, VIEW_BOX, paths).copy(paths = paths).let {
            IconSpec(it.viewMode, it.viewBox, it.paths.map { p -> p.copy(strokeDp = if (p.strokeDp > 2f) p.strokeDp else stroke) })
        }
    }

    fun easeInOutCubic(t: Float): Float =
        if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Half-pixel snapping (x.0 / x.5) keeps `1dp`/`1.5dp` strokes crisp on integer-scale densities. */
    fun hint(v: Float): Float = (floor(v * 2f + 0.5f)) / 2f

    /** Crispness ratio: 1.0 when the stroke width is an exact half-pixel multiple at the target density. */
    fun crispAlignmentRatio(strokeDp: Float, densityScale: Float): Float {
        require(densityScale > 0f) { "density must be positive" }
        val devicePx = strokeDp * densityScale
        val nearestHalf = floor(devicePx * 2f + 0.5f) / 2f
        return if (devicePx == 0f) 1f else min(1f, 1f - kotlin.math.abs(nearestHalf - devicePx) / devicePx)
    }

    data class Sizing(val sizeDp: Int, val strokeDp: Float, val centreSnapped: Boolean)

    fun sizeFor(canvasPx: Int, densityScale: Float): Sizing {
        require(canvasPx in 16..48) { "icon canvas must be 16..48 px, got $canvasPx" }
        val sizeDp = (canvasPx / densityScale).let { if (it < 20f) 20 else if (it > 32f) 32 else 24 }
        val stroke = if (canvasPx <= 20) 1.25f else STROKE_DP
        val snapped = (canvasPx % 2 == 0)
        return Sizing(sizeDp, stroke, snapped)
    }

    fun viewModeOrder(): List<ViewMode> = ViewMode.entries.toList()

    fun nextInCycle(current: ViewMode): ViewMode {
        val order = viewModeOrder()
        return order[(order.indexOf(current) + 1) % order.size]
    }
}
