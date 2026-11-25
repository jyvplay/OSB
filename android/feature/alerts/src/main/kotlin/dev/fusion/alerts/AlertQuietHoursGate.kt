// SPDX-License-Identifier: Apache-2.0
package dev.fusion.alerts

/**
 * Bounded quiet-hours gate over minute-of-day [0, 1439] with midnight wrap-around.
 */
class AlertQuietHoursGate private constructor() {
    companion object {
        const val MINUTES_PER_DAY = 1440

        fun isQuiet(minuteOfDay: Int, startMinute: Int, endMinute: Int): Boolean {
            require(minuteOfDay in 0 until MINUTES_PER_DAY) { "minuteOfDay out of bounds" }
            require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute out of bounds" }
            require(endMinute in 0 until MINUTES_PER_DAY) { "endMinute out of bounds" }
            if (startMinute == endMinute) return false
            return if (startMinute < endMinute) {
                minuteOfDay in startMinute until endMinute
            } else {
                minuteOfDay >= startMinute || minuteOfDay < endMinute
            }
        }
    }
}
