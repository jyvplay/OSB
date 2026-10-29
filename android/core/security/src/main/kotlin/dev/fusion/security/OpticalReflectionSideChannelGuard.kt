package dev.fusion.security

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Clean-room Anti-Optical-Reflection (`SpecEye` / `EyeTell` Eyeglasses & Corneal Glint)
 * Low-Specular PIN Pad Luminance Equalizer (`V19`, Apache-2.0).
 *
 * When ambient lux is low (`<= 80 lux`, where screen reflections on eyeglasses/windows peak!)
 * or high-risk privacy mode is active:
 * - Disables key-press bright flash ripple (`pressHighlightAlpha = 0.0f`),
 * - Caps background emission luminance (`<= 0.025`) while still guaranteeing readable local
 *   contrast (`>= 4.5:1` AA or `>= 7.0:1` AAA when standard mode is active),
 * - Computes corneal-reflection SNR attenuation in dB vs an unguarded bright white (`#FFFFFF`) ripple!
 */
object OpticalReflectionSideChannelGuard {
    const val SCOPE =
        "Anti-eyeglasses/corneal optical-reflection PIN pad luminance equalizer + press-flash suppressor (V19)"

    data class PadOpticalProfile(
        val stealthModeActive: Boolean,
        val backgroundRgb24: Int,
        val glyphRgb24: Int,
        val pressFlashAlpha: Float,
        val localContrastRatio: Double,
        val specularGlintReductionDb: Double,
    )

    fun computeProfile(
        ambientLux: Double,
        forceStealth: Boolean = false,
    ): PadOpticalProfile {
        require(ambientLux >= 0.0)
        val stealth = forceStealth || ambientLux <= 80.0
        val bg = 0x0B0F17 // deep low-radiance slate
        // In stealth mode, avoid 255-white specular bursts while preserving >= 4.8:1 legibility for close-range user
        val fg = if (stealth) 0x7B8BA3 else 0xF8FAFC
        val flashAlpha = if (stealth) 0.0f else 0.14f

        val bgLum = relativeLum(bg)
        val fgLum = relativeLum(fg)
        val contrast = (max(fgLum, bgLum) + 0.05) / (min(fgLum, bgLum) + 0.05)

        // Unguarded white key-press flash emits ~1.0 relative luminance delta; stealth emits 0.0 press delta!
        val effectiveDeltaLum = if (stealth) 0.004 else (0.14 * 1.0)
        val reductionDb = (-10.0 * kotlin.math.log10(effectiveDeltaLum / 1.0)).let { (it * 100.0).roundToInt() / 100.0 }

        return PadOpticalProfile(
            stealthModeActive = stealth,
            backgroundRgb24 = bg,
            glyphRgb24 = fg,
            pressFlashAlpha = flashAlpha,
            localContrastRatio = contrast,
            specularGlintReductionDb = reductionDb,
        )
    }

    private fun relativeLum(rgb: Int): Double {
        fun lin(u8: Int): Double {
            val c = u8 / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin((rgb ushr 16) and 0xFF) + 0.7152 * lin((rgb ushr 8) and 0xFF) + 0.0722 * lin(rgb and 0xFF)
    }
}
