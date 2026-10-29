// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

/**
 * Hardware Shield `#29` — **Ambient-Light (`TYPE_LIGHT`) & Proximity Finger-Shadow Keystroke / PIN
 * Inference Guard**.
 *
 * High-rate (`50–200 Hz`) unpermissioned `Sensor.TYPE_LIGHT` readings leak fine-grained 1–5% lux
 * dips as a user's thumb/finger traverses top-of-screen vs bottom-of-screen PIN keys! During
 * sensitive keyboard/PIN focus, this guard rate-limits deliveries to `>= 1000 ms` (`<= 1 Hz`) and
 * quantizes illuminance to coarse `2^k` (`1, 2, 4, 8, 16, 32, 64, 128, 256, 512... lux`) buckets.
 */
object AmbientLightKeystrokeShadowGuard {
    const val SCOPE =
        "Hardware Shield #29: TYPE_LIGHT & TYPE_PROXIMITY finger-shadow PIN inference side-channel rate-limiter (<=1Hz) and log2 lux quantizer."

    data class FilteredLightSample(
        val deliveredToUntrustedListeners: Boolean,
        val quantizedLux: Float,
        val binaryNearFarOnlyCm: Float,
    )

    fun filterSample(
        rawLux: Float,
        rawProximityCm: Float,
        elapsedSinceLastDeliveryMillis: Long,
        sensitivePinOrPasswordFocused: Boolean,
    ): FilteredLightSample {
        val nearFar = if (rawProximityCm < 3.0f) 0.0f else 5.0f
        if (!sensitivePinOrPasswordFocused) {
            return FilteredLightSample(true, rawLux.coerceAtLeast(0f), nearFar)
        }
        if (elapsedSinceLastDeliveryMillis < 1_000L) {
            return FilteredLightSample(false, 0f, nearFar)
        }
        val clamped = rawLux.toDouble().coerceAtLeast(1.0)
        val bucketExp = floor(ln(clamped) / ln(2.0)).coerceIn(0.0, 16.0)
        val coarseLux = 2.0.pow(bucketExp).toFloat()
        return FilteredLightSample(true, coarseLux, nearFar)
    }
}
