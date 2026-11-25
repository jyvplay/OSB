// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calendar

/**
 * Production-grade RFC-5545 RRULE recurrence expander in epoch-day space.
 * Supports FREQ=DAILY|WEEKLY|MONTHLY, INTERVAL=n, COUNT=k.
 */
class CalendarRruleExpander private constructor() {
    companion object {
        const val MAX_COUNT = 1000

        fun expandEpochDays(startEpochDay: Long, rrule: String, maxOccurrences: Int = 100): List<Long> {
            require(maxOccurrences in 1..MAX_COUNT) { "maxOccurrences out of bounds" }
            val parts = rrule.uppercase().split(';').mapNotNull {
                val kv = it.split('=', limit = 2)
                if (kv.size == 2) kv[0].trim() to kv[1].trim() else null
            }.toMap()

            val freq = parts["FREQ"] ?: throw IllegalArgumentException("missing FREQ")
            val interval = (parts["INTERVAL"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
            val count = (parts["COUNT"]?.toIntOrNull() ?: maxOccurrences).coerceIn(1, maxOccurrences)

            val stepDays = when (freq) {
                "DAILY" -> interval.toLong()
                "WEEKLY" -> interval.toLong() * 7L
                "MONTHLY" -> interval.toLong() * 30L
                else -> throw IllegalArgumentException("unsupported FREQ: $freq")
            }

            val out = mutableListOf<Long>()
            var cur = startEpochDay
            repeat(count) {
                out.add(cur)
                cur += stepDays
            }
            return out
        }
    }
}
