package dev.fusion.documents

/**
 * Soft-hyphen (U+00AD SHY) handling for the writer surface.
 *
 * Primary semantics: Unicode UAX #14 — SHY is an invisible break
 * opportunity; it renders only when a line break is taken there, as a
 * visible hyphen. Searching, counting and export-for-diff must see the text
 * WITHOUT soft hyphens.
 *
 * Coordinates: break candidates are reported as indices into the STRIPPED
 * text (the position the visible hyphen would precede-split), because that
 * is the string every other writer kernel operates on.
 *
 * Declared narrowings: SHY only — no hyphenation dictionary, no other
 * UAX #14 classes; leading SHY and runs of SHY collapse to at most one
 * candidate per position, and a candidate at position 0 or text end is
 * dropped (nothing to split).
 */
object HyphenSoft {
    const val SHY = '\u00AD'
    const val SCOPE = "U+00AD handling only; no dictionary hyphenation; candidates in stripped-text coordinates."

    fun strip(text: String): String = text.filter { it != SHY }

    fun candidates(text: String): List<Int> {
        val out = ArrayList<Int>()
        var visible = 0
        for (c in text) {
            if (c == SHY) {
                if (visible > 0 && (out.isEmpty() || out.last() != visible)) out.add(visible)
            } else visible++
        }
        return out.filter { it < visible }
    }

    fun segments(text: String): List<String> {
        val stripped = strip(text)
        val cuts = candidates(text)
        val out = ArrayList<String>(cuts.size + 1)
        var prev = 0
        for (c in cuts) { out.add(stripped.substring(prev, c)); prev = c }
        out.add(stripped.substring(prev))
        return out
    }

    /** Render with a real hyphen-minus taken at [breakAt] (a candidate index). */
    fun display(text: String, breakAt: Int): Pair<String, String> {
        require(breakAt in candidates(text)) { "position $breakAt is not a soft-hyphen candidate" }
        val stripped = strip(text)
        return stripped.substring(0, breakAt) + "-" to stripped.substring(breakAt)
    }
}
