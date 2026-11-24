// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

/**
 * Bounded window snap grid solver. Snaps a floating window's edges to
 * work-area boundaries when within [snapThresholdPx].
 */
class WindowSnapGridSolver private constructor() {
    companion object {
        const val MAX_COORD = 32768

        data class WindowRect(val x: Int, val y: Int, val width: Int, val height: Int)

        fun snapToWorkArea(
            win: WindowRect,
            workArea: WindowRect,
            snapThresholdPx: Int
        ): WindowRect {
            require(win.width > 0 && win.height > 0) { "invalid window size" }
            require(workArea.width > 0 && workArea.height > 0) { "invalid workArea size" }
            require(snapThresholdPx >= 0) { "negative snapThresholdPx" }

            var x = win.x
            var y = win.y
            val waRight = workArea.x + workArea.width
            val waBottom = workArea.y + workArea.height

            if (kotlin.math.abs(x - workArea.x) <= snapThresholdPx) {
                x = workArea.x
            } else if (kotlin.math.abs((x + win.width) - waRight) <= snapThresholdPx) {
                x = waRight - win.width
            }

            if (kotlin.math.abs(y - workArea.y) <= snapThresholdPx) {
                y = workArea.y
            } else if (kotlin.math.abs((y + win.height) - waBottom) <= snapThresholdPx) {
                y = waBottom - win.height
            }

            return WindowRect(x, y, win.width, win.height)
        }
    }
}
