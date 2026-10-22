// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * Sliding-window attempt gate over caller instants.
 *
 * Sits next to [LockoutPolicy] (escalating backoff levels) and [PinRules]
 * (static policy): neither counts "how many attempts in the last N
 * seconds". Given prior attempt instants and now, this admits the attempt
 * when fewer than [max] priors fall in (now − window, now]. The window is
 * half-open on the left so a prior exactly on the boundary has expired —
 * documented, and pinned. Priors are sorted internally (unsorted caller
 * logs are tolerated, not refused) and future-dated priors are ignored,
 * not counted, because clock skew must fail closed toward admission, not
 * toward locking a legitimate user out. Pure predicate: attempt storage
 * is caller duty, and the clock is a parameter, never read.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object AttemptWindow {
    const val MAX_PRIORS = 100_000

    /**
     * Returns true when an attempt at [now] is admitted: strictly fewer
     * than [max] priors in the last [windowSec] seconds.
     *
     * @throws IllegalArgumentException on negative instants, max < 1,
     * window < 1, or over-cap priors.
     */
    fun allows(prior: List<Long>, now: Long, max: Int, windowSec: Long): Boolean {
        require(now >= 0) { "bad now" }
        require(max >= 1) { "bad max" }
        require(windowSec >= 1) { "bad window" }
        require(prior.size <= MAX_PRIORS) { "prior budget exceeded" }
        require(prior.all { it >= 0 }) { "bad prior" }
        val floor = now - windowSec
        var count = 0
        for (t in prior) {
            if (t > floor && t <= now) {
                count++
                if (count >= max) return false
            }
        }
        return true
    }
}
