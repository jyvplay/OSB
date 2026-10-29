// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settings

/**
 * Clean-room Enterprise `RestrictionsManager` Managed Configuration Schema & External-Display
 * Independent UI Scale Resolver (`ST-07`, `ST-08`).
 *
 * Validates MDM/enterprise policy bundles without allowing an enterprise profile to silently
 * weaken mandatory privacy invariants (e.g., MDM can *require* `FLAG_SECURE` or disable external
 * casting, never *disable* Vault encryption or force cloud telemetry).
 */
object ManagedRestrictionsSchema {
    data class EffectiveManagedPolicy(
        val disableBeamAndCast: Boolean,
        val forceStrictFlagSecure: Boolean,
        val disallowThirdPartyAutomation: Boolean,
        val allowedBrowserOriginsPattern: String?,
        val externalDisplayUiScale: Double, // ST-07: independent 0.75x .. 2.00x scale
        val rejectedKeys: List<String>,
    )

    private val KNOWN_KEYS = setOf(
        "disable_beam_and_cast",
        "force_strict_flag_secure",
        "disallow_third_party_automation",
        "allowed_browser_origins_pattern",
        "external_display_ui_scale",
    )

    fun resolve(rawRestrictions: Map<String, Any?>, userExternalScale: Double = 1.0): EffectiveManagedPolicy {
        require(userExternalScale.isFinite()) { "non-finite userExternalScale" }
        val rejected = ArrayList<String>()
        for (k in rawRestrictions.keys) {
            if (k !in KNOWN_KEYS) rejected.add(k)
        }

        val managedScale = (rawRestrictions["external_display_ui_scale"] as? Number)?.toDouble()
        val chosenScale = (managedScale ?: userExternalScale).coerceIn(0.75, 2.00)
        // Quantize to 5% steps (0.75, 0.80, ..., 2.00) for crisp glyph rasterization
        val snappedScale = kotlin.math.round(chosenScale * 20.0) / 20.0

        val pattern = (rawRestrictions["allowed_browser_origins_pattern"] as? String)
            ?.trim()?.takeIf { it.isNotEmpty() && it.length <= 256 }

        return EffectiveManagedPolicy(
            disableBeamAndCast = (rawRestrictions["disable_beam_and_cast"] as? Boolean) ?: false,
            forceStrictFlagSecure = (rawRestrictions["force_strict_flag_secure"] as? Boolean) ?: false,
            disallowThirdPartyAutomation = (rawRestrictions["disallow_third_party_automation"] as? Boolean) ?: false,
            allowedBrowserOriginsPattern = pattern,
            externalDisplayUiScale = snappedScale,
            rejectedKeys = rejected.sorted(),
        )
    }

    fun effectiveExternalDensityDpi(baseExternalDpi: Int, externalUiScale: Double): Int {
        require(baseExternalDpi in 72..640) { "baseExternalDpi outside 72..640" }
        val clamped = externalUiScale.coerceIn(0.75, 2.00)
        return kotlin.math.round(baseExternalDpi * clamped).toInt().coerceIn(72, 960)
    }
}
