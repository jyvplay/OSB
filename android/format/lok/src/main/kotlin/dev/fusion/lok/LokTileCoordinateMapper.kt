// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

/**
 * Bounded LOK tile coordinate mapper. Maps pixel coordinates to tile grid
 * indices `(tileX, tileY)` and clips dirty rectangles to document bounds.
 */
class LokTileCoordinateMapper private constructor() {
    companion object {
        const val MAX_DOC_PX = 1_000_000
        const val MAX_TILE_PX = 4096

        data class TileCoord(val tileX: Int, val tileY: Int)
        data class Rect(val x: Int, val y: Int, val width: Int, val height: Int)

        fun pixelToTile(px: Int, py: Int, tileSizePx: Int): TileCoord {
            require(px in 0..MAX_DOC_PX && py in 0..MAX_DOC_PX) { "pixel out of bounds" }
            require(tileSizePx in 1..MAX_TILE_PX) { "tile size out of bounds" }
            return TileCoord(px / tileSizePx, py / tileSizePx)
        }

        fun clipToDocument(rect: Rect, docWidth: Int, docHeight: Int): Rect? {
            require(docWidth in 1..MAX_DOC_PX && docHeight in 1..MAX_DOC_PX) { "doc bounds invalid" }
            require(rect.width >= 0 && rect.height >= 0) { "negative rect dimensions" }
            val x1 = rect.x.coerceIn(0, docWidth)
            val y1 = rect.y.coerceIn(0, docHeight)
            val x2 = (rect.x.toLong() + rect.width.toLong()).coerceIn(0L, docWidth.toLong()).toInt()
            val y2 = (rect.y.toLong() + rect.height.toLong()).coerceIn(0L, docHeight.toLong()).toInt()
            val w = x2 - x1
            val h = y2 - y1
            if (w <= 0 || h <= 0) return null
            return Rect(x1, y1, w, h)
        }
    }
}
