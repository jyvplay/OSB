// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settings

/**
 * Clean-room Default-Apps Role Guide (`ST-03`), Curated Per-OEM Settings Intent Resolver (`ST-02`),
 * and Enterprise `RestrictionsManager` Policy Override Merger (`ST-08`).
 */
object DefaultAppsRoleGuideEngine {
    const val SCOPE =
        "Pure-JVM Android RoleManager default-app readiness guide (ST-03), per-OEM fallback settings deep-link resolver (ST-02), and RestrictionsManager managed-config merger (ST-08)."

    enum class FusionRole(val androidRoleName: String, val requiredForFeature: String) {
        HOME("android.app.role.HOME", "Desktop Launcher & Tabletop Dock"),
        BROWSER("android.app.role.BROWSER", "Isolated Fusion Browser & Tor Handoff"),
        SMS("android.app.role.SMS", "Local SMS/MMS & RCS Handoff"),
    }

    data class RoleGuideStep(
        val role: FusionRole,
        val currentlyHeld: Boolean,
        val primarySettingsAction: String,
        val oemFallbackComponent: String?,
        val userExplanation: String,
    )

    fun buildRoleGuide(heldRoles: Set<FusionRole>, manufacturer: String): List<RoleGuideStep> {
        val oem = manufacturer.trim().lowercase()
        val fallback = when {
            "xiaomi" in oem || "redmi" in oem -> "com.miui.securitycenter/.ApplicationsDetailsActivity"
            "samsung" in oem -> "com.samsung.android.SettingsReceiver"
            "oppo" in oem || "oneplus" in oem || "realme" in oem -> "com.coloros.safecenter/.permission.PermissionManagerActivity"
            else -> null
        }
        return FusionRole.entries.map { r ->
            RoleGuideStep(
                role = r,
                currentlyHeld = r in heldRoles,
                primarySettingsAction = "android.settings.MANAGE_DEFAULT_APPS_SETTINGS",
                oemFallbackComponent = fallback,
                userExplanation = "${r.requiredForFeature} uses ${r.androidRoleName}; fully optional and revocable at any time.",
            )
        }
    }

    fun mergeManagedRestrictions(
        userPrefs: Map<String, String>,
        managedBundle: Map<String, String>,
    ): Pair<Map<String, String>, Set<String>> {
        val merged = LinkedHashMap(userPrefs)
        val lockedKeys = LinkedHashSet<String>()
        for ((k, v) in managedBundle) {
            merged[k] = v
            lockedKeys.add(k)
        }
        return merged to lockedKeys
    }
}
