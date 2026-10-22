// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

/**
 * Display-density tier selection over [DensityProfile]. Touch-first devices
 * always land TOUCH (finger targets dominate); otherwise the tier follows
 * the dot pitch: under 180dpi is COMPACT (dense desktop-style layout fits),
 * 180–279dpi is BALANCED, 280dpi and up is TOUCH (physical pixels are too
 * small for COMPACT hit targets). Thresholds are documented policy, not
 * physical law: callers that measured their own audience keep their own
 * table. Range is 80..1000dpi; anything outside is a broken probe reading
 * and refuses.
 */
object DensitySelect {
    const val MIN_DPI = 80
    const val MAX_DPI = 1000
    const val TOUCH_DPI = 280
    const val BALANCED_DPI = 180

    fun validDpi(dpi: Int) = dpi in MIN_DPI..MAX_DPI

    fun select(dpi: Int, touch: Boolean): DensityProfile {
        require(validDpi(dpi))
        return when {
            touch || dpi >= TOUCH_DPI -> DensityProfile.TOUCH
            dpi >= BALANCED_DPI -> DensityProfile.BALANCED
            else -> DensityProfile.COMPACT
        }
    }
}
