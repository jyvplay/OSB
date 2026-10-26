// SPDX-License-Identifier: Apache-2.0
package dev.fusion.automation

/**
 * ISO 8601 week-date calculator for schedule rules.
 *
 * Definitions implemented (ISO 8601-1 calendar week rules):
 * - Weeks run Monday (1) to Sunday (7).
 * - Week 01 of a week-based year is the week containing the first
 *   Thursday of the Gregorian year (equivalently, the week containing
 *   4 January).
 * - A week-based year has 53 weeks iff 1 January falls on a Thursday,
 *   or it is a leap year and 1 January falls on a Wednesday; otherwise
 *   52.
 * - Dates in late December can belong to week 01 of the next week-based
 *   year; dates in early January can belong to week 52/53 of the
 *   previous one.
 *
 * The cross-language verifier for this kernel is Python's
 * datetime.date.isocalendar(), an independent held-out implementation.
 * Day-of-week uses the days-from-civil algorithm (Howard Hinnant's
 * public-domain chrono math). Pure JVM integer logic; no java.time, no
 * timezone, no clock.
 */
object IsoWeek {
    data class WeekDate(val weekYear: Int, val week: Int, val dayOfWeek: Int)

    fun isLeap(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    private val DAYS = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    fun daysInMonth(year: Int, month: Int): Int {
        require(month in 1..12) { "month out of 1..12: $month" }
        return if (month == 2 && isLeap(year)) 29 else DAYS[month - 1]
    }

    /** Days since 1970-01-01 (negative before); Hinnant days_from_civil. */
    fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        require(year in 1..9999) { "year out of 1..9999: $year" }
        require(day in 1..daysInMonth(year, month)) { "day out of range: $year-$month-$day" }
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    /** ISO day of week: Monday=1..Sunday=7. 1970-01-01 was a Thursday (4). */
    fun dayOfWeek(year: Int, month: Int, day: Int): Int {
        val z = daysFromCivil(year, month, day)
        val wd = ((z + 3) % 7 + 7) % 7 // 0 = Monday
        return wd.toInt() + 1
    }

    fun ordinalDay(year: Int, month: Int, day: Int): Int {
        require(day in 1..daysInMonth(year, month)) { "day out of range" }
        var ord = day
        for (m in 1 until month) ord += daysInMonth(year, m)
        return ord
    }

    fun longYear(year: Int): Boolean {
        val jan1 = dayOfWeek(year, 1, 1)
        return jan1 == 4 || (isLeap(year) && jan1 == 3)
    }

    fun weekDate(year: Int, month: Int, day: Int): WeekDate {
        val dow = dayOfWeek(year, month, day)
        val ord = ordinalDay(year, month, day)
        var week = (ord - dow + 10) / 7
        var weekYear = year
        if (week < 1) {
            weekYear = year - 1
            week = if (longYear(weekYear)) 53 else 52
        } else if (week == 53 && !longYear(year)) {
            weekYear = year + 1
            week = 1
        }
        return WeekDate(weekYear, week, dow)
    }
}
