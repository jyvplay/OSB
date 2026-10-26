// SPDX-License-Identifier: Apache-2.0
package dev.fusion.backup

/**
 * Grandfather-father-son snapshot retention over caller-supplied bucket keys.
 * A snapshot carries its day, week, and month bucket indices; the kernel keeps
 * the newest N distinct days, the latest snapshot of the newest M distinct
 * weeks, and the latest of the newest K distinct months. Calendar bucketing
 * (ISO weeks, month starts, time zones) stays with the caller, so this kernel
 * never mis-keeps across a DST change or a locale's first-day-of-week.
 */
data class Snapshot(val id: String, val day: Int, val week: Int, val month: Int)

class RetentionPlan private constructor(val daily: Int, val weekly: Int, val monthly: Int) {
    companion object {
        const val MAX_SNAPSHOTS = 4096
        const val MAX_KEEP = 366
        private val ID = Regex("[A-Za-z0-9._-]{1,64}")

        fun of(daily: Int, weekly: Int, monthly: Int): RetentionPlan {
            require(daily in 0..MAX_KEEP && weekly in 0..MAX_KEEP && monthly in 0..MAX_KEEP)
            return RetentionPlan(daily, weekly, monthly)
        }
    }

    fun select(snapshots: List<Snapshot>): Set<String> {
        require(snapshots.size <= MAX_SNAPSHOTS)
        val ids = HashSet<String>()
        for (s in snapshots) {
            require(ID.matches(s.id) && s.day >= 0 && s.week >= 0 && s.month >= 0)
            require(ids.add(s.id)) { "duplicate snapshot id" }
        }
        val kept = HashSet<String>()
        fun newestPerBucket(key: (Snapshot) -> Int, buckets: Int) {
            if (buckets == 0) return
            val keys = snapshots.map(key).distinct().sortedDescending().take(buckets).toSet()
            for (k in keys) {
                val best = snapshots.filter { key(it) == k }
                    .maxWithOrNull(compareBy({ it.day }, { it.id })) ?: continue
                kept.add(best.id)
            }
        }
        // Daily keeps whole days (every snapshot of the newest N days).
        if (daily > 0) {
            val days = snapshots.map { it.day }.distinct().sortedDescending().take(daily).toSet()
            snapshots.filter { it.day in days }.forEach { kept.add(it.id) } 
        }
        newestPerBucket({ it.week }, weekly)
        newestPerBucket({ it.month }, monthly)
        return kept
    }
}
