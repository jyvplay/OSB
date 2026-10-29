// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Clean-room Accessible Focus-Ring, Pointer/Touchpad Morph, and Minimum Hit-Target Tokens (`AX-04`).
 *
 * Enforces WCAG 2.2 SC 2.4.11 / SC 1.4.11 (`>= 3.0:1` non-text focus-ring contrast against both
 * surface background and adjacent border, `>= 2dp` focus ring thickness, `>= 48dp` touch target).
 */
object FocusRingPointerTokens {
    enum class InputModality { TOUCH, MOUSE_OR_TRACKPAD, KEYBOARD_OR_DPAD, STYLUS }

    data class FocusRingSpec(
        val visible: Boolean,
        val strokeWidthDp: Float,
        val outerGapDp: Float,
        val ringHexRgb: Int,
        val contrastRatioVsBackground: Double,
        val minHitTargetDp: Float,
    )

    fun resolveForModality(
        modality: InputModality,
        surfaceBackgroundRgb: Int,
        highContrastRequested: Boolean = false,
    ): FocusRingSpec {
        // Choose focus ring color (#38BDF8 sky-400 on dark surfaces, #0369A1 sky-700 on light surfaces)
        val bgLum = relativeLuminance(surfaceBackgroundRgb)
        val ringColor = if (bgLum < 0.22) {
            if (highContrastRequested) 0xFACC15 else 0x38BDF8
        } else {
            if (highContrastRequested) 0x0F172A else 0x0369A1
        }
        val contrast = contrastRatio(ringColor, surfaceBackgroundRgb)
        check(contrast >= 3.0) { "AX-04 focus ring contrast $contrast < 3.0:1 against 0x${surfaceBackgroundRgb.toString(16)}" }

        val showRing = modality == InputModality.KEYBOARD_OR_DPAD || highContrastRequested
        val minHit = when (modality) {
            InputModality.TOUCH -> 48f
            InputModality.KEYBOARD_OR_DPAD -> 44f
            InputModality.MOUSE_OR_TRACKPAD, InputModality.STYLUS -> 36f
        }
        return FocusRingSpec(
            visible = showRing,
            strokeWidthDp = if (highContrastRequested) 3f else 2f,
            outerGapDp = 2f,
            ringHexRgb = ringColor,
            contrastRatioVsBackground = contrast,
            minHitTargetDp = minHit,
        )
    }

    fun contrastRatio(rgbA: Int, rgbB: Int): Double {
        val l1 = relativeLuminance(rgbA)
        val l2 = relativeLuminance(rgbB)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    fun relativeLuminance(rgb: Int): Double {
        fun chan(shift: Int): Double {
            val s = ((rgb ushr shift) and 0xFF) / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * chan(16) + 0.7152 * chan(8) + 0.0722 * chan(0)
    }
}
