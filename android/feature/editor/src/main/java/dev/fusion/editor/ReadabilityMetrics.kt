// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Deterministic Writer readability, lexical-density, and prose-lint engine.
 *
 * Computes Flesch Reading Ease, Flesch-Kincaid Grade Level, Coleman-Liau Index, Gunning Fog Index,
 * estimated reading time (`225 wpm`), syllable counts, and detects adjacent repeated words
 * (`"the the"`) without network calls or external NLP models.
 */
object ReadabilityMetrics {
    const val SCOPE =
        "Deterministic Writer readability & prose lint engine (Flesch Reading Ease, Flesch-Kincaid Grade, Coleman-Liau Index, Gunning Fog, vowel-cluster syllable estimator, adjacent duplicate-word lint, 225-wpm reading time)."

    data class Report(
        val wordCount: Int,
        val sentenceCount: Int,
        val syllableCount: Int,
        val complexWordCount: Int,
        val fleschReadingEase: Double,
        val fleschKincaidGrade: Double,
        val colemanLiauIndex: Double,
        val gunningFogIndex: Double,
        val estimatedReadingSeconds: Int,
        val repeatedAdjacentWords: List<String>,
    )

    fun analyze(text: String): Report {
        val words = Regex("[A-Za-z0-9']+").findAll(text).map { it.value }.toList()
        if (words.isEmpty()) {
            return Report(0, 0, 0, 0, 100.0, 0.0, 0.0, 0.0, 0, emptyList())
        }
        val rawSentences = text.split(Regex("[.!?]+(?:\\s+|$)")).count { it.any { ch -> ch.isLetterOrDigit() } }
        val sentences = max(1, rawSentences)
        var syllables = 0
        var complexWords = 0
        var alphaChars = 0
        val duplicates = ArrayList<String>()
        var prevLower = ""

        for (w in words) {
            val clean = w.filter { it.isLetter() }
            alphaChars += clean.length
            val syl = countSyllables(clean)
            syllables += syl
            if (syl >= 3) complexWords++
            val low = clean.lowercase()
            if (low.isNotEmpty() && low == prevLower && low !in duplicates) {
                duplicates.add(low)
            }
            prevLower = low
        }

        val wPerS = words.size.toDouble() / sentences
        val sylPerW = syllables.toDouble() / words.size
        val fre = 206.835 - 1.015 * wPerS - 84.6 * sylPerW
        val fkg = 0.39 * wPerS + 11.8 * sylPerW - 15.59
        val lPer100 = (alphaChars.toDouble() / words.size) * 100.0
        val sPer100 = (sentences.toDouble() / words.size) * 100.0
        val cli = 0.0588 * lPer100 - 0.296 * sPer100 - 15.8
        val fog = 0.4 * (wPerS + 100.0 * (complexWords.toDouble() / words.size))
        val secs = max(1, ((words.size / 225.0) * 60.0).roundToInt())

        return Report(
            wordCount = words.size,
            sentenceCount = sentences,
            syllableCount = syllables,
            complexWordCount = complexWords,
            fleschReadingEase = fre,
            fleschKincaidGrade = max(0.0, fkg),
            colemanLiauIndex = max(0.0, cli),
            gunningFogIndex = max(0.0, fog),
            estimatedReadingSeconds = secs,
            repeatedAdjacentWords = duplicates,
        )
    }

    fun countSyllables(rawWord: String): Int {
        val w = rawWord.lowercase().filter { it in 'a'..'z' }
        if (w.isEmpty()) return 1
        if (w.length <= 3) return 1
        var trimmed = w
        if (trimmed.endsWith("e") && !trimmed.endsWith("le")) {
            trimmed = trimmed.dropLast(1)
        }
        val vowels = "aeiouy"
        var groups = 0
        var prevVowel = false
        for (c in trimmed) {
            val isV = c in vowels
            if (isV && !prevVowel) groups++
            prevVowel = isV
        }
        return max(1, groups)
    }
}
