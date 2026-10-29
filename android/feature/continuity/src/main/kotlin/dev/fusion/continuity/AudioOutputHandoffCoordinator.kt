package dev.fusion.continuity

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong

/**
 * Clean-room Seamless Cross-Device Audio Output Handoff Coordinator (`TD-06`, Apache-2.0).
 *
 * Computes NTP-style clock-offset + half-RTT compensated target resume position and equal-power
 * (`cos`/`sin`) crossfade gain envelopes so audio route handoffs occur without clicks or pops!
 */
object AudioOutputHandoffCoordinator {
    const val SCOPE =
        "NTP clock-offset + RTT-compensated cross-device audio handoff planner + equal-power click-free crossfade envelope (TD-06)"

    data class HandoffPlan(
        val clockOffsetMs: Long,
        val oneWayLatencyMs: Long,
        val prefillLeadMs: Long,
        val targetStartMediaPositionMs: Long,
        val fadeOutGains: List<Double>,
        val fadeInGains: List<Double>,
    )

    /**
     * Given NTP four-timestamp probe `(t1ClientSend, t2PeerRecv, t3PeerSend, t4ClientRecv)` and
     * current playback position [sourcePositionMs], schedules exact synchronized handoff.
     */
    fun planHandoff(
        sourcePositionMs: Long,
        t1ClientSendMs: Long,
        t2PeerRecvMs: Long,
        t3PeerSendMs: Long,
        t4ClientRecvMs: Long,
        jitterMarginMs: Long = 40L,
        crossfadeSteps: Int = 8,
    ): HandoffPlan {
        require(t4ClientRecvMs >= t1ClientSendMs && t3PeerSendMs >= t2PeerRecvMs && crossfadeSteps in 2..64)
        val rtt = (t4ClientRecvMs - t1ClientSendMs) - (t3PeerSendMs - t2PeerRecvMs)
        val oneWay = (rtt.coerceAtLeast(0L) / 2.0).roundToLong()
        val clockOffset = (((t2PeerRecvMs - t1ClientSendMs) + (t3PeerSendMs - t4ClientRecvMs)) / 2.0).roundToLong()
        val lead = oneWay + jitterMarginMs.coerceAtLeast(10L)
        val resumePos = sourcePositionMs + lead

        // Equal-power crossfade: fadeOut^2 + fadeIn^2 == 1.0 at every step!
        val fadeOut = List(crossfadeSteps) { i ->
            val theta = (PI * 0.5) * (i.toDouble() / (crossfadeSteps - 1))
            cos(theta)
        }
        val fadeIn = List(crossfadeSteps) { i ->
            val theta = (PI * 0.5) * (i.toDouble() / (crossfadeSteps - 1))
            kotlin.math.sin(theta)
        }
        return HandoffPlan(clockOffset, oneWay, lead, resumePos, fadeOut, fadeIn)
    }
}
