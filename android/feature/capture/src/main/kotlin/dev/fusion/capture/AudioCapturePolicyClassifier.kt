// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

/**
 * Pure-JVM Android API 29–37 `AudioPlaybackCaptureConfiguration` effective-policy resolver
 * computing the exact intersection of app manifest `allowAudioPlaybackCapture`, runtime
 * `AudioManager.allowedCapturePolicy`, `AudioAttributes.allowedCapturePolicy`, and `AudioAttributes.usage`.
 */
object AudioCapturePolicyClassifier {
    const val SCOPE =
        "Android API 29-37 AudioPlaybackCaptureConfiguration effective capture policy intersection (ALLOW_CAPTURE_BY_ALL / BY_SYSTEM / BY_NONE) and usage filter."

    const val ALLOW_CAPTURE_BY_ALL = 1
    const val ALLOW_CAPTURE_BY_SYSTEM = 2
    const val ALLOW_CAPTURE_BY_NONE = 3

    enum class UsageKind { USAGE_MEDIA, USAGE_GAME, USAGE_UNKNOWN, USAGE_VOICE_COMMUNICATION, USAGE_ALARM }

    data class EffectiveVerdict(
        val effectivePolicy: Int,
        val permittedForThirdPartyRecall: Boolean,
        val reason: String,
    )

    fun evaluate(
        manifestAllowsCapture: Boolean,
        targetSdk: Int,
        audioManagerPolicy: Int,
        streamAttributesPolicy: Int,
        usage: UsageKind,
        sameUserProfile: Boolean = true,
    ): EffectiveVerdict {
        if (!sameUserProfile) {
            return EffectiveVerdict(ALLOW_CAPTURE_BY_NONE, false, "Cross-profile audio capture forbidden by Android platform")
        }
        if (usage != UsageKind.USAGE_MEDIA && usage != UsageKind.USAGE_GAME && usage != UsageKind.USAGE_UNKNOWN) {
            return EffectiveVerdict(ALLOW_CAPTURE_BY_NONE, false, "Usage $usage cannot be captured via AudioPlaybackCapture")
        }
        val manifestFloor = when {
            !manifestAllowsCapture -> ALLOW_CAPTURE_BY_NONE
            targetSdk < 29 -> ALLOW_CAPTURE_BY_SYSTEM
            else -> ALLOW_CAPTURE_BY_ALL
        }
        // Strictest (numerically highest: 3 > 2 > 1) policy wins on Android!
        val effective = maxOf(manifestFloor, audioManagerPolicy.coerceIn(1, 3), streamAttributesPolicy.coerceIn(1, 3))
        val ok = effective == ALLOW_CAPTURE_BY_ALL
        val why = if (ok) "Permitted under ALLOW_CAPTURE_BY_ALL for $usage" else "Blocked by effective policy=$effective"
        return EffectiveVerdict(effective, ok, why)
    }
}
