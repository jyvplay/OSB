package dev.fusion.automation

/**
 * ISO 8601 durations for automation schedules (`PnYnMnDTnHnMnS`, `PnW`).
 *
 * Structure rules enforced: `P` required; weeks are exclusive (a `PnW`
 * duration cannot mix with other designators); `T` must precede any
 * time components and must be followed by at least one; designator
 * order fixed (Y M D / H M S); at least one component overall.
 *
 * Semantics split honestly: [toSeconds] is only defined for EXACT
 * durations (weeks/days/hours/minutes/seconds — fixed 86400-second
 * days, declared); durations carrying years or months REFUSE
 * conversion because their length depends on a calendar anchor this
 * kernel does not have. Parsing keeps them; converting does not guess.
 *
 * Declared narrowings: non-negative integer components only (no
 * fractional seconds, no negative durations, no alternative
 * `PYYYYMMDD` format).
 */
object DurationIso {
    const val SCOPE = "Integer ISO 8601 durations; toSeconds refuses Y/M components rather than guess a calendar."

    data class Dur(
        val years: Int, val months: Int, val weeks: Int, val days: Int,
        val hours: Int, val minutes: Int, val seconds: Int
    ) {
        val calendarDependent: Boolean get() = years > 0 || months > 0
    }

    private val FULL = Regex("P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?")
    private val WEEK = Regex("P(\\d+)W")

    fun parse(text: String): Dur {
        WEEK.matchEntire(text)?.let {
            return Dur(0, 0, it.groupValues[1].toInt(), 0, 0, 0, 0)
        }
        val m = FULL.matchEntire(text) ?: throw IllegalArgumentException("'$text' is not an ISO 8601 duration")
        val g = m.groupValues
        require(g.drop(1).any { it.isNotEmpty() }) { "'$text' has no components" }
        if ("T" in text) require(g[4].isNotEmpty() || g[5].isNotEmpty() || g[6].isNotEmpty()) {
            "'T' must introduce at least one time component"
        }
        return Dur(
            years = g[1].ifEmpty { "0" }.toInt(), months = g[2].ifEmpty { "0" }.toInt(),
            weeks = 0, days = g[3].ifEmpty { "0" }.toInt(),
            hours = g[4].ifEmpty { "0" }.toInt(), minutes = g[5].ifEmpty { "0" }.toInt(),
            seconds = g[6].ifEmpty { "0" }.toInt()
        )
    }

    fun toSeconds(d: Dur): Long {
        require(!d.calendarDependent) {
            "years/months need a calendar anchor; this kernel refuses rather than assume one"
        }
        return d.weeks * 604800L + d.days * 86400L + d.hours * 3600L + d.minutes * 60L + d.seconds
    }

    fun format(d: Dur): String {
        if (d.weeks > 0) {
            require(d.years == 0 && d.months == 0 && d.days == 0 && d.hours == 0 && d.minutes == 0 && d.seconds == 0) {
                "weeks are exclusive in ISO 8601 durations"
            }
            return "P${d.weeks}W"
        }
        val sb = StringBuilder("P")
        if (d.years > 0) sb.append(d.years).append('Y')
        if (d.months > 0) sb.append(d.months).append('M')
        if (d.days > 0) sb.append(d.days).append('D')
        if (d.hours > 0 || d.minutes > 0 || d.seconds > 0) {
            sb.append('T')
            if (d.hours > 0) sb.append(d.hours).append('H')
            if (d.minutes > 0) sb.append(d.minutes).append('M')
            if (d.seconds > 0) sb.append(d.seconds).append('S')
        }
        if (sb.length == 1) sb.append("T0S")
        return sb.toString()
    }
}
