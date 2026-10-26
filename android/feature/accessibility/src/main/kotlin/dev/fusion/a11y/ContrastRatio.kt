// SPDX-License-Identifier: Apache-2.0
package dev.fusion.a11y

import kotlin.math.pow

/**
 * WCAG 2.x contrast math for opaque sRGB pairs: relative luminance with the
 * standard piecewise linearization, contrast ratio, and the AA/AAA thresholds
 * for normal and large text. Parses #rgb and #rrggbb only; alpha-channel forms
 * are refused because a ratio against an unknown backdrop is meaningless.
 * Reference: WCAG 2.1 guideline 1.4.3 / 1.4.6 (unchanged in 2.2).
 */
object ContrastRatio {
    const val AA_NORMAL = 4.5
    const val AA_LARGE = 3.0
    const val AAA_NORMAL = 7.0
    const val AAA_LARGE = 4.5

    fun parse(hex: String): Triple<Int, Int, Int> {
        require(hex.startsWith("#"))
        val h = hex.substring(1)
        fun hex2(s: String): Int {
            require(s.length == 2 && s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
            return s.toInt(16)
        }
        return when (h.length) {
            3 -> {
                require(h.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
                Triple(hex2("${h[0]}${h[0]}"), hex2("${h[1]}${h[1]}"), hex2("${h[2]}${h[2]}"))
            }
            6 -> Triple(hex2(h.substring(0, 2)), hex2(h.substring(2, 4)), hex2(h.substring(4, 6)))
            else -> throw IllegalArgumentException("need #rgb or #rrggbb")
        }
    }

    private fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(rgb: Triple<Int, Int, Int>): Double {
        require(rgb.first in 0..255 && rgb.second in 0..255 && rgb.third in 0..255)
        return 0.2126 * channel(rgb.first) + 0.7152 * channel(rgb.second) + 0.0722 * channel(rgb.third)
    }

    fun ratio(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Double {
        val l1 = luminance(a); val l2 = luminance(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    fun ratioHex(fg: String, bg: String): Double = ratio(parse(fg), parse(bg))

    fun threshold(largeText: Boolean, aaa: Boolean): Double = when {
        aaa && !largeText -> AAA_NORMAL
        largeText -> if (aaa) AAA_LARGE else AA_LARGE
        else -> AA_NORMAL
    }

    fun meets(fg: String, bg: String, largeText: Boolean = false, aaa: Boolean = false): Boolean =
        ratioHex(fg, bg) >= threshold(largeText, aaa)
}
