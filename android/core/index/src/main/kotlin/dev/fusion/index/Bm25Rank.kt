// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

import kotlin.math.ln

/**
 * Clean-room Okapi BM25 relevance scorer for `:core:index` (complements `Trigram`,
 * `Levenshtein`, and `SearchQuery`).
 *
 * Uses the Lucene-variant non-negative Robertson-Spärck Jones IDF:
 *   `idf(t) = ln(1 + (N - df(t) + 0.5) / (df(t) + 0.5))`
 * and length-normalized term saturation:
 *   `tfNorm(t, d) = (tf * (k1 + 1)) / (tf + k1 * (1 - b + b * (|d| / avgdl)))`
 * so ubiquitous terms (`df == N`) never produce negative IDF inversions and empty/zero-TF
 * documents strictly score `0.0`.
 */
object Bm25Rank {
    const val SCOPE =
        "Okapi BM25 ranking over caller-tokenized documents with non-negative RSJ IDF, configurable k1/b, and deterministic docId tie-breaking; stemming and phrase proximity out of scope."

    const val MAX_DOCS = 65_536
    const val MAX_QUERY_TERMS = 128

    data class Params(val k1: Double = 1.2, val b: Double = 0.75) {
        init {
            require(k1.isFinite() && k1 >= 0.0) { "k1=$k1 must be finite and >= 0" }
            require(b.isFinite() && b in 0.0..1.0) { "b=$b must be in 0.0..1.0" }
        }
    }

    data class DocTokens(val docId: String, val tokens: List<String>) {
        init {
            require(docId.isNotEmpty()) { "docId must not be empty" }
        }
    }

    data class ScoredDoc(val docId: String, val score: Double, val matchedTerms: Int)

    fun rank(
        corpus: List<DocTokens>,
        queryTerms: List<String>,
        params: Params = Params(),
        limit: Int = corpus.size,
    ): List<ScoredDoc> {
        require(corpus.size <= MAX_DOCS) { "corpus exceeds $MAX_DOCS documents" }
        require(queryTerms.size <= MAX_QUERY_TERMS) { "query exceeds $MAX_QUERY_TERMS terms" }
        require(limit >= 0) { "limit $limit must be >= 0" }
        if (corpus.isEmpty() || limit == 0) return emptyList()

        val seenIds = HashSet<String>(corpus.size)
        val termFreqs = ArrayList<Map<String, Int>>(corpus.size)
        val docFreq = HashMap<String, Int>()
        var totalTokens = 0L

        for (doc in corpus) {
            require(seenIds.add(doc.docId)) { "duplicate docId '${doc.docId}'" }
            val tf = HashMap<String, Int>()
            for (raw in doc.tokens) {
                val norm = raw.lowercase()
                if (norm.isEmpty()) continue
                tf[norm] = (tf[norm] ?: 0) + 1
                totalTokens++
            }
            for (term in tf.keys) {
                docFreq[term] = (docFreq[term] ?: 0) + 1
            }
            termFreqs.add(tf)
        }

        val uniqueQuery = queryTerms.map { it.lowercase() }.filter { it.isNotEmpty() }.distinct()
        if (uniqueQuery.isEmpty()) return emptyList()

        val nDocs = corpus.size.toDouble()
        val avgDl = if (totalTokens == 0L) 1.0 else totalTokens.toDouble() / nDocs

        val results = ArrayList<ScoredDoc>()
        for ((idx, doc) in corpus.withIndex()) {
            val tfMap = termFreqs[idx]
            val docLen = tfMap.values.sum().toDouble()
            var score = 0.0
            var matched = 0
            for (q in uniqueQuery) {
                val tf = tfMap[q] ?: 0
                if (tf <= 0) continue
                matched++
                val df = (docFreq[q] ?: 0).toDouble()
                val idf = ln(1.0 + (nDocs - df + 0.5) / (df + 0.5))
                val normDenom = tf + params.k1 * (1.0 - params.b + params.b * (docLen / avgDl))
                val tfSat = if (normDenom == 0.0) 0.0 else (tf * (params.k1 + 1.0)) / normDenom
                score += idf * tfSat
            }
            if (matched > 0 && score > 0.0) {
                results.add(ScoredDoc(doc.docId, score, matched))
            }
        }
        results.sortWith(compareByDescending<ScoredDoc> { it.score }.thenBy { it.docId })
        return if (limit < results.size) results.subList(0, limit) else results
    }
}
