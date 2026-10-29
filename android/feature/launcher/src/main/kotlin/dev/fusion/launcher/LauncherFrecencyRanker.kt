// SPDX-License-Identifier: Apache-2.0
package dev.fusion.launcher

import kotlin.math.exp

/**
 * Pure-JVM **Frecency (Frequency + Exponential Recency Decay) App & Command Launcher Ranker**
 * for `:feature:launcher` (zero telemetry; strictly local session counters).
 */
object LauncherFrecencyRanker {
    const val SCOPE =
        "Offline exponential-decay frecency ranker + prefix/acronym fuzzy matcher for Fusion Launcher."

    const val HALF_LIFE_HOURS = 72.0

    data class LaunchHistoryRecord(
        val appId: String,
        val displayName: String,
        val launchTimestampsMs: List<Long>,
    )

    data class RankedLaunchTarget(
        val appId: String,
        val displayName: String,
        val frecencyScore: Double,
    )

    fun rankTargets(records: List<LaunchHistoryRecord>, query: String, nowMs: Long): List<RankedLaunchTarget> {
        val q = query.trim().lowercase()
        val decayLambda = kotlin.math.ln(2.0) / (HALF_LIFE_HOURS * 3_600_000.0)
        return records.mapNotNull { rec ->
            val matchBonus = matchMultiplier(rec.displayName, q)
            if (matchBonus <= 0.0) return@mapNotNull null
            val rawFrecency = rec.launchTimestampsMs.sumOf { ts ->
                val ageMs = (nowMs - ts).coerceAtLeast(0L).toDouble()
                exp(-decayLambda * ageMs)
            }
            RankedLaunchTarget(rec.appId, rec.displayName, (1.0 + rawFrecency) * matchBonus)
        }.sortedWith(compareByDescending<RankedLaunchTarget> { it.frecencyScore }.thenBy { it.displayName })
    }

    private fun matchMultiplier(name: String, q: String): Double {
        if (q.isEmpty()) return 1.0
        val lower = name.lowercase()
        if (lower == q) return 4.0
        if (lower.startsWith(q)) return 2.5
        val acronym = lower.split(Regex("\\s+")).mapNotNull { it.firstOrNull() }.joinToString("")
        if (acronym.startsWith(q)) return 2.0
        if (q in lower) return 1.2
        return 0.0
    }
}
