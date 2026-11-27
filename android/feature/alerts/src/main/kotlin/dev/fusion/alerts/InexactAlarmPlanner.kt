// SPDX-License-Identifier: Apache-2.0
package dev.fusion.alerts

/**
 * R146 — battery-friendly inexact alarm planner (owner:v2.0 §7.9 PR-10).
 *
 * Android delivers inexact alarms inside a window the OS is free to shift;
 * this kernel computes that window deterministically and batches alarms that
 * can share one maintenance wake-up. It never talks to AlarmManager and never
 * grants SCHEDULE_EXACT_ALARM: an alarm marked `exactAllowed` is planned as an
 * exact singleton so the caller can see what a permission would buy, while
 * every other alarm is snapped to a shared grid.
 *
 * Contract (all quantities are epoch milliseconds, UTC, integers):
 *  - `now`, `firstDue` >= 0; `gridMs` in [60_000, 86_400_000];
 *    `intervalMs` in [60_000, 30 days]; `flexMs` in [0, intervalMs/4].
 *  - The next due time for an alarm is the smallest `firstDue + k*intervalMs`
 *    that is >= `now` (k >= 0, overflow refused).
 *  - Inexact window: `start = ceil(due / grid) * grid` (first grid boundary
 *    strictly at or after due), `end = start + flexMs`.
 *    Exact alarms keep `start = end = due`.
 *  - Batching: alarms are grouped by window `start`; exact alarms always form
 *    singleton batches keyed by their due time. A batch fires at its key.
 *  - Output preserves input order per alarm, then lists batches sorted by key
 *    with member ids sorted ascending inside each batch.
 *
 * Declared narrowings: at most [MAX_ALARMS] alarms; ids `[A-Za-z0-9_-]{1,32}`
 * and unique; at most [MAX_PLAN_HORIZON_MS] (30 days) beyond `now`.
 */
object InexactAlarmPlanner {
    const val SCOPE =
        "Grid-aligned inexact alarm windows with maintenance batching and exact-singleton classification; no AlarmManager, no permissions."

    const val MAX_ALARMS = 64
    const val MIN_INTERVAL_MS = 60_000L
    const val MAX_INTERVAL_MS = 30L * 86_400_000L
    const val MIN_GRID_MS = 60_000L
    const val MAX_GRID_MS = 86_400_000L
    const val MAX_PLAN_HORIZON_MS = 30L * 86_400_000L

    private val ID_RE = Regex("[A-Za-z0-9_-]{1,32}")

    data class Alarm(
        val id: String,
        val intervalMs: Long,
        val firstDueEpochMs: Long,
        val flexMs: Long,
        val exactAllowed: Boolean,
    )

    data class Planned(
        val id: String,
        val exact: Boolean,
        val dueEpochMs: Long,
        val windowStartEpochMs: Long,
        val windowEndEpochMs: Long,
    )

    data class Batch(val keyEpochMs: Long, val ids: List<String>)

    data class Plan(val alarms: List<Planned>, val batches: List<Batch>)

    fun plan(nowEpochMs: Long, gridMs: Long, alarms: List<Alarm>): Plan {
        require(nowEpochMs >= 0) { "now must be >= 0" }
        require(gridMs in MIN_GRID_MS..MAX_GRID_MS) { "gridMs out of range" }
        require(alarms.isNotEmpty()) { "no alarms to plan" }
        require(alarms.size <= MAX_ALARMS) { "too many alarms: $MAX_ALARMS" }
        val ids = HashSet<String>()
        for (a in alarms) {
            require(ID_RE.matches(a.id)) { "invalid alarm id: ${a.id}" }
            require(ids.add(a.id)) { "duplicate alarm id: ${a.id}" }
            require(a.intervalMs in MIN_INTERVAL_MS..MAX_INTERVAL_MS) {
                "intervalMs out of range for ${a.id}"
            }
            require(a.flexMs in 0..(a.intervalMs / 4)) { "flexMs out of range for ${a.id}" }
            require(a.firstDueEpochMs >= 0) { "firstDue must be >= 0 for ${a.id}" }
        }

        val planned = ArrayList<Planned>(alarms.size)
        for (a in alarms) {
            val due = nextDue(nowEpochMs, a)
            require(due - nowEpochMs <= MAX_PLAN_HORIZON_MS) {
                "next due for ${a.id} exceeds the 30-day planning horizon"
            }
            if (a.exactAllowed) {
                planned.add(Planned(a.id, true, due, due, due))
            } else {
                val start = ceilToGrid(due, gridMs)
                // start >= due >= 0, and start <= due + grid, so no overflow
                // beyond the horizon check above plus one grid (<= 1 day).
                require(start - nowEpochMs <= MAX_PLAN_HORIZON_MS + MAX_GRID_MS) {
                    "window start for ${a.id} exceeds the planning horizon"
                }
                planned.add(Planned(a.id, false, due, start, start + a.flexMs))
            }
        }

        val grouped = LinkedHashMap<Long, MutableList<String>>()
        for (p in planned) {
            val key = if (p.exact) p.dueEpochMs else p.windowStartEpochMs
            grouped.getOrPut(key) { mutableListOf() }.add(p.id)
        }
        val batches = grouped.entries
            .sortedWith(compareBy({ it.key }))
            .map { Batch(it.key, it.value.sorted()) }
        return Plan(planned.toList(), batches)
    }

    private fun nextDue(now: Long, a: Alarm): Long {
        if (a.firstDueEpochMs >= now) return a.firstDueEpochMs
        val elapsed = now - a.firstDueEpochMs
        val k = elapsed / a.intervalMs + 1
        val due = try {
            Math.addExact(a.firstDueEpochMs, Math.multiplyExact(k, a.intervalMs))
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException("next due for ${a.id} overflows epoch arithmetic", e)
        }
        require(due >= now) { "next due for ${a.id} failed to advance" }
        return due
    }

    private fun ceilToGrid(value: Long, gridMs: Long): Long {
        val rem = value % gridMs
        if (rem == 0L) return value
        return try {
            Math.addExact(value, gridMs - rem)
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException("grid alignment overflows epoch arithmetic", e)
        }
    }
}
