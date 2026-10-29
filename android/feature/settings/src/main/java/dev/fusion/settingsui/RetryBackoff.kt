// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settingsui

import kotlin.math.min
import kotlin.random.Random

/**
 * Clean-room AWS-architecture-style jittered exponential backoff + 3-state circuit breaker (`RetryBackoff`).
 *
 * Prevents thundering-herd retry storms and battery drain across sync/cast/network settings:
 * - Strategies: `FULL_JITTER` (`rand(0, cap)`), `EQUAL_JITTER` (`cap/2 + rand(0, cap/2)`), and
 *   `DECORRELATED_JITTER` (`min(maxDelayMs, rand(baseDelayMs, prevDelayMs * 3))`).
 * - 3-state `CircuitBreaker` (`CLOSED -> OPEN -> HALF_OPEN -> CLOSED`) tripping after `failureThreshold`
 *   consecutive failures and probing after `openCooldownMs`.
 */
object RetryBackoff {
    const val SCOPE =
        "Exponential backoff calculator (FULL_JITTER, EQUAL_JITTER, DECORRELATED_JITTER, overflow-safe cap) + 3-state CLOSED/OPEN/HALF_OPEN circuit breaker; adaptiveAIMD concurrency limits out of scope."

    enum class Strategy { FULL_JITTER, EQUAL_JITTER, DECORRELATED_JITTER }
    enum class BreakerState { CLOSED, OPEN, HALF_OPEN }

    fun nextDelayMs(
        strategy: Strategy,
        attempt: Int,
        baseDelayMs: Long = 200L,
        maxDelayMs: Long = 30_000L,
        previousDelayMs: Long = baseDelayMs,
        rng: Random = Random.Default,
    ): Long {
        require(attempt >= 0) { "attempt must be >= 0" }
        require(baseDelayMs > 0L && maxDelayMs >= baseDelayMs) { "Invalid backoff bounds" }
        val expShift = attempt.coerceAtMost(30)
        val rawExp = (baseDelayMs.toDouble() * (1L shl expShift).toDouble()).toLong().coerceAtMost(maxDelayMs)
        val cap = min(maxDelayMs, rawExp.coerceAtLeast(baseDelayMs))

        return when (strategy) {
            Strategy.FULL_JITTER -> if (cap == 0L) 0L else rng.nextLong(0L, cap + 1L)
            Strategy.EQUAL_JITTER -> {
                val half = cap / 2L
                half + rng.nextLong(0L, (cap - half) + 1L)
            }
            Strategy.DECORRELATED_JITTER -> {
                val upper = min(maxDelayMs, (previousDelayMs.coerceAtLeast(baseDelayMs) * 3L).coerceAtLeast(baseDelayMs))
                if (upper <= baseDelayMs) baseDelayMs else rng.nextLong(baseDelayMs, upper + 1L)
            }
        }
    }

    class CircuitBreaker(
        val failureThreshold: Int = 5,
        val openCooldownMs: Long = 15_000L,
    ) {
        init {
            require(failureThreshold >= 1 && openCooldownMs >= 1L) { "Invalid CircuitBreaker parameters" }
        }

        var state: BreakerState = BreakerState.CLOSED
            private set
        var consecutiveFailures: Int = 0
            private set
        private var openedAtMs: Long = 0L

        fun allowRequest(nowMs: Long): Boolean = when (state) {
            BreakerState.CLOSED -> true
            BreakerState.OPEN -> {
                if (nowMs - openedAtMs >= openCooldownMs) {
                    state = BreakerState.HALF_OPEN
                    true
                } else {
                    false
                }
            }
            BreakerState.HALF_OPEN -> true
        }

        fun onSuccess() {
            consecutiveFailures = 0
            state = BreakerState.CLOSED
        }

        fun onFailure(nowMs: Long) {
            consecutiveFailures++
            if (state == BreakerState.HALF_OPEN || consecutiveFailures >= failureThreshold) {
                state = BreakerState.OPEN
                openedAtMs = nowMs
            }
        }
    }
}
