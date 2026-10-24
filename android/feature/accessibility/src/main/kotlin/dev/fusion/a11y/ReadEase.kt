package dev.fusion.a11y

/**
 * Flesch Reading Ease and Flesch-Kincaid Grade Level from COUNTS
 * (Flesch 1948 constants 206.835 / 1.015 / 84.6; Kincaid 1975
 * constants 0.39 / 11.8 / 15.59).
 *
 * Inputs are word, sentence and syllable COUNTS supplied by the
 * caller. Syllable counting in English is genuinely heuristic —
 * putting it inside would smuggle an unverifiable step into a verified
 * kernel, so it stays outside and this KDoc says so. The arithmetic
 * and the published banding are what this kernel vouches for.
 *
 * Declared narrowings: English-calibrated formulas only (the constants
 * ARE the English calibration); no text processing; scores are not
 * clamped — genuine formula outputs outside 0..100 are reported as-is
 * with the band `OUT_OF_RANGE_*` rather than hidden.
 */
object ReadEase {
    const val SCOPE = "Published formula arithmetic over caller counts; syllable counting stays outside, stated."

    enum class Band { VERY_EASY, EASY, FAIRLY_EASY, STANDARD, FAIRLY_DIFFICULT, DIFFICULT, VERY_CONFUSING, OUT_OF_RANGE_HIGH, OUT_OF_RANGE_LOW }

    fun fleschEase(words: Int, sentences: Int, syllables: Int): Double {
        require(words > 0 && sentences > 0 && syllables > 0) { "counts must be positive" }
        require(syllables >= words) { "every word has at least one syllable ($syllables < $words)" }
        return 206.835 - 1.015 * (words.toDouble() / sentences) - 84.6 * (syllables.toDouble() / words)
    }

    fun kincaidGrade(words: Int, sentences: Int, syllables: Int): Double {
        require(words > 0 && sentences > 0 && syllables > 0) { "counts must be positive" }
        require(syllables >= words) { "every word has at least one syllable" }
        return 0.39 * (words.toDouble() / sentences) + 11.8 * (syllables.toDouble() / words) - 15.59
    }

    /** The published 1949 bands, with out-of-range reported, not clamped. */
    fun band(score: Double): Band = when {
        score > 100.0 -> Band.OUT_OF_RANGE_HIGH
        score >= 90.0 -> Band.VERY_EASY
        score >= 80.0 -> Band.EASY
        score >= 70.0 -> Band.FAIRLY_EASY
        score >= 60.0 -> Band.STANDARD
        score >= 50.0 -> Band.FAIRLY_DIFFICULT
        score >= 30.0 -> Band.DIFFICULT
        score >= 0.0 -> Band.VERY_CONFUSING
        else -> Band.OUT_OF_RANGE_LOW
    }
}
