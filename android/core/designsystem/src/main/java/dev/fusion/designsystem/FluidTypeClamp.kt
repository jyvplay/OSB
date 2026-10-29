// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Clean-room CSS `clamp()` fluid typography & WCAG 1.4.4 `200%` text-reflow ramp (`FluidTypeClamp`).
 *
 * Powers `:core:designsystem` adaptive typography across `320dp` phones, `720dp` foldables,
 * `1280dp` tabletops, and `1920dp` wall displays while respecting user `fontScale` (`0.85 .. 2.0`):
 * - Linearly interpolates between `minSp` at `minViewportDp` (`360dp`) and `maxSp` at `maxViewportDp` (`1440dp`).
 * - Applies non-linear accessibility damping at huge display headlines when `fontScale == 2.0` so body copy
 *   scales full `200%` (WCAG 1.4.4) without pushing `H1` headlines off narrow `320dp` screens!
 */
object FluidTypeClamp {
    const val SCOPE =
        "Fluid clamp(minSp, vw, maxSp) type-scale calculator with WCAG 1.4.4 200% fontScale reflow & line-height rhythm across 320..1920dp; OpenType variable-font axis tables out of scope."

    enum class TypeRole(val minSp: Double, val maxSp: Double, val lineHeightRatio: Double) {
        CAPTION(11.0, 13.0, 1.40),
        BODY(14.0, 17.0, 1.50),
        SUBTITLE(16.0, 20.0, 1.42),
        TITLE(20.0, 26.0, 1.32),
        HEADLINE(24.0, 34.0, 1.24),
        DISPLAY(30.0, 46.0, 1.18),
    }

    data class ComputedType(
        val role: TypeRole,
        val fontSizeSp: Double,
        val lineHeightSp: Double,
        val maxCharsPerLineEstimate: Int,
    )

    fun clampSp(
        minSp: Double,
        maxSp: Double,
        viewportWidthDp: Double,
        minViewportDp: Double = 360.0,
        maxViewportDp: Double = 1440.0,
    ): Double {
        require(minSp > 0.0 && maxSp >= minSp) { "Invalid clamp bounds: $minSp .. $maxSp" }
        require(maxViewportDp > minViewportDp) { "Invalid viewport bounds" }
        val t = ((viewportWidthDp - minViewportDp) / (maxViewportDp - minViewportDp)).coerceIn(0.0, 1.0)
        return minSp + (maxSp - minSp) * t
    }

    fun resolveRole(
        role: TypeRole,
        viewportWidthDp: Double,
        userFontScale: Double = 1.0,
    ): ComputedType {
        require(viewportWidthDp in 240.0..8192.0) { "viewportWidthDp out of range: $viewportWidthDp" }
        val clampedScale = userFontScale.coerceIn(0.85, 2.0)
        val baseSp = clampSp(role.minSp, role.maxSp, viewportWidthDp)
        val scaledSp = when (role) {
            // Body & Caption honor full linear WCAG 1.4.4 200% zoom; Display caps overflow on narrow screens.
            TypeRole.CAPTION, TypeRole.BODY, TypeRole.SUBTITLE -> baseSp * clampedScale
            TypeRole.TITLE, TypeRole.HEADLINE, TypeRole.DISPLAY -> {
                val maxAllowedOnViewport = viewportWidthDp * 0.16
                min(baseSp * clampedScale, max(baseSp, maxAllowedOnViewport))
            }
        }
        val lhSp = scaledSp * role.lineHeightRatio
        val estChars = max(12, ((viewportWidthDp - 32.0) / (scaledSp * 0.54)).roundToInt())
        return ComputedType(role, scaledSp, lhSp, estChars)
    }

    fun fullRamp(viewportWidthDp: Double, userFontScale: Double = 1.0): Map<TypeRole, ComputedType> =
        TypeRole.entries.associateWith { resolveRole(it, viewportWidthDp, userFontScale) }
}
