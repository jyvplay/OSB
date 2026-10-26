// SPDX-License-Identifier: Apache-2.0
package dev.fusion.automation

import java.time.Instant
import java.time.ZoneOffset
import java.util.Collections

/**
 * Vixie 5-field cron parser and matcher (`minute hour dom month dow`, UTC).
 * Supports `*`, lists, ranges, steps, month/day names and 0/7=Sunday. Quartz-only
 * constructs (6-7 fields, seconds, years, L/W/#/?) are REFUSED with an explicit reason:
 * this is the Unix dialect, not a Quartz implementation. Day-of-month/day-of-week both
 * restricted uses standard Vixie OR semantics. Parser + matcher only — no scheduler,
 * alarm, or exact-fire-time guarantee is claimed (Doze, reboot and standby shift work).
 */
object CronSpec {
    const val MAX_EXPRESSION = 128
    const val MAX_SCAN_MINUTES = 525600L // ~1 year, bounded.

    data class Schedule(val minutes: Set<Int>, val hours: Set<Int>, val days: Set<Int>,
                        val months: Set<Int>, val weekdays: Set<Int>, val domRestricted: Boolean, val dowRestricted: Boolean)

    private val MONTHS = mapOf("jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12)
    private val DAYS = mapOf("sun" to 0, "mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6)

    private fun names(value: String, table: Map<String, Int>): String {
        var out = value.lowercase()
        table.forEach { (name, number) -> out = out.replace(name, number.toString()) }
        return out
    }

    private fun field(raw: String, min: Int, max: Int, label: String, table: Map<String, Int> = emptyMap()): Set<Int> {
        require(raw.isNotEmpty() && raw.length <= 64) { "Bad $label field" }
        require(raw.all { it.isDigit() || it in "*,/-" || it.isLetter() }) { "Bad $label characters (Quartz L/W/#/? are not supported)" }
        require('?' !in raw && 'L' !in raw.uppercase() && 'W' !in raw.uppercase() && '#' !in raw) { "Quartz-only syntax refused in $label" }
        val normalized = names(raw, table)
        require(normalized.all { it.isDigit() || it in "*,/-" }) { "Unknown $label name" }
        val values = sortedSetOf<Int>()
        normalized.split(',').forEach { part ->
            require(part.isNotEmpty()) { "Empty $label list item" }
            val (range, step) = if ('/' in part) {
                val sides = part.split('/')
                require(sides.size == 2 && sides[1].isNotEmpty()) { "Bad $label step" }
                sides[0] to (sides[1].toIntOrNull() ?: throw IllegalArgumentException("Bad $label step"))
            } else part to 1
            require(step in 1..(max - min + 1)) { "Bad $label step" }
            val (from, to) = if (range == "*" || range.isEmpty()) min to max
            else if ('-' in range) {
                val ends = range.split('-')
                require(ends.size == 2) { "Bad $label range" }
                (ends[0].toIntOrNull() to ends[1].toIntOrNull()).let { (a, b) ->
                    require(a != null && b != null && a in min..max && b in min..max && a <= b) { "Bad $label range" }
                    a to b
                }
            } else {
                val single = range.toIntOrNull()
                require(single != null && single in min..max) { "Bad $label value" }
                single to single
            }
            var v = from
            while (v <= to) { values += v; v += step }
        }
        require(values.isNotEmpty()) { "Empty $label set" }
        return Collections.unmodifiableSet(values)
    }

    fun parse(expression: String): Schedule {
        require(expression.length in 9..MAX_EXPRESSION) { "Expression must be 5 Vixie fields" }
        val fields = expression.trim().split(Regex("\\s+"))
        require(fields.size == 5) { "Need exactly 5 fields (got ${fields.size}); Quartz 6-7 field form is refused" }
        val minutes = field(fields[0], 0, 59, "minute")
        val hours = field(fields[1], 0, 23, "hour")
        val days = field(fields[2], 1, 31, "day-of-month")
        val months = field(fields[3], 1, 12, "month", MONTHS)
        var weekdays = field(fields[4], 0, 7, "day-of-week", DAYS)
        if (7 in weekdays) weekdays = Collections.unmodifiableSet((weekdays - 7) + 0)
        return Schedule(minutes, hours, days, months, weekdays,
            fields[2] != "*", fields[4] != "*")
    }

    /** Vixie match at a UTC epoch minute. dom/dow both restricted → OR; else the restricted one. */
    fun matches(schedule: Schedule, epochMinute: Long): Boolean {
        val t = Instant.ofEpochSecond(epochMinute * 60).atZone(ZoneOffset.UTC)
        if (t.minute !in schedule.minutes || t.hour !in schedule.hours || t.monthValue !in schedule.months) return false
        val dom = t.dayOfMonth in schedule.days
        val dow = (t.dayOfWeek.value % 7) in schedule.weekdays
        return when {
            schedule.domRestricted && schedule.dowRestricted -> dom || dow
            schedule.domRestricted -> dom
            schedule.dowRestricted -> dow
            else -> true
        }
    }

    /** Next firing strictly after [afterEpochMinute], scanning at most ~1 year. Null if none. */
    fun nextFire(schedule: Schedule, afterEpochMinute: Long): Long? {
        require(afterEpochMinute in 0..4102444799L) { "Start outside 1970..2099" }
        var m = afterEpochMinute + 1
        val end = minOf(m + MAX_SCAN_MINUTES, 4102444799L)
        while (m <= end) {
            if (matches(schedule, m)) return m
            m++
        }
        return null
    }

    fun describe(schedule: Schedule): String =
        "minutes=${schedule.minutes.size}/60 hours=${schedule.hours.size}/24 days=${schedule.days.size}/31 " +
            "months=${schedule.months.size}/12 weekdays=${schedule.weekdays.size}/7 (UTC, Vixie OR day semantics)"
}
