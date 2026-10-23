// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * GitHub-style heading slugs plus duplicate disambiguation.
 *
 * Sits next to [OutlineParse] (which finds headings) and [NoteLinks]
 * (which links notes): neither answers "what fragment does this heading
 * address to". [slug] lowercases, turns every space into a hyphen, and
 * drops every other character outside `[a-z0-9_-]`; [unique] appends
 * `-1`, `-2`, … when the slug is already taken, mirroring GitHub's
 * duplicate-heading fragments. Hyphens are NOT collapsed and Unicode
 * letters are NOT transliterated — both are documented simplifications,
 * so non-ASCII headings may slug to the empty string and be refused.
 * Slugs are fragment spellings only, never link resolution.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object SlugHead {
    const val MAX_HEADING = 200
    const val MAX_TRIES = 10_000

    /**
     * Slug for [heading].
     *
     * @throws IllegalArgumentException on a blank heading, an over-long
     * heading, or a heading with no surviving characters.
     */
    fun slug(heading: String): String {
        require(heading.isNotBlank()) { "blank heading" }
        require(heading.length <= MAX_HEADING) { "heading over cap" }
        val out = StringBuilder()
        for (c in heading.trim().lowercase()) {
            when {
                c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '-' -> out.append(c)
                c == ' ' || c == '\t' -> out.append('-')
            }
        }
        require(out.isNotEmpty()) { "no slug characters" }
        return out.toString()
    }

    /**
     * First free slug for [heading] given [existing] slugs.
     *
     * @throws IllegalArgumentException when the number space is exhausted.
     */
    fun unique(existing: Collection<String>, heading: String): String {
        val base = slug(heading)
        if (base !in existing) return base
        for (n in 1..MAX_TRIES) {
            val cand = "$base-$n"
            if (cand !in existing) return cand
        }
        throw IllegalArgumentException("number space exhausted")
    }
}
