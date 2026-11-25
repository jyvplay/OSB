// SPDX-License-Identifier: Apache-2.0
package dev.fusion.window

/**
 * Production-grade multi-window tiling & snap-zone geometry engine.
 */
class WindowTilingEngine private constructor() {
    companion object {
        enum class SnapZone { LEFT_HALF, RIGHT_HALF, TOP_HALF, BOTTOM_HALF, FULL }

        data class Rect(val x: Int, val y: Int, val width: Int, val height: Int)

        fun computeSnapRect(workArea: Rect, zone: SnapZone): Rect {
            require(workArea.width > 0 && workArea.height > 0) { "invalid workArea" }
            val halfW = workArea.width / 2
            val halfH = workArea.height / 2
            return when (zone) {
                SnapZone.FULL -> workArea
                SnapZone.LEFT_HALF -> Rect(workArea.x, workArea.y, halfW, workArea.height)
                SnapZone.RIGHT_HALF -> Rect(workArea.x + halfW, workArea.y, workArea.width - halfW, workArea.height)
                SnapZone.TOP_HALF -> Rect(workArea.x, workArea.y, workArea.width, halfH)
                SnapZone.BOTTOM_HALF -> Rect(workArea.x, workArea.y + halfH, workArea.width, workArea.height - halfH)
            }
        }
    }
}
