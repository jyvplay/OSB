// SPDX-License-Identifier: Apache-2.0
package dev.fusion.airplay

/**
 * Pure-JVM **Presenter Teleprompter & Rehearsal Pacing Variance Analyzer** for `:feature:slides`,
 * calculating slide-by-slide cumulative schedule budgets, live ahead/behind variance, and
 * words-per-minute (`WPM`) speaker-note pacing cues.
 */
object SlideRehearsalPacingCoach {
    const val SCOPE =
        "Presentation rehearsal timer, per-slide speaker-notes WPM budget allocator, and live ahead/behind schedule drift coach."

    data class SlideScript(
        val slideNumber: Int,
        val speakerNotes: String,
        val explicitTargetSeconds: Int? = null,
    )

    data class PacingReport(
        val targetDeckDurationSec: Int,
        val slideBudgetsSec: List<Int>,
        val actualElapsedSec: Int,
        val varianceSec: Int, // positive = over budget (behind), negative = ahead of schedule
        val observedWordsPerMinute: Double,
        val pacingStatus: String, // ON_TRACK, TOO_FAST, BEHIND_SCHEDULE
    )

    fun evaluateRehearsal(
        slides: List<SlideScript>,
        actualDurationsSec: List<Int>,
        targetWpm: Int = 140,
    ): PacingReport {
        require(slides.isNotEmpty() && actualDurationsSec.size == slides.size && targetWpm in 80..220) {
            "Matching non-empty slides and actualDurationsSec required"
        }
        val budgets = slides.map { s ->
            s.explicitTargetSeconds ?: run {
                val words = Regex("\\S+").findAll(s.speakerNotes).count()
                ((words * 60 + targetWpm - 1) / targetWpm).coerceAtLeast(15)
            }
        }
        val totalBudget = budgets.sum()
        val totalActual = actualDurationsSec.sum()
        val totalWords = slides.sumOf { Regex("\\S+").findAll(it.speakerNotes).count() }
        val wpm = if (totalActual > 0) (totalWords.toDouble() * 60.0) / totalActual.toDouble() else 0.0
        val variance = totalActual - totalBudget
        val status = when {
            variance > 15 && variance.toDouble() / totalBudget.toDouble() > 0.10 -> "BEHIND_SCHEDULE"
            variance < -15 && -variance.toDouble() / totalBudget.toDouble() > 0.15 -> "TOO_FAST"
            else -> "ON_TRACK"
        }
        return PacingReport(totalBudget, budgets, totalActual, variance, wpm, status)
    }
}
