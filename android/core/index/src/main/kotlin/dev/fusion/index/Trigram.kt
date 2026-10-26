package dev.fusion.index

/**
 * Character trigrams with pg_trgm-compatible padding for fuzzy index
 * lookup (PostgreSQL pg_trgm convention: each word is padded with two
 * leading spaces and one trailing space before extracting 3-grams;
 * input is lowercased; non-alphanumerics separate words).
 *
 * Similarity is set-based Jaccard: |shared| / |union| — 1.0 for equal
 * trigram sets, 0.0 for disjoint. Distinct from :feature:find's
 * FuzzyScore (subsequence scoring) — different algebra, different
 * module, and this one feeds INDEX candidate retrieval, not ranking.
 *
 * Declared narrowings: ASCII-lowercase folding only (locale-correct
 * case folding is a separate decision); words shorter than 1 char
 * contribute nothing; similarity over empty sets refuses rather than
 * defining 0/0.
 */
object Trigram {
    const val SCOPE = "pg_trgm-style padded trigrams + Jaccard; ASCII folding; retrieval not ranking."

    fun words(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    fun ofWord(word: String): Set<String> {
        require(word.isNotEmpty()) { "empty word" }
        require(word.all { it.isLetterOrDigit() }) { "ofWord takes a single tokenized word" }
        val padded = "  ${word.lowercase()} "
        return (0..padded.length - 3).map { padded.substring(it, it + 3) }.toSet()
    }

    fun of(text: String): Set<String> =
        words(text).flatMap { ofWord(it) }.toSet()

    fun similarity(a: String, b: String): Double {
        val ta = of(a); val tb = of(b)
        require(ta.isNotEmpty() && tb.isNotEmpty()) { "similarity undefined over empty trigram sets" }
        val shared = ta.intersect(tb).size
        val union = ta.union(tb).size
        return shared.toDouble() / union
    }

    /** Candidate filter: true when similarity meets the threshold. */
    fun matches(query: String, candidate: String, threshold: Double = 0.3): Boolean {
        require(threshold in 0.0..1.0) { "threshold $threshold" }
        return similarity(query, candidate) >= threshold
    }
}
