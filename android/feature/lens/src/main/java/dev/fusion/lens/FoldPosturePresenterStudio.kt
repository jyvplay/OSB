// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

/**
 * Clean-room Lens Studio Posture, Multi-Lens Switcher, Critically-Damped Auto-Framing Spring,
 * Presenter Teleprompter/Spotlight Overlay, and Kill-Switch Shutter State Machine
 * (`LN-01`, `LN-02`, `LN-03`, `LN-04`, `LN-07`, `LN-13`, `LN-14`).
 */
object FoldPosturePresenterStudio {
    const val SCOPE =
        "Pure-JVM Camera2 multi-lens focal switcher, foldable tabletop/tent/book viewport mapper, critically-damped face-framing spring, presenter teleprompter/spotlight, and kill-switch shutter guard (LN-01..04, LN-07, LN-13, LN-14)."

    enum class HingePosture { FLAT_HANDHELD, TABLETOP_HALF_OPEN, TENT_PRESENTATION, BOOK_SPLIT, DESKTOP_DOCKED }
    enum class LensOptic(val focalEqMm: Int) { FRONT_WIDE(24), REAR_ULTRAWIDE(13), REAR_PRIMARY(26), REAR_TELEPHOTO(70), USB_UVC_EXTERNAL(35) }

    data class FramingBox(val centerXNorm: Double, val centerYNorm: Double, val zoomFactor: Double)

    data class StudioPosturePlan(
        val posture: HingePosture,
        val activeOptic: LensOptic,
        val previewViewportFractionH: Double,
        val controlsDeckBottomHalf: Boolean,
        val cameraPrivacyIndicatorVisible: Boolean,
        val micPrivacyIndicatorVisible: Boolean,
        val feedMutedBlackFrame: Boolean,
    )

    fun resolveStudioPosture(
        hingeAngleDeg: Double,
        externalDisplayDocked: Boolean,
        requestedOptic: LensOptic,
        hardwareCamKillSwitchClosed: Boolean,
        softwareCamMuted: Boolean,
        micActive: Boolean,
    ): StudioPosturePlan {
        require(hingeAngleDeg in 0.0..360.0) { "invalid hinge angle $hingeAngleDeg" }
        val posture = when {
            externalDisplayDocked -> HingePosture.DESKTOP_DOCKED
            hingeAngleDeg in 75.0..135.0 -> HingePosture.TABLETOP_HALF_OPEN
            hingeAngleDeg in 225.0..320.0 -> HingePosture.TENT_PRESENTATION
            hingeAngleDeg in 135.01..168.0 -> HingePosture.BOOK_SPLIT
            else -> HingePosture.FLAT_HANDHELD
        }
        val blackedOut = hardwareCamKillSwitchClosed || softwareCamMuted
        val optic = if (posture == HingePosture.TENT_PRESENTATION && requestedOptic == LensOptic.FRONT_WIDE) {
            LensOptic.REAR_PRIMARY
        } else {
            requestedOptic
        }
        return StudioPosturePlan(
            posture = posture,
            activeOptic = optic,
            previewViewportFractionH = if (posture == HingePosture.TABLETOP_HALF_OPEN) 0.50 else 1.0,
            controlsDeckBottomHalf = posture == HingePosture.TABLETOP_HALF_OPEN,
            cameraPrivacyIndicatorVisible = !blackedOut,
            micPrivacyIndicatorVisible = micActive,
            feedMutedBlackFrame = blackedOut,
        )
    }

    /**
     * Smooth critically-damped auto-framing pan/zoom step (`LN-04`) preventing seasick jitter.
     */
    fun stepAutoFrameSpring(
        current: FramingBox,
        detectedFaceCenterX: Double,
        detectedFaceCenterY: Double,
        faceHeightFraction: Double,
        alpha: Double = 0.18,
    ): FramingBox {
        require(alpha in 0.01..1.0) { "alpha outside 0.01..1.0" }
        val targetZoom = (0.34 / faceHeightFraction.coerceIn(0.08, 0.85)).coerceIn(1.0, 2.8)
        val nx = (current.centerXNorm + alpha * (detectedFaceCenterX.coerceIn(0.15, 0.85) - current.centerXNorm)).coerceIn(0.15, 0.85)
        val ny = (current.centerYNorm + alpha * (detectedFaceCenterY.coerceIn(0.15, 0.85) - current.centerYNorm)).coerceIn(0.15, 0.85)
        val nz = (current.zoomFactor + alpha * (targetZoom - current.zoomFactor)).coerceIn(1.0, 2.8)
        return FramingBox(nx, ny, nz)
    }

    fun computeTeleprompterScrollLine(scriptLines: List<String>, wordsPerMinute: Int, elapsedSeconds: Double): Pair<Int, String> {
        require(scriptLines.isNotEmpty() && wordsPerMinute in 60..320 && elapsedSeconds >= 0.0) { "invalid teleprompter params" }
        val targetWords = (wordsPerMinute / 60.0) * elapsedSeconds
        var accWords = 0
        for ((idx, line) in scriptLines.withIndex()) {
            accWords += line.trim().split(Regex("\\s+")).count { it.isNotEmpty() }.coerceAtLeast(1)
            if (accWords >= targetWords) return idx to line
        }
        return scriptLines.lastIndex to scriptLines.last()
    }
}
