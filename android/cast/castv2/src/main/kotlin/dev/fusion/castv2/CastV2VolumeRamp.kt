// SPDX-License-Identifier: Apache-2.0
package dev.fusion.castv2

/**
 * Bounded CastV2 volume step calculator clamped to [0.0, 1.0].
 */
class CastV2VolumeRamp private constructor() {
    companion object {
        fun step(current: Double, delta: Double): Double {
            require(current in 0.0..1.0 && delta.isFinite()) { "invalid volume" }
            return (current + delta).coerceIn(0.0, 1.0)
        }
    }
}
