// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * WCAG 2.2 SC 1.4.4 (`200%` Resize Text) **Non-Linear Fluid Typography Ramp**.
 *
 * Scales body/caption copy linearly up to `2.0x` (`fontScale = 2.0`) while damping display/headline
 * growth on compact `360dp` viewports so giant headings never overflow horizontally!
 */
object AdaptiveTypographyScale {
    const val SCOPE =
        "WCAG 2.2 SC 1.4.4 200%-zoom non-linear fluid typography ramp preventing 360dp compact viewport overflow."

    enum class Role(val baseSp: Double, val dampingExponent: Double) {
        CAPTION(12.0, 1.0),
        BODY(14.0, 1.0),
        SUBHEAD(16.0, 0.92),
        TITLE(20.0, 0.80),
        HEADLINE(26.0, 0.68),
        DISPLAY(34.0, 0.55),
    }

    data class ScaledSpec(
        val fontSizeSp: Double,
        val lineHeightSp: Double,
        val maxCharsPerLineEstimate: Int,
    )

    fun resolve(role: Role, userFontScale: Double, viewportWidthDp: Int = 360): ScaledSpec {
        require(userFontScale in 0.75..3.0 && viewportWidthDp in 240..3840) {
            "invalid userFontScale ($userFontScale) or viewportWidthDp ($viewportWidthDp)"
        }
        val compactFactor = if (viewportWidthDp <= 360) 0.95 else min(1.12, 1.0 + (viewportWidthDp - 360) / 3600.0)
        val dampedScale = if (userFontScale <= 1.0) userFontScale else userFontScale.pow(role.dampingExponent)
        val rawSp = role.baseSp * dampedScale * (if (role.baseSp >= 20.0) compactFactor else 1.0)
        // Cap heading size so at least 10 average glyphs (~0.55 * sp each) fit inside (viewportWidthDp - 32dp)
        val usableDp = (viewportWidthDp - 32).coerceAtLeast(160)
        val maxSafeSp = usableDp / (10.0 * 0.55)
        val finalSp = round(min(rawSp, maxSafeSp) * 100.0) / 100.0
        val lineHeight = round(finalSp * (if (role.baseSp <= 16.0) 1.45 else 1.22) * 100.0) / 100.0
        val charsEst = (usableDp / (finalSp * 0.55)).toInt().coerceAtLeast(8)
        return ScaledSpec(finalSp, lineHeight, charsEst)
    }
}
