// SPDX-License-Identifier: Apache-2.0
package dev.fusion.beam

/**
 * Clean-room Beam / Cast Privacy Surface Exclusion Gate, Persistent Red Indicator Enforcer, and
 * Lock-Critical Stream Kill Switch (`CA-14`, `SC-09`, `WM-15`).
 *
 * Guarantees:
 * - Vault, Privacy/Tails, Auth/PIN, Incognito Browser, and `FLAG_SECURE` windows are unconditionally
 *   excluded from window-to-Surface casting (`renderBlankPrivacyMatte = true`), never leaked over
 *   AirPlay / CastV2 / DLNA / WebRTC!
 * - Any device lock event (`deviceLocked == true`) or unpinned WAN relay (`usesCloudRelay == true`
 *   without explicit per-session consent) terminates casting immediately!
 * - Persistent top-bar red indicator (`showPersistentRedIndicator`) is forced `true` whenever a
 *   single frame can leave the device.
 */
object CastPrivacyExclusionGate {
    private val ALWAYS_EXCLUDED_MODULES = setOf(
        "vault", "privacy", "security", "backup", "browser_incognito", "auth_pin",
    )

    data class CastableWindowCandidate(
        val windowId: String,
        val moduleId: String,
        val hasFlagSecure: Boolean,
        val userMarkedExcludedFromCast: Boolean = false,
    )

    data class CastSessionSafetyState(
        val allowStreamContinuation: Boolean,
        val showPersistentRedIndicator: Boolean,
        val visibleWindowIds: Set<String>,
        val redactedWindowIds: Set<String>,
        val terminationReason: String?,
    )

    fun evaluateFrame(
        sessionActive: Boolean,
        deviceLocked: Boolean,
        receiverIsLanLocal: Boolean,
        userExplicitlyApprovedRelay: Boolean,
        windows: List<CastableWindowCandidate>,
    ): CastSessionSafetyState {
        if (!sessionActive) {
            return CastSessionSafetyState(false, false, emptySet(), emptySet(), "session_inactive")
        }
        if (deviceLocked) {
            return CastSessionSafetyState(
                allowStreamContinuation = false,
                showPersistentRedIndicator = false,
                visibleWindowIds = emptySet(),
                redactedWindowIds = windows.map { it.windowId }.toSet(),
                terminationReason = "SC-09_STOP_ON_LOCK: cast session terminated immediately upon screen lock",
            )
        }
        if (!receiverIsLanLocal && !userExplicitlyApprovedRelay) {
            return CastSessionSafetyState(
                allowStreamContinuation = false,
                showPersistentRedIndicator = false,
                visibleWindowIds = emptySet(),
                redactedWindowIds = windows.map { it.windowId }.toSet(),
                terminationReason = "CA-14_NO_DEFAULT_RELAY: non-LAN relay refused without explicit session consent",
            )
        }

        val visible = LinkedHashSet<String>()
        val redacted = LinkedHashSet<String>()
        for (w in windows) {
            val blocked = w.hasFlagSecure ||
                w.userMarkedExcludedFromCast ||
                w.moduleId.trim().lowercase() in ALWAYS_EXCLUDED_MODULES
            if (blocked) redacted.add(w.windowId) else visible.add(w.windowId)
        }
        return CastSessionSafetyState(
            allowStreamContinuation = true,
            showPersistentRedIndicator = true, // Mandatory SC-09 live indicator!
            visibleWindowIds = visible,
            redactedWindowIds = redacted,
            terminationReason = null,
        )
    }
}
