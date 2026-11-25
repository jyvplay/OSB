// SPDX-License-Identifier: Apache-2.0
package dev.fusion.automation

/**
 * Bounded cron field range validator.
 */
class AutomationCronFieldRange private constructor() {
    companion object {
        enum class Field(val min: Int, val max: Int) {
            MINUTE(0, 59),
            HOUR(0, 23),
            DAY_OF_MONTH(1, 31),
            MONTH(1, 12),
            DAY_OF_WEEK(0, 6)
        }

        fun isValidValue(field: Field, value: Int): Boolean = value in field.min..field.max
    }
}
