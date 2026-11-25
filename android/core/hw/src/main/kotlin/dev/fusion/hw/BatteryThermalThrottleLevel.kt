// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

/**
 * Bounded battery thermal throttle level classifier (tenths of degrees Celsius).
 */
class BatteryThermalThrottleLevel private constructor() {
    companion object {
        enum class Level { NONE, LIGHT, MODERATE, SEVERE, CRITICAL }

        fun classify(tempTenthsC: Int): Level {
            require(tempTenthsC in -400..1200) { "temperature out of physical bounds: $tempTenthsC" }
            return when {
                tempTenthsC < 380 -> Level.NONE
                tempTenthsC < 420 -> Level.LIGHT
                tempTenthsC < 450 -> Level.MODERATE
                tempTenthsC < 480 -> Level.SEVERE
                else -> Level.CRITICAL
            }
        }
    }
}
