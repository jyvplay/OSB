// SPDX-License-Identifier: Apache-2.0
package dev.fusion.backup

/**
 * Bounded backup retention window planner. Given sorted-newest-first
 * snapshot timestamps (days), keeps up to [maxCount] within [maxAgeDays].
 */
class BackupRetentionWindow private constructor() {
    companion object {
        const val MAX_SNAPSHOTS = 4096

        fun selectRetained(agesDays: List<Int>, maxCount: Int, maxAgeDays: Int): List<Int> {
            require(agesDays.size <= MAX_SNAPSHOTS) { "too many snapshots" }
            require(maxCount >= 0 && maxAgeDays >= 0) { "negative retention bound" }
            val kept = mutableListOf<Int>()
            for (age in agesDays) {
                require(age >= 0) { "negative age" }
                if (age <= maxAgeDays && kept.size < maxCount) {
                    kept.add(age)
                }
            }
            return kept
        }
    }
}
