// SPDX-License-Identifier: Apache-2.0
package dev.fusion.alerts

import java.time.ZonedDateTime

/**
 * Clean-room 5-Field POSIX **`cron` Expression Parser & Deterministic Next-Fire Calculator**
 * (`minute hour dayOfMonth month dayOfWeek`, supporting wildcard, `a-b`, `step`, `a-b/step`, and `,` lists).
 */
object CronExpressionSchedule {
    const val SCOPE =
        "Clean-room 5-field POSIX cron expression parser (*, lists, ranges, /steps) & deterministic next-fire ZonedDateTime resolver."

    data class CronSpec(
        val minutes: Set<Int>,
        val hours: Set<Int>,
        val daysOfMonth: Set<Int>,
        val months: Set<Int>,
        val daysOfWeek: Set<Int>, // 0=Sunday .. 6=Saturday
        val domWildcard: Boolean,
        val dowWildcard: Boolean,
    )

    fun parse(expr: String): CronSpec {
        val parts = expr.trim().split(Regex("\\s+"))
        require(parts.size == 5) { "POSIX cron requires 5 fields (min hour dom mon dow), got ${parts.size}" }
        val mins = parseField(parts[0], 0, 59)
        val hrs = parseField(parts[1], 0, 23)
        val dom = parseField(parts[2], 1, 31)
        val mon = parseField(parts[3], 1, 12)
        val dowRaw = parseField(parts[4], 0, 7).map { if (it == 7) 0 else it }.toSortedSet()
        return CronSpec(mins, hrs, dom, mon, dowRaw, parts[2] == "*", parts[4] == "*")
    }

    fun nextFireAfter(spec: CronSpec, after: ZonedDateTime): ZonedDateTime {
        var cur = after.withSecond(0).withNano(0).plusMinutes(1)
        val maxEnd = cur.plusYears(5)
        while (!cur.isAfter(maxEnd)) {
            if (cur.monthValue !in spec.months) {
                cur = cur.withDayOfMonth(1).withHour(0).withMinute(0).plusMonths(1)
                continue
            }
            val cronDow = cur.dayOfWeek.value % 7 // Mon=1..Sat=6, Sun=0
            val dayMatches = when {
                spec.domWildcard && spec.dowWildcard -> true
                spec.domWildcard -> cronDow in spec.daysOfWeek
                spec.dowWildcard -> cur.dayOfMonth in spec.daysOfMonth
                else -> cur.dayOfMonth in spec.daysOfMonth || cronDow in spec.daysOfWeek // POSIX OR semantics!
            }
            if (!dayMatches) {
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
        throw IllegalArgumentException("cron expression has no fire time within 5 years")
    }

    private fun parseField(field: String, min: Int, max: Int): Set<Int> {
        val out = sortedSetOf<Int>()
        for (item in field.split(',')) {
            val slash = item.split('/')
            require(slash.size in 1..2) { "malformed cron step '$item'" }
            val step = if (slash.size == 2) slash[1].toInt().also { require(it >= 1) { "step < 1" } } else 1
            val rangePart = slash[0]
            val (lo, hi) = if (rangePart == "*") {
                min to max
            } else if ('-' in rangePart) {
                val ab = rangePart.split('-')
                require(ab.size == 2) { "malformed cron range '$rangePart'" }
                ab[0].toInt() to ab[1].toInt()
            } else {
                val single = rangePart.toInt()
                single to (if (slash.size == 2) max else single)
            }
            require(lo in min..max && hi in min..max && lo <= hi) { "cron value [$lo..$hi] outside [$min..$max]" }
            var v = lo
            while (v <= hi) {
                out.add(v)
                v += step
            }
        }
        return out
    }
}
