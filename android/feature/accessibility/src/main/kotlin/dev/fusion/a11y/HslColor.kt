// SPDX-License-Identifier: Apache-2.0
package dev.fusion.a11y

/**
 * HSL -> sRGB conversion pinned to the CSS Color Module Level 4
 * hsl-to-rgb algorithm (W3C, section "HSL Colors"):
 *
 *   f(n) = L - a * max(-1, min(k - 3, 9 - k, 1))
 *   where k = (n + H/30) mod 12 and a = S * min(L, 1 - L)
 *   channels: R = f(0), G = f(8), B = f(4)
 *
 * with H in degrees (any real value, wrapped mod 360 including
 * negatives), S and L as fractions 0..1. Output channels are fractions
 * 0..1. This is mathematically the same bi-hexcone model as Python's
 * colorsys.hls_to_rgb, which serves as the independent cross-language
 * verifier.
 *
 * The companion sRGB relative-luminance / contrast code in this module
 * (ContrastRatio) is NOT duplicated here; this kernel only converts.
 * Pure JVM math; refusal is IllegalArgumentException.
 */
object HslColor {
    data class Rgb(val r: Double, val g: Double, val b: Double)

    fun hslToRgb(hDegrees: Double, s: Double, l: Double): Rgb {
        require(hDegrees.isFinite()) { "non-finite hue" }
        require(s in 0.0..1.0) { "saturation out of 0..1: $s" }
        require(l in 0.0..1.0) { "lightness out of 0..1: $l" }
        val h = ((hDegrees % 360.0) + 360.0) % 360.0
        val a = s * minOf(l, 1.0 - l)
        fun f(n: Double): Double {
            val k = (n + h / 30.0) % 12.0
            return l - a * maxOf(-1.0, minOf(k - 3.0, 9.0 - k, 1.0))
        }
        return Rgb(f(0.0), f(8.0), f(4.0))
    }

    /** Convenience: 8-bit channels, round-half-up like CSS serialization. */
    fun hslToRgb8(hDegrees: Double, s: Double, l: Double): Triple<Int, Int, Int> {
        val c = hslToRgb(hDegrees, s, l)
        fun q(x: Double) = Math.floor(x * 255.0 + 0.5).toInt().coerceIn(0, 255)
        return Triple(q(c.r), q(c.g), q(c.b))
    }
}
