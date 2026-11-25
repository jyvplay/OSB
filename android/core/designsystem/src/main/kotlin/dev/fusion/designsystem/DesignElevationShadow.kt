// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

/**
 * Bounded elevation dp [0..24] to shadow alpha mapper.
 */
class DesignElevationShadow private constructor() {
    companion object {
        fun shadowAlpha(elevationDp: Int): Double {
            val clamped = elevationDp.coerceIn(0, 24)
            return if (clamped == 0) 0.0 else (0.04 + (clamped / 24.0) * 0.20)
        }
    }
}
