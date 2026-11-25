// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

import kotlin.math.PI

/**
 * Bounded degrees <-> radians converter.
 */
class CalcAngleModeConverter private constructor() {
    companion object {
        fun degToRad(deg: Double): Double {
            require(deg.isFinite()) { "non-finite deg" }
            return deg * (PI / 180.0)
        }

        fun radToDeg(rad: Double): Double {
            require(rad.isFinite()) { "non-finite rad" }
            return rad * (180.0 / PI)
        }
    }
}
