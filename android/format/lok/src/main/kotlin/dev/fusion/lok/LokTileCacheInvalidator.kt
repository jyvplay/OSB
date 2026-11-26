// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

/**
 * Production-grade LibreOfficeKit (LOK) tile-grid cache key & dirty-rect invalidator.
 */
class LokTileCacheInvalidator private constructor() {
    companion object {
        data class TileKey(val part: Int, val tileX: Int, val tileY: Int)

        fun intersectingTiles(
            part: Int,
            dirtyX: Int,
            dirtyY: Int,
            dirtyW: Int,
            dirtyH: Int,
            tileSize: Int = 256
        ): List<TileKey> {
            require(tileSize > 0 && dirtyW >= 0 && dirtyH >= 0) { "invalid dirty rect" }
            if (dirtyW == 0 || dirtyH == 0) return emptyList()
            val minTx = (dirtyX.coerceAtLeast(0)) / tileSize
            val maxTx = (dirtyX + dirtyW - 1).coerceAtLeast(0) / tileSize
            val minTy = (dirtyY.coerceAtLeast(0)) / tileSize
            val maxTy = (dirtyY + dirtyH - 1).coerceAtLeast(0) / tileSize
            val out = mutableListOf<TileKey>()
            for (ty in minTy..maxTy) {
                for (tx in minTx..maxTx) {
                    out.add(TileKey(part, tx, ty))
                }
            }
            return out
        }
    }
}
