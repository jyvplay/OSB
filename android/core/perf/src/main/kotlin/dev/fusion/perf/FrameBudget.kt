package dev.fusion.perf

/**
 * Frame-budget arithmetic: budget ns = 1e9/refreshHz (16.67ms at
 * 60Hz, 8.33ms at 120Hz); a frame DROPS when its duration exceeds
 * the budget, and the missed-vsync count is ceil(duration/budget)-1
 * (a 40ms frame at 60Hz misses two vsyncs, not one — executed).
 *
 * Declared narrowings: fixed refresh rates 30..240; VRR windows are
 * a range policy, refused by absence; duration inputs are the
 * caller's measured ns (no clocks here).
 */
object FrameBudget {
    const val SCOPE = "Fixed-rate budgets + missed-vsync law; VRR refused by absence; no clocks."

    fun budgetNs(refreshHz: Int): Long {
        require(refreshHz in 30..240) { "refresh " + refreshHz }
        return 1_000_000_000L / refreshHz
    }

    fun missedVsyncs(durationNs: Long, refreshHz: Int): Long {
        require(durationNs > 0) { "non-positive frame duration" }
        val b = budgetNs(refreshHz)
        return (durationNs + b - 1) / b - 1
    }

    fun droppedFrames(durationsNs: LongArray, refreshHz: Int): Long {
        var total = 0L
        for (d in durationsNs) total += missedVsyncs(d, refreshHz)
        return total
    }
}
