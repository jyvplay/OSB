// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

import java.security.SecureRandom

/**
 * **Local Hardware Attack Vector `#21` — `Charger-Surfing` Defense (Cronin et al., USENIX Security 2021):**
 * While USB VBUS charging is active, localized touchscreen button-press ripple animations produce
 * position-correlated `-50..+50 mV` power-line traces (`98.7%` single-button inference accuracy).
 *
 * This pure-JVM defense engine:
 * 1. Detects active VBUS / wireless-Qi charging during credential/PIN entry;
 * 2. Suppresses localized touch-ripple shaders (`allowLocalizedRipple = false`);
 * 3. Computes complementary full-frame uniform dummy luminance load (`compensationDutyCycle`) so
 *    instantaneous panel current `I_panel(t)` stays constant regardless of touch `(x, y)` coordinate;
 * 4. Emits a CSPRNG Fisher-Yates 10-digit PIN pad permutation after every keystroke when VBUS is hot!
 */
object ChargerSurfingPowerMasker {
    const val SCOPE =
        "Vector #21 Charger-Surfing (USENIX Security 2021) USB power-line side-channel defense: VBUS touch-ripple suppression, constant-current full-screen luminance equalizer, and per-digit CSPRNG keypad permutation."

    data class ChargingInputContext(
        val usbVbusAttached: Boolean,
        val wirelessQiCharging: Boolean,
        val activePinPadEntry: Boolean,
        val touchedButtonSubpixelLoadMilliwatts: Double,
        val targetConstantFrameLoadMilliwatts: Double = 240.0,
    )

    data class PowerMaskPlan(
        val mitigationArmed: Boolean,
        val allowLocalizedTouchRipple: Boolean,
        val dummyComplementaryLoadMilliwatts: Double,
        val totalEqualizedLoadMilliwatts: Double,
        val permutedDigits: List<Int>,
        val singleDigitLeakAdvantageBound: Double,
    )

    fun evaluate(ctx: ChargingInputContext, rng: SecureRandom = SecureRandom()): PowerMaskPlan {
        require(ctx.touchedButtonSubpixelLoadMilliwatts >= 0.0 && ctx.targetConstantFrameLoadMilliwatts >= 50.0) {
            "Invalid display load parameters"
        }
        val charging = ctx.usbVbusAttached || ctx.wirelessQiCharging
        if (!charging || !ctx.activePinPadEntry) {
            return PowerMaskPlan(
                mitigationArmed = false,
                allowLocalizedTouchRipple = true,
                dummyComplementaryLoadMilliwatts = 0.0,
                totalEqualizedLoadMilliwatts = ctx.touchedButtonSubpixelLoadMilliwatts,
                permutedDigits = (0..9).toList(),
                singleDigitLeakAdvantageBound = 0.0,
            )
        }
        val target = maxOf(ctx.targetConstantFrameLoadMilliwatts, ctx.touchedButtonSubpixelLoadMilliwatts)
        val dummyLoad = target - ctx.touchedButtonSubpixelLoadMilliwatts
        val digits = (0..9).toMutableList()
        for (i in digits.lastIndex downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = digits[i]; digits[i] = digits[j]; digits[j] = tmp
        }
        return PowerMaskPlan(
            mitigationArmed = true,
            allowLocalizedTouchRipple = false,
            dummyComplementaryLoadMilliwatts = dummyLoad,
            totalEqualizedLoadMilliwatts = ctx.touchedButtonSubpixelLoadMilliwatts + dummyLoad,
            permutedDigits = digits,
            singleDigitLeakAdvantageBound = 0.0, // Constant load + uniform S_10 permutation => exact 1/10 = 10% prior
        )
    }
}
