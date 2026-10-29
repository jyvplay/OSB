// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

import kotlin.math.roundToInt

/**
 * Clean-room Probe-Chosen `S1..S4` External-App Launch Broker + Display Pinning (`WM-06`),
 * Per-App Density Override with Honest Android Platform Limits Label (`WM-12`), and Independent
 * External-Display UI Scale (`ST-07`).
 */
object ExternalAppBrokerDensityPin {
    const val SCOPE =
        "Pure-JVM S1-S4 external-app launch strategy broker & display pinning (WM-06), per-app DPI override with explicit limits label (WM-12), and independent external-display UI scale calculator (ST-07)."

    enum class BrokerStrategy {
        S1_OWNED_IN_PROCESS_SURFACE,
        S2_ACTIVITY_OPTIONS_LAUNCH_DISPLAY_BOUNDS,
        S3_ADJACENT_SPLIT_INTENT,
        S4_FULLSCREEN_HANDOFF_FALLBACK,
    }

    data class BrokerDecision(
        val strategy: BrokerStrategy,
        val targetDisplayId: Int,
        val effectiveDpi: Int,
        val limitsDisclosure: String,
    )

    fun chooseLaunchStrategy(
        isFusionOwnedModule: Boolean,
        supportsMultiWindowResizeable: Boolean,
        freeformDesktopActive: Boolean,
        pinnedDisplayId: Int,
        connectedDisplayIds: Set<Int>,
        baseDisplayDpi: Int = 320,
        perAppScaleRatio: Double = 1.0,
        externalDisplayScaleRatio: Double = 1.0,
    ): BrokerDecision {
        require(baseDisplayDpi in 120..640) { "baseDisplayDpi outside 120..640" }
        require(perAppScaleRatio in 0.70..1.60 && externalDisplayScaleRatio in 0.75..2.00) {
            "scale ratios out of supported bounds"
        }
        val resolvedDisplay = if (pinnedDisplayId in connectedDisplayIds) pinnedDisplayId else 0
        val extScale = if (resolvedDisplay != 0) externalDisplayScaleRatio else 1.0
        val computedDpi = (baseDisplayDpi * perAppScaleRatio * extScale).roundToInt().coerceIn(120, 640)

        return when {
            isFusionOwnedModule -> BrokerDecision(
                BrokerStrategy.S1_OWNED_IN_PROCESS_SURFACE,
                resolvedDisplay,
                computedDpi,
                "Owned Fusion surface applies exact per-surface density & external scale without OS Activity restart.",
            )
            freeformDesktopActive && supportsMultiWindowResizeable -> BrokerDecision(
                BrokerStrategy.S2_ACTIVITY_OPTIONS_LAUNCH_DISPLAY_BOUNDS,
                resolvedDisplay,
                computedDpi,
                "Unprivileged Android limit: ActivityOptions sets target display & bounds; third-party app internal Configuration.densityDpi cannot be overridden without Shizuku/wm density.",
            )
            supportsMultiWindowResizeable -> BrokerDecision(
                BrokerStrategy.S3_ADJACENT_SPLIT_INTENT,
                resolvedDisplay,
                baseDisplayDpi,
                "Split-adjacent launch only; third-party app inherits system display DPI ($baseDisplayDpi).",
            )
            else -> BrokerDecision(
                BrokerStrategy.S4_FULLSCREEN_HANDOFF_FALLBACK,
                resolvedDisplay,
                baseDisplayDpi,
                "Target app declares resizeableActivity=false; honest fullscreen handoff on display $resolvedDisplay.",
            )
        }
    }
}
