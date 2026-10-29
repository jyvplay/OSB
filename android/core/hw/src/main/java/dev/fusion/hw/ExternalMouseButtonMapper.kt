// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sign

/**
 * Clean-room Desktop Mouse Button Mapper (`IN-01`, `SH-09`), "Learn My Mouse" Wizard,
 * Gamepad Radial-Deadzone Pointer Curve (`IN-07`), and Hardware Keyboard IME Switcher (`IN-08`).
 */
object ExternalMouseButtonMapper {
    enum class DesktopAction {
        PRIMARY_CLICK,
        CONTEXT_MENU,
        MIDDLE_PASTE_OR_AUTOSCROLL,
        NAVIGATE_BACK,
        NAVIGATE_FORWARD,
        OPEN_OVERVIEW,
        OPEN_COMMAND_PALETTE,
    }

    val DEFAULT_BUTTON_MAP: Map<Int, DesktopAction> = mapOf(
        1 to DesktopAction.PRIMARY_CLICK,
        2 to DesktopAction.CONTEXT_MENU,
        4 to DesktopAction.MIDDLE_PASTE_OR_AUTOSCROLL,
        8 to DesktopAction.NAVIGATE_BACK,
        16 to DesktopAction.NAVIGATE_FORWARD,
    )

    data class MouseProfile(
        val deviceKey: String,
        val buttonActions: Map<Int, DesktopAction> = DEFAULT_BUTTON_MAP,
        val naturalScrolling: Boolean = false,
        val scrollMultiplier: Double = 1.0,
    ) {
        init {
            require(deviceKey.isNotBlank() && scrollMultiplier in 0.25..5.0) { "invalid MouseProfile" }
        }

        fun learnButton(rawButtonMask: Int, assignedAction: DesktopAction): MouseProfile {
            require(rawButtonMask > 0 && Integer.bitCount(rawButtonMask) == 1) {
                "Learn-my-mouse expects a single pressed button bit, got $rawButtonMask"
            }
            require(rawButtonMask != 1 || assignedAction == DesktopAction.PRIMARY_CLICK) {
                "Primary button (bit 1) must remain PRIMARY_CLICK to prevent UI lockout"
            }
            return copy(buttonActions = buttonActions + (rawButtonMask to assignedAction))
        }

        fun mapScrollDelta(rawWheelV: Double, rawWheelH: Double): Pair<Double, Double> {
            val sign = if (naturalScrolling) -1.0 else 1.0
            return (rawWheelH * scrollMultiplier * sign) to (rawWheelV * scrollMultiplier * sign)
        }
    }

    /**
     * Radial deadzone + quadratic acceleration curve for `IN-07` game-controller pointer mode
     * (avoids axial cross-snap artifacts of naive per-axis deadzones!).
     */
    fun mapGamepadStickToPointerVelocity(
        stickX: Double,
        stickY: Double,
        radialDeadzone: Double = 0.14,
        maxPxPerFrame: Double = 22.0,
    ): Pair<Double, Double> {
        require(radialDeadzone in 0.02..0.45 && maxPxPerFrame > 0.0) { "invalid gamepad pointer curve" }
        val cx = stickX.coerceIn(-1.0, 1.0)
        val cy = stickY.coerceIn(-1.0, 1.0)
        val mag = hypot(cx, cy)
        if (mag <= radialDeadzone) return 0.0 to 0.0
        val clampedMag = mag.coerceAtMost(1.0)
        val norm = (clampedMag - radialDeadzone) / (1.0 - radialDeadzone)
        // Blend 35% linear precision + 65% quadratic fast traversal
        val speed = (0.35 * norm + 0.65 * norm.pow(2.0)) * maxPxPerFrame
        val ux = cx / mag
        val uy = cy / mag
        return (ux * speed) to (uy * speed)
    }

    /** Cycles physical-keyboard layouts (`IN-08`) on `Ctrl+Space` or `Meta+Space`. */
    fun cycleHardwareImeLayout(enabledLayouts: List<String>, currentLayout: String, reverse: Boolean = false): String {
        require(enabledLayouts.isNotEmpty()) { "enabledLayouts cannot be empty" }
        val idx = enabledLayouts.indexOf(currentLayout)
        if (idx < 0) return enabledLayouts.first()
        val step = if (reverse) -1 else 1
        return enabledLayouts[(idx + step + enabledLayouts.size) % enabledLayouts.size]
    }
}
