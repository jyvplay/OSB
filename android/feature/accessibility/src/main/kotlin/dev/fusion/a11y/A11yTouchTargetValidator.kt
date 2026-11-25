// SPDX-License-Identifier: Apache-2.0
package dev.fusion.a11y

/**
 * Bounded WCAG/Android 48dp x 48dp touch target validator.
 */
class A11yTouchTargetValidator private constructor() {
    companion object {
        const val MIN_DP = 48.0

        fun meetsMinimum(widthDp: Double, heightDp: Double): Boolean {
            require(widthDp.isFinite() && heightDp.isFinite()) { "non-finite dimension" }
            require(widthDp >= 0.0 && heightDp >= 0.0) { "negative dimension" }
            return widthDp >= MIN_DP && heightDp >= MIN_DP
        }
    }
}
