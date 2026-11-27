// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * R146 — hierarchical note-tag index (extends the flat
 * [NoteTagNormalizer] grammar with `/`-separated paths).
 *
 * A tag path is `segment("/" segment)*`. The hierarchy is derived purely
 * from paths: `project/alpha` has ancestor `project`, and a prefix query
 * returns the prefix itself plus every tag that extends it. Because paths
 * define the tree, structural cycles are impossible by construction — the
 * refusals here are grammar and bound violations, which keeps the kernel
 * honest instead of pretending to detect what cannot occur.
 *
 * Declared narrowings: at most [MAX_TAGS] tags; at most [MAX_SEGMENTS]
 * segments per tag; each segment `[a-z0-9_-]{1,64}` after lowercase
 * normalisation (leading `#` and surrounding whitespace stripped, mirroring
 * [NoteTagNormalizer]); duplicate tags after normalisation are refused;
 * ancestor/descendant queries on unknown tags return `!MISSING` (a query
 * miss, not a refusal of the whole index).
 */
object NoteTagHierarchy {
    const val SCOPE =
        "Path-derived note tag hierarchy: ancestors, prefix descendants, validation; no file IO, no aliasing."

    const val MAX_TAGS = 128
    const val MAX_SEGMENTS = 8
    const val MAX_SEGMENT_LEN = 64

    private val SEGMENT_RE = Regex("[a-z0-9_-]{1,64}")

    data class TagPath(val segments: List<String>) {
        val text: String get() = segments.joinToString("/")
    }

    fun normalizeTag(raw: String): String {
        val t = raw.trim().trimStart('#').lowercase()
        require(t.isNotEmpty()) { "empty tag" }
        val segments = t.split('/')
        require(segments.size <= MAX_SEGMENTS) { "too many segments" }
        for (s in segments) {
            require(s.length <= MAX_SEGMENT_LEN) { "segment too long" }
            require(SEGMENT_RE.matches(s)) { "bad tag segment: $s" }
        }
        return segments.joinToString("/")
    }

    fun parseCorpus(rawTags: List<String>): List<TagPath> {
        require(rawTags.size <= MAX_TAGS) { "too many tags: $MAX_TAGS" }
        val seen = HashSet<String>()
        return rawTags.map { raw ->
            val norm = normalizeTag(raw)
            require(seen.add(norm)) { "duplicate tag: $norm" }
            TagPath(norm.split('/'))
        }
    }

    /** `anc` lane: root→tag inclusive, `!MISSING` for unknown tags. */
    fun ancestors(corpus: List<TagPath>, tag: String): String {
        val norm = normalizeTag(tag)
        val match = corpus.firstOrNull { it.text == norm } ?: return "!MISSING"
        // Prefix chain joined with ",": `a,a/b,a/b/c` — the separator keeps
        // each ancestor addressable as a tag in its own right.
        return (1..match.segments.size).joinToString(",") { i ->
            match.segments.subList(0, i).joinToString("/")
        }
    }

    /**
     * `desc` lane: the prefix itself (if present) plus every tag strictly
     * extending it, sorted lexicographically, `!MISSING` if no tag equals or
     * extends the prefix.
     */
    fun descendants(corpus: List<TagPath>, prefix: String): String {
        val norm = normalizeTag(prefix)
        val hits = corpus.map { it.text }
            .filter { it == norm || it.startsWith("$norm/") }
            .sorted()
        if (hits.isEmpty()) return "!MISSING"
        return hits.joinToString(",")
    }

    /** `validate` lane: publishes the canonical corpus or refuses it. */
    fun validate(corpus: List<TagPath>): String =
        corpus.joinToString(",") { it.text }
}
