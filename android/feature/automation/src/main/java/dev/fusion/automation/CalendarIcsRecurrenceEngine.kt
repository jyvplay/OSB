// SPDX-License-Identifier: Apache-2.0
package dev.fusion.automation

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Clean-room IETF `RFC 5545` iCalendar (`VEVENT` / `VTODO` + `RRULE` Recurrence Expander, `PR-04`),
 * Inexact Reminder Window Planner (`PR-03`, `PR-10` — zero `SCHEDULE_EXACT_ALARM` permission!),
 * and `RFC 6350` vCard 4.0 Contact Card Parser (`PR-05`).
 */
object CalendarIcsRecurrenceEngine {
    enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

    data class RecurrenceRule(
        val freq: Freq,
        val interval: Int = 1,
        val count: Int? = null,
        val untilDate: LocalDate? = null,
        val byDay: Set<DayOfWeek> = emptySet(),
    ) {
        init {
            require(interval in 1..366) { "RRULE INTERVAL outside 1..366" }
            require(count == null || count in 1..1_000) { "RRULE COUNT outside 1..1000" }
        }
    }

    data class InexactReminderWindow(
        val taskId: String,
        val earliestTriggerEpochMs: Long,
        val latestFlexEpochMs: Long,
        val usesExactAlarmPermission: Boolean = false,
    ) {
        init {
            check(!usesExactAlarmPermission) { "PR-03/PR-10 invariant: reminders must use inexact flex windows, never SCHEDULE_EXACT_ALARM" }
        }
    }

    private val BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE // yyyyMMdd

    fun parseRrule(rruleLine: String): RecurrenceRule {
        val raw = rruleLine.trim().removePrefix("RRULE:")
        val kv = raw.split(';').filter { '=' in it }.associate {
            val p = it.split('=', limit = 2)
            p[0].trim().uppercase() to p[1].trim().uppercase()
        }
        val freq = Freq.valueOf(kv.getValue("FREQ"))
        val interval = kv["INTERVAL"]?.toInt() ?: 1
        val count = kv["COUNT"]?.toInt()
        val until = kv["UNTIL"]?.take(8)?.let { LocalDate.parse(it, BASIC_DATE) }
        val byDay = kv["BYDAY"]?.split(',')?.filter { it.isNotEmpty() }?.map { code ->
            when (code.takeLast(2)) {
                "MO" -> DayOfWeek.MONDAY
                "TU" -> DayOfWeek.TUESDAY
                "WE" -> DayOfWeek.WEDNESDAY
                "TH" -> DayOfWeek.THURSDAY
                "FR" -> DayOfWeek.FRIDAY
                "SA" -> DayOfWeek.SATURDAY
                "SU" -> DayOfWeek.SUNDAY
                else -> throw IllegalArgumentException("invalid BYDAY '$code'")
            }
        }?.toSet().orEmpty()
        return RecurrenceRule(freq, interval, count, until, byDay)
    }

    fun expandOccurrences(
        dtStart: LocalDate,
        rule: RecurrenceRule,
        rangeEndInclusive: LocalDate,
        maxOccurrences: Int = 366,
    ): List<LocalDate> {
        val cap = minOf(maxOccurrences, rule.count ?: maxOccurrences)
        val hardEnd = if (rule.untilDate != null && rule.untilDate.isBefore(rangeEndInclusive)) {
            rule.untilDate
        } else {
            rangeEndInclusive
        }
        val out = ArrayList<LocalDate>()
        var cursor = dtStart
        var step = 0
        while (!cursor.isAfter(hardEnd) && out.size < cap && step < 4_096) {
            if (rule.freq == Freq.WEEKLY && rule.byDay.isNotEmpty()) {
                val weekMonday = cursor.minusDays((cursor.dayOfWeek.value - 1).toLong())
                for (offset in 0L..6L) {
                    val day = weekMonday.plusDays(offset)
                    if (!day.isBefore(dtStart) && !day.isAfter(hardEnd) && day.dayOfWeek in rule.byDay) {
                        out.add(day)
                        if (out.size >= cap) break
                    }
                }
                cursor = weekMonday.plusWeeks(rule.interval.toLong())
            } else {
                if (rule.byDay.isEmpty() || cursor.dayOfWeek in rule.byDay) {
                    out.add(cursor)
                }
                cursor = when (rule.freq) {
                    Freq.DAILY -> cursor.plusDays(rule.interval.toLong())
                    Freq.WEEKLY -> cursor.plusWeeks(rule.interval.toLong())
                    Freq.MONTHLY -> cursor.plusMonths(rule.interval.toLong())
                    Freq.YEARLY -> cursor.plusYears(rule.interval.toLong())
                }
            }
            step++
        }
        return out
    }

    fun scheduleInexactReminder(taskId: String, targetEpochMs: Long, flexWindowMs: Long = 15 * 60_000L): InexactReminderWindow {
        require(flexWindowMs in 60_000L..3_600_000L) { "flexWindowMs outside 1m..60m" }
        return InexactReminderWindow(
            taskId = taskId,
            earliestTriggerEpochMs = targetEpochMs,
            latestFlexEpochMs = targetEpochMs + flexWindowMs,
            usesExactAlarmPermission = false,
        )
    }

    data class VCardContact(val fullName: String, val emails: List<String>, val phones: List<String>)

    fun parseVCard(vcardText: String): VCardContact {
        val lines = vcardText.replace("\r\n", "\n").replace("\n ", "").lines()
        require(lines.any { it.equals("BEGIN:VCARD", ignoreCase = true) }) { "missing BEGIN:VCARD" }
        var fn = ""
        val emails = ArrayList<String>()
        val phones = ArrayList<String>()
        for (l in lines) {
            val idx = l.indexOf(':')
            if (idx <= 0) continue
            val key = l.substring(0, idx).substringBefore(';').uppercase()
            val value = l.substring(idx + 1).trim()
            when (key) {
                "FN" -> fn = value
                "EMAIL" -> if (value.isNotEmpty()) emails.add(value)
                "TEL" -> if (value.isNotEmpty()) phones.add(value.removePrefix("tel:"))
            }
        }
        return VCardContact(fn.ifEmpty { "Unnamed" }, emails, phones)
    }
}
