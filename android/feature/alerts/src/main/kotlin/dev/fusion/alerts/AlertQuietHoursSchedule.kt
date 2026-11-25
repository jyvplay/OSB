// SPDX-License-Identifier: Apache-2.0
package dev.fusion.alerts

/**
 * Production-grade quiet-hours schedule evaluator with midnight wrap-around
 * and CRITICAL priority bypass.
 */
class AlertQuietHoursSchedule private constructor() {
    companion object {
        fun isSuppressed(
            minuteOfDay: Int,
            quietStartMin: Int,
            quietEndMin: Int,
            isCritical: Boolean
        ): Boolean {
            require(minuteOfDay in 0..1439 && quietStartMin in 0..1439 && quietEndMin in 0..1439) {
                "minuteOfDay out of bounds"
            }
            if (isCritical || quietStartMin == quietEndMin) return false
            return if (quietStartMin < quietEndMin) {
                minuteOfDay in quietStartMin until quietEndMin
            } else {
                minuteOfDay >= quietStartMin || minuteOfDay < quietEndMin
            }
        }
    }
}
