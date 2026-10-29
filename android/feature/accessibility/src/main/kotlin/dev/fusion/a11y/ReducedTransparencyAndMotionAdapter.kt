// SPDX-License-Identifier: Apache-2.0
package dev.fusion.a11y

/**
 * **WCAG 2.2 Reduced-Transparency & Reduced-Motion Accessibility Adapter** upgrading translucent
 * bottom-right Undo cards (`alpha = 0.88`) and context menus to near-opaque (`alpha = 0.98`) high
 * contrast surfaces and collapsing spring animations to instant `0 ms` jumps when OS accessibility
 * flags (`prefersReducedTransparency`, `prefersReducedMotion`) are enabled!
 */
object ReducedTransparencyAndMotionAdapter {
    const val SCOPE =
        "WCAG 2.2 prefers-reduced-transparency & prefers-reduced-motion adapter for translucent Undo Z-stacks and context menus."

    data class AdaptedSurfaceTokens(
        val frontCardAlpha: Double,
        val midCardAlpha: Double,
        val backCardAlpha: Double,
        val borderStrokeDp: Double,
        val animationDurationMillis: Int,
    )

    fun adapt(
        prefersReducedTransparency: Boolean,
        prefersReducedMotion: Boolean,
        highContrastTextEnabled: Boolean = false,
    ): AdaptedSurfaceTokens {
        val opaque = prefersReducedTransparency || highContrastTextEnabled
        return AdaptedSurfaceTokens(
            frontCardAlpha = if (opaque) 0.98 else 0.88,
            midCardAlpha = if (opaque) 0.94 else 0.68,
            backCardAlpha = if (opaque) 0.90 else 0.48,
            borderStrokeDp = if (highContrastTextEnabled) 2.0 else 1.0,
            animationDurationMillis = if (prefersReducedMotion) 0 else 180,
        )
    }
}
