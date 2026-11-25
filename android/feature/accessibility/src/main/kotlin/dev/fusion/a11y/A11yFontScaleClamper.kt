// SPDX-License-Identifier: Apache-2.0
package dev.fusion.a11y

/**
 * Bounded accessibility font scale clamper [0.85, 2.0].
 */
class A11yFontScaleClamper private constructor() {
    companion object {
        const val MIN_SCALE = 0.85
        const val MAX_SCALE = 2.0

        fun clamp(scale: Double): Double {
            require(scale.isFinite()) { "non-finite scale" }
            return scale.coerceIn(MIN_SCALE, MAX_SCALE)
        }
    }
}
