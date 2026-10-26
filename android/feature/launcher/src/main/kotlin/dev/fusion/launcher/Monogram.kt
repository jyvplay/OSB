package dev.fusion.launcher

/**
 * App-label monograms for launcher tiles.
 *
 * Deterministic rule: split the label on whitespace; take the first
 * character that is a Unicode letter or digit from each of the first
 * two such tokens; uppercase (locale-neutral). One qualifying token
 * yields one initial. No qualifying character anywhere → refusal, and
 * the launcher falls back to its generic glyph EXPLICITLY.
 *
 * Declared narrowings: BMP-first — the "first character" is the first
 * CODE POINT, so surrogate-pair emoji are skipped correctly but
 * combining sequences are not clustered (no grapheme segmentation; that
 * is declared, not hidden). No transliteration.
 */
object Monogram {
    const val SCOPE = "First code point letter/digit of up to two tokens; no grapheme clustering; refusal over guessing."

    private fun initialOf(token: String): String? {
        var i = 0
        while (i < token.length) {
            val cp = token.codePointAt(i)
            if (Character.isLetterOrDigit(cp)) {
                return String(Character.toChars(Character.toUpperCase(cp)))
            }
            i += Character.charCount(cp)
        }
        return null
    }

    fun of(label: String): String {
        val initials = label.split(Regex("\\s+")).filter { it.isNotEmpty() }
            .mapNotNull { initialOf(it) }
            .take(2)
        require(initials.isNotEmpty()) {
            "no letter or digit in label '${label.take(24)}' — caller must use the generic glyph"
        }
        return initials.joinToString("")
    }

    fun orNull(label: String): String? = try { of(label) } catch (_: IllegalArgumentException) { null }
}
