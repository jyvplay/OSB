// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import java.util.Collections
import kotlin.math.pow
import kotlin.math.round

/**
 * Type budgets: the exact Material 3 baseline scale (15 styles, official size/line-height
 * pairs) plus a phi-derived editorial scale and a large-font overflow predicate.
 * Numbers only — font files, rendering, and human preference remain outside this kernel.
 * Phi is an optional composition proportion, never an accessibility rule.
 */
object TypeScale {
    data class Style(val name: String, val sizeSp: Double, val lineHeightSp: Double, val bold: Boolean) {
        init { require(name.length in 2..24 && sizeSp in 1.0..256.0 && lineHeightSp >= sizeSp) }
        /** WCAG large-text threshold: ≥24sp regular or ≥18.66sp bold. */
        val large: Boolean get() = sizeSp >= 24.0 || (bold && sizeSp >= 18.66)
    }

    /** Official M3 baseline pairs (Roboto). Source: Android Developers M3 typography. */
    val BASELINE: List<Style> = Collections.unmodifiableList(listOf(
        Style("displayLarge", 57.0, 64.0, false), Style("displayMedium", 45.0, 52.0, false),
        Style("displaySmall", 36.0, 44.0, false), Style("headlineLarge", 32.0, 40.0, false),
        Style("headlineMedium", 28.0, 36.0, false), Style("headlineSmall", 24.0, 32.0, false),
        Style("titleLarge", 22.0, 28.0, false), Style("titleMedium", 16.0, 24.0, true),
        Style("titleSmall", 14.0, 20.0, true), Style("bodyLarge", 16.0, 24.0, false),
        Style("bodyMedium", 14.0, 20.0, false), Style("bodySmall", 12.0, 16.0, false),
        Style("labelLarge", 14.0, 20.0, true), Style("labelMedium", 12.0, 16.0, true),
        Style("labelSmall", 11.0, 16.0, true),
    ))

    fun style(name: String): Style = BASELINE.single { it.name == name }

    /** Editorial sizes from a base: base × phi^step, rounded to 0.5sp. Proportion hint only. */
    fun phiSize(baseSp: Double, step: Int): Double {
        require(baseSp.isFinite() && baseSp in 4.0..128.0 && step in -4..8)
        return round(baseSp * BeautyStandard.PHI.pow(step) * 2) / 2
    }

    enum class Fit { FITS, SCROLLS }

    /**
     * Large-font overflow predicate: at [fontScale], do [lines] of [style] fit in
     * [containerSp]? SCROLLS is the honest answer (content scrolls), never shrinkage —
     * text must never be shrunk to fit.
     */
    fun fit(style: Style, lines: Int, containerSp: Double, fontScale: Double): Fit {
        require(lines in 1..10000 && containerSp.isFinite() && containerSp > 0 && fontScale.isFinite() && fontScale in .5..5.0)
        return if (style.lineHeightSp * fontScale * lines <= containerSp + 0.01) Fit.FITS else Fit.SCROLLS
    }
}
