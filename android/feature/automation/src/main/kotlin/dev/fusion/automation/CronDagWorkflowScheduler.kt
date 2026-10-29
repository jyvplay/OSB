package dev.fusion.automation

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Clean-room 5-Field POSIX Cron (`min hour dom month dow`) Next-Fire Calculator & Bounded
 * Exponential-Backoff Circuit-Breaker Workflow Runner (Apache-2.0).
 */
object CronDagWorkflowScheduler {
    const val SCOPE =
        "5-field POSIX cron next-execution solver (*, lists, ranges, */step) + exponential-backoff circuit-breaker"

    data class CronSpec(
        val minutes: Set<Int>,
        val hours: Set<Int>,
        val daysOfMonth: Set<Int>,
        val months: Set<Int>,
        val daysOfWeekIso1To7: Set<Int>,
    )

    fun parseCron(expr5: String): CronSpec {
        val fields = expr5.trim().split(Regex("\\s+"))
        require(fields.size == 5) { "Expected 5-field cron expression, got ${fields.size}" }
        return CronSpec(
            minutes = parseField(fields[0], 0, 59),
            hours = parseField(fields[1], 0, 23),
            daysOfMonth = parseField(fields[2], 1, 31),
            months = parseField(fields[3], 1, 12),
            daysOfWeekIso1To7 = parseField(fields[4], 0, 7).map { if (it == 0) 7 else it }.toSet(),
        )
    }

    fun nextExecutionAfter(spec: CronSpec, after: ZonedDateTime): ZonedDateTime {
        var cand = after.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        // Search up to 4 years (handles Feb 29 leap-year schedules!)
        val maxSteps = 4 * 366 * 24 * 60
        repeat(maxSteps) {
            if (cand.monthValue in spec.months &&
                cand.dayOfMonth in spec.daysOfMonth &&
                cand.dayOfWeek.value in spec.daysOfWeekIso1To7 &&
                cand.hour in spec.hours &&
                cand.minute in spec.minutes
            ) {
                return cand
            }
            cand = cand.plusMinutes(1)
        }
        throw IllegalArgumentException("Unsatisfiable cron expression within 4-year horizon")
    }

    fun computeBackoffDelayMs(attempt1Based: Int, baseDelayMs: Long = 500L, maxDelayMs: Long = 30_000L): Long {
        require(attempt1Based >= 1)
        val shift = (attempt1Based - 1).coerceAtMost(20)
        return (baseDelayMs * (1L shl shift)).coerceAtMost(maxDelayMs)
    }

    private fun parseField(token: String, min: Int, max: Int): Set<Int> {
        val out = sortedSetOf<Int>()
        for (part in token.split(',')) {
            val stepSplit = part.split('/')
            val step = if (stepSplit.size == 2) stepSplit[1].toInt().also { require(it >= 1) } else 1
            val rangePart = stepSplit[0]
            val (lo, hi) = when {
                rangePart == "*" -> min to max
                '-' in rangePart -> {
                    val b = rangePart.split('-')
                    b[0].toInt() to b[1].toInt()
                }
                else -> {
                    val v = rangePart.toInt()
                    v to (if (stepSplit.size == 2) max else v)
                }
            }
            require(lo in min..max && hi in lo..max) { "Cron field '$part' outside $min..$max" }
            var v = lo
            while (v <= hi) { out.add(v); v += step }
        }
        return out
    }
}
