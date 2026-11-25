// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

/**
 * Bounded ISO-8601 `YYYY-MM-DD` calendar date validator with leap-year rules.
 */
class Iso8601DateValidator private constructor() {
    companion object {
        private val DATE_RE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

        fun isLeapYear(year: Int): Boolean =
            (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

        fun isValid(date: String): Boolean {
            val m = DATE_RE.matchEntire(date) ?: return false
            val year = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val day = m.groupValues[3].toInt()
            if (month !in 1..12) return false
            val maxDay = when (month) {
                1, 3, 5, 7, 8, 10, 12 -> 31
                4, 6, 9, 11 -> 30
                2 -> if (isLeapYear(year)) 29 else 28
                else -> return false
            }
            return day in 1..maxDay
        }
    }
}
