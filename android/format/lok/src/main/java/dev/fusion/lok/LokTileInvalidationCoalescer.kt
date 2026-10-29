// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

/**
 * LibreOfficeKit (`LOK_CALLBACK_INVALIDATE_TILES`) twip-space dirty-rectangle coalescer and
 * `256×256 px` zoom-level tile-grid scheduler avoiding redundant re-rasterizations during rapid typing.
 */
object LokTileInvalidationCoalescer {
    const val SCOPE =
        "LOK_CALLBACK_INVALIDATE_TILES dirty-twip rectangle union coalescer and 256x256 tile-coordinate planner."

    data class TwipRect(val x: Int, val y: Int, val width: Int, val height: Int) {
        val right: Int get() = x + width
        val bottom: Int get() = y + height
    }

    data class TileCoord(val zoomPercent: Int, val tileCol: Int, val tileRow: Int)

    fun coalesceAndScheduleTiles(
        dirtyRects: List<TwipRect>,
        zoomPercent: Int = 100,
        tileSizePx: Int = 256,
        twipsPerPixelAt100Pct: Int = 15,
    ): Set<TileCoord> {
        require(zoomPercent in 25..400 && tileSizePx >= 64 && twipsPerPixelAt100Pct > 0)
        val valid = dirtyRects.filter { it.width > 0 && it.height > 0 }
        if (valid.isEmpty()) return emptySet()

        val merged = mergeIntersectingOrAdjacent(valid)
        val tileTwips = ((tileSizePx * twipsPerPixelAt100Pct * 100) / zoomPercent).coerceAtLeast(64)
        val scheduled = LinkedHashSet<TileCoord>()
        for (r in merged) {
            val c0 = (r.x.coerceAtLeast(0)) / tileTwips
            val c1 = ((r.right - 1).coerceAtLeast(0)) / tileTwips
            val r0 = (r.y.coerceAtLeast(0)) / tileTwips
            val r1 = ((r.bottom - 1).coerceAtLeast(0)) / tileTwips
            for (tr in r0..r1) {
                for (tc in c0..c1) {
                    scheduled.add(TileCoord(zoomPercent, tc, tr))
                }
            }
        }
        return scheduled
    }

    fun mergeIntersectingOrAdjacent(rects: List<TwipRect>): List<TwipRect> {
        val pool = rects.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            outer@ for (i in pool.indices) {
                for (j in i + 1 until pool.size) {
                    val a = pool[i]; val b = pool[j]
                    if (a.x <= b.right && b.x <= a.right && a.y <= b.bottom && b.y <= a.bottom) {
                        val nx = minOf(a.x, b.x)
                        val ny = minOf(a.y, b.y)
                        val nr = maxOf(a.right, b.right)
                        val nb = maxOf(a.bottom, b.bottom)
                        pool[i] = TwipRect(nx, ny, nr - nx, nb - ny)
                        pool.removeAt(j)
                        changed = true
                        break@outer
                    }
                }
            }
        }
        return pool
    }
}
