// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settingsui

/**
 * Clean-room Curated OEM System-Settings Deep-Link Catalog (`ST-02`), Default-Apps Checklist
 * (`ST-03`), and Phone vs External-Desktop Per-Context Profile Diff Planner (`ST-09`).
 */
object OemSettingsDeepLinkCatalog {
    enum class SystemTarget {
        DISPLAY_EXTERNAL_MODE,
        BATTERY_UNRESTRICTED,
        DEFAULT_APPS_HOME_AND_BROWSER,
        NOTIFICATION_LISTENER_ACCESS,
        KEYBOARD_AND_INPUT_METHODS,
        USB_PREFERENCES,
    }

    data class ResolvedDeepLink(
        val target: SystemTarget,
        val primaryIntentAction: String,
        val oemComponentHint: String?,
        val fallbackIntentAction: String,
        val userGuidance: String,
    )

    fun resolveForOem(manufacturer: String, target: SystemTarget): ResolvedDeepLink {
        val m = manufacturer.trim().lowercase()
        return when (target) {
            SystemTarget.DISPLAY_EXTERNAL_MODE -> ResolvedDeepLink(
                target,
                "android.settings.DISPLAY_SETTINGS",
                when {
                    "samsung" in m -> "com.sec.android.desktopmode.uiservice"
                    "motorola" in m -> "com.motorola.mobiledesktop"
                    else -> null
                },
                "android.settings.SETTINGS",
                "Configure external display resolution/mirroring; Fusion Shell coexists without replacing stock launcher.",
            )
            SystemTarget.BATTERY_UNRESTRICTED -> ResolvedDeepLink(
                target,
                "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS",
                when {
                    "xiaomi" in m || "redmi" in m -> "com.miui.securitycenter/.PowerCenter"
                    "samsung" in m -> "com.samsung.android.lool"
                    else -> null
                },
                "android.settings.APPLICATION_DETAILS_SETTINGS",
                "Keep active desktop/terminal sessions alive while external display is attached.",
            )
            SystemTarget.DEFAULT_APPS_HOME_AND_BROWSER -> ResolvedDeepLink(
                target,
                "android.settings.MANAGE_DEFAULT_APPS_SETTINGS",
                null,
                "android.settings.SETTINGS",
                "Optional user-controlled default browser/launcher roles; Fusion never nags if left non-default.",
            )
            SystemTarget.NOTIFICATION_LISTENER_ACCESS -> ResolvedDeepLink(
                target,
                "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
                null,
                "android.settings.SETTINGS",
                "Required only if you opt into Fusion Alerts notification center.",
            )
            SystemTarget.KEYBOARD_AND_INPUT_METHODS -> ResolvedDeepLink(
                target,
                "android.settings.INPUT_METHOD_SETTINGS",
                null,
                "android.settings.HARD_KEYBOARD_SETTINGS",
                "Configure physical keyboard layouts and optional local Fusion IME.",
            )
            SystemTarget.USB_PREFERENCES -> ResolvedDeepLink(
                target,
                "android.settings.USB_SETTINGS",
                null,
                "android.settings.DEVICE_INFO_SETTINGS",
                "Verify Charge-Only USB data lock when connecting to untrusted power ports.",
            )
        }
    }

    enum class ExecutionContext { HANDHELD_PHONE, EXTERNAL_DESKTOP_DOCK }

    data class ContextProfile(
        val densityPreset: String,
        val shelfMode: String,
        val pointerSpeedMultiplier: Double,
        val defaultSplitWorkspace: Boolean,
    )

    fun switchContextProfile(
        handheld: ContextProfile,
        desktop: ContextProfile,
        activeContext: ExecutionContext,
    ): Pair<ContextProfile, Map<String, Pair<String, String>>> {
        val chosen = if (activeContext == ExecutionContext.HANDHELD_PHONE) handheld else desktop
        val other = if (activeContext == ExecutionContext.HANDHELD_PHONE) desktop else handheld
        val diffs = LinkedHashMap<String, Pair<String, String>>()
        if (other.densityPreset != chosen.densityPreset) diffs["density"] = other.densityPreset to chosen.densityPreset
        if (other.shelfMode != chosen.shelfMode) diffs["shelfMode"] = other.shelfMode to chosen.shelfMode
        if (other.pointerSpeedMultiplier != chosen.pointerSpeedMultiplier) {
            diffs["pointerSpeed"] = other.pointerSpeedMultiplier.toString() to chosen.pointerSpeedMultiplier.toString()
        }
        if (other.defaultSplitWorkspace != chosen.defaultSplitWorkspace) {
            diffs["splitWorkspace"] = other.defaultSplitWorkspace.toString() to chosen.defaultSplitWorkspace.toString()
        }
        return chosen to diffs
    }
}
