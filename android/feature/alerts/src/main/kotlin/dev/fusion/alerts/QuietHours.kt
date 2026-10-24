package dev.fusion.alerts

/**
 * Quiet-hours window arithmetic on minutes-of-day.
 *
 * Model: a window is [start, end) in minutes 0..1439; when end < start the
 * window crosses midnight (22:00→07:00 style). start == end is REFUSED —
 * it is ambiguous between "never" and "always" and the alerts surface must
 * pick one explicitly with an enabled flag, not encode it in the times.
 *
 * Declared narrowings: civil minutes only — DST transitions, time zones
 * and leap seconds are the caller's problem (the alerts scheduler resolves
 * wall-clock minutes before calling in); this kernel is pure interval
 * logic so that property can actually be tested.
 */
object QuietHours {
    const val SCOPE = "Half-open minute-of-day windows with midnight wrap; DST/zones resolved by the caller."
    const val DAY = 1440

    data class Window(val start: Int, val end: Int) {
        init {
            require(start in 0 until DAY && end in 0 until DAY) { "minutes outside 0..1439" }
            require(start != end) { "start==end is ambiguous — use an enabled flag, not a degenerate window" }
        }
        val wraps: Boolean get() = end < start
        val minutes: Int get() = if (wraps) DAY - start + end else end - start
    }

    fun contains(w: Window, minute: Int): Boolean {
        require(minute in 0 until DAY) { "minute $minute" }
        return if (w.wraps) minute >= w.start || minute < w.end else minute in w.start until w.end
    }

    /** Minutes until the window state next changes, looking forward from [minute]. */
    fun untilBoundary(w: Window, minute: Int): Int {
        require(minute in 0 until DAY) { "minute $minute" }
        val target = if (contains(w, minute)) w.end else w.start
        val delta = (target - minute + DAY) % DAY
        return if (delta == 0) DAY else delta
    }

    /** Overlap in minutes between two windows across one 24h cycle. */
    fun overlapMinutes(a: Window, b: Window): Int {
        var count = 0
        for (m in 0 until DAY) if (contains(a, m) && contains(b, m)) count++
        return count
    }
}
