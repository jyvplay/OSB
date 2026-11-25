// SPDX-License-Identifier: Apache-2.0
package dev.fusion.network

import kotlin.math.min

/**
 * Production-grade per-host token-bucket rate limiter & deterministic
 * exponential-backoff retry scheduler with Retry-After support.
 */
class NetworkRetryTokenBucket private constructor(
    private val capacity: Double,
    private val refillPerSec: Double,
    private val baseBackoffMs: Long,
    private val maxBackoffMs: Long
) {
    companion object {
        fun create(
            capacity: Double = 10.0,
            refillPerSec: Double = 2.0,
            baseBackoffMs: Long = 250L,
            maxBackoffMs: Long = 30_000L
        ): NetworkRetryTokenBucket {
            require(capacity > 0.0 && refillPerSec > 0.0) { "invalid bucket params" }
            require(baseBackoffMs in 1L..maxBackoffMs) { "invalid backoff bounds" }
            return NetworkRetryTokenBucket(capacity, refillPerSec, baseBackoffMs, maxBackoffMs)
        }
    }

    private data class Bucket(var tokens: Double, var lastMs: Long)
    private val buckets = HashMap<String, Bucket>()

    fun tryAcquire(host: String, nowMs: Long, cost: Double = 1.0): Boolean {
        require(host.isNotEmpty() && nowMs >= 0L && cost > 0.0) { "invalid args" }
        val b = buckets.getOrPut(host) { Bucket(capacity, nowMs) }
        if (nowMs > b.lastMs) {
            val elapsedSec = (nowMs - b.lastMs) / 1000.0
            b.tokens = min(capacity, b.tokens + elapsedSec * refillPerSec)
            b.lastMs = nowMs
        }
        return if (b.tokens >= cost) {
            b.tokens -= cost
            true
        } else {
            false
        }
    }

    fun isRetryableStatus(statusCode: Int): Boolean = statusCode == 429 || statusCode in 500..599

    fun nextDelayMs(attempt: Int, retryAfterHeader: String? = null): Long {
        require(attempt >= 0) { "negative attempt" }
        val parsedRetryAfter = retryAfterHeader?.trim()?.toLongOrNull()?.let { (it * 1000L).coerceIn(0L, maxBackoffMs) }
        val exp = (baseBackoffMs shl attempt.coerceAtMost(20)).coerceAtMost(maxBackoffMs)
        return if (parsedRetryAfter != null) maxOf(exp, parsedRetryAfter) else exp
    }
}
