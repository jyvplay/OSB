// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import kotlin.math.exp
import kotlin.math.ln

/**
 * **Local Hardware Attack Vector `#23` — `Thermanator` Thermal Residue Defense (Kaczmarek, Ozturk & Tsudik, UCI `arXiv:1806.10189`):**
 * Models fingertip heat transfer and Newton's Law of Cooling across touchscreen/keyboard materials
 * (`GLASS_GORILLA`, `PLASTIC_ABS`, `ALUMINUM_ANODIZED`) and mandates a post-authentication full-pad
 * palm/multi-touch thermal scrub gesture whenever residual thermal contrast at `t = 30s` exceeds the
 * FLIR/bolometer detection threshold (`0.18 °C`).
 */
object ThermalResidueWipeAdvisor {
    const val SCOPE =
        "Vector #23 Thermanator (UCI arXiv:1806.10189) post-entry thermal-camera PIN residue defense using Newton cooling decay and mandatory multi-key palm thermal scrub verification."

    const val BOLOMETER_CONTRAST_THRESHOLD_C = 0.18

    enum class SurfaceMaterial(val tauDecaySeconds: Double, val initialTransferFactor: Double) {
        PLASTIC_ABS(44.0, 0.24),
        GLASS_GORILLA(26.0, 0.19),
        ALUMINUM_ANODIZED(5.5, 0.06),
    }

    data class ThermalRiskAssessment(
        val material: SurfaceMaterial,
        val initialDeltaTCelsius: Double,
        val residualAt30sCelsius: Double,
        val safeWaitSecondsWithoutWipe: Double,
        val palmScrubRequiredImmediately: Boolean,
    )

    fun assess(
        material: SurfaceMaterial,
        fingerTempC: Double = 33.5,
        ambientTempC: Double = 21.0,
        dwellDurationMs: Long = 220L,
    ): ThermalRiskAssessment {
        require(dwellDurationMs > 0L && fingerTempC > ambientTempC) { "Invalid thermal input parameters" }
        val dwellFactor = (1.0 - exp(-dwellDurationMs.toDouble() / 250.0)).coerceIn(0.15, 1.0)
        val deltaT0 = (fingerTempC - ambientTempC) * material.initialTransferFactor * dwellFactor
        val residual30 = deltaT0 * exp(-30.0 / material.tauDecaySeconds)
        val safeSec = if (deltaT0 <= BOLOMETER_CONTRAST_THRESHOLD_C) 0.0
        else -material.tauDecaySeconds * ln(BOLOMETER_CONTRAST_THRESHOLD_C / deltaT0)
        return ThermalRiskAssessment(
            material = material,
            initialDeltaTCelsius = deltaT0,
            residualAt30sCelsius = residual30,
            safeWaitSecondsWithoutWipe = safeSec,
            palmScrubRequiredImmediately = residual30 >= BOLOMETER_CONTRAST_THRESHOLD_C,
        )
    }

    fun verifyPalmScrubCoverage(scrubbedGridCells: Set<Int>, totalKeypadCells: Int = 12): Boolean {
        require(totalKeypadCells >= 10)
        val covered = scrubbedGridCells.count { it in 0 until totalKeypadCells }
        return covered * 100 >= totalKeypadCells * 80 // >= 80% keypad surface thermal equalization
    }
}
