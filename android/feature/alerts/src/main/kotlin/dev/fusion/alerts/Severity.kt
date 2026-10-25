package dev.fusion.alerts

/**
 * Alert severity algebra (stated contract): the total order INFO <
 * WARN < ERROR < CRITICAL; [atLeast] filtering; [escalate] which only
 * moves UP (de-escalation is a human decision, refused here so audit
 * trails never show silent downgrades); and [highest] aggregation for
 * grouped alerts.
 *
 * Declared narrowings: four levels only (a TRACE/DEBUG ladder is a
 * logging concern, not an alerting one — named, not merged); parsing
 * accepts the canonical uppercase names only.
 */
object Severity {
    const val SCOPE = "Four-level total order; escalation only moves up; canonical names only."

    enum class Level { INFO, WARN, ERROR, CRITICAL }

    fun parse(name: String): Level = when (name) {
        "INFO" -> Level.INFO; "WARN" -> Level.WARN
        "ERROR" -> Level.ERROR; "CRITICAL" -> Level.CRITICAL
        else -> throw IllegalArgumentException("'" + name + "' is not a canonical severity name")
    }

    fun atLeast(level: Level, floor: Level): Boolean = level >= floor

    fun escalate(from: Level, to: Level): Level {
        require(to >= from) { "de-escalation " + from + "->" + to + " refused (human decision, not kernel math)" }
        return to
    }

    fun highest(levels: List<Level>): Level {
        require(levels.isNotEmpty()) { "no alerts to aggregate" }
        return levels.max()
    }

    fun filter(alerts: List<Pair<String, Level>>, floor: Level): List<Pair<String, Level>> =
        alerts.filter { it.second >= floor }
}
