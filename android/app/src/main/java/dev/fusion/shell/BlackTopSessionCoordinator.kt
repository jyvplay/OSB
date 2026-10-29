// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

/**
 * Clean-room BlackTop External-Display Session Coordinator (`SH-10`), Phone-as-Trackpad Motion
 * Mapper (`SH-11`), and OEM Background-Phantom-Process Throttle Detector (`SH-12`).
 */
object BlackTopSessionCoordinator {
    enum class PhoneSurfaceMode {
        NORMAL_HANDHELD,
        OLED_BLACKOUT_DIMMED,
        VIRTUAL_TOUCHPAD_AND_KEYBOARD,
    }

    data class ExternalDisplaySpec(
        val displayId: Int,
        val widthPx: Int,
        val heightPx: Int,
        val refreshHz: Int,
    ) {
        init {
            require(displayId > 0 && widthPx >= 640 && heightPx >= 480 && refreshHz in 24..360) {
                "invalid external display spec"
            }
        }
    }

    data class BlackTopState(
        val active: Boolean,
        val externalDisplay: ExternalDisplaySpec?,
        val phoneSurfaceMode: PhoneSurfaceMode,
        val foregroundKeepAliveRequired: Boolean,
        val oledPixelShiftOffsetPx: Pair<Int, Int>,
    )

    fun evaluateSession(
        externalDisplay: ExternalDisplaySpec?,
        preferTrackpadOnPhone: Boolean = true,
        elapsedMinutes: Int = 0,
    ): BlackTopState {
        if (externalDisplay == null) {
            return BlackTopState(false, null, PhoneSurfaceMode.NORMAL_HANDHELD, false, 0 to 0)
        }
        // Periodic ±2px burn-in pixel shift for minimal status indicator on OLED blackout surface
        val shiftX = ((elapsedMinutes % 5) - 2)
        val shiftY = (((elapsedMinutes / 5) % 5) - 2)
        return BlackTopState(
            active = true,
            externalDisplay = externalDisplay,
            phoneSurfaceMode = if (preferTrackpadOnPhone) {
                PhoneSurfaceMode.VIRTUAL_TOUCHPAD_AND_KEYBOARD
            } else {
                PhoneSurfaceMode.OLED_BLACKOUT_DIMMED
            },
            foregroundKeepAliveRequired = true,
            oledPixelShiftOffsetPx = shiftX to shiftY,
        )
    }

    /** Ballistic touchpad pointer curve (`SH-11`) mapping phone finger delta (dp) to external cursor px. */
    fun mapTrackpadDeltaToExternalCursor(
        deltaDpX: Double,
        deltaDpY: Double,
        cursorX: Double,
        cursorY: Double,
        display: ExternalDisplaySpec,
        sensitivity: Double = 1.6,
    ): Pair<Double, Double> {
        val speed = kotlin.math.hypot(deltaDpX, deltaDpY)
        val accel = (1.0 + (speed / 18.0).coerceAtMost(2.2)) * sensitivity
        val nx = (cursorX + deltaDpX * accel).coerceIn(0.0, (display.widthPx - 1).toDouble())
        val ny = (cursorY + deltaDpY * accel).coerceIn(0.0, (display.heightPx - 1).toDouble())
        return nx to ny
    }

    data class OemThrottleGuidance(
        val oemNormalized: String,
        val phantomProcessRisk: Boolean,
        val steps: List<String>,
    )

    fun diagnoseOemThrottling(
        manufacturer: String,
        sdkInt: Int,
        ignoringBatteryOptimizations: Boolean,
    ): OemThrottleGuidance {
        val m = manufacturer.trim().lowercase()
        val steps = ArrayList<String>()
        if (!ignoringBatteryOptimizations) {
            steps.add("Set Fusion Shell battery usage to 'Unrestricted' in App Info -> Battery.")
        }
        when {
            "samsung" in m -> steps.add("Remove Fusion Shell from Device Care -> Background usage limits -> Sleeping / Deep sleeping apps.")
            "xiaomi" in m || "redmi" in m || "poco" in m -> steps.add("Enable Autostart and set MIUI/HyperOS Battery Saver to 'No restrictions'.")
            "oneplus" in m || "oppo" in m || "realme" in m -> steps.add("Enable 'Allow background activity' under App Battery Management.")
            "huawei" in m || "honor" in m -> steps.add("Disable 'Manage automatically' in App Launch and keep Run in background enabled.")
        }
        val phantomRisk = sdkInt in 31..33 // Android 12-13 max_phantom_processes=32 cap unless disabled in Developer Options
        if (phantomRisk) {
            steps.add("Android 12/13 detected: enable 'Disable child process restrictions' in Developer Options if terminal workloads exit with signal 9.")
        }
        return OemThrottleGuidance(m.ifEmpty { "generic" }, phantomRisk, steps)
    }
}
