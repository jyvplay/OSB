package dev.fusion.beam

import dev.fusion.perf.ThermalState

data class LadderQuality(
    val codec: String,
    val bitrateBps: Int,
    val fps: Int,
    val resolutionLabel: String,
    val reason: String
)

/**
 * QualityGovernor (CA-13):
 * Dynamically computes encoding bitrate, frame rate, and codec ladder
 * based on Wi-Fi link speed (RSSI/negotiated link rate) and hardware thermal headroom.
 * Steps down gracefully BEFORE critical states occur.
 */
object QualityGovernor {
    fun selectQuality(
        linkSpeedMbps: Int,
        thermalState: ThermalState,
        receiverSupportsHevc: Boolean
    ): LadderQuality {
        val codec = if (receiverSupportsHevc && linkSpeedMbps >= 50) "HEVC" else "AVC"

        return when {
            thermalState == ThermalState.CRITICAL || thermalState == ThermalState.THROTTLED ->
                LadderQuality(
                    codec = "AVC",
                    bitrateBps = 1_500_000,
                    fps = 24,
                    resolutionLabel = "720p",
                    reason = "Thermal throttling active ($thermalState) -> stepped down to 720p@24fps"
                )
            thermalState == ThermalState.HOT ->
                LadderQuality(
                    codec = codec,
                    bitrateBps = 3_000_000,
                    fps = 30,
                    resolutionLabel = "1080p",
                    reason = "Thermal severe warning -> capped at 3 Mbps"
                )
            linkSpeedMbps < 20 ->
                LadderQuality(
                    codec = "AVC",
                    bitrateBps = 1_800_000,
                    fps = 24,
                    resolutionLabel = "720p",
                    reason = "Weak Wi-Fi link ($linkSpeedMbps Mbps) -> 720p safe tier"
                )
            linkSpeedMbps < 60 ->
                LadderQuality(
                    codec = codec,
                    bitrateBps = 4_500_000,
                    fps = 30,
                    resolutionLabel = "1080p",
                    reason = "Moderate Wi-Fi link ($linkSpeedMbps Mbps) -> 1080p@30fps"
                )
            else ->
                LadderQuality(
                    codec = codec,
                    bitrateBps = 8_000_000,
                    fps = 60,
                    resolutionLabel = "1080p",
                    reason = "Optimal link ($linkSpeedMbps Mbps) and thermals ($thermalState) -> 1080p@60fps"
                )
        }
    }
}
