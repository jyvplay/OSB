// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import kotlin.math.pow

/**
 * Bounded WCAG 2.1 relative luminance and contrast ratio calculator.
 */
class ContrastRatioChecker private constructor() {
    companion object {
        fun channelLuminance(c8: Int): Double {
            require(c8 in 0..255) { "channel out of bounds: $c8" }
            val s = c8 / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }

        fun relativeLuminance(r: Int, g: Int, b: Int): Double =
            0.2126 * channelLuminance(r) + 0.7152 * channelLuminance(g) + 0.0722 * channelLuminance(b)

        fun contrastRatio(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Double {
            val l1 = relativeLuminance(r1, g1, b1)
            val l2 = relativeLuminance(r2, g2, b2)
            val lighter = if (l1 >= l2) l1 else l2
            val darker = if (l1 >= l2) l2 else l1
            return (lighter + 0.05) / (darker + 0.05)
        }

        fun passesWcagAA(ratio: Double, largeText: Boolean = false): Boolean =
            ratio >= if (largeText) 3.0 else 4.5
    }
}
