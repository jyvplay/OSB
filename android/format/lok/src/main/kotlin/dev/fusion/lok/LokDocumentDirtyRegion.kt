// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

/**
 * Bounded LOK dirty-rectangle union merger.
 */
class LokDocumentDirtyRegion private constructor() {
    companion object {
        data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

        fun union(a: Rect, b: Rect): Rect {
            require(a.w >= 0 && a.h >= 0 && b.w >= 0 && b.h >= 0) { "negative dimensions" }
            val x1 = minOf(a.x, b.x)
            val y1 = minOf(a.y, b.y)
            val x2 = maxOf(a.x + a.w, b.x + b.w)
            val y2 = maxOf(a.y + a.h, b.y + b.h)
            return Rect(x1, y1, x2 - x1, y2 - y1)
        }
    }
}
