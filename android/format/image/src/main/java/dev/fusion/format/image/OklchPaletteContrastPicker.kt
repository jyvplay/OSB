// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.image

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Clean-room perceptual `sRGB <-> OKLab <-> OKLCH` color picker & WCAG 2.1 contrast solver (`MD-07`,
 * derived from Björn Ottosson's public-domain / MIT OKLab matrix formulation).
 */
object OklchPaletteContrastPicker {
    const val SCOPE =
        "Pure-JVM sRGB <-> OKLab <-> OKLCH perceptual color space converter, sRGB gamut clamp, and WCAG 2.1 contrast-ratio solver (MD-07)."

    data class Oklch(val l: Double, val c: Double, val hDeg: Double)

    fun srgbToOklch(r8: Int, g8: Int, b8: Int): Oklch {
        val r = srgbToLinear(r8 / 255.0)
        val g = srgbToLinear(g8 / 255.0)
        val b = srgbToLinear(b8 / 255.0)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        val bigL = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
        val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val ob = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        val chroma = sqrt(a * a + ob * ob)
        val hue = if (chroma < 1e-9) 0.0 else ((Math.toDegrees(atan2(ob, a)) % 360.0) + 360.0) % 360.0
        return Oklch(bigL.coerceIn(0.0, 1.0), chroma, hue)
    }

    fun oklchToSrgbHex(ok: Oklch): String {
        val rgb = oklchToSrgbTriplet(ok)
        return "#%02X%02X%02X".format(rgb[0], rgb[1], rgb[2])
    }

    fun oklchToSrgbTriplet(ok: Oklch): IntArray {
        var c = ok.c.coerceAtLeast(0.0)
        repeat(16) {
            val lin = oklchToLinearUnclamped(ok.l.coerceIn(0.0, 1.0), c, ok.hDeg)
            if (lin.all { it in -1e-4..1.0001 }) {
                return IntArray(3) { i -> (linearToSrgb(lin[i].coerceIn(0.0, 1.0)) * 255.0).roundToInt().coerceIn(0, 255) }
            }
            c *= 0.85
        }
        val lin = oklchToLinearUnclamped(ok.l.coerceIn(0.0, 1.0), c, ok.hDeg)
        return IntArray(3) { i -> (linearToSrgb(lin[i].coerceIn(0.0, 1.0)) * 255.0).roundToInt().coerceIn(0, 255) }
    }

    fun wcagContrastRatio(fgRgb: IntArray, bgRgb: IntArray): Double {
        val l1 = relativeLuminance(fgRgb)
        val l2 = relativeLuminance(bgRgb)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun relativeLuminance(rgb: IntArray): Double {
        val r = srgbToLinear(rgb[0] / 255.0)
        val g = srgbToLinear(rgb[1] / 255.0)
        val b = srgbToLinear(rgb[2] / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun oklchToLinearUnclamped(bigL: Double, c: Double, hDeg: Double): DoubleArray {
        val rad = Math.toRadians(hDeg)
        val a = c * cos(rad)
        val b = c * sin(rad)
        val l_ = bigL + 0.3963377774 * a + 0.2158037573 * b
        val m_ = bigL - 0.1055613458 * a - 0.0638541728 * b
        val s_ = bigL - 0.0894841775 * a - 1.2914855480 * b
        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_
        return doubleArrayOf(
            +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
    }

    private fun srgbToLinear(u: Double): Double =
        if (u <= 0.04045) u / 12.92 else ((u + 0.055) / 1.055).pow(2.4)

    private fun linearToSrgb(u: Double): Double =
        if (u <= 0.0031308) 12.92 * u else 1.055 * u.pow(1.0 / 2.4) - 0.055
}
