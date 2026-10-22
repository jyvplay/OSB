// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

/** Lockout state: consecutive failures plus the lock expiry instant. */
data class LockState(val failures: Int, val lockedUntilMillis: Long)

/**
 * Vault brute-force backoff over caller-supplied instants (no clock reads):
 * 3 free failures, then 30s doubling per further failure (32min at the
 * 10-failure saturation; the 1h cap is a safety margin that never binds). Any success resets to zero. Failures persist
 * until success (no decay window — conservative by design). Attempt
 * counting only; credential checking is outside this kernel.
 */
object LockoutPolicy {
    const val FREE_FAILURES = 3
    const val MAX_FAILURES = 10
    const val BASE_MS = 30_000L
    const val MAX_MS = 3_600_000L

    fun record(state: LockState, nowMillis: Long, success: Boolean): LockState {
        require(nowMillis >= 0) { "negative now" }
        require(state.failures in 0..MAX_FAILURES && state.lockedUntilMillis >= 0) { "bad state" }
        if (success) return LockState(0, 0)
        val failures = (state.failures + 1).coerceAtMost(MAX_FAILURES)
        if (failures <= FREE_FAILURES) return LockState(failures, 0)
        var delay = BASE_MS
        repeat(failures - FREE_FAILURES - 1) { delay = (delay * 2).coerceAtMost(MAX_MS) }
        return LockState(failures, Math.addExact(nowMillis, delay))
    }

    /** True while [nowMillis] precedes the lock expiry. */
    fun locked(state: LockState, nowMillis: Long): Boolean {
        require(nowMillis >= 0) { "negative now" }
        require(state.failures in 0..MAX_FAILURES && state.lockedUntilMillis >= 0) { "bad state" }
        return nowMillis < state.lockedUntilMillis
    }
}
