// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

import kotlin.math.abs

/**
 * Bounded LOK zoom scale quantizer snapping continuous zoom to discrete levels.
 */
class LokZoomScaleQuantizer private constructor() {
    companion object {
        val LEVELS = listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0, 4.0)

        fun quantize(zoom: Double): Double {
            require(zoom.isFinite() && zoom > 0.0) { "invalid zoom" }
            var best = LEVELS[0]
            var bestDist = abs(zoom - best)
            for (lvl in LEVELS) {
                val d = abs(zoom - lvl)
                if (d < bestDist) {
                    best = lvl
                    bestDist = d
                }
            }
            return best
        }
    }
}
