// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Clean-room Game-Controller Desktop Pointer Mode Mapper (`IN-07`): radial inner/outer deadzone
 * compensation, S-curve pointer velocity, right-stick smooth scroll, and bumper/trigger click map.
 */
object GamepadPointerModeMapper {
    const val SCOPE =
        "Pure-JVM game-controller analog-stick radial deadzone + S-curve desktop pointer & scroll velocity mapper (IN-07)."

    data class GamepadAxesButtons(
        val leftStickX: Double, // -1..+1
        val leftStickY: Double,
        val rightStickX: Double = 0.0,
        val rightStickY: Double = 0.0,
        val buttonSouthA: Boolean = false,
        val buttonEastB: Boolean = false,
        val rightBumper: Boolean = false,
        val leftTrigger: Double = 0.0, // precision slow-down modifier when > 0.5
    )

    data class PointerSynthesisStep(
        val nextCursorX: Double,
        val nextCursorY: Double,
        val scrollDeltaX: Double,
        val scrollDeltaY: Double,
        val primaryButtonDown: Boolean,
        val secondaryButtonDown: Boolean,
    )

    fun step(
        curX: Double,
        curY: Double,
        viewportW: Int,
        viewportH: Int,
        input: GamepadAxesButtons,
        dtSeconds: Double = 1.0 / 60.0,
        innerDeadzone: Double = 0.14,
        maxSpeedPxPerSec: Double = 960.0,
    ): PointerSynthesisStep {
        require(viewportW >= 64 && viewportH >= 64 && dtSeconds in 0.001..0.25) { "invalid viewport/dt" }
        val precisionScale = if (input.leftTrigger >= 0.5) 0.35 else 1.0
        val (vx, vy) = radialCurve(input.leftStickX, input.leftStickY, innerDeadzone)
        val nx = (curX + vx * maxSpeedPxPerSec * precisionScale * dtSeconds).coerceIn(0.0, (viewportW - 1).toDouble())
        val ny = (curY + vy * maxSpeedPxPerSec * precisionScale * dtSeconds).coerceIn(0.0, (viewportH - 1).toDouble())
        val (sx, sy) = radialCurve(input.rightStickX, input.rightStickY, innerDeadzone)
        return PointerSynthesisStep(
            nextCursorX = nx,
            nextCursorY = ny,
            scrollDeltaX = sx * 420.0 * dtSeconds,
            scrollDeltaY = -sy * 420.0 * dtSeconds,
            primaryButtonDown = input.buttonSouthA || input.rightBumper,
            secondaryButtonDown = input.buttonEastB,
        )
    }

    private fun radialCurve(rawX: Double, rawY: Double, deadzone: Double): Pair<Double, Double> {
        val x = rawX.coerceIn(-1.0, 1.0)
        val y = rawY.coerceIn(-1.0, 1.0)
        val mag = sqrt(x * x + y * y)
        if (mag <= deadzone) return 0.0 to 0.0
        val norm = ((mag - deadzone) / (1.0 - deadzone)).coerceIn(0.0, 1.0)
        val curved = norm.pow(1.65)
        return (x / mag) * curved to (y / mag) * curved
    }
}
