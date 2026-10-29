package dev.fusion.castv2

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Clean-room Virtual Display Mirror Aspect-Ratio Letterbox/Pillarbox Scaler & Adaptive Bitrate
 * Budget Planner (Apache-2.0) ensuring even-pixel macroblock alignment (`mod 2 == 0`) for H.264/VP8.
 */
object CastVirtualDisplayBoundsScaler {
    const val SCOPE =
        "Even-macroblock letterbox/pillarbox virtual-display scaler + RTT/packet-loss adaptive bitrate budgeter"

    data class ScaledViewport(
        val contentWidthPx: Int,
        val contentHeightPx: Int,
        val offsetXPx: Int,
        val offsetYPx: Int,
        val targetBitrateKbps: Int,
    )

    fun computeViewport(
        sourceWidthPx: Int,
        sourceHeightPx: Int,
        sinkWidthPx: Int,
        sinkHeightPx: Int,
        fps: Int = 60,
        packetLossRatio: Double = 0.0,
    ): ScaledViewport {
        require(sourceWidthPx >= 64 && sourceHeightPx >= 64 && sinkWidthPx >= 64 && sinkHeightPx >= 64)
        require(fps in 15..120 && packetLossRatio in 0.0..1.0)
        val scale = min(sinkWidthPx.toDouble() / sourceWidthPx, sinkHeightPx.toDouble() / sourceHeightPx)
        val rawW = (sourceWidthPx * scale).roundToInt().coerceIn(2, sinkWidthPx)
        val rawH = (sourceHeightPx * scale).roundToInt().coerceIn(2, sinkHeightPx)
        val evenW = rawW and 1.inv()
        val evenH = rawH and 1.inv()
        val offX = ((sinkWidthPx - evenW) / 2) and 1.inv()
        val offY = ((sinkHeightPx - evenH) / 2) and 1.inv()

        // Kush-gauge style bits-per-pixel motion factor throttled smoothly under packet loss
        val rawBps = evenW.toLong() * evenH.toLong() * fps * 0.09
        val lossPenalty = (1.0 - (packetLossRatio * 3.0).coerceAtMost(0.75))
        val kbps = ((rawBps * lossPenalty) / 1000.0).roundToInt().coerceIn(500, 25_000)
        return ScaledViewport(evenW, evenH, offX, offY, kbps)
    }
}
