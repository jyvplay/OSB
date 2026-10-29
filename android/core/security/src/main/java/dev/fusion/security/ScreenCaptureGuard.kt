// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * Android 14–17 (`API 34–37`) Screen-Capture, `MediaProjection`, Untrusted Virtual Display, and
 * Untrusted `AutofillService` credential-exfiltration policy engine.
 *
 * Complements `FLAG_SECURE` (which blocks screenshots at the SurfaceFlinger layer on physical
 * hardware) with deterministic pure-JVM decision logic covering:
 * - Android 14+ `Activity.ScreenCaptureCallback` (`DETECT_SCREEN_CAPTURE`) auto-lock & redaction;
 * - Active `MediaProjection` / wireless mirror / non-system virtual display (`FLAG_PRESENTATION`
 *   without `VIRTUAL_DISPLAY_FLAG_SECURE`) blackout;
 * - Malicious third-party `AutofillService` / overlay package rejection on Vault secret fields
 *   (`IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS`).
 */
object ScreenCaptureGuard {
    const val SCOPE =
        "Android 14-17 screen-capture, MediaProjection, untrusted virtual-display, and untrusted AutofillService credential-redaction policy engine; actual SurfaceFlinger FLAG_SECURE frame blocking requires Android device verification."

    enum class ThreatVector {
        SCREENSHOT_CAPTURED,
        ACTIVE_MEDIA_PROJECTION,
        UNTRUSTED_VIRTUAL_DISPLAY,
        ACCESSIBILITY_SCREEN_READ_UNVERIFIED,
        UNTRUSTED_AUTOFILL_PROVIDER,
    }

    data class DisplaySnapshot(
        val displayId: Int,
        val isVirtual: Boolean,
        val hasSecureFlag: Boolean,
        val ownerPackage: String?,
    )

    data class EnvironmentState(
        val screenshotCallbackFired: Boolean = false,
        val mediaProjectionActive: Boolean = false,
        val displays: List<DisplaySnapshot> = emptyList(),
        val activeAutofillPackage: String? = null,
        val trustedSystemPackages: Set<String> = setOf("com.android.systemui"),
        val allowScreenReaderWithExplicitConsent: Boolean = false,
        val unverifiedAccessibilityOverlayActive: Boolean = false,
    )

    data class GuardDecision(
        val redactSensitiveContent: Boolean,
        val lockVaultImmediately: Boolean,
        val disableAutofillDescendants: Boolean,
        val enforceWindowFlagSecure: Boolean,
        val triggeredThreats: List<ThreatVector>,
    )

    fun evaluate(state: EnvironmentState): GuardDecision {
        val threats = ArrayList<ThreatVector>()
        if (state.screenshotCallbackFired) threats.add(ThreatVector.SCREENSHOT_CAPTURED)
        if (state.mediaProjectionActive) threats.add(ThreatVector.ACTIVE_MEDIA_PROJECTION)
        if (state.displays.any { d ->
                d.isVirtual && (!d.hasSecureFlag || (d.ownerPackage != null && d.ownerPackage !in state.trustedSystemPackages))
            }
        ) {
            threats.add(ThreatVector.UNTRUSTED_VIRTUAL_DISPLAY)
        }
        if (state.unverifiedAccessibilityOverlayActive && !state.allowScreenReaderWithExplicitConsent) {
            threats.add(ThreatVector.ACCESSIBILITY_SCREEN_READ_UNVERIFIED)
        }
        val autofillUntrusted = state.activeAutofillPackage != null &&
            state.activeAutofillPackage !in state.trustedSystemPackages
        if (autofillUntrusted) {
            threats.add(ThreatVector.UNTRUSTED_AUTOFILL_PROVIDER)
        }
        val visualLeakRisk = threats.any { it != ThreatVector.UNTRUSTED_AUTOFILL_PROVIDER }
        val lockNow = ThreatVector.ACTIVE_MEDIA_PROJECTION in threats ||
            ThreatVector.UNTRUSTED_VIRTUAL_DISPLAY in threats
        return GuardDecision(
            redactSensitiveContent = visualLeakRisk,
            lockVaultImmediately = lockNow,
            disableAutofillDescendants = true,
            enforceWindowFlagSecure = true,
            triggeredThreats = threats,
        )
    }

    fun redactSecretForPreview(secret: String): String =
        if (secret.isEmpty()) "" else "•".repeat(secret.length.coerceIn(8, 16))
}
