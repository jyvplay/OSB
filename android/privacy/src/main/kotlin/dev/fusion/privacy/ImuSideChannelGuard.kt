// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import kotlin.math.roundToLong

/**
 * Clean-room IMU motion-sensor side-channel keylogger firewall (`ImuSideChannelGuard`).
 *
 * Grounded in three verified academic attacks (`docs/research/r47-primary.md`):
 * 1. **`Glitch in Time` (`arXiv:2409.16438`, 2024)**: Android 12+ caps individual unprivileged IMU sensors
 *    at `200 Hz`, but a `2.5 ms` phase offset between `200 Hz` accelerometer + `200 Hz` gyroscope allows
 *    interleaved `400 Hz` signal reconstruction!
 * 2. **`AlphaLogger` (`JAIHC`, 2020)**: fuses accelerometer + gyroscope + magnetometer at just `10 Hz`
 *    to infer soft-keyboard alphabetic keystrokes with `90.2%` accuracy!
 * 3. **`TouchLogger` / `WiSec`**: single-sensor gyroscope accuracy is `79%` at `100 Hz` vs `18%` at `<= 20 Hz`.
 *
 * Countermeasures enforced by [ImuSideChannelGuard]:
 * - **Complete Blackout During Any Text/Secret Focus**: whenever `textFieldFocused` or `secureFieldFocused`
 *   is `true`, ALL IMU delivery (`ACCELEROMETER`, `GYROSCOPE`, `MAGNETOMETER`, `LINEAR_ACCELERATION`,
 *   `ROTATION_VECTOR`) is dropped (`Action.DROP_TEXT_ENTRY_BLACKOUT`) — even `10 Hz` is unsafe!
 * - **Anti-Interleaving Single-Sensor Mutual Exclusion**: unfocused background/UI views may subscribe to at
 *   most ONE motion modality per window (`Action.DROP_MULTI_SENSOR_FUSION`), defeating both `Glitch in Time`
 *   phase-interleaving and `AlphaLogger` 3-sensor fusion!
 * - **Rate Cap + Amplitude Quantization**: allowed unfocused samples are rate-limited to `minIntervalNs`
 *   (`>= 100_000_000 ns` = `10 Hz`) and rounded to `quantStep` (`0.25 m/s^2` or `rad/s`) to strip micro-tap vibrations.
 */
object ImuSideChannelGuard {
    const val SCOPE =
        "Motion-sensor keylogger side-channel firewall (100% IMU blackout during text/secret focus, single-sensor mutual exclusion vs Glitch-in-Time/AlphaLogger fusion, <=10 Hz rate cap + 0.25 amplitude quantization); kernel /dev/input evdev SELinux policy out of scope."

    enum class SensorKind {
        ACCELEROMETER,
        GYROSCOPE,
        MAGNETOMETER,
        LINEAR_ACCELERATION,
        ROTATION_VECTOR,
    }

    enum class VerdictKind {
        ALLOW_QUANTIZED,
        DROP_TEXT_ENTRY_BLACKOUT,
        DROP_BACKGROUND_APP,
        DROP_MULTI_SENSOR_FUSION,
        DROP_RATE_LIMIT,
    }

    data class SensorDecision(
        val verdict: VerdictKind,
        val quantizedX: Double = 0.0,
        val quantizedY: Double = 0.0,
        val quantizedZ: Double = 0.0,
    ) {
        val allowed: Boolean get() = verdict == VerdictKind.ALLOW_QUANTIZED
    }

    class SessionGate(
        val maxUnfocusedHz: Int = 10,
        val quantStep: Double = 0.25,
    ) {
        init {
            require(maxUnfocusedHz in 1..20) {
                "maxUnfocusedHz ($maxUnfocusedHz) must be in 1..20 Hz (Android 200 Hz cap is insufficient)"
            }
            require(quantStep >= 0.05) { "quantStep must be >= 0.05" }
        }

        private val minIntervalNs: Long = 1_000_000_000L / maxUnfocusedHz.toLong()
        private var activeModality: SensorKind? = null
        private var lastDeliveredNs: Long = Long.MIN_VALUE / 2

        fun resetModalityLock() {
            activeModality = null
        }

        fun evaluateSample(
            sensor: SensorKind,
            timestampNs: Long,
            x: Double,
            y: Double,
            z: Double,
            appInForeground: Boolean,
            textOrSecretFocused: Boolean,
        ): SensorDecision {
            if (!appInForeground) {
                return SensorDecision(VerdictKind.DROP_BACKGROUND_APP)
            }
            if (textOrSecretFocused) {
                return SensorDecision(VerdictKind.DROP_TEXT_ENTRY_BLACKOUT)
            }
            // Rotation vector & linear acceleration internally fuse accel+gyro+mag!
            if (sensor == SensorKind.ROTATION_VECTOR || sensor == SensorKind.LINEAR_ACCELERATION) {
                return SensorDecision(VerdictKind.DROP_MULTI_SENSOR_FUSION)
            }
            val locked = activeModality
            if (locked != null && locked != sensor) {
                return SensorDecision(VerdictKind.DROP_MULTI_SENSOR_FUSION)
            }
            if (timestampNs - lastDeliveredNs < minIntervalNs) {
                return SensorDecision(VerdictKind.DROP_RATE_LIMIT)
            }
            activeModality = sensor
            lastDeliveredNs = timestampNs
            return SensorDecision(
                verdict = VerdictKind.ALLOW_QUANTIZED,
                quantizedX = quantize(x),
                quantizedY = quantize(y),
                quantizedZ = quantize(z),
            )
        }

        private fun quantize(v: Double): Double {
            if (!v.isFinite()) return 0.0
            return (v / quantStep).roundToLong() * quantStep
        }
    }
}
