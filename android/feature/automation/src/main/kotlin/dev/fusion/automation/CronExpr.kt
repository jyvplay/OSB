// SPDX-License-Identifier: Apache-2.0
package dev.fusion.automation

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.BitSet

/**
 * Clean-room POSIX 5-Field Cron Expression Parser & Exact Next-Fire ZonedDateTime Calculator.
 *
 * Implements standard `minute hour day-of-month month day-of-week` semantics (`0-59`, `0-23`,
 * `1-31`, `1-12`, `0-7` where both `0` and `7` denote Sunday), including the classic **POSIX
 * OR-rule**: when BOTH `day-of-month` and `day-of-week` are restricted (neither is `*`), a date
 * matches if EITHER `dom` OR `dow` matches; otherwise both must match!
 */
object CronExpr {
    const val SCOPE =
        "POSIX 5-field cron parser (*, ranges, steps, lists, Sunday 0/7 alias, POSIX dom/dow OR-rule) + DST-aware ZonedDateTime next-occurrence finder; Quartz 6/7-field seconds/L/W/# extensions out of scope."

    class Schedule internal constructor(
        private val minutes: BitSet,
        private val hours: BitSet,
        private val doms: BitSet,
        private val months: BitSet,
        private val dows: BitSet, // 0..6 (0 = Sunday, 1 = Monday .. 6 = Saturday)
        private val domWildcard: Boolean,
        private val dowWildcard: Boolean,
    ) {
        fun matches(dt: ZonedDateTime): Boolean {
            if (!months.get(dt.monthValue)) return false
            if (!matchesDay(dt)) return false
            if (!hours.get(dt.hour)) return false
            return minutes.get(dt.minute)
        }

        /** Finds the strictly next (`> after`) minute-aligned occurrence within up to 4 leap-year cycles (5 years). */
        fun nextAfter(after: ZonedDateTime): ZonedDateTime {
            var cur = after.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
            val horizon = cur.plusYears(5)
            while (cur.isBefore(horizon)) {
                if (!months.get(cur.monthValue)) {
                    cur = cur.withDayOfMonth(1).withHour(0).withMinute(0).plusMonths(1)
                    continue
                }
                if (!matchesDay(cur)) {
                    cur = cur.withHour(0).withMinute(0).plusDays(1)
                    continue
                }
                if (!hours.get(cur.hour)) {
                    cur = cur.withMinute(0).plusHours(1)
                    continue
                }
                if (!minutes.get(cur.minute)) {
                    cur = cur.plusMinutes(1)
                    continue
                }
                return cur
            }
            throw IllegalArgumentException("cron expression has no firing time within 5 years (e.g. Feb 31)")
        }

        private fun matchesDay(dt: ZonedDateTime): Boolean {
            val domOk = doms.get(dt.dayOfMonth)
            val cronDow = dt.dayOfWeek.value % 7 // ISO 7 (Sun) -> 0; 1..6 -> 1..6
            val dowOk = dows.get(cronDow)
            return if (!domWildcard && !dowWildcard) {
                domOk || dowOk // POSIX cron OR-rule!
            } else {
                domOk && dowOk
            }
        }
    }

    fun parse(expr: String): Schedule {
        val clean = when (expr.trim().lowercase()) {
            "@hourly" -> "0 * * * *"
            "@daily", "@midnight" -> "0 0 * * *"
            "@weekly" -> "0 0 * * 0"
            "@monthly" -> "0 0 1 * *"
            "@yearly", "@annually" -> "0 0 1 1 *"
            else -> expr.trim()
        }
        val fields = clean.split(Regex("\\s+"))
        require(fields.size == 5) { "expected 5 cron fields (min hour dom month dow), got ${fields.size} in '$expr'" }

        val minSet = parseField(fields[0], 0, 59)
        val hourSet = parseField(fields[1], 0, 23)
        val domSet = parseField(fields[2], 1, 31)
        val monSet = parseField(fields[3], 1, 12)
        val rawDow = parseField(fields[4], 0, 7)
        if (rawDow.get(7)) {
            rawDow.set(0)
            rawDow.clear(7)
        }
        return Schedule(
            minutes = minSet,
            hours = hourSet,
            doms = domSet,
            months = monSet,
            dows = rawDow,
            domWildcard = fields[2] == "*",
            dowWildcard = fields[4] == "*",
        )
    }

    private fun parseField(field: String, min: Int, max: Int): BitSet {
        require(field.isNotEmpty()) { "empty cron field" }
        val bits = BitSet(max + 1)
        for (part in field.split(',')) {
            require(part.isNotEmpty()) { "empty list item in cron field '$field'" }
            val slash = part.indexOf('/')
            val rangePart = if (slash >= 0) part.substring(0, slash) else part
            val step = if (slash >= 0) {
                val s = part.substring(slash + 1).toInt()
                require(s >= 1) { "cron step must be >= 1, got $s" }
                s
            } else 1

            val (lo, hi) = when {
                rangePart == "*" -> min to max
                '-' in rangePart -> {
                    val b = rangePart.split('-')
                    require(b.size == 2) { "malformed cron range '$rangePart'" }
                    val a = b[0].toInt(); val z = b[1].toInt()
                    require(a in min..max && z in min..max && a <= z) {
                        "cron range $a-$z outside $min..$max"
                    }
                    a to z
                }
                else -> {
                    val v = rangePart.toInt()
                    require(v in min..max) { "cron value $v outside $min..$max" }
                    v to (if (slash >= 0) max else v)
                }
            }
            var v = lo
            while (v <= hi) {
                bits.set(v)
                v += step
            }
        }
        require(!bits.isEmpty) { "cron field '$field' matched empty set" }
        return bits
    }
}
