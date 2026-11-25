// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

/**
 * Bounded PCM audio sample-rate allowlist validator.
 */
class CaptureAudioSampleRateGuard private constructor() {
    companion object {
        val SUPPORTED_RATES = setOf(8000, 16000, 22050, 44100, 48000, 96000)

        fun isSupported(hz: Int): Boolean = hz in SUPPORTED_RATES
    }
}
