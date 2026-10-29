// SPDX-License-Identifier: Apache-2.0
package dev.fusion.together

import kotlin.math.abs

/**
 * Clean-room NTP-4-Timestamp Clock-Offset Estimator + Bounded PLL Playback Rate Adjuster for
 * Together Co-Watch / Co-Listen Sessions.
 */
object TogetherCoWatchSyncClock {
    const val SCOPE =
        "Pure-JVM 4-timestamp clock-offset & RTT estimator with pitch-preserving 0.97x..1.03x PLL playback drift correction vs hard seek."

    data class ClockSample(val offsetMs: Double, val rttMs: Double)

    enum class DriftAction { IN_SYNC_1_00X, PLL_NUDGE_RATE, HARD_SEEK }

    data class SyncCorrection(val action: DriftAction, val playbackRate: Double, val seekTargetMs: Long?)

    /**
     * Standard 4-timestamp NTP formula:
     *   $$\theta = \frac{(t_2 - t_1) + (t_3 - t_4)}{2}, \quad \delta = (t_4 - t_1) - (t_3 - t_2)$$
     */
    fun estimateOffsetAndRtt(t1ClientSendMs: Long, t2HostRecvMs: Long, t3HostSendMs: Long, t4ClientRecvMs: Long): ClockSample {
        require(t4ClientRecvMs >= t1ClientSendMs && t3HostSendMs >= t2HostRecvMs) { "non-monotonic timestamps" }
        val rtt = ((t4ClientRecvMs - t1ClientSendMs) - (t3HostSendMs - t2HostRecvMs)).toDouble().coerceAtLeast(0.0)
        val offset = ((t2HostRecvMs - t1ClientSendMs) + (t3HostSendMs - t4ClientRecvMs)) / 2.0
        return ClockSample(offset, rtt)
    }

    fun planPlayheadCorrection(localPositionMs: Long, expectedLeaderPositionMs: Long): SyncCorrection {
        val driftMs = expectedLeaderPositionMs - localPositionMs // positive => local is behind leader
        val absDrift = abs(driftMs)
        return when {
            absDrift <= 25L -> SyncCorrection(DriftAction.IN_SYNC_1_00X, 1.0, null)
            absDrift <= 600L -> {
                val gain = (driftMs / 10_000.0).coerceIn(-0.03, 0.03)
                SyncCorrection(DriftAction.PLL_NUDGE_RATE, 1.0 + gain, null)
            }
            else -> SyncCorrection(DriftAction.HARD_SEEK, 1.0, expectedLeaderPositionMs.coerceAtLeast(0L))
        }
    }
}
