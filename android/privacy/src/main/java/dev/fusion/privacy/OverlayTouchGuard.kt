// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Clean-room local display/input anti-keylogger & tapjacking gate for high-assurance secret
 * surfaces (passphrase keypad, vault unlock, private-mode confirmation).
 *
 * Counters Android local software/display exfiltration vectors:
 * - Tapjacking / obscured touch injection (`MotionEvent.FLAG_WINDOW_IS_OBSCURED = 0x1`,
 *   `FLAG_WINDOW_IS_PARTIALLY_OBSCURED = 0x2`),
 * - Missing `WindowManager.LayoutParams.FLAG_SECURE` screenshot/MediaProjection protection,
 * - Non-system virtual/presentation displays (`activeVirtualDisplays > 0`),
 * - RatHat-class `AccessibilityService` trojans holding `canRetrieveWindowContent` or
 *   `canPerformGestures` outside an explicit user-verified accessibility allowlist.
 */
object OverlayTouchGuard {
    const val SCOPE =
        "Obscured-touch bitmask, cross-UID overlay, FLAG_SECURE, virtual-display, and AccessibilityService capability policy gate; Android View.setFilterTouchesWhenObscured wiring lives in UI hosts."

    const val MOTION_FLAG_WINDOW_IS_OBSCURED = 0x1
    const val MOTION_FLAG_WINDOW_IS_PARTIALLY_OBSCURED = 0x2

    data class AccessibilityAgent(
        val packageName: String,
        val canRetrieveWindowContent: Boolean,
        val canPerformGestures: Boolean,
    ) {
        init {
            require(packageName.isNotBlank()) { "packageName must not be blank" }
        }
    }

    data class SurfaceContext(
        val motionEventFlags: Int = 0,
        val hasCrossUidOverlay: Boolean = false,
        val hasFlagSecure: Boolean,
        val activeUntrustedVirtualDisplays: Int = 0,
        val enabledAccessibilityAgents: List<AccessibilityAgent> = emptyList(),
        val allowlistedAccessibilityPackages: Set<String> = emptySet(),
    ) {
        init {
            require(activeUntrustedVirtualDisplays >= 0) { "activeUntrustedVirtualDisplays must be >= 0" }
        }
    }

    enum class BlockReason {
        TOUCH_OBSCURED,
        TOUCH_PARTIALLY_OBSCURED,
        CROSS_UID_OVERLAY_PRESENT,
        MISSING_FLAG_SECURE,
        UNTRUSTED_VIRTUAL_DISPLAY_CAPTURE,
        UNVERIFIED_A11Y_WINDOW_READER,
        UNVERIFIED_A11Y_GESTURE_INJECTOR,
    }

    data class Decision(
        val allowSecretInput: Boolean,
        val reasons: Set<BlockReason>,
        val flaggedAccessibilityPackages: List<String>,
    )

    fun evaluate(ctx: SurfaceContext): Decision {
        val reasons = LinkedHashSet<BlockReason>()
        val flaggedPkgs = LinkedHashSet<String>()

        if ((ctx.motionEventFlags and MOTION_FLAG_WINDOW_IS_OBSCURED) != 0) {
            reasons.add(BlockReason.TOUCH_OBSCURED)
        }
        if ((ctx.motionEventFlags and MOTION_FLAG_WINDOW_IS_PARTIALLY_OBSCURED) != 0) {
            reasons.add(BlockReason.TOUCH_PARTIALLY_OBSCURED)
        }
        if (ctx.hasCrossUidOverlay) {
            reasons.add(BlockReason.CROSS_UID_OVERLAY_PRESENT)
        }
        if (!ctx.hasFlagSecure) {
            reasons.add(BlockReason.MISSING_FLAG_SECURE)
        }
        if (ctx.activeUntrustedVirtualDisplays > 0) {
            reasons.add(BlockReason.UNTRUSTED_VIRTUAL_DISPLAY_CAPTURE)
        }

        for (agent in ctx.enabledAccessibilityAgents) {
            if (agent.packageName in ctx.allowlistedAccessibilityPackages) continue
            if (agent.canRetrieveWindowContent) {
                reasons.add(BlockReason.UNVERIFIED_A11Y_WINDOW_READER)
                flaggedPkgs.add(agent.packageName)
            }
            if (agent.canPerformGestures) {
                reasons.add(BlockReason.UNVERIFIED_A11Y_GESTURE_INJECTOR)
                flaggedPkgs.add(agent.packageName)
            }
        }

        return Decision(
            allowSecretInput = reasons.isEmpty(),
            reasons = reasons,
            flaggedAccessibilityPackages = flaggedPkgs.toList().sorted(),
        )
    }
}
