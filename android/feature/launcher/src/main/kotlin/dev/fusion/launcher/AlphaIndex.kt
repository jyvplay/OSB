package dev.fusion.launcher

/**
 * Fast-scroll alphabet bucketing (distinct from Monogram: that kernel
 * renders tile INITIALS; this one assigns list-index BUCKETS A..Z
 * plus '#'): the bucket is the uppercase first Unicode LETTER A..Z of
 * the label; digits and everything else bucket to '#'; non-Latin
 * letters bucket to '#' as a DECLARED narrowing (locale-correct
 * indexing is ICU work, named not faked).
 *
 * Declared narrowings above are the kernel; buckets are stable so
 * section headers never move under sort.
 */
object AlphaIndex {
    const val SCOPE = "A-Z/# bucketing, Latin-only declared; ICU locale indexing named, not faked."

    fun bucket(label: String): Char {
        require(label.isNotBlank()) { "blank label" }
        for (ch in label) {
            if (ch.isLetter()) {
                val up = ch.uppercaseChar()
                return if (up in 'A'..'Z') up else '#'
            }
            if (ch.isDigit()) return '#'
        }
        return '#'
    }

    fun sections(labels: List<String>): Map<Char, List<String>> {
        val out = LinkedHashMap<Char, MutableList<String>>()
        for (l in labels.sortedBy { it.lowercase() }) out.getOrPut(bucket(l)) { ArrayList() }.add(l)
        return out
    }

    fun sectionOrder(sections: Set<Char>): List<Char> =
        sections.sortedWith(compareBy { if (it == '#') 'Z' + 1 else it })
}
