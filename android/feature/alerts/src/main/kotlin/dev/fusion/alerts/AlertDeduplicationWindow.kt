// SPDX-License-Identifier: Apache-2.0
package dev.fusion.alerts

/**
 * Bounded alert deduplication window suppressor.
 */
class AlertDeduplicationWindow private constructor(private val windowMs: Long) {
    companion object {
        fun create(windowMs: Long): AlertDeduplicationWindow {
            require(windowMs >= 0L) { "negative windowMs" }
            return AlertDeduplicationWindow(windowMs)
        }
    }

    private val lastSeen = HashMap<String, Long>()

    fun shouldEmit(key: String, nowMs: Long): Boolean {
        require(key.isNotEmpty() && nowMs >= 0L) { "invalid args" }
        val prev = lastSeen[key]
        if (prev != null && nowMs - prev < windowMs) return false
        lastSeen[key] = nowMs
        return true
    }
}
