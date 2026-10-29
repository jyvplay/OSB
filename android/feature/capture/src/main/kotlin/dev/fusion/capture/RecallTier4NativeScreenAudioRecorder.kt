// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

import kotlin.math.abs

/**
 * **Recall Method 4 (Ultimate / Complex / Slow Native Screenrecorder + Internal Audio Siphoner):**
 * Handles difficult non-DRM websites and unusual/obfuscated media players where Tiers 1–3 cannot
 * extract raw network assets or MSE/WebAudio buffers directly.
 *
 * Enforces:
 * 1. Exact Android API 29–37 `AudioPlaybackCaptureConfiguration` + `WindowManager.LayoutParams.FLAG_SECURE`
 *    + EME DRM refusal policy (`ALLOW_CAPTURE_BY_ALL = 1`, `ALLOW_CAPTURE_BY_SYSTEM = 2`, `ALLOW_CAPTURE_BY_NONE = 3`);
 * 2. Even-macroblock (`YUV420p` `2x2`) browser-viewport crop geometry excluding system status/nav & tab chrome;
 * 3. Sample-accurate Bresenham / PLL audio-video presentation-timestamp (`PTS`) drift compensation keeping
 *    `|audioPtsUs - videoPtsUs| <= 20_000 us` (`20 ms`) across dropped/variable-rate screen frames!
 */
object RecallTier4NativeScreenAudioRecorder {
    const val SCOPE =
        "Recall Method 4 native viewport MediaProjection + AudioPlaybackCaptureConfiguration policy gate, even-YUV420p browser-viewport cropper, and <=20ms A/V PTS drift synchronizer (strictly refusing FLAG_SECURE, EME DRM, and ALLOW_CAPTURE_BY_NONE)."

    const val USAGE_UNKNOWN = 0
    const val USAGE_MEDIA = 1
    const val USAGE_VOICE_COMMUNICATION = 2
    const val USAGE_GAME = 14

    const val ALLOW_CAPTURE_BY_ALL = 1
    const val ALLOW_CAPTURE_BY_SYSTEM = 2
    const val ALLOW_CAPTURE_BY_NONE = 3

    const val MAX_AV_DRIFT_US = 20_000L

    data class NativeCapturePolicyRequest(
        val mediaProjectionUserConsented: Boolean,
        val windowFlagSecure: Boolean,
        val emeHardwareDrmActive: Boolean,
        val audioUsage: Int,
        val appAllowedCapturePolicy: Int,
        val manifestAllowAudioPlaybackCapture: Boolean?,
        val targetSdkVersion: Int,
        val sameAndroidUserProfile: Boolean,
    )

    sealed interface NativeCaptureGateVerdict {
        data class Permitted(
            val videoPermitted: Boolean,
            val internalAudioPermitted: Boolean,
            val audioNote: String,
        ) : NativeCaptureGateVerdict

        data class Refused(val code: String, val reason: String) : NativeCaptureGateVerdict
    }

    data class ViewportCropSpec(
        val displayWidthPx: Int,
        val displayHeightPx: Int,
        val topChromePx: Int,
        val bottomChromePx: Int,
        val leftInsetPx: Int = 0,
        val rightInsetPx: Int = 0,
    )

    data class EvenViewportRect(
        val x: Int,
        val y: Int,
        val widthEven: Int,
        val heightEven: Int,
        val suggestedBitrateBps: Long,
    )

    enum class FrameSyncAction { KEEP_FRAME, REPEAT_PREVIOUS_AND_KEEP, DROP_LATE_FRAME }

    data class SyncedFrameDecision(
        val inputIndex: Int,
        val action: FrameSyncAction,
        val emittedPtsUs: List<Long>,
        val residualDriftUs: Long,
    )

    fun evaluateCapturePolicy(req: NativeCapturePolicyRequest): NativeCaptureGateVerdict {
        if (!req.mediaProjectionUserConsented) {
            return NativeCaptureGateVerdict.Refused("NO_USER_MEDIA_PROJECTION_CONSENT", "Explicit MediaProjection user consent required")
        }
        if (req.windowFlagSecure) {
            return NativeCaptureGateVerdict.Refused("FLAG_SECURE_WINDOW_REFUSED", "Window carries FLAG_SECURE; screen capture strictly refused")
        }
        if (req.emeHardwareDrmActive) {
            return NativeCaptureGateVerdict.Refused("EME_DRM_SURFACE_REFUSED", "Protected Widevine/PlayReady/FairPlay surface cannot and will not be recorded")
        }
        if (!req.sameAndroidUserProfile) {
            return NativeCaptureGateVerdict.Refused("CROSS_PROFILE_CAPTURE_FORBIDDEN", "Work/personal cross-profile capture forbidden by Android AudioPlaybackCapture")
        }

        val usageAllowed = req.audioUsage == USAGE_MEDIA || req.audioUsage == USAGE_GAME || req.audioUsage == USAGE_UNKNOWN
        val manifestEffectiveAllow = req.manifestAllowAudioPlaybackCapture ?: (req.targetSdkVersion >= 29)
        val policyAll = req.appAllowedCapturePolicy == ALLOW_CAPTURE_BY_ALL

        return when {
            req.appAllowedCapturePolicy == ALLOW_CAPTURE_BY_NONE ->
                NativeCaptureGateVerdict.Refused("ALLOW_CAPTURE_BY_NONE_REFUSED", "Source explicitly forbids audio capture via ALLOW_CAPTURE_BY_NONE")
            !usageAllowed ->
                NativeCaptureGateVerdict.Permitted(videoPermitted = true, internalAudioPermitted = false, audioNote = "VIDEO_ONLY_NON_MEDIA_AUDIO_USAGE")
            !manifestEffectiveAllow || !policyAll ->
                NativeCaptureGateVerdict.Permitted(videoPermitted = true, internalAudioPermitted = false, audioNote = "VIDEO_ONLY_APP_RESTRICTED_AUDIO_POLICY")
            else ->
                NativeCaptureGateVerdict.Permitted(videoPermitted = true, internalAudioPermitted = true, audioNote = "FULL_VIEWPORT_VIDEO_AND_INTERNAL_PCM_AUDIO")
        }
    }

    fun computeBrowserViewportCrop(spec: ViewportCropSpec, targetFps: Int = 30): EvenViewportRect {
        require(spec.displayWidthPx >= 240 && spec.displayHeightPx >= 240 && targetFps in 15..60) {
            "Invalid display bounds or targetFps"
        }
        val rawW = spec.displayWidthPx - spec.leftInsetPx - spec.rightInsetPx
        val rawH = spec.displayHeightPx - spec.topChromePx - spec.bottomChromePx
        require(rawW >= 64 && rawH >= 64) { "Browser viewport crop collapsed ($rawW x $rawH)" }
        // Round down to even integers so H.264/VP9 4:2:0 chroma planes never fail MediaCodec configure()
        val evenX = spec.leftInsetPx and 1.inv()
        val evenY = spec.topChromePx and 1.inv()
        val evenW = rawW and 1.inv()
        val evenH = rawH and 1.inv()
        // Bits-per-pixel-per-frame 0.11 Kush gauge for crisp text/video browser capture
        val bitrate = (evenW.toLong() * evenH.toLong() * targetFps.toLong() * 11L / 100L).coerceIn(500_000L, 24_000_000L)
        return EvenViewportRect(evenX, evenY, evenW, evenH, bitrate)
    }

    fun synchronizeVideoFramesToAudioClock(
        videoFrameCaptureTimestampsUs: List<Long>,
        audioMasterClockSamplesAt48k: List<Long>,
        targetFps: Int = 30,
    ): List<SyncedFrameDecision> {
        require(videoFrameCaptureTimestampsUs.isNotEmpty() && audioMasterClockSamplesAt48k.size == videoFrameCaptureTimestampsUs.size) {
            "Video timestamps and audio sample counters must have matching non-empty length"
        }
        require(targetFps in 15..120) { "targetFps ($targetFps) must be in 15..120" }
        for (k in 1 until audioMasterClockSamplesAt48k.size) {
            require(audioMasterClockSamplesAt48k[k] >= audioMasterClockSamplesAt48k[k - 1]) {
                "Audio master PCM sample counter must be monotonic non-decreasing at index $k"
            }
        }

        fun slotPtsUs(slotIndex: Long): Long = (slotIndex * 1_000_000L) / targetFps.toLong()

        var nextSlotIndex = 0L
        var lastDisplayedPtsUs = 0L
        val decisions = ArrayList<SyncedFrameDecision>(videoFrameCaptureTimestampsUs.size)

        for (i in videoFrameCaptureTimestampsUs.indices) {
            val audioPtsUs = (audioMasterClockSamplesAt48k[i] * 1_000_000L) / 48_000L
            val candidatePtsUs = slotPtsUs(nextSlotIndex)
            val driftUs = audioPtsUs - candidatePtsUs // positive => video lagging behind audio; negative => next slot ahead of audio
            when {
                driftUs > MAX_AV_DRIFT_US -> {
                    // Browser render stall: emit catch-up frame(s) + current frame until within [-MAX_AV_DRIFT_US, +MAX_AV_DRIFT_US]
                    val emitted = ArrayList<Long>(4)
                    var guard = 0
                    while (guard < 16 && (audioPtsUs - slotPtsUs(nextSlotIndex)) > MAX_AV_DRIFT_US) {
                        val catchupPts = slotPtsUs(nextSlotIndex++)
                        emitted.add(catchupPts)
                        lastDisplayedPtsUs = catchupPts
                        guard++
                    }
                    // Emit current frame if it brings us closer to audioPtsUs without overshooting > MAX_AV_DRIFT_US
                    val postSlotPts = slotPtsUs(nextSlotIndex)
                    if (abs(audioPtsUs - postSlotPts) <= MAX_AV_DRIFT_US || emitted.isEmpty()) {
                        emitted.add(postSlotPts)
                        lastDisplayedPtsUs = postSlotPts
                        nextSlotIndex++
                    }
                    val residual = audioPtsUs - lastDisplayedPtsUs
                    decisions.add(SyncedFrameDecision(i, FrameSyncAction.REPEAT_PREVIOUS_AND_KEEP, emitted, residual))
                }
                driftUs < -MAX_AV_DRIFT_US && nextSlotIndex > 0L -> {
                    // Next video slot is >20ms ahead of audio -> drop incoming early frame; viewer still sees lastDisplayedPtsUs
                    val residual = audioPtsUs - lastDisplayedPtsUs
                    decisions.add(SyncedFrameDecision(i, FrameSyncAction.DROP_LATE_FRAME, emptyList(), residual))
                }
                else -> {
                    val pts = candidatePtsUs
                    nextSlotIndex++
                    lastDisplayedPtsUs = pts
                    val residual = audioPtsUs - lastDisplayedPtsUs
                    decisions.add(SyncedFrameDecision(i, FrameSyncAction.KEEP_FRAME, listOf(pts), residual))
                }
            }
        }
        return decisions
    }
}
