// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * **Local Hardware Attack Vector `#24` — `TapLogger` / `ACCessory` IMU Keystroke Inference Defense:**
 * Unprivileged Android background observers can sample `TYPE_ACCELEROMETER` and `TYPE_GYROSCOPE`
 * Tait-Bryan pitch/roll micro-impulses to infer 6-char passwords in `4.5` median trials.
 *
 * Enforces a `±180 ms` temporal blackout/quantization window around every secure keystroke so
 * exported IMU samples collapse to coarse `90°` screen-rotation buckets with zero high-frequency
 * tap impulse variance.
 */
object ImuKeystrokeMotionZeroizer {
    const val SCOPE =
        "Vector #24 TapLogger/ACCessory accelerometer & gyroscope keystroke inference firewall: ±180ms temporal guard window + 90-degree coarse orientation quantization."

    const val GUARD_WINDOW_MS = 180L

    data class ImuSample(
        val timestampMs: Long,
        val ax: Double,
        val ay: Double,
        val az: Double,
        val gx: Double,
        val gy: Double,
        val gz: Double,
    )

    fun sanitizeStream(samples: List<ImuSample>, keystrokeTimestampsMs: List<Long>): List<ImuSample> {
        if (samples.isEmpty()) return emptyList()
        val sortedKeys = keystrokeTimestampsMs.sorted()
        return samples.map { s ->
            val insideGuard = isWithinGuardWindow(s.timestampMs, sortedKeys)
            if (!insideGuard) s else {
                // Preserve only coarse gravity axis (+/- 9.81 m/s^2) for legitimate portrait/landscape rotation
                val absX = abs(s.ax); val absY = abs(s.ay); val absZ = abs(s.az)
                val (qx, qy, qz) = when {
                    absZ >= absX && absZ >= absY -> Triple(0.0, 0.0, if (s.az >= 0) 9.81 else -9.81)
                    absY >= absX -> Triple(0.0, if (s.ay >= 0) 9.81 else -9.81, 0.0)
                    else -> Triple(if (s.ax >= 0) 9.81 else -9.81, 0.0, 0.0)
                }
                ImuSample(s.timestampMs, qx, qy, qz, 0.0, 0.0, 0.0)
            }
        }
    }

    fun computeTiltImpulseEnergy(samples: List<ImuSample>): Double {
        if (samples.size < 2) return 0.0
        var energy = 0.0
        for (i in 1 until samples.size) {
            val prev = samples[i - 1]; val cur = samples[i]
            val pPitch = atan2(-prev.ax, sqrt(prev.ay * prev.ay + prev.az * prev.az))
            val cPitch = atan2(-cur.ax, sqrt(cur.ay * cur.ay + cur.az * cur.az))
            val dPitch = cPitch - pPitch
            val gyroMagSq = cur.gx * cur.gx + cur.gy * cur.gy + cur.gz * cur.gz
            energy += dPitch * dPitch + gyroMagSq
        }
        return energy
    }

    private fun isWithinGuardWindow(ts: Long, sortedKeys: List<Long>): Boolean {
        var lo = 0
        var hi = sortedKeys.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val k = sortedKeys[mid]
            if (abs(ts - k) <= GUARD_WINDOW_MS) return true
            if (k < ts) lo = mid + 1 else hi = mid - 1
        }
        return false
    }
}
