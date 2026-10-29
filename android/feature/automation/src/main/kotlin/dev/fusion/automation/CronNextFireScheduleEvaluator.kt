// SPDX-License-Identifier: Apache-2.0
package dev.fusion.automation

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * POSIX 5-field **Cron Schedule Parser & Next-Fire Evaluator** (`minute hour dom month dow` with
 * `*`, ranges `1-5`, steps `* /15`, and comma lists `0,30`) for local offline Fusion Automation jobs!
 */
object CronNextFireScheduleEvaluator {
    const val SCOPE =
        "POSIX 5-field cron expression parser (*, a-b, */step, lists) and exact ZonedDateTime next-fire calculator."

    data class CronSpec(
        val minutes: Set<Int>,
        val hours: Set<Int>,
        val daysOfMonth: Set<Int>,
        val months: Set<Int>,
        val daysOfWeekIso1To7: Set<Int>,
    )

    fun parse5Field(expr: String): CronSpec {
        val parts = expr.trim().split(Regex("\\s+"))
        require(parts.size == 5) { "Expected 5 cron fields (min hour dom month dow), got ${parts.size}" }
        val mins = parseField(parts[0], 0, 59)
        val hrs = parseField(parts[1], 0, 23)
        val dom = parseField(parts[2], 1, 31)
        val mon = parseField(parts[3], 1, 12)
        // Cron DOW allows 0..7 where both 0 and 7 mean Sunday (ISO 7)
        val rawDow = parseField(parts[4], 0, 7).map { if (it == 0) 7 else it }.toSet()
        return CronSpec(mins, hrs, dom, mon, rawDow)
    }

    fun nextFireAfter(spec: CronSpec, after: ZonedDateTime): ZonedDateTime {
        var cur = after.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        val limit = cur.plusYears(4)
        while (!cur.isAfter(limit)) {
            if (cur.monthValue !in spec.months) {
                cur = cur.withDayOfMonth(1).withHour(0).withMinute(0).plusMonths(1)
                continue
            }
            if (cur.dayOfMonth !in spec.daysOfMonth || cur.dayOfWeek.value !in spec.daysOfWeekIso1To7) {
                cur = cur.withHour(0).withMinute(0).plusDays(1)
                continue
            }
            if (cur.hour !in spec.hours) {
                cur = cur.withMinute(0).plusHours(1)
                continue
            }
            if (cur.minute !in spec.minutes) {
                cur = cur.plusMinutes(1)
                continue
            }
            return cur
        }
        error("No matching cron fire time within 4-year horizon")
    }

    private fun parseField(token: String, min: Int, max: Int): Set<Int> {
        val out = LinkedHashSet<Int>()
        for (piece in token.split(',')) {
            val slash = piece.split('/', limit = 2)
            val step = if (slash.size == 2) slash[1].toInt().also { require(it >= 1) } else 1
            val rangePart = slash[0]
            val (lo, hi) = when {
                rangePart == "*" -> min to max
                '-' in rangePart -> {
                    val (a, b) = rangePart.split('-', limit = 2)
                    a.toInt() to b.toInt()
                }
                else -> {
                    val v = rangePart.toInt()
                    v to (if (slash.size == 2) max else v)
                }
            }
            require(lo in min..max && hi in min..max && lo <= hi) { "Cron value out of [$min..$max]: $piece" }
            var v = lo
            while (v <= hi) { out.add(v); v += step }
        }
        return out
    }
}
