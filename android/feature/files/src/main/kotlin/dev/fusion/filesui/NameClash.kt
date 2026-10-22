// SPDX-License-Identifier: Apache-2.0
package dev.fusion.filesui

/**
 * Copy-name disambiguation ("name (2).txt") over a directory listing.
 *
 * Sits next to [FileSort] (ordering) and [DupGroup] (identity grouping):
 * neither answers "what should the copy be CALLED". Given the existing
 * names and a desired name, this returns the desired name when free,
 * otherwise appends ` (2)`, ` (3)`, … before the extension (or at the end
 * when there is no extension), taking the LOWEST free number so gaps left
 * by deletions are reused deterministically. A name that already ends in
 * ` (N)` keeps its stem — copies of copies still group visually. Matching
 * is exact and case-sensitive (the filesystem's job, not this kernel's,
 * to define case folding); blank names and a full number space are
 * refused.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object NameClash {
    const val MAX_TRIES = 10_000

    private val NUMBERED = Regex("^(.*) \\((\\d+)\\)$")

    /**
     * Returns the first free name for [want] given [existing] names.
     *
     * @throws IllegalArgumentException on a blank name or an exhausted
     * number space.
     */
    fun nextName(existing: Collection<String>, want: String): String {
        require(want.isNotBlank()) { "blank name" }
        if (want !in existing) return want
        val dot = want.lastIndexOf('.')
        val (stem, ext) = if (dot > 0) want.substring(0, dot) to want.substring(dot) else want to ""
        val base = NUMBERED.matchEntire(stem)?.groupValues?.get(1) ?: stem
        for (n in 2..MAX_TRIES) {
            val cand = "$base ($n)$ext"
            if (cand !in existing) return cand
        }
        throw IllegalArgumentException("number space exhausted")
    }
}
