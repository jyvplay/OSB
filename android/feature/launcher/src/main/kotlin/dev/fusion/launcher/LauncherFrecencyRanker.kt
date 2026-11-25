// SPDX-License-Identifier: Apache-2.0
package dev.fusion.launcher

import kotlin.math.exp

/**
 * Production-grade exponential-decay frecency app ranker with pinned-app priority.
 */
class LauncherFrecencyRanker private constructor() {
    companion object {
        data class AppUsage(
            val packageName: String,
            val launchCount: Int,
            val lastLaunchHoursAgo: Double,
            val pinned: Boolean = false
        )

        fun score(usage: AppUsage, halfLifeHours: Double = 24.0): Double {
            require(usage.launchCount >= 0 && usage.lastLaunchHoursAgo >= 0.0 && halfLifeHours > 0.0) {
                "invalid usage params"
            }
            val lambda = kotlin.math.ln(2.0) / halfLifeHours
            return usage.launchCount * exp(-lambda * usage.lastLaunchHoursAgo)
        }

        fun rank(apps: List<AppUsage>, halfLifeHours: Double = 24.0): List<String> =
            apps.sortedWith(
                compareByDescending<AppUsage> { it.pinned }
                    .thenByDescending { score(it, halfLifeHours) }
                    .thenBy { it.packageName }
            ).map { it.packageName }
    }
}
