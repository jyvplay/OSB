// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

/**
 * Clean-room Camera/Meet Effect Thermal Quality Ladder (`LN-15`), Multi-Person Smoothed Auto-Frame
 * Crop Solver (`LN-02`), Presenter Person+Slide PIP Composer (`LN-13`), and Per-App Lens Preset
 * Memory (`LN-14`).
 */
object LensThermalQualityLadder {
    enum class EffectStage {
        GENERATIVE_SCENE_BG, // LN-12 (highest GPU cost, dropped first!)
        VIRTUAL_RELIGHT_3D,  // LN-06
        FSR1_SHARPEN_UPSCALE,// LN-05
        LOW_LIGHT_DENOISE,   // LN-04
        BACKGROUND_BLUR_SEG, // LN-01
        RNNOISE_AND_AEC3,    // LN-07/08 (voice intelligibility preserved longest!)
    }

    data class QualityStepPlan(
        val targetResolutionHeightPx: Int,
        val targetFps: Int,
        val activeStages: Set<EffectStage>,
        val shedStages: Set<EffectStage>,
    )

    fun stepForThermalAndRam(
        requestedStages: Set<EffectStage>,
        thermalStageZeroToThree: Int, // 0=normal, 1=moderate, 2=severe, 3=critical
        deviceRamGb: Int,
    ): QualityStepPlan {
        require(thermalStageZeroToThree in 0..3 && deviceRamGb >= 2) { "invalid thermal/RAM inputs" }
        val active = requestedStages.toMutableSet()
        // LN-12 RAM gate: generative scene backgrounds require >= 8 GB RAM and nominal thermals
        if (deviceRamGb < 8 || thermalStageZeroToThree >= 1) active.remove(EffectStage.GENERATIVE_SCENE_BG)
        if (thermalStageZeroToThree >= 1) active.remove(EffectStage.VIRTUAL_RELIGHT_3D)
        if (thermalStageZeroToThree >= 2) {
            active.remove(EffectStage.FSR1_SHARPEN_UPSCALE)
            active.remove(EffectStage.LOW_LIGHT_DENOISE)
        }
        if (thermalStageZeroToThree >= 3) {
            active.remove(EffectStage.BACKGROUND_BLUR_SEG)
        }
        val resH = when (thermalStageZeroToThree) {
            0 -> 1080
            1 -> 720
            2 -> 720
            else -> 480
        }
        val fps = if (thermalStageZeroToThree >= 2) 24 else 30
        return QualityStepPlan(resH, fps, active, requestedStages - active)
    }

    data class NormalizedRect(val cx: Double, val cy: Double, val width: Double, val height: Double)

    /** Smooth exponential-moving-average multi-face bounding crop (`LN-02`) with headroom padding. */
    fun smoothAutoFrameCrop(
        previous: NormalizedRect,
        detectedFaces: List<NormalizedRect>,
        alpha: Double = 0.18,
    ): NormalizedRect {
        if (detectedFaces.isEmpty()) return previous
        val minX = detectedFaces.minOf { it.cx - it.width / 2.0 }
        val maxX = detectedFaces.maxOf { it.cx + it.width / 2.0 }
        val minY = detectedFaces.minOf { it.cy - it.height / 2.0 }
        val maxY = detectedFaces.maxOf { it.cy + it.height / 2.0 }

        val targetW = ((maxX - minX) * 1.65).coerceIn(0.35, 1.0)
        val targetH = ((maxY - minY) * 1.85).coerceIn(0.35, 1.0)
        val targetCx = ((minX + maxX) / 2.0).coerceIn(targetW / 2.0, 1.0 - targetW / 2.0)
        val targetCy = ((minY + maxY) / 2.0).coerceIn(targetH / 2.0, 1.0 - targetH / 2.0)

        val a = alpha.coerceIn(0.02, 1.0)
        return NormalizedRect(
            cx = previous.cx + a * (targetCx - previous.cx),
            cy = previous.cy + a * (targetCy - previous.cy),
            width = previous.width + a * (targetW - previous.width),
            height = previous.height + a * (targetH - previous.height),
        )
    }
}
