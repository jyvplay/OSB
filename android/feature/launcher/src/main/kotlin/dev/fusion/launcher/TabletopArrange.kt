// SPDX-License-Identifier: Apache-2.0
package dev.fusion.launcher

import java.util.Collections
import kotlin.math.round

/**
 * Arrangement helpers for the tabletop: snap-to-grid, tidy rows and first-free-slot
 * placement. Pure geometry over caller-supplied id/point pairs — it never mutates a
 * document; the caller applies results through [TabletopEngine] actions so revision,
 * pile and capacity rules still govern. Finite grids only; "infinite" pinning stays
 * refused by the engine's 512-pin cap.
 */
object TabletopArrange {
    const val GRID = 88.0
    const val MAX_SCAN_CELLS = 4096

    fun snap(point: DeskPoint): DeskPoint {
        val x = (round(point.x / GRID) * GRID).coerceIn(-DeskPoint.LIMIT, DeskPoint.LIMIT)
        val y = (round(point.y / GRID) * GRID).coerceIn(-DeskPoint.LIMIT, DeskPoint.LIMIT)
        return DeskPoint(x, y)
    }

    /**
     * Row-major tidy layout: stable sort by (y, x), then assign grid slots left to right,
     * top to bottom starting at [origin]. Returns id → snapped point. Input order never
     * affects output for equal points (id tiebreak), so tidy is deterministic.
     */
    fun tidy(items: List<Pair<Long, DeskPoint>>, columns: Int, origin: DeskPoint = DeskPoint(0.0, 0.0)): Map<Long, DeskPoint> {
        require(columns in 1..64 && items.size in 1..TabletopScene.MAX_PINS)
        require(items.map { it.first }.distinct().size == items.size) { "Duplicate id" }
        val ordered = items.sortedWith(compareBy({ it.second.y }, { it.second.x }, { it.first }))
        val out = LinkedHashMap<Long, DeskPoint>()
        ordered.forEachIndexed { i, (id, _) ->
            val x = origin.x + (i % columns) * GRID
            val y = origin.y + (i / columns) * GRID
            require(kotlin.math.abs(x) <= DeskPoint.LIMIT && kotlin.math.abs(y) <= DeskPoint.LIMIT) { "Tidy grid exceeds the finite workspace" }
            out[id] = DeskPoint(x, y)
        }
        return Collections.unmodifiableMap(out)
    }

    /**
     * First free grid cell scanning rows from [origin] within [columns]×bounded rows.
     * Occupancy is exact-point equality on snapped inputs. Returns null when the bounded
     * scan finds no free cell — never an unbounded search, never a fabricated position.
     */
    fun firstFree(occupied: Set<DeskPoint>, columns: Int, origin: DeskPoint = DeskPoint(0.0, 0.0)): DeskPoint? {
        require(columns in 1..64 && occupied.size <= TabletopScene.MAX_PINS)
        val rows = (MAX_SCAN_CELLS + columns - 1) / columns
        for (r in 0 until rows) for (c in 0 until columns) {
            val x = origin.x + c * GRID
            val y = origin.y + r * GRID
            if (kotlin.math.abs(x) > DeskPoint.LIMIT || kotlin.math.abs(y) > DeskPoint.LIMIT) return null
            val cell = DeskPoint(x, y)
            if (cell !in occupied) return cell
        }
        return null
    }
}
