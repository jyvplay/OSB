// SPDX-License-Identifier: Apache-2.0
package dev.fusion.a11y

import kotlin.math.hypot

/**
 * Clean-room High-Visibility Focus Ring Token Builder & Pointer/Head-Tracker Dwell-Click State
 * Machine (`AX-06`).
 */
object FocusRingDwellClickController {
    const val SCOPE =
        "Pure-JVM high-contrast dual-stroke focus ring token calculator and tremor-tolerant pointer dwell-click state machine (AX-06)."

    enum class DwellAction { PRIMARY_CLICK, SECONDARY_CONTEXT_MENU, SCROLL_DRAG }

    data class FocusRingSpec(
        val innerStrokeDp: Float,
        val outerHaloStrokeDp: Float,
        val innerArgbHex: String,
        val outerHaloArgbHex: String,
        val cornerPaddingDp: Float,
    )

    data class DwellState(
        val anchorX: Float = 0f,
        val anchorY: Float = 0f,
        val accumulatedMs: Long = 0L,
        val firedForCurrentAnchor: Boolean = false,
        val armedAction: DwellAction = DwellAction.PRIMARY_CLICK,
    )

    data class DwellStepOutput(
        val nextState: DwellState,
        val progress0To1: Float,
        val triggeredAction: DwellAction?,
    )

    fun buildFocusRing(highVisibilityEnabled: Boolean, darkTheme: Boolean): FocusRingSpec =
        if (highVisibilityEnabled) {
            FocusRingSpec(
                innerStrokeDp = 3.5f,
                outerHaloStrokeDp = 2.0f,
                innerArgbHex = if (darkTheme) "#FFFFD60A" else "#FF0040DD",
                outerHaloArgbHex = if (darkTheme) "#FF000000" else "#FFFFFFFF",
                cornerPaddingDp = 4.0f,
            )
        } else {
            FocusRingSpec(2.0f, 1.0f, if (darkTheme) "#FF64D2FF" else "#FF0A84FF", "#66000000", 2.0f)
        }

    fun stepDwell(
        state: DwellState,
        pointerX: Float,
        pointerY: Float,
        dtMs: Long,
        dwellThresholdMs: Long = 650L,
        tremorTolerancePx: Float = 14f,
    ): DwellStepOutput {
        require(dtMs >= 0L && dwellThresholdMs in 200L..3000L && tremorTolerancePx in 2f..80f) {
            "invalid dwell-click timing or tolerance"
        }
        val dist = hypot((pointerX - state.anchorX).toDouble(), (pointerY - state.anchorY).toDouble()).toFloat()
        if (dist > tremorTolerancePx) {
            val reset = state.copy(anchorX = pointerX, anchorY = pointerY, accumulatedMs = 0L, firedForCurrentAnchor = false)
            return DwellStepOutput(reset, 0f, null)
        }
        if (state.firedForCurrentAnchor) {
            return DwellStepOutput(state, 1f, null)
        }
        val nextAcc = state.accumulatedMs + dtMs
        return if (nextAcc >= dwellThresholdMs) {
            val done = state.copy(accumulatedMs = dwellThresholdMs, firedForCurrentAnchor = true)
            DwellStepOutput(done, 1f, state.armedAction)
        } else {
            val upd = state.copy(accumulatedMs = nextAcc)
            DwellStepOutput(upd, (nextAcc.toFloat() / dwellThresholdMs.toFloat()).coerceIn(0f, 1f), null)
        }
    }
}
