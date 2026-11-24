// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

/**
 * Bounded display refresh rate selector. Picks the supported refresh rate
 * closest to [targetHz] within `[minHz, maxHz]`, breaking ties toward higher Hz.
 */
class DisplayRefreshRateSelector private constructor() {
    companion object {
        const val MAX_MODES = 64

        fun select(supportedHz: List<Int>, targetHz: Int, minHz: Int = 24, maxHz: Int = 240): Int? {
            require(supportedHz.size <= MAX_MODES) { "too many modes" }
            require(minHz in 1..maxHz) { "invalid Hz bounds" }
            val candidates = supportedHz.filter { it in minHz..maxHz }
            if (candidates.isEmpty()) return null
            var best = candidates[0]
            var bestDist = kotlin.math.abs(best - targetHz)
            for (hz in candidates) {
                val dist = kotlin.math.abs(hz - targetHz)
                if (dist < bestDist || (dist == bestDist && hz > best)) {
                    best = hz
                    bestDist = dist
                }
            }
            return best
        }
    }
}
