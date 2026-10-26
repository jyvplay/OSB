// SPDX-License-Identifier: Apache-2.0
package dev.fusion.automation

import java.time.LocalDate

/**
 * R28 EXECUTED host kernel. RFC 5545-RRULE subset for task reminders:
 * FREQ=DAILY|WEEKLY|MONTHLY with INTERVAL, COUNT xor UNTIL, and BYDAY for
 * WEEKLY. Expansion is date-based (UTC, no time zones) and capped at 366
 * instances. Monthly dates that do not exist (e.g. Feb 30) are SKIPPED per
 * RFC 5545; nothing is shifted or invented.
 */
class Recurrence private constructor(
    val freq: Freq, val interval: Int, val count: Int?, val until: LocalDate?, val byDay: Set<Int>,
) {
    enum class Freq { DAILY, WEEKLY, MONTHLY }

    companion object {
        const val MAX_INSTANCES = 366
        const val MAX_INTERVAL = 999
        private val DAYS = mapOf("MO" to 1, "TU" to 2, "WE" to 3, "TH" to 4, "FR" to 5, "SA" to 6, "SU" to 7)

        fun parse(rule: String): Recurrence {
            require(rule.length in 10..256) { "RRULE must be 10..256 chars" }
            val body = rule.removePrefix("RRULE:")
            require(body != rule) { "RRULE must start with RRULE:" }
            val props = body.split(';').associate { part ->
                val k = part.substringBefore('=')
                require('=' in part && k.matches(Regex("[A-Z]+"))) { "Bad RRULE property: $part" }
                k to part.substringAfter('=')
            }
            val freq = when (props["FREQ"]) {
                "DAILY" -> Freq.DAILY
                "WEEKLY" -> Freq.WEEKLY
                "MONTHLY" -> Freq.MONTHLY
                else -> throw IllegalArgumentException("FREQ must be DAILY, WEEKLY or MONTHLY")
            }
            val interval = props["INTERVAL"]?.toIntOrNull() ?: 1
            require(interval in 1..MAX_INTERVAL) { "INTERVAL must be 1..$MAX_INTERVAL" }
            val count = props["COUNT"]?.toIntOrNull()
            if ("COUNT" in props) require(count != null && count in 1..MAX_INSTANCES) {
                "COUNT must be 1..$MAX_INSTANCES"
            }
            val until = props["UNTIL"]?.let {
                require(it.matches(Regex("\\d{8}"))) { "UNTIL must be YYYYMMDD" }
                LocalDate.of(it.substring(0, 4).toInt(), it.substring(4, 6).toInt(), it.substring(6, 8).toInt())
            }
            require((count == null) != (until == null)) { "Exactly one of COUNT or UNTIL is required" }
            val byDay = props["BYDAY"]?.split(',')?.map {
                DAYS[it] ?: throw IllegalArgumentException("BYDAY must be MO,TU,WE,TH,FR,SA,SU")
            }?.toSet().orEmpty()
            require(byDay.isEmpty() || freq == Freq.WEEKLY) { "BYDAY is only supported for WEEKLY" }
            require(byDay.size <= 7) { "At most 7 BYDAY entries" }
            val known = setOf("FREQ", "INTERVAL", "COUNT", "UNTIL", "BYDAY")
            require(props.keys.all { it in known }) { "Unsupported RRULE properties are refused" }
            return Recurrence(freq, interval, count, until, byDay)
        }
    }

    /** Expand from [start] (inclusive). Deterministic, bounded, date-only. */
    fun expand(start: LocalDate): List<LocalDate> {
        require(start.year in 1970..2100) { "start out of range" }
        until?.let { require(!it.isBefore(start) && it.year <= start.year + 5) { "UNTIL must be within 5 years after start" } }
        val out = mutableListOf<LocalDate>()
        var day = start
        val horizon = start.plusYears(6)
        while (out.size < MAX_INSTANCES && !day.isAfter(horizon)) {
            if (until != null && day.isAfter(until)) break
            if (count != null && out.size >= count) break
            if (matches(day, start)) out.add(day)
            day = day.plusDays(1)
            if (until == null && day.isAfter(start.plusYears(5).plusDays(2))) break
        }
        if (count != null) require(out.size == count) { "Rule cannot produce COUNT instances within bounds" }
        return out
    }

    private fun matches(day: LocalDate, start: LocalDate): Boolean {
        if (day.isBefore(start)) return false
        return when (freq) {
            Freq.DAILY -> daysBetween(start, day) % interval == 0L
            Freq.WEEKLY -> {
                val week = daysBetween(startOfWeek(start), startOfWeek(day)) / 7
                if (week % interval != 0L) return false
                val days = byDay.ifEmpty { setOf(start.dayOfWeek.value) }
                day.dayOfWeek.value in days
            }
            Freq.MONTHLY -> {
                val months = (day.year - start.year) * 12 + (day.monthValue - start.monthValue)
                if (months % interval != 0) return false
                day.dayOfMonth == start.dayOfMonth
            }
        }
    }

    private fun daysBetween(a: LocalDate, b: LocalDate): Long = b.toEpochDay() - a.toEpochDay()
    private fun startOfWeek(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())
}
