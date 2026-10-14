package dev.fusion.beam

import dev.fusion.perf.ThermalState

data class LadderQuality(
    val codec: String,
    val bitrateBps: Int,
    val fps: Int,
    val resolutionLabel: String,
    val reason: String,
    val mustStop: Boolean = false
)

/** CA-13/14: proposed policy only, not an active encoder or a measured throughput claim.
 * Unknown capabilities or critical thermal status fail closed. Transports must obey mustStop.
 */
object QualityGovernor {
    fun selectQuality(
        linkSpeedMbps: Int,
        thermalState: ThermalState,
        receiverSupportsHevc: Boolean,
        hardwareHevcAvailable: Boolean = false,
        hardwareAvcAvailable: Boolean = false
    ): LadderQuality {
        if (thermalState == ThermalState.CRITICAL || thermalState == ThermalState.UNKNOWN || linkSpeedMbps <= 0 || !hardwareAvcAvailable) {
            return LadderQuality("none", 0, 0, "stopped", "Critical/unknown thermal, missing link observation or unverified hardware AVC fallback; stop rather than invent a working encoder.", mustStop = true)
        }
        val codec = if (receiverSupportsHevc && hardwareHevcAvailable && linkSpeedMbps >= 50) "HEVC" else "AVC"

        return when {
            thermalState == ThermalState.THROTTLED ->
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
                    reason = "Thermal moderate warning -> capped at 3 Mbps"
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
                    reason = "Proposed tier from observed link ($linkSpeedMbps Mbps) and thermal status ($thermalState) -> 1080p@60fps"
                )
        }
    }
}
