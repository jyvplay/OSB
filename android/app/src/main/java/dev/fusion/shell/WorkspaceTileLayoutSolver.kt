// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

/**
 * Bounded responsive workspace tile layout solver. Computes column count,
 * row count, and tile dimensions for a desktop/tabletop grid given viewport
 * size, minimum tile width, and spacing.
 */
class WorkspaceTileLayoutSolver private constructor() {
    companion object {
        const val MAX_TILES = 1024
        const val MAX_VIEWPORT_PX = 16384

        data class GridLayout(
            val columns: Int,
            val rows: Int,
            val tileWidthPx: Int,
            val tileHeightPx: Int
        )

        fun solve(
            tileCount: Int,
            viewportWidthPx: Int,
            minTileWidthPx: Int,
            gapPx: Int,
            aspectRatio: Double = 1.0
        ): GridLayout {
            require(tileCount in 0..MAX_TILES) { "tileCount out of bounds" }
            require(viewportWidthPx in 1..MAX_VIEWPORT_PX) { "viewportWidthPx out of bounds" }
            require(minTileWidthPx in 1..viewportWidthPx) { "minTileWidthPx out of bounds" }
            require(gapPx >= 0 && gapPx < viewportWidthPx) { "gapPx out of bounds" }
            require(aspectRatio > 0.0 && aspectRatio.isFinite()) { "invalid aspectRatio" }

            if (tileCount == 0) return GridLayout(1, 0, viewportWidthPx, (viewportWidthPx / aspectRatio).toInt().coerceAtLeast(1))
            val cols = ((viewportWidthPx + gapPx) / (minTileWidthPx + gapPx)).coerceAtLeast(1)
            val rows = (tileCount + cols - 1) / cols
            val totalGap = (cols - 1) * gapPx
            val tileW = ((viewportWidthPx - totalGap) / cols).coerceAtLeast(1)
            val tileH = (tileW / aspectRatio).toInt().coerceAtLeast(1)
            return GridLayout(cols, rows, tileW, tileH)
        }
    }
}
