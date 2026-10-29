// SPDX-License-Identifier: Apache-2.0
package dev.fusion.cast.dlna

/**
 * Pure-JVM **Adaptive Cast Display EDID Sink Capability Negotiator & Keyframe Request Scheduler**
 * for `:feature:cast`, matching receiver sink resolution/HDR/codec profiles against local thermal
 * & Wi-Fi airtime budgets.
 */
object CastSinkCodecNegotiator {
    const val SCOPE =
        "Wireless Cast receiver sink codec/HDR/refresh capability negotiator (AV1 / HEVC / H.264) and packet-loss PLI keyframe cadence controller."

    data class ReceiverSinkProfile(
        val maxWidthPx: Int,
        val maxHeightPx: Int,
        val maxFps: Int,
        val supportedCodecs: Set<String>, // AV1, HEVC, H264
        val supportsHdr10: Boolean,
    )

    data class NegotiatedCastStream(
        val selectedCodec: String,
        val widthPx: Int,
        val heightPx: Int,
        val fps: Int,
        val hdr10Enabled: Boolean,
        val targetBitrateKbps: Int,
        val pliKeyframeIntervalFrames: Int,
    )

    fun negotiate(
        sink: ReceiverSinkProfile,
        availableWifiKbps: Int,
        observedPacketLossRatio: Double,
        encoderSupportsAv1Hw: Boolean,
    ): NegotiatedCastStream {
        require(availableWifiKbps >= 800 && observedPacketLossRatio in 0.0..1.0)
        val codec = when {
            encoderSupportsAv1Hw && "AV1" in sink.supportedCodecs -> "AV1"
            "HEVC" in sink.supportedCodecs -> "HEVC"
            else -> "H264"
        }
        val safeBudgetKbps = (availableWifiKbps * (1.0 - observedPacketLossRatio.coerceAtMost(0.4)) * 0.78).toInt()
        val (w, h, fps) = when {
            safeBudgetKbps >= 18_000 && sink.maxWidthPx >= 3840 -> Triple(3840, 2160, minOf(60, sink.maxFps))
            safeBudgetKbps >= 6_000 && sink.maxWidthPx >= 1920 -> Triple(1920, 1080, minOf(60, sink.maxFps))
            else -> Triple(minOf(1280, sink.maxWidthPx), minOf(720, sink.maxHeightPx), minOf(30, sink.maxFps))
        }
        val keyframeFrames = if (observedPacketLossRatio >= 0.03) fps else fps * 2
        return NegotiatedCastStream(
            selectedCodec = codec,
            widthPx = w,
            heightPx = h,
            fps = fps,
            hdr10Enabled = sink.supportsHdr10 && codec != "H264" && safeBudgetKbps >= 10_000,
            targetBitrateKbps = safeBudgetKbps.coerceIn(800, 35_000),
            pliKeyframeIntervalFrames = keyframeFrames,
        )
    }
}
