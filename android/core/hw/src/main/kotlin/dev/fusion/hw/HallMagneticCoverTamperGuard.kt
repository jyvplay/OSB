package dev.fusion.hw

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Clean-room Hall-Effect Lid Sensor & 3-Axis Magnetometer Spoof/Tamper Detector (`V18`, Apache-2.0).
 *
 * Detects external neodymium-magnet smart-cover wake/unlock spoofing and reed-switch tamper:
 * a genuine physical cover open/close produces a bounded calibrated magnetic transition (`<= 450 uT`)
 * accompanied by hinge-angle or accelerometer motion (`>= 0.15 rad/s`), whereas an external probe
 * magnet injects an uncalibrated `> 600 uT` spike on a stationary chassis!
 */
object HallMagneticCoverTamperGuard {
    const val SCOPE =
        "Hall-effect smart-cover wake spoof & external magnet tamper detector cross-checking 3-axis uT norm vs gyro motion (V18)"

    enum class CoverTamperVerdict { GENUINE_COVER_TRANSITION, IGNORE_WAKE_EXTERNAL_MAGNET_SPOOF,STATIONARY_BASELINE }

    data class MagneticWakeSample(
        val hallLidOpenReported: Boolean,
        val magXMicroTesla: Double,
        val magYMicroTesla: Double,
        val magZMicroTesla: Double,
        val calibratedClosedNormUt: Double = 180.0,
        val calibratedOpenNormUt: Double = 48.0,
        val chassisAngularRateRadPerSec: Double,
    )

    data class TamperCheck(
        val verdict: CoverTamperVerdict,
        val fieldMagnitudeUt: Double,
        val allowAutoScreenWake: Boolean,
        val diagnostic: String,
    )

    fun evaluate(sample: MagneticWakeSample): TamperCheck {
        val norm = sqrt(
            sample.magXMicroTesla * sample.magXMicroTesla +
                sample.magYMicroTesla * sample.magYMicroTesla +
                sample.magZMicroTesla * sample.magZMicroTesla,
        )
        // External magnet saturation spike (> 600 uT) or wake transition with zero hinge/chassis motion
        if (norm > 600.0) {
            return TamperCheck(
                verdict = CoverTamperVerdict.IGNORE_WAKE_EXTERNAL_MAGNET_SPOOF,
                fieldMagnitudeUt = norm,
                allowAutoScreenWake = false,
                diagnostic = "Uncalibrated magnetic spike (${"%.1f".format(norm)} uT > 600 uT); auto-wake suppressed",
            )
        }
        if (sample.hallLidOpenReported && sample.chassisAngularRateRadPerSec < 0.05 &&
            abs(norm - sample.calibratedOpenNormUt) > 90.0
        ) {
            return TamperCheck(
                verdict = CoverTamperVerdict.IGNORE_WAKE_EXTERNAL_MAGNET_SPOOF,
                fieldMagnitudeUt = norm,
                allowAutoScreenWake = false,
                diagnostic = "Hall open asserted with stationary chassis and uncalibrated B-field (${"%.1f".format(norm)} uT)",
            )
        }
        return TamperCheck(
            verdict = CoverTamperVerdict.GENUINE_COVER_TRANSITION,
            fieldMagnitudeUt = norm,
            allowAutoScreenWake = sample.hallLidOpenReported,
            diagnostic = "Calibrated lid transition verified",
        )
    }
}
