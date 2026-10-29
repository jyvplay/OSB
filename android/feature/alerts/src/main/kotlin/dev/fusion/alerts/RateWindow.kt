// SPDX-License-Identifier: Apache-2.0
package dev.fusion.alerts

/**
 * Sliding-Window Alert Storm Suppressor & Fingerprint Coalescer.
 *
 * Protects user attention and notification channels from runaway alert loops:
 * - Enforces a global sliding-window rate cap (`maxAlertsPerWindow` within `windowMs`).
 * - Deduplicates identical `fingerprint` alerts inside `dedupCooldownMs`, accumulating a
 *   `suppressedDuplicates` counter that surfaces cleanly when the cooldown elapses!
 */
object RateWindow {
    const val SCOPE =
        "Monotonic sliding-window notification rate limiter and per-fingerprint duplicate coalescer; Android NotificationManager channels out of scope."

    sealed interface Decision {
        data class Deliver(val coalescedPrevCount: Int) : Decision
        data class SuppressDuplicate(val duplicateCountInCooldown: Int) : Decision
        data class SuppressRateCap(val windowActiveCount: Int) : Decision
    }

    class Gate(
        val windowMs: Long,
        val maxAlertsPerWindow: Int,
        val dedupCooldownMs: Long,
    ) {
        init {
            require(windowMs > 0L && dedupCooldownMs >= 0L) { "invalid window/cooldown durations" }
            require(maxAlertsPerWindow >= 1) { "maxAlertsPerWindow must be >= 1" }
        }

        private data class FingerprintState(var lastDeliveredMs: Long, var suppressedCount: Int)

        private val deliveredTimestamps = ArrayDeque<Long>()
        private val byFingerprint = LinkedHashMap<String, FingerprintState>()
        private var lastNowMs: Long = Long.MIN_VALUE

        fun submit(fingerprint: String, nowMs: Long): Decision {
            require(fingerprint.isNotBlank()) { "fingerprint must not be blank" }
            require(lastNowMs == Long.MIN_VALUE || nowMs >= lastNowMs) {
                "non-monotonic alert timestamp $nowMs < $lastNowMs"
            }
            lastNowMs = nowMs

            // Evict expired global sliding-window deliveries
            while (deliveredTimestamps.isNotEmpty() && nowMs - deliveredTimestamps.first() >= windowMs) {
                deliveredTimestamps.removeFirst()
            }

            val fpState = byFingerprint[fingerprint]
            if (fpState != null && nowMs - fpState.lastDeliveredMs < dedupCooldownMs) {
                fpState.suppressedCount++
                return Decision.SuppressDuplicate(fpState.suppressedCount)
            }

            if (deliveredTimestamps.size >= maxAlertsPerWindow) {
                return Decision.SuppressRateCap(deliveredTimestamps.size)
            }

            deliveredTimestamps.addLast(nowMs)
            val prevSuppressed = fpState?.suppressedCount ?: 0
            byFingerprint[fingerprint] = FingerprintState(nowMs, 0)
            // Bound fingerprint table size
            if (byFingerprint.size > 4_096) {
                val eldest = byFingerprint.keys.first()
                byFingerprint.remove(eldest)
            }
            return Decision.Deliver(prevSuppressed)
        }
    }
}
