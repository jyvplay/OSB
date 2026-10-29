// SPDX-License-Identifier: Apache-2.0
package dev.fusion.launcher

import kotlin.math.pow

/**
 * Exponential Half-Life **Frecency App Ranker** (`score = sum(weight * 2^(-ageHours / halfLifeHours))`)
 * with Ephemeral-Mode Zero-History Enforcement.
 */
object FrecencyAppRanker {
    const val SCOPE =
        "Exponential half-life frecency app ranker with time-of-day affinity boost and Tails-light ephemeral zero-history guard."

    data class LaunchEvent(
        val appId: String,
        val timestampEpochMs: Long,
        val hourOfDay: Int,
    ) {
        init {
            require(appId.isNotBlank() && hourOfDay in 0..23) { "invalid LaunchEvent($appId, hour=$hourOfDay)" }
        }
    }

    data class RankedApp(val appId: String, val score: Double)

    fun rank(
        events: List<LaunchEvent>,
        nowEpochMs: Long,
        currentHourOfDay: Int,
        halfLifeHours: Double = 72.0,
        ephemeralPrivacyMode: Boolean = false,
    ): List<RankedApp> {
        require(halfLifeHours > 0.0 && currentHourOfDay in 0..23) { "invalid frecency parameters" }
        if (ephemeralPrivacyMode || events.isEmpty()) return emptyList()
        val totals = LinkedHashMap<String, Double>()
        for (ev in events) {
            val ageHours = ((nowEpochMs - ev.timestampEpochMs).coerceAtLeast(0L)) / 3_600_000.0
            val decay = 2.0.pow(-ageHours / halfLifeHours)
            val circDist = minOf(
                kotlin.math.abs(currentHourOfDay - ev.hourOfDay),
                24 - kotlin.math.abs(currentHourOfDay - ev.hourOfDay),
            )
            val todBoost = if (circDist <= 1) 1.25 else 1.0
            totals[ev.appId] = (totals[ev.appId] ?: 0.0) + decay * todBoost
        }
        return totals.entries
            .map { RankedApp(it.key, it.value) }
            .sortedWith(compareByDescending<RankedApp> { it.score }.thenBy { it.appId })
    }
}
