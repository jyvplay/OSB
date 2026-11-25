// SPDX-License-Identifier: Apache-2.0
package dev.fusion.a11y

/**
 * Bounded WCAG relative luminance light/dark background classifier.
 */
class A11yLuminanceThreshold private constructor() {
    companion object {
        const val THRESHOLD = 0.179

        fun isLightBackground(relativeLuminance: Double): Boolean {
            require(relativeLuminance in 0.0..1.0) { "luminance out of bounds" }
            return relativeLuminance >= THRESHOLD
        }
    }
}
