// SPDX-License-Identifier: Apache-2.0
package dev.fusion.castv2

/**
 * Clean-room Dual-Screen Presentation Speaker-Notes & Pacing Console Planner (`CA-11`):
 * - Internal device display renders Speaker Notes, Next-Slide Thumbnail, and Elapsed/Remaining
 *   Pacing Timer.
 * - External HDMI / Cast presentation sink receives ONLY the clean Audience Slide (zero private
 *   notes, notifications, or presenter cursor chrome!).
 */
object PresentationSpeakerNotesDualView {
    const val SCOPE =
        "Pure-JVM dual-display presenter console splitter isolating private speaker notes & pacing clock from the clean external audience surface (CA-11)."

    data class AudienceViewport(
        val displayWidthPx: Int,
        val displayHeightPx: Int,
        val slideBoxW: Int,
        val slideBoxH: Int,
        val letterboxPadX: Int,
        val letterboxPadY: Int,
        val slideIndex: Int,
        val containsSpeakerNotes: Boolean,
        val notificationsSuppressed: Boolean,
    )

    data class PresenterConsoleState(
        val currentSlideIndex: Int,
        val nextSlideIndex: Int?,
        val privateNotesMarkdown: String,
        val elapsedSec: Int,
        val targetTotalSec: Int,
        val pacingDeltaSec: Int, // negative = ahead of schedule, positive = behind schedule
    )

    fun planDualPresentation(
        slideIndex: Int,
        totalSlides: Int,
        speakerNotesBySlide: List<String>,
        extWidthPx: Int,
        extHeightPx: Int,
        aspectW: Int = 16,
        aspectH: Int = 9,
        elapsedSec: Int = 0,
        targetTotalSec: Int = 1200,
    ): Pair<AudienceViewport, PresenterConsoleState> {
        require(totalSlides >= 1 && slideIndex in 0 until totalSlides && speakerNotesBySlide.size == totalSlides) {
            "invalid slide index or notes deck size"
        }
        require(extWidthPx >= 320 && extHeightPx >= 240 && aspectW > 0 && aspectH > 0) { "invalid external display geometry" }

        val scale = minOf(extWidthPx.toDouble() / aspectW, extHeightPx.toDouble() / aspectH)
        val boxW = (aspectW * scale).toInt()
        val boxH = (aspectH * scale).toInt()
        val padX = (extWidthPx - boxW) / 2
        val padY = (extHeightPx - boxH) / 2

        val audience = AudienceViewport(
            displayWidthPx = extWidthPx,
            displayHeightPx = extHeightPx,
            slideBoxW = boxW,
            slideBoxH = boxH,
            letterboxPadX = padX,
            letterboxPadY = padY,
            slideIndex = slideIndex,
            containsSpeakerNotes = false,
            notificationsSuppressed = true,
        )

        val idealElapsed = ((slideIndex + 1).toDouble() / totalSlides * targetTotalSec).toInt()
        val console = PresenterConsoleState(
            currentSlideIndex = slideIndex,
            nextSlideIndex = (slideIndex + 1).takeIf { it < totalSlides },
            privateNotesMarkdown = speakerNotesBySlide[slideIndex],
            elapsedSec = elapsedSec,
            targetTotalSec = targetTotalSec,
            pacingDeltaSec = elapsedSec - idealElapsed,
        )
        return audience to console
    }
}
