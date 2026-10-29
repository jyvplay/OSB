// SPDX-License-Identifier: Apache-2.0
package dev.fusion.designsystem

import kotlin.math.pow

/**
 * **Translucent Frosted-Glass Backdrop WCAG Contrast Guard** auditing foreground text contrast over
 * semi-transparent Undo notification layers (`alpha = 0.88 / 0.68 / 0.48`) and right-click menus
 * against both pure-white (`#FFFFFF`) and pure-black (`#000000`) underlying page backdrops!
 *
 * Automatically boosts surface alpha when a bright page backdrop would drop effective text contrast
 * below WCAG 2.2 AA (`4.5:1`)!
 */
object TranslucentGlassBackdropContrastGuard {
    const val SCOPE =
        "Porter-Duff source-over translucent backdrop WCAG 2.2 contrast auditor auto-clamping alpha over bright/dark page backgrounds."

    data class GlassAuditResult(
        val effectiveAlpha: Double,
        val contrastOverWhitePage: Double,
        val contrastOverBlackPage: Double,
        val worstCaseContrast: Double,
        val wcagAaCompliant: Boolean,
    )

    fun solveSafeTranslucentAlpha(
        surfaceRgb24: Int = 0x121926,
        textRgb24: Int = 0xF2F6FC,
        requestedAlpha: Double = 0.88,
        minWcagContrast: Double = 4.5,
    ): GlassAuditResult {
        var alpha = requestedAlpha.coerceIn(0.40, 0.98)
        while (alpha < 0.98) {
            val overWhite = compositeOver(surfaceRgb24, alpha, 0xFFFFFF)
            val overBlack = compositeOver(surfaceRgb24, alpha, 0x000000)
            val cWhite = contrast(textRgb24, overWhite)
            val cBlack = contrast(textRgb24, overBlack)
            if (minOf(cWhite, cBlack) >= minWcagContrast) {
                return GlassAuditResult(alpha, cWhite, cBlack, minOf(cWhite, cBlack), true)
            }
            alpha = (alpha + 0.02).coerceAtMost(0.98)
        }
        val overWhite = compositeOver(surfaceRgb24, alpha, 0xFFFFFF)
        val overBlack = compositeOver(surfaceRgb24, alpha, 0x000000)
        val cW = contrast(textRgb24, overWhite)
        val cB = contrast(textRgb24, overBlack)
        return GlassAuditResult(alpha, cW, cB, minOf(cW, cB), minOf(cW, cB) >= minWcagContrast)
    }

    private fun compositeOver(srcRgb: Int, alpha: Double, dstRgb: Int): Int {
        fun ch(shift: Int): Int {
            val s = (srcRgb ushr shift) and 0xFF
            val d = (dstRgb ushr shift) and 0xFF
            return (s * alpha + d * (1.0 - alpha)).toInt().coerceIn(0, 255)
        }
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun contrast(rgbA: Int, rgbB: Int): Double {
        val l1 = luminance(rgbA)
        val l2 = luminance(rgbB)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun luminance(rgb: Int): Double {
        fun lin(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin((rgb ushr 16) and 0xFF) + 0.7152 * lin((rgb ushr 8) and 0xFF) + 0.0722 * lin(rgb and 0xFF)
    }
}
