package dev.fusion.alerts

/**
 * Snooze-duration ladders for the alerts surface (stated contract): a
 * geometric ladder from [baseMinutes] doubling per snooze, capped at
 * [capMinutes]; once at the cap it stays there (no overflow, no
 * wraparound). The whole ladder is enumerable for settings UI.
 *
 * Executed properties: monotone non-decreasing; reaches the cap in
 * exactly ceil(log2(cap/base)) steps; never exceeds the cap.
 *
 * Declared narrowings: minute arithmetic only — scheduling against a
 * clock is the alert scheduler's job (QuietHours composes there too).
 */
object SnoozeSeq {
    const val SCOPE = "Doubling ladder with hard cap; pure minute arithmetic; scheduling lives elsewhere."

    fun duration(baseMinutes: Int, capMinutes: Int, snoozeIndex: Int): Int {
        require(baseMinutes >= 1) { "base $baseMinutes" }
        require(capMinutes >= baseMinutes) { "cap below base" }
        require(snoozeIndex >= 0) { "negative snooze index" }
        var v = baseMinutes.toLong()
        repeat(snoozeIndex) {
            if (v < capMinutes) v = minOf(v * 2, capMinutes.toLong())
        }
        return v.toInt()
    }

    fun ladder(baseMinutes: Int, capMinutes: Int): List<Int> {
        val out = ArrayList<Int>()
        var i = 0
        while (true) {
            val d = duration(baseMinutes, capMinutes, i)
            out.add(d)
            if (d >= capMinutes) return out
            i++
            require(i <= 64) { "unreachable: ladder failed to cap" }
        }
    }

    fun stepsToCap(baseMinutes: Int, capMinutes: Int): Int = ladder(baseMinutes, capMinutes).size - 1
}
