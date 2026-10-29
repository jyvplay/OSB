// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import kotlin.math.cos
import kotlin.math.pow

/**
 * **Local Hardware / Physical Observation Vector `#25` — Shoulder-Surfing Peripheral Privacy Veil:**
 * Computes Lambertian + micro-louver off-axis contrast attenuation and generates a focal-band
 * spotlight mask keeping the user's active reading band at WCAG AAA (`>= 7.0:1`) while attenuating
 * peripheral rows so `>= 38°` off-axis shoulder-surfers fall below the `1.6:1` legibility floor.
 */
object ShoulderSurfingPrivacyVeil {
    const val SCOPE =
        "Vector #25 physical shoulder-surfing peripheral privacy veil: focal reading spotlight + >=38-degree off-axis contrast attenuation below 1.6:1 while preserving >=7.0:1 on-axis WCAG AAA legibility."

    data class VeilEvaluation(
        val onAxisFocalContrast: Double,
        val offAxis45DegContrast: Double,
        val focalBandStartY: Int,
        val focalBandEndY: Int,
        val shoulderSurferBlocked: Boolean,
    )

    fun evaluateVeil(
        viewportHeightPx: Int,
        cursorY: Int,
        focalBandHeightPx: Int = 96,
        fgLuminance: Double = 0.90,
        bgLuminance: Double = 0.04,
        veilAlphaOutsideBand: Double = 0.82,
    ): VeilEvaluation {
        require(viewportHeightPx >= 120 && focalBandHeightPx in 32..viewportHeightPx)
        require(veilAlphaOutsideBand in 0.0..0.98)
        val half = focalBandHeightPx / 2
        val startY = (cursorY - half).coerceIn(0, viewportHeightPx - focalBandHeightPx)
        val endY = startY + focalBandHeightPx

        val onAxisContrast = wcagContrast(fgLuminance, bgLuminance)
        // Peripheral veiled luminance compresses foreground & background toward mid-veil luminance 0.09,
        // compounded by cos^3.2(45 deg) oblique viewing contrast rolloff
        val veilLum = 0.09
        val veiledFg = fgLuminance * (1.0 - veilAlphaOutsideBand) + veilLum * veilAlphaOutsideBand
        val veiledBg = bgLuminance * (1.0 - veilAlphaOutsideBand) + veilLum * veilAlphaOutsideBand
        val rawPeripheralContrast = wcagContrast(veiledFg, veiledBg)
        val obliqueFactor = cos(Math.toRadians(45.0)).pow(3.2)
        val offAxisContrast = 1.0 + (rawPeripheralContrast - 1.0) * obliqueFactor

        return VeilEvaluation(
            onAxisFocalContrast = onAxisContrast,
            offAxis45DegContrast = offAxisContrast,
            focalBandStartY = startY,
            focalBandEndY = endY,
            shoulderSurferBlocked = onAxisContrast >= 7.0 && offAxisContrast < 1.6,
        )
    }

    private fun wcagContrast(l1: Double, l2: Double): Double {
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }
}
