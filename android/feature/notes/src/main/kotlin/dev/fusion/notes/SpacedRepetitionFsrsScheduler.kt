package dev.fusion.notes

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Clean-room `FSRS`-Inspired Memory Stability (`S`) & Difficulty (`D`) Spaced-Repetition Scheduler
 * + Markdown `{{c1::answer::hint}}` Cloze Card Extractor (Apache-2.0).
 */
object SpacedRepetitionFsrsScheduler {
    const val SCOPE =
        "FSRS memory-stability/difficulty spaced-repetition interval scheduler + {{c1::answer}} Markdown cloze extractor"

    enum class ReviewGrade(val code: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

    data class CardMemoryState(
        val stabilityDays: Double,
        val difficulty: Double, // 1.0 .. 10.0
        val nextIntervalDays: Int,
        val reps: Int,
    )

    data class ClozePrompt(val clozeIndex: Int, val questionMarkdown: String, val answer: String)

    private val CLOZE_RE = Regex("\\{\\{c([1-9][0-9]*)::([^:}]+)(?:::([^}]+))?\\}\\}")

    fun scheduleReview(
        previous: CardMemoryState?,
        grade: ReviewGrade,
        elapsedDays: Double = previous?.nextIntervalDays?.toDouble() ?: 0.0,
        desiredRetention: Double = 0.90,
    ): CardMemoryState {
        require(desiredRetention in 0.70..0.99)
        if (previous == null) {
            val initS = when (grade) {
                ReviewGrade.AGAIN -> 0.4
                ReviewGrade.HARD -> 1.2
                ReviewGrade.GOOD -> 3.1
                ReviewGrade.EASY -> 8.5
            }
            val initD = (7.2 - 0.8 * (grade.code - 1)).coerceIn(1.0, 10.0)
            return CardMemoryState(initS, initD, intervalFromStability(initS, desiredRetention), 1)
        }

        val retrievability = (1.0 + elapsedDays / (9.0 * previous.stabilityDays)).pow(-1.0)
        val newD = (previous.difficulty - 0.55 * (grade.code - 3)).coerceIn(1.0, 10.0)
        val newS = if (grade == ReviewGrade.AGAIN) {
            (previous.stabilityDays * 0.28).coerceAtLeast(0.25)
        } else {
            val hardPenalty = if (grade == ReviewGrade.HARD) 0.78 else 1.0
            val easyBonus = if (grade == ReviewGrade.EASY) 1.35 else 1.0
            val growth = 1.0 + kotlin.math.exp(1.6) * (11.0 - newD) * previous.stabilityDays.pow(-0.18) *
                (kotlin.math.exp(0.9 * (1.0 - retrievability)) - 1.0) * hardPenalty * easyBonus * 0.12
            previous.stabilityDays * growth
        }
        return CardMemoryState(newS, newD, intervalFromStability(newS, desiredRetention), previous.reps + 1)
    }

    fun extractClozeCards(noteMarkdown: String): List<ClozePrompt> {
        val matches = CLOZE_RE.findAll(noteMarkdown).toList()
        val indices = matches.map { it.groupValues[1].toInt() }.distinct().sorted()
        return indices.map { targetIdx ->
            val answers = mutableListOf<String>()
            val q = CLOZE_RE.replace(noteMarkdown) { m ->
                val idx = m.groupValues[1].toInt()
                val ans = m.groupValues[2]
                val hint = m.groupValues[3]
                if (idx == targetIdx) {
                    answers.add(ans)
                    if (hint.isEmpty()) "[...]" else "[...:$hint]"
                } else {
                    ans
                }
            }
            ClozePrompt(targetIdx, q, answers.joinToString("; "))
        }
    }

    private fun intervalFromStability(s: Double, r: Double): Int =
        (9.0 * s * (r.pow(-1.0) - 1.0)).roundToInt().coerceAtLeast(1)
}
