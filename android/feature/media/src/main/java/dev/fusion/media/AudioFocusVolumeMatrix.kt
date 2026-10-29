// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Clean-room Per-Module Audio Focus & Gain Ducking Matrix (`MD-06`, `MD-09`), Voice Recorder Level
 * Meter (`MD-04`), Eyedropper sRGB <-> OKLCH Perceptual Color Picker (`MD-07`), and Keyframe-Snapped
 * Video Trim Planner (`MD-08`).
 */
object AudioFocusVolumeMatrix {
    enum class StreamRole { VOICE_CALL_OR_MEET, NAVIGATION_PROMPT, MEDIA_PLAYER, BROWSER_TAB, NOTIFICATION_CHIME }

    data class ChannelInput(
        val moduleId: String,
        val role: StreamRole,
        val userSliderZeroToOne: Double,
        val muted: Boolean = false,
    )

    fun computeEffectiveGains(channels: List<ChannelInput>): Map<String, Double> {
        val voiceActive = channels.any { !it.muted && it.userSliderZeroToOne > 0.0 && it.role == StreamRole.VOICE_CALL_OR_MEET }
        val navActive = channels.any { !it.muted && it.userSliderZeroToOne > 0.0 && it.role == StreamRole.NAVIGATION_PROMPT }
        return channels.associate { ch ->
            if (ch.muted) {
                ch.moduleId to 0.0
            } else {
                val base = ch.userSliderZeroToOne.coerceIn(0.0, 1.0)
                val duckFactor = when (ch.role) {
                    StreamRole.VOICE_CALL_OR_MEET -> 1.0
                    StreamRole.NAVIGATION_PROMPT -> if (voiceActive) 0.50 else 1.0
                    StreamRole.MEDIA_PLAYER, StreamRole.BROWSER_TAB -> when {
                        voiceActive -> 0.15 // Strong ducking during Meet/voice call
                        navActive -> 0.35   // Moderate ducking during turn-by-turn prompt
                        else -> 1.0
                    }
                    StreamRole.NOTIFICATION_CHIME -> if (voiceActive) 0.20 else 0.85
                }
                ch.moduleId to (base * duckFactor)
            }
        }
    }

    /** Björn Ottosson perceptual OKLCH `(L in 0..1, C >= 0, H in 0..360)` from 24-bit `0xRRGGBB` (`MD-07`). */
    fun srgbToOklch(rgb24: Int): Triple<Double, Double, Double> {
        fun lin(shift: Int): Double {
            val c = ((rgb24 ushr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val r = lin(16); val g = lin(8); val b = lin(0)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)

        val okL = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
        val okA = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val okB = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        val chroma = hypot(okA, okB)
        val hueDeg = ((Math.toDegrees(atan2(okB, okA)) % 360.0) + 360.0) % 360.0
        return Triple(okL.coerceIn(0.0, 1.0), chroma, if (chroma < 1e-6) 0.0 else hueDeg)
    }

    /** Snaps lossless stream-copy trim start to nearest preceding IDR keyframe (`MD-08`). */
    fun planLosslessKeyframeTrim(
        keyframeTimestampsMs: List<Long>,
        requestedStartMs: Long,
        requestedEndMs: Long,
    ): Pair<Long, Long> {
        require(requestedEndMs > requestedStartMs && requestedStartMs >= 0L) { "invalid trim window" }
        val sorted = keyframeTimestampsMs.sorted()
        require(sorted.isNotEmpty()) { "keyframe table cannot be empty" }
        val snappedStart = sorted.lastOrNull { it <= requestedStartMs } ?: sorted.first()
        return snappedStart to requestedEndMs
    }
}
