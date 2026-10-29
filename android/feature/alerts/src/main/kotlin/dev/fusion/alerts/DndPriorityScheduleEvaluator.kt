package dev.fusion.alerts

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Clean-room Overnight-Wrap Do-Not-Disturb (`DND`) Weekly Schedule & Repeat-Caller (`<= 15 min`)
 * Breakthrough Policy Evaluator (Apache-2.0).
 */
object DndPriorityScheduleEvaluator {
    const val SCOPE =
        "Overnight-wrap DND weekly schedule + starred-contact & <=15-min repeat-caller breakthrough evaluator"

    data class QuietRule(
        val activeStartDays: Set<DayOfWeek>,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val allowStarredContacts: Boolean = true,
        val allowRepeatCallersWithinMinutes: Int = 15,
    )

    enum class AlertDecision { DELIVER_AUDIBLE, DELIVER_REPEAT_CALLER_BREAKTHROUGH, DELIVER_STARRED_BREAKTHROUGH, SILENCE_DND }

    fun evaluate(
        now: ZonedDateTime,
        rule: QuietRule,
        isAlarmCategory: Boolean,
        isStarredSender: Boolean,
        previousCallEpochSecFromSameSender: Long? = null,
    ): AlertDecision {
        if (isAlarmCategory) return AlertDecision.DELIVER_AUDIBLE
        val inQuietWindow = isWithinQuietHours(now, rule)
        if (!inQuietWindow) return AlertDecision.DELIVER_AUDIBLE
        if (rule.allowStarredContacts && isStarredSender) return AlertDecision.DELIVER_STARRED_BREAKTHROUGH
        if (previousCallEpochSecFromSameSender != null && rule.allowRepeatCallersWithinMinutes > 0) {
            val deltaSec = now.toEpochSecond() - previousCallEpochSecFromSameSender
            if (deltaSec in 1L..(rule.allowRepeatCallersWithinMinutes * 60L)) {
                return AlertDecision.DELIVER_REPEAT_CALLER_BREAKTHROUGH
            }
        }
        return AlertDecision.SILENCE_DND
    }

    private fun isWithinQuietHours(now: ZonedDateTime, rule: QuietRule): Boolean {
        val t = now.toLocalTime()
        return if (!rule.startTime.isAfter(rule.endTime)) {
            now.dayOfWeek in rule.activeStartDays && !t.isBefore(rule.startTime) && t.isBefore(rule.endTime)
        } else {
            // Overnight span, e.g. 22:00 -> 07:00 next morning!
            val startedToday = now.dayOfWeek in rule.activeStartDays && !t.isBefore(rule.startTime)
            val startedYesterday = now.minusDays(1).dayOfWeek in rule.activeStartDays && t.isBefore(rule.endTime)
            startedToday || startedYesterday
        }
    }
}
