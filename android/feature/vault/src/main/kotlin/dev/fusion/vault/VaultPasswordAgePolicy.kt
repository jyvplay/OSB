// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

/**
 * Bounded vault password age policy evaluator. Evaluates password age in days
 * against [maxAgeDays] and [warnBeforeDays] thresholds.
 */
class VaultPasswordAgePolicy private constructor() {
    companion object {
        const val MAX_AGE_DAYS = 3650

        enum class Status { OK, WARN, EXPIRED }

        data class Evaluation(val status: Status, val daysRemaining: Int)

        fun evaluate(ageDays: Int, maxAgeDays: Int, warnBeforeDays: Int): Evaluation {
            require(ageDays >= 0) { "negative ageDays" }
            require(maxAgeDays in 1..MAX_AGE_DAYS) { "maxAgeDays out of bounds" }
            require(warnBeforeDays in 0..maxAgeDays) { "warnBeforeDays out of bounds" }
            val remaining = maxAgeDays - ageDays
            val status = when {
                remaining <= 0 -> Status.EXPIRED
                remaining <= warnBeforeDays -> Status.WARN
                else -> Status.OK
            }
            return Evaluation(status, if (remaining < 0) 0 else remaining)
        }
    }
}
