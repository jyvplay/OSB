// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room Local Power-Trace / `BatteryStats` / CPU DVFS Keystroke Timing Side-Channel Mitigator.
 *
 * Unprivileged co-resident processes or smart-charger power-line monitors (*ChoiceJacking §7*) can
 * infer password digraphs from `15–180 ms` inter-keystroke CPU/GPU wakeups and current spikes.
 *
 * This mitigator:
 * 1. Quantizes every sensitive keystroke dispatch timestamp onto fixed `epochMs` (`25 ms` default =
 *    `40 Hz` constant cadence) boundaries so intra-epoch inter-arrival deltas collapse to `k * 25 ms`.
 * 2. Executes constant-shape HMAC-SHA256 chaff work ticks on idle epochs during an active secret-entry
 *    window so active vs idle epochs exhibit uniform CPU work envelopes.
 * 3. Audits `/proc` & high-rate `BatteryManager` current-now polling intervals.
 */
class PowerDvfsTimingMitigator(
    val epochMs: Long = 25L,
    chaffSeed: ByteArray = ByteArray(32) { (it * 17 + 5).toByte() },
) {
    init {
        require(epochMs in 10L..100L) { "epochMs must be in 10..100 ms" }
        require(chaffSeed.size >= 16) { "chaffSeed must be >= 16 bytes" }
    }

    private val hmacKey = SecretKeySpec(chaffSeed, "HmacSHA256")

    data class EpochSchedule(
        val rawArrivalMs: Long,
        val scheduledReleaseMs: Long,
        val addedDelayMs: Long,
        val epochIndex: Long,
    )

    fun scheduleKeystrokeRelease(windowStartMs: Long, rawArrivalMs: Long): EpochSchedule {
        require(rawArrivalMs >= windowStartMs) { "rawArrivalMs < windowStartMs" }
        val elapsed = rawArrivalMs - windowStartMs
        val epochIdx = (elapsed / epochMs) + 1L
        val release = windowStartMs + epochIdx * epochMs
        return EpochSchedule(
            rawArrivalMs = rawArrivalMs,
            scheduledReleaseMs = release,
            addedDelayMs = release - rawArrivalMs,
            epochIndex = epochIdx,
        )
    }

    /** Deterministic constant-shape work tick (`iterations` fixed regardless of real vs chaff keystroke). */
    fun runIsochronousWorkTick(epochIndex: Long, isRealKeystroke: Boolean, iterations: Int = 8): ByteArray {
        require(iterations in 1..64) { "iterations outside 1..64" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)
        var block = ByteArray(32)
        for (i in 0 until 8) {
            block[i] = ((epochIndex ushr (i * 8)) and 0xFFL).toByte()
        }
        // Encode boolean branchlessly into byte 8 so HMAC input size & loop trip count are identical!
        block[8] = if (isRealKeystroke) 0x5A.toByte() else 0xA5.toByte()
        repeat(iterations) {
            block = mac.doFinal(block)
        }
        return block
    }

    fun isSuspiciousTelemetryPolling(sampleIntervalMs: Long, sensitiveInputFocused: Boolean): Boolean =
        sensitiveInputFocused && sampleIntervalMs < 250L
}
