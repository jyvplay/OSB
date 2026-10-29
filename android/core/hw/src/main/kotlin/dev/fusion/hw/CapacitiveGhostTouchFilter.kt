package dev.fusion.hw

import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Clean-room Capacitive EMI Touch-Injection Detector (`V16`, Apache-2.0) defending against:
 * - **GhostTouch** (USENIX Security 2022, Kai Wang et al.): contactless near-field EMI (`<= 40 mm`)
 *   inducing phantom taps with characteristic sub-human inter-tap intervals (`< 18 ms`), zero or
 *   pinned contact-major ellipse (`sizeMajor == 0`), and `~14–20 px` spatial scatter,
 * - **WIGHT** (IEEE S&P 2022): wired charger common-mode (`100–500 kHz`) noise phantom touches
 *   while `acChargerConnected == true` without correlated IMU micro-impulse!
 */
object CapacitiveGhostTouchFilter {
    const val SCOPE =
        "GhostTouch (USENIX Sec 22) & WIGHT (IEEE S&P 22) capacitive EMI phantom-touch anomaly filter + IMU co-presence gate (V16)"

    enum class TouchVerdict { ACCEPT_HUMAN_TOUCH, QUARANTINE_REQUIRE_SECOND_MODALITY, REJECT_EMI_PHANTOM_INJECTION }

    data class TouchSample(
        val timestampMs: Long,
        val xPx: Double,
        val yPx: Double,
        val pressure: Double,
        val contactMajorPx: Double,
        val imuMicroImpulseMilliG: Double,
    )

    data class InspectionReport(
        val verdict: TouchVerdict,
        val riskScore: Int,
        val anomalies: List<String>,
    )

    fun inspectBurst(
        samples: List<TouchSample>,
        acChargerConnected: Boolean,
        privilegedSurface: Boolean = true,
    ): InspectionReport {
        require(samples.isNotEmpty()) { "Touch burst requires >= 1 sample" }
        val anomalies = mutableListOf<String>()
        var risk = 0

        for (s in samples) {
            if (s.pressure <= 0.0 || s.contactMajorPx <= 0.01) {
                anomalies.add("ZERO_CAPACITIVE_ELLIPSE_OR_PRESSURE")
                risk += 55
                break
            }
        }

        // Sub-human inter-tap interval (< 18 ms between distinct tap onsets)
        for (i in 1 until samples.size) {
            val dt = samples[i].timestampMs - samples[i - 1].timestampMs
            if (dt in 0..17) {
                anomalies.add("SUB_18MS_EMI_BURST_DT_${dt}MS")
                risk += 60
                break
            }
        }

        // Spatial jitter with near-zero physical chassis micro-motion (classic GhostTouch / WIGHT signature!)
        val maxImu = samples.maxOf { it.imuMicroImpulseMilliG }
        if (maxImu < 1.5) {
            anomalies.add("MISSING_CHASSIS_IMU_CO_IMPULSE")
            risk += if (acChargerConnected) 50 else 35
        }

        if (samples.size >= 3 && acChargerConnected) {
            val meanX = samples.map { it.xPx }.average()
            val meanY = samples.map { it.yPx }.average()
            val rms = sqrt(samples.sumOf { val d = hypot(it.xPx - meanX, it.yPx - meanY); d * d } / samples.size)
            if (rms in 8.0..35.0 && maxImu < 2.0) {
                anomalies.add("WIGHT_CHARGER_COMMON_MODE_SCATTER_RMS_${"%.1f".format(rms)}PX")
                risk += 45
            }
        }

        val capped = risk.coerceAtMost(100)
        val verdict = when {
            capped >= 75 -> TouchVerdict.REJECT_EMI_PHANTOM_INJECTION
            capped >= 35 && privilegedSurface -> TouchVerdict.QUARANTINE_REQUIRE_SECOND_MODALITY
            else -> TouchVerdict.ACCEPT_HUMAN_TOUCH
        }
        return InspectionReport(verdict, capped, anomalies)
    }
}
