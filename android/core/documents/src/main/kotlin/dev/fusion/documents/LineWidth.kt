package dev.fusion.documents

/**
 * Monospace display-column width (East Asian Width, UAX #11) for the
 * writer's plain-text and diff views.
 *
 * Classification subset implemented (code-point ranges from UAX #11's
 * data, the ranges used being cross-checked against Python's
 * unicodedata.east_asian_width — this kernel's held-out verifier):
 * width 2 for Wide/Fullwidth (CJK Unified Ideographs incl. Ext A,
 * Hiragana/Katakana, Hangul Jamo + Syllables, CJK symbols, fullwidth
 * forms, wide punctuation); width 0 for combining marks (0300..036F,
 * 1AB0..1AFF, 20D0..20FF) and zero-width characters (200B..200F,
 * FEFF); width 1 otherwise, including Halfwidth Katakana (FF61..FFDC
 * is Halfwidth = narrow) and AMBIGUOUS characters (resolved narrow,
 * declared — the terminal-emulator wide-ambiguous mode is a different
 * contract).
 *
 * Declared narrowings: the listed ranges only — unassigned planes
 * default to 1; control characters REFUSE (a width for `\u0007` is a
 * category error); no grapheme clustering (combining marks simply add
 * zero, which matches terminal behavior, not typography).
 */
object LineWidth {
    const val SCOPE = "UAX #11 subset: wide=2, combining/zero-width=0, ambiguous=narrow (declared); controls refuse."

    private val WIDE = listOf(
        0x1100..0x115F,   // Hangul Jamo leading
        0x2E80..0x303E,   // CJK Radicals .. CJK Symbols (3000-303E incl. ideographic space)
        0x3041..0x33FF,   // Hiragana .. CJK Compatibility
        0x3400..0x4DBF,   // CJK Ext A
        0x4E00..0x9FFF,   // CJK Unified
        0xA000..0xA4CF,   // Yi
        0xAC00..0xD7A3,   // Hangul Syllables
        0xF900..0xFAFF,   // CJK Compatibility Ideographs
        0xFE30..0xFE4F,   // CJK Compatibility Forms
        0xFF00..0xFF60,   // Fullwidth Forms
        0xFFE0..0xFFE6,   // Fullwidth signs
        0x20000..0x2FFFD, // CJK Ext B..
        0x30000..0x3FFFD
    )

    private val ZERO = listOf(
        0x0300..0x036F, 0x1AB0..0x1AFF, 0x20D0..0x20FF,
        0x200B..0x200F, 0xFEFF..0xFEFF
    )

    fun of(codePoint: Int): Int {
        require(codePoint in 0..0x10FFFF) { "not a code point: $codePoint" }
        require(!(codePoint < 0x20 || codePoint in 0x7F..0x9F)) {
            "control U+%04X has no display width — expand or strip it first".format(codePoint)
        }
        if (ZERO.any { codePoint in it }) return 0
        if (WIDE.any { codePoint in it }) return 2
        return 1
    }

    fun of(text: String): Int {
        var total = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            total += of(cp)
            i += Character.charCount(cp)
        }
        return total
    }

    /** Longest prefix fitting in [columns]; never splits a wide char. */
    fun fit(text: String, columns: Int): String {
        require(columns >= 0) { "negative columns" }
        var used = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val w = of(cp)
            if (used + w > columns) break
            used += w
            i += Character.charCount(cp)
        }
        return text.substring(0, i)
    }
}
