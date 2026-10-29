// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

import kotlin.math.hypot

/**
 * Pure-JVM **Desktop/Trackpad/Stylus Secondary-Click (Right-Click) vs Long-Press Disambiguator**
 * for `:core:input`, distinguishing hardware mouse secondary button (`BUTTON_SECONDARY`), two-finger
 * trackpad tap, stylus barrel button, and stationary touch long-press (`>= 450 ms`, slop `<= 8 dp`).
 */
object SecondaryClickGestureClassifier {
    const val SCOPE =
        "Hardware mouse right-click, two-finger trackpad tap, stylus barrel-button, and touch long-press context-menu trigger classifier."

    enum class ContextTriggerSource {
        MOUSE_RIGHT_BUTTON,
        TRACKPAD_TWO_FINGER_TAP,
        STYLUS_BARREL_SECONDARY,
        TOUCH_LONG_PRESS,
        NOT_CONTEXT_TRIGGER,
    }

    data class PointerGestureSample(
        val isMouseSecondaryButtonDown: Boolean = false,
        val isStylusBarrelPressed: Boolean = false,
        val activeTouchPointerCount: Int = 1,
        val durationMs: Long = 0L,
        val startXDp: Double = 0.0,
        val startYDp: Double = 0.0,
        val endXDp: Double = 0.0,
        val endYDp: Double = 0.0,
    )

    fun classify(sample: PointerGestureSample): ContextTriggerSource {
        val driftDp = hypot(sample.endXDp - sample.startXDp, sample.endYDp - sample.startYDp)
        if (sample.isMouseSecondaryButtonDown && driftDp <= 6.0) return ContextTriggerSource.MOUSE_RIGHT_BUTTON
        if (sample.isStylusBarrelPressed && driftDp <= 8.0) return ContextTriggerSource.STYLUS_BARREL_SECONDARY
        if (sample.activeTouchPointerCount == 2 && sample.durationMs in 20L..260L && driftDp <= 10.0) {
            return ContextTriggerSource.TRACKPAD_TWO_FINGER_TAP
        }
        if (sample.activeTouchPointerCount == 1 && sample.durationMs >= 450L && driftDp <= 8.0) {
            return ContextTriggerSource.TOUCH_LONG_PRESS
        }
        return ContextTriggerSource.NOT_CONTEXT_TRIGGER
    }
}
