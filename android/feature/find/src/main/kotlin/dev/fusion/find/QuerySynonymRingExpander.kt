// SPDX-License-Identifier: Apache-2.0
package dev.fusion.find

/**
 * R146 — bounded synonym-ring query expander for local search.
 *
 * Takes a list of synonym rings (each ring is an unordered group of terms
 * that may be substituted for one another) and a whitespace-separated query,
 * and rewrites each query term as the term itself followed by its ring
 * partners in first-seen ring order, de-duplicating globally while preserving
 * first occurrence. Expansion never invents terms: only terms present in a
 * ring are added.
 *
 * Declared narrowings: at most [MAX_RINGS] rings; at most [MAX_RING_SIZE]
 * terms per ring; at most [MAX_QUERY_TERMS] query terms; every term
 * `[a-z0-9_-]{1,32}` after lowercase normalisation (uppercase input is
 * folded); at most [MAX_OUTPUT_TERMS] emitted terms — exceeding it refuses
 * instead of truncating so a caller never sees a silently shortened query.
 * Overlapping rings are allowed; the first ring (input order) that contains
 * a term defines its partners.
 */
object QuerySynonymRingExpander {
    const val SCOPE =
        "Deterministic synonym-ring expansion with global de-duplication and output cap refusal; no embeddings, no network."

    const val MAX_RINGS = 256
    const val MAX_RING_SIZE = 8
    const val MAX_QUERY_TERMS = 16
    const val MAX_OUTPUT_TERMS = 64
    const val MAX_TERM = 32

    private val TERM_RE = Regex("[a-z0-9_-]{1,32}")

    data class Ring(val partners: List<String>)

    fun parseRings(spec: String): List<Ring> {
        require(spec.isNotEmpty()) { "empty ring spec" }
        val raw = spec.split('|')
        require(raw.size <= MAX_RINGS) { "too many rings: $MAX_RINGS" }
        return raw.map { ringRaw ->
            val terms = ringRaw.split(',')
            require(terms.size in 1..MAX_RING_SIZE) { "ring size out of 1..$MAX_RING_SIZE" }
            val norm = terms.map { it.lowercase() }
            for (t in norm) require(TERM_RE.matches(t)) { "bad ring term: $t" }
            require(norm.toSet().size == norm.size) { "duplicate term inside a ring" }
            Ring(norm)
        }
    }

    fun expand(ringSpec: String, query: String): String {
        val rings = parseRings(ringSpec)
        val terms = if (query.isBlank()) emptyList() else query.trim().split(Regex("\\s+"))
        require(terms.size <= MAX_QUERY_TERMS) { "too many query terms: $MAX_QUERY_TERMS" }
        for (t in terms) {
            val norm = t.lowercase()
            require(TERM_RE.matches(norm)) { "bad query term: $t" }
        }
        val out = LinkedHashSet<String>()
        for (raw in terms) {
            val term = raw.lowercase()
            out.add(term)
            val ring = rings.firstOrNull { term in it.partners } ?: continue
            for (partner in ring.partners) {
                out.add(partner)
                require(out.size <= MAX_OUTPUT_TERMS) { "expansion over $MAX_OUTPUT_TERMS terms" }
            }
            require(out.size <= MAX_OUTPUT_TERMS) { "expansion over $MAX_OUTPUT_TERMS terms" }
        }
        return out.joinToString(" ")
    }
}
