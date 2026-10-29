// SPDX-License-Identifier: Apache-2.0
package dev.fusion.perf

/**
 * Clean-room Charging / Battery / Metered-Network Background Work Gate (`RG-06`) and Deterministic
 * Low-RAM Mode Resource Budget Planner (`RG-07`).
 */
object MeteredBatteryLowRamScheduler {
    const val SCOPE =
        "Pure-JVM charging/battery/metered-network background work scheduler (RG-06) and Low-RAM mode cache/tab/animation budget calculator (RG-07)."

    enum class WorkClass { USER_INTERACTIVE, INCREMENTAL_INDEX, CLOUD_OR_LAN_BACKUP, THUMBNAIL_PREFETCH }

    data class DeviceTelemetry(
        val batteryPercent: Int,
        val isCharging: Boolean,
        val batterySaverEnabled: Boolean,
        val isNetworkMetered: Boolean,
        val totalRamMb: Int,
        val availRamMb: Int,
    )

    data class LowRamProfile(
        val lowRamActive: Boolean,
        val maxWarmBrowserTabs: Int,
        val thumbnailCacheMb: Int,
        val disableBlurAndSpringAnimations: Boolean,
    )

    fun shouldRunWorkNow(work: WorkClass, t: DeviceTelemetry): Pair<Boolean, String> {
        require(t.batteryPercent in 0..100 && t.totalRamMb > 0) { "invalid telemetry" }
        if (work == WorkClass.USER_INTERACTIVE) return true to "ALLOW_FOREGROUND_INTERACTIVE"
        if (t.batterySaverEnabled && !t.isCharging) return false to "DEFER_BATTERY_SAVER_ACTIVE"
        if (!t.isCharging && t.batteryPercent < 20) return false to "DEFER_BATTERY_BELOW_20PCT"
        if (work == WorkClass.CLOUD_OR_LAN_BACKUP && t.isNetworkMetered) return false to "DEFER_METERED_NETWORK"
        if (computeLowRamProfile(t).lowRamActive && work == WorkClass.THUMBNAIL_PREFETCH) {
            return false to "DEFER_LOW_RAM_PREFETCH_SUPPRESSED"
        }
        return true to "ALLOW_BACKGROUND_BUDGET_OK"
    }

    fun computeLowRamProfile(t: DeviceTelemetry): LowRamProfile {
        val low = t.totalRamMb <= 4096 || t.availRamMb <= 512 || t.batterySaverEnabled
        return if (low) {
            LowRamProfile(
                lowRamActive = true,
                maxWarmBrowserTabs = 2,
                thumbnailCacheMb = 12,
                disableBlurAndSpringAnimations = true,
            )
        } else {
            LowRamProfile(
                lowRamActive = false,
                maxWarmBrowserTabs = 8,
                thumbnailCacheMb = 64,
                disableBlurAndSpringAnimations = false,
            )
        }
    }
}
