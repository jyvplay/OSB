// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

/**
 * Bounded window min/max size clamper.
 */
class WindowMinMaxClamp private constructor() {
    companion object {
        fun clamp(w: Int, h: Int, minW: Int, minH: Int, maxW: Int, maxH: Int): Pair<Int, Int> {
            require(minW in 1..maxW && minH in 1..maxH) { "invalid bounds" }
            return w.coerceIn(minW, maxW) to h.coerceIn(minH, maxH)
        }
    }
}
