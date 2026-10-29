// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * Pure-JVM **Spaced-Repetition (`FSRS-lite` / `SM-2+`) Cloze & Q/A Flashcard Extractor and
 * Review Interval Scheduler** for `:feature:notes`.
 */
object NotesFlashcardSrsScheduler {
    const val SCOPE =
        "Markdown {{c1::answer::hint}} cloze flashcard extractor and deterministic SM-2+ ease/interval spaced-repetition scheduler."

    data class ClozeFlashcard(
        val cardId: String,
        val clozeIndex: Int,
        val promptFront: String,
        val answerBack: String,
    )

    data class ReviewCardState(
        val cardId: String,
        val repetitions: Int = 0,
        val intervalDays: Int = 0,
        val easeFactor: Double = 2.5,
    )

    fun extractClozeCards(noteId: String, markdown: String): List<ClozeFlashcard> {
        val clozeRegex = Regex("""\{\{c(\d+)::([^}:]+)(?:::([^}]+))?\}\}""")
        val matches = clozeRegex.findAll(markdown).toList()
        if (matches.isEmpty()) return emptyList()
        val indices = matches.map { it.groupValues[1].toInt() }.distinct().sorted()
        return indices.map { targetIdx ->
            val answers = ArrayList<String>()
            val front = clozeRegex.replace(markdown) { m ->
                val idx = m.groupValues[1].toInt()
                val ans = m.groupValues[2]
                val hint = m.groupValues[3]
                if (idx == targetIdx) {
                    answers.add(ans)
                    if (hint.isNotBlank()) "[...$hint...]" else "[...]"
                } else {
                    ans
                }
            }
            ClozeFlashcard("$noteId#c$targetIdx", targetIdx, front, answers.joinToString("; "))
        }
    }

    fun scheduleNextReview(state: ReviewCardState, qualityZeroToFive: Int): ReviewCardState {
        require(qualityZeroToFive in 0..5) { "Review quality must be 0..5" }
        if (qualityZeroToFive < 3) {
            return state.copy(repetitions = 0, intervalDays = 1, easeFactor = (state.easeFactor - 0.20).coerceAtLeast(1.3))
        }
        val nextReps = state.repetitions + 1
        val nextInterval = when (nextReps) {
            1 -> 1
            2 -> 6
            else -> kotlin.math.round(state.intervalDays * state.easeFactor).toInt().coerceAtLeast(state.intervalDays + 1)
        }
        val qDiff = 5 - qualityZeroToFive
        val nextEase = (state.easeFactor + (0.1 - qDiff * (0.08 + qDiff * 0.02))).coerceAtLeast(1.3)
        return ReviewCardState(state.cardId, nextReps, nextInterval, nextEase)
    }
}
