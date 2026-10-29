// SPDX-License-Identifier: Apache-2.0
package dev.fusion.perf

/**
 * Clean-room Android ADPF -> Sustained-Performance -> Own-Governor Thermal Ladder (`RG-02`),
 * Interaction/Idle Variable Refresh Policy (`RG-03`), Charging/Battery/Metered Background Gate
 * (`RG-06`), and Low-RAM Mode Budget (`RG-07`).
 */
object AdpfThermalRefreshPolicy {
    enum class GovernorBackend { ADPF_HINT_SESSION, SUSTAINED_PERFORMANCE_WINDOW, OWN_QUALITY_GOVERNOR }
    enum class ThermalTier { NOMINAL, ELEVATED_THROTTLE_EFFECTS, CRITICAL_MINIMAL_SHELL }

    data class FrameGovernorDecision(
        val backend: GovernorBackend,
        val thermalTier: ThermalTier,
        val targetRefreshHz: Int,
        val maxSimultaneousLivePreviews: Int,
        val backgroundIndexingAllowed: Boolean,
        val lowRamModeActive: Boolean,
    )

    fun evaluate(
        adpfSupported: Boolean,
        sustainedPerfSupported: Boolean,
        thermalHeadroom: Double, // < 0.85 nominal, 0.85..1.00 elevated, > 1.00 critical per Android PowerManager
        panelSupportedHz: List<Int>,
        msSinceLastUserInput: Long,
        batteryPercent: Int,
        isCharging: Boolean,
        isMeteredNetwork: Boolean,
        totalDeviceRamMb: Int,
    ): FrameGovernorDecision {
        require(panelSupportedHz.isNotEmpty() && panelSupportedHz.all { it in 24..360 }) { "invalid panel Hz list" }
        require(batteryPercent in 0..100 && totalDeviceRamMb >= 512) { "invalid battery/RAM telemetry" }

        val backend = when {
            adpfSupported -> GovernorBackend.ADPF_HINT_SESSION
            sustainedPerfSupported -> GovernorBackend.SUSTAINED_PERFORMANCE_WINDOW
            else -> GovernorBackend.OWN_QUALITY_GOVERNOR
        }

        val tier = when {
            thermalHeadroom > 1.00 -> ThermalTier.CRITICAL_MINIMAL_SHELL
            thermalHeadroom >= 0.85 -> ThermalTier.ELEVATED_THROTTLE_EFFECTS
            else -> ThermalTier.NOMINAL
        }

        val lowRam = totalDeviceRamMb <= 4_096
        val sortedHz = panelSupportedHz.sorted()
        val interactive = msSinceLastUserInput in 0..2_500L

        val chosenHz = when {
            tier == ThermalTier.CRITICAL_MINIMAL_SHELL || (!isCharging && batteryPercent <= 15) ->
                sortedHz.firstOrNull { it >= 60 } ?: sortedHz.first()
            !interactive ->
                sortedHz.first() // Drop to lowest supported LTPO/idle rate (e.g. 24/30/60 Hz)
            tier == ThermalTier.ELEVATED_THROTTLE_EFFECTS || lowRam ->
                sortedHz.lastOrNull { it <= 90 } ?: sortedHz.first()
            else ->
                sortedHz.last() // Peak 120/144 Hz during active pointer/scroll interaction
        }

        val maxPreviews = when {
            lowRam || tier == ThermalTier.CRITICAL_MINIMAL_SHELL -> 1
            tier == ThermalTier.ELEVATED_THROTTLE_EFFECTS -> 2
            else -> 6
        }

        val allowBackgroundWork = tier == ThermalTier.NOMINAL &&
            !lowRam &&
            (isCharging || batteryPercent >= 35) &&
            !isMeteredNetwork

        return FrameGovernorDecision(
            backend = backend,
            thermalTier = tier,
            targetRefreshHz = chosenHz,
            maxSimultaneousLivePreviews = maxPreviews,
            backgroundIndexingAllowed = allowBackgroundWork,
            lowRamModeActive = lowRam,
        )
    }
}
