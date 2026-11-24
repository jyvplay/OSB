// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

/**
 * Bounded token-bucket message rate limiter.
 */
class MessageRateLimiter private constructor(
    private val capacity: Double,
    private val refillPerSec: Double
) {
    companion object {
        fun create(capacity: Double, refillPerSec: Double): MessageRateLimiter {
            require(capacity > 0.0 && capacity.isFinite()) { "invalid capacity" }
            require(refillPerSec >= 0.0 && refillPerSec.isFinite()) { "invalid refillPerSec" }
            return MessageRateLimiter(capacity, refillPerSec)
        }
    }

    private var tokens: Double = capacity
    private var lastRefillMs: Long = 0L

    fun tryConsume(nowMs: Long, cost: Double = 1.0): Boolean {
        require(nowMs >= lastRefillMs) { "non-monotonic nowMs" }
        require(cost > 0.0 && cost.isFinite()) { "invalid cost" }
        val elapsedSec = (nowMs - lastRefillMs) / 1000.0
        tokens = (tokens + elapsedSec * refillPerSec).coerceAtMost(capacity)
        lastRefillMs = nowMs
        if (tokens >= cost) {
            tokens -= cost
            return true
        }
        return false
    }

    fun availableTokens(): Double = tokens
}
