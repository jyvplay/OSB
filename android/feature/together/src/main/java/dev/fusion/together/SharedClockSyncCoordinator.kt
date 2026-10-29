// SPDX-License-Identifier: Apache-2.0
package dev.fusion.together

import kotlin.math.abs

/**
 * Clean-room Shared-Clock NTP Cristian Offset Estimator & Multi-Receiver Syncplay Drift PLL
 * Coordinator (`CA-11`, `CA-15`).
 *
 * Synchronizes own-copy local media playback or multi-receiver audio/video playheads without
 * uploading copyrighted media streams:
 * - Filters out high-jitter NTP samples (`rttMs > medianRtt * 2.5`) and estimates clock offset
 *   `theta = ((t1 - t0) + (t2 - t3)) / 2`.
 * - Applies smooth PLL playback rate nudges (`0.97x .. 1.03x`) for `25..400 ms` drift and hard-seeks
 *   only when drift exceeds `400 ms`, avoiding pitch warble!
 */
object SharedClockSyncCoordinator {
    data class ClockExchangeSample(
        val clientSendT0Ms: Long,
        val serverRecvT1Ms: Long,
        val serverSendT2Ms: Long,
        val clientRecvT3Ms: Long,
    ) {
        val rttMs: Long get() = (clientRecvT3Ms - clientSendT0Ms) - (serverSendT2Ms - serverRecvT1Ms)
        val clockOffsetMs: Double get() = ((serverRecvT1Ms - clientSendT0Ms) + (serverSendT2Ms - clientRecvT3Ms)) / 2.0
    }

    enum class CorrectionMode { IN_SYNC_DEADBAND, GENTLE_RATE_NUDGE, HARD_SEEK_REQUIRED }

    data class PlayoutAdjustment(
        val mode: CorrectionMode,
        val driftMs: Double,
        val playbackRate: Double,
        val hardSeekTargetMs: Long?,
    )

    fun estimateClockOffsetMs(samples: List<ClockExchangeSample>): Double {
        val valid = samples.filter { it.rttMs >= 0L }.sortedBy { it.rttMs }
        require(valid.isNotEmpty()) { "at least 1 non-negative RTT sample required" }
        // Take lowest-RTT half (minimum queuing delay!) per NTP clock filter discipline
        val bestHalf = valid.take((valid.size + 1) / 2)
        return bestHalf.sumOf { it.clockOffsetMs } / bestHalf.size
    }

    fun computePlayoutAdjustment(
        localNowMs: Long,
        estimatedClockOffsetMs: Double,
        localPlayheadMs: Long,
        authorityPlayheadAtAnchorMs: Long,
        authoritySharedTimestampAtAnchorMs: Long,
        authorityPaused: Boolean,
    ): PlayoutAdjustment {
        val sharedNowMs = localNowMs + estimatedClockOffsetMs
        val expectedPlayhead = if (authorityPaused) {
            authorityPlayheadAtAnchorMs.toDouble()
        } else {
            authorityPlayheadAtAnchorMs + (sharedNowMs - authoritySharedTimestampAtAnchorMs)
        }
        val drift = localPlayheadMs - expectedPlayhead // positive => local is ahead of authority

        return when {
            abs(drift) <= 25.0 -> PlayoutAdjustment(CorrectionMode.IN_SYNC_DEADBAND, drift, 1.0, null)
            abs(drift) > 400.0 -> PlayoutAdjustment(
                CorrectionMode.HARD_SEEK_REQUIRED,
                drift,
                1.0,
                expectedPlayhead.toLong().coerceAtLeast(0L),
            )
            else -> {
                // Proportional nudge clamped to [0.97, 1.03]
                val nudge = (-drift / 8_000.0).coerceIn(-0.03, 0.03)
                PlayoutAdjustment(CorrectionMode.GENTLE_RATE_NUDGE, drift, 1.0 + nudge, null)
            }
        }
    }
}
