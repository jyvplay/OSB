package dev.fusion.automation

/**
 * Fixed-interval schedule arithmetic (anchor + period model — the
 * simple recurrence CronSpec does NOT cover: "every N minutes from
 * anchor T", where next-run math is pure modulo and therefore
 * exactly testable).
 *
 * Law executed as a property: nextAfter(t) is the SMALLEST anchor +
 * k·period strictly greater than t, for k >= 0; times before the
 * anchor return the anchor itself.
 *
 * Declared narrowings: epoch-second arithmetic only — no zones, no
 * DST (civil schedules are CronSpec's domain); periods below 60s
 * refuse (sub-minute automation is a power drain the product
 * refuses, and the kernel enforces the floor loudly).
 */
object NextRun {
    const val SCOPE = "anchor+k*period modulo math; >=60s periods; zones/DST belong to CronSpec."
    const val MIN_PERIOD = 60L

    fun nextAfter(anchorEpochS: Long, periodS: Long, nowEpochS: Long): Long {
        require(periodS >= MIN_PERIOD) { "period ${periodS}s below the ${MIN_PERIOD}s floor" }
        require(anchorEpochS >= 0 && nowEpochS >= 0) { "negative epoch" }
        if (nowEpochS < anchorEpochS) return anchorEpochS
        val elapsed = nowEpochS - anchorEpochS
        val k = elapsed / periodS + 1
        return anchorEpochS + k * periodS
    }

    fun runsBetween(anchorEpochS: Long, periodS: Long, fromExclusive: Long, toInclusive: Long): Long {
        require(toInclusive >= fromExclusive) { "inverted window" }
        var count = 0L
        var t = nextAfter(anchorEpochS, periodS, fromExclusive)
        while (t <= toInclusive) {
            count++
            t += periodS
        }
        return count
    }

    fun isRunTime(anchorEpochS: Long, periodS: Long, t: Long): Boolean {
        require(periodS >= MIN_PERIOD) { "period floor" }
        return t >= anchorEpochS && (t - anchorEpochS) % periodS == 0L
    }
}
