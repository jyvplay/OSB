// SPDX-License-Identifier: Apache-2.0
package dev.fusion.launcher

import kotlin.math.roundToInt

/**
 * Bounded launcher grid cell snapper.
 */
class LauncherGridSnapSolver private constructor() {
    companion object {
        data class GridCell(val col: Int, val row: Int)

        fun snap(xPx: Double, yPx: Double, cellW: Double, cellH: Double, cols: Int, rows: Int): GridCell {
            require(cellW > 0.0 && cellH > 0.0) { "invalid cell size" }
            require(cols > 0 && rows > 0) { "invalid grid dimensions" }
            val col = (xPx / cellW).roundToInt().coerceIn(0, cols - 1)
            val row = (yPx / cellH).roundToInt().coerceIn(0, rows - 1)
            return GridCell(col, row)
        }
    }
}
