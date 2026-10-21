// SPDX-License-Identifier: Apache-2.0
package dev.fusion.alerts

/**
 * Per-key token-bucket throttle for alert delivery. Each key (e.g. an alert
 * rule id) gets [capacity] burst tokens refilling at [refillPerSec]. Time is an
 * explicit caller-supplied millisecond clock; backwards timestamps are clamped
 * to the key's last-seen time (a device clock step never grants extra tokens).
 * New keys past MAX_KEYS are refused rather than evicted so no rule silently
 * loses its throttle state. Note tokens() is an observing refill: reading it
 * advances the key's clock exactly as an acquisition would.
 */
class AlertThrottle private constructor(val capacity: Double, val refillPerSec: Double) {
    private data class Bucket(var tokens: Double, var lastMs: Long)
    private val buckets = LinkedHashMap<String, Bucket>()

    companion object {
        const val MAX_KEYS = 512
        private val KEY = Regex("[A-Za-z0-9._-]{1,64}")

        fun validKey(s: String) = KEY.matches(s)
        fun of(capacity: Double, refillPerSec: Double): AlertThrottle {
            require(capacity.isFinite() && capacity >= 1.0 && capacity <= 1000.0)
            require(refillPerSec.isFinite() && refillPerSec > 0.0 && refillPerSec <= 1000.0)
            return AlertThrottle(capacity, refillPerSec)
        }
    }

    fun keys(): Set<String> = buckets.keys.toSet()

    fun tokens(key: String, nowMs: Long): Double? {
        val b = buckets[key] ?: return null
        return refill(b, nowMs)
    }

    fun tryAcquire(key: String, nowMs: Long, cost: Double = 1.0): Boolean {
        require(validKey(key)); require(nowMs >= 0); require(cost.isFinite() && cost > 0 && cost <= capacity)
        var b = buckets[key]
        if (b == null) {
            require(buckets.size < MAX_KEYS) { "throttle table full" }
            b = Bucket(capacity, nowMs); buckets[key] = b
        }
        val avail = refill(b, nowMs)
        if (avail < cost) return false
        b.tokens = avail - cost
        return true
    }

    fun reset(key: String): Boolean = buckets.remove(key) != null
    fun clear() = buckets.clear()

    private fun refill(b: Bucket, nowMs: Long): Double {
        val t = maxOf(nowMs, b.lastMs)
        b.tokens = minOf(capacity, b.tokens + (t - b.lastMs) / 1000.0 * refillPerSec)
        b.lastMs = t
        return b.tokens
    }
}
