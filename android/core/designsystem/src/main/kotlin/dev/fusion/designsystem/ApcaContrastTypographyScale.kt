package dev.fusion.designsystem

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Clean-room WCAG 2.2 Relative-Luminance + Perceptual APCA-Style Polarity-Sensitive Lightness
 * Contrast (`Lc`) & Fluid Modular (`1.200` Minor-Third) Typography Scale Generator (Apache-2.0).
 */
object ApcaContrastTypographyScale {
    const val SCOPE =
        "WCAG 2.2 + polarity-sensitive perceptual Lc contrast evaluator + 1.200 minor-third fluid typography token scale"

    data class ContrastMetrics(
        val wcag22Ratio: Double,
        val perceptualSignedLc: Double,
        val meetsWcagAaaBody: Boolean,
        val meetsBodyLegibility: Boolean,
    )

    fun evaluatePair(textRgb24: Int, bgRgb24: Int): ContrastMetrics {
        val yTxt = srgbToY(textRgb24)
        val yBg = srgbToY(bgRgb24)
        val wcag = (max(yTxt, yBg) + 0.05) / (min(yTxt, yBg) + 0.05)

        // Polarity-aware perceptual power-curve lightness contrast (dark-on-light vs light-on-dark!)
        val yTxtClamp = if (yTxt > 0.022) yTxt else yTxt + (0.022 - yTxt).pow(1.414)
        val yBgClamp = if (yBg > 0.022) yBg else yBg + (0.022 - yBg).pow(1.414)
        val sapc = if (yBgClamp > yTxtClamp) {
            (yBgClamp.pow(0.56) - yTxtClamp.pow(0.57)) * 1.14
        } else {
            (yBgClamp.pow(0.65) - yTxtClamp.pow(0.62)) * 1.14
        }
        val lc = when {
            abs(sapc) < 0.1 -> 0.0
            sapc > 0.0 -> (sapc - 0.027) * 100.0
            else -> (sapc + 0.027) * 100.0
        }
        return ContrastMetrics(
            wcag22Ratio = wcag,
            perceptualSignedLc = lc,
            meetsWcagAaaBody = wcag >= 7.0,
            meetsBodyLegibility = abs(lc) >= 75.0 && wcag >= 4.5,
        )
    }

    fun buildModularTypeScaleSp(baseSp: Double = 16.0, ratio: Double = 1.200): Map<String, Double> {
        require(baseSp in 10.0..32.0 && ratio in 1.05..1.618)
        return linkedMapOf(
            "caption" to baseSp / ratio,
            "body" to baseSp,
            "titleSm" to baseSp * ratio,
            "titleMd" to baseSp * ratio.pow(2.0),
            "headlineSm" to baseSp * ratio.pow(3.0),
            "headlineLg" to baseSp * ratio.pow(4.0),
        )
    }

    private fun srgbToY(rgb24: Int): Double {
        fun lin(u8: Int): Double = (u8 / 255.0).pow(2.4)
        return 0.2126729 * lin((rgb24 ushr 16) and 0xFF) +
            0.7151522 * lin((rgb24 ushr 8) and 0xFF) +
            0.0721750 * lin(rgb24 and 0xFF)
    }
}
