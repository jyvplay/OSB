// SPDX-License-Identifier: Apache-2.0
package dev.fusion.design

import kotlin.math.pow

/**
 * Production-grade WCAG 2.1 sRGB relative luminance & contrast-ratio calculator.
 */
class DesignWcagContrast private constructor() {
    companion object {
        private fun linearize(channel8Bit: Int): Double {
            val c = channel8Bit.coerceIn(0, 255) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }

        fun relativeLuminance(rgb: Int): Double {
            val r = (rgb ushr 16) and 0xFF
            val g = (rgb ushr 8) and 0xFF
            val b = rgb and 0xFF
            return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
        }

        fun contrastRatio(rgb1: Int, rgb2: Int): Double {
            val l1 = relativeLuminance(rgb1)
            val l2 = relativeLuminance(rgb2)
            val hi = maxOf(l1, l2)
            val lo = minOf(l1, l2)
            return (hi + 0.05) / (lo + 0.05)
        }

        fun meetsAaNormalText(rgb1: Int, rgb2: Int): Boolean = contrastRatio(rgb1, rgb2) >= 4.5
        fun meetsAaaNormalText(rgb1: Int, rgb2: Int): Boolean = contrastRatio(rgb1, rgb2) >= 7.0
    }
}
