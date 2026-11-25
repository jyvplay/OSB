// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

import kotlin.math.roundToInt

/**
 * Bounded aspect-ratio preserving window resizer.
 */
class WindowAspectRatioResizer private constructor() {
    companion object {
        fun resizeByWidth(width: Int, aspectRatio: Double, minW: Int, maxW: Int): Pair<Int, Int> {
            require(minW in 1..maxW) { "invalid width bounds" }
            require(aspectRatio > 0.0 && aspectRatio.isFinite()) { "invalid aspectRatio" }
            val w = width.coerceIn(minW, maxW)
            val h = (w / aspectRatio).roundToInt().coerceAtLeast(1)
            return w to h
        }
    }
}
