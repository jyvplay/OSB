// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

import java.util.Locale
import kotlin.math.ln

/**
 * Clean-room Robertson-Zaragoza Okapi BM25 probabilistic full-text ranking kernel (`Bm25Scorer`).
 */
object Bm25Scorer {
    const val SCOPE =
        "Okapi BM25 (k1=1.2, b=0.75, Robertson-Sparck-Jones smoothed IDF) local full-text document ranker; learned neural rerankers out of scope."

    data class DocInput(val id: String, val text: String)
    data class ScoredHit(val id: String, val score: Double)

    class Corpus internal constructor(
        private val docLengths: Map<String, Int>,
        private val avgDocLen: Double,
        private val postings: Map<String, Map<String, Int>>,
        val docCount: Int,
    ) {
        fun search(query: String, k1: Double = 1.2, b: Double = 0.75, topK: Int = 10): List<ScoredHit> {
            val qTerms = tokenize(query).distinct()
            if (qTerms.isEmpty() || docCount == 0) return emptyList()
            val scores = HashMap<String, Double>()
            for (term in qTerms) {
                val tfByDoc = postings[term] ?: continue
                val df = tfByDoc.size
                val idf = ln(1.0 + (docCount - df + 0.5) / (df + 0.5))
                for ((docId, tf) in tfByDoc) {
                    val dl = docLengths.getValue(docId).toDouble()
                    val norm = tf * (k1 + 1.0) / (tf + k1 * (1.0 - b + b * (dl / avgDocLen.coerceAtLeast(1.0))))
                    scores[docId] = (scores[docId] ?: 0.0) + idf * norm
                }
            }
            return scores.entries
                .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
                .take(topK)
                .map { ScoredHit(it.key, it.value) }
        }
    }

    fun index(docs: List<DocInput>): Corpus {
        val lengths = LinkedHashMap<String, Int>()
        val postings = LinkedHashMap<String, MutableMap<String, Int>>()
        var totalTokens = 0
        for (d in docs) {
            val toks = tokenize(d.text)
            lengths[d.id] = toks.size
            totalTokens += toks.size
            for (t in toks) {
                val m = postings.getOrPut(t) { LinkedHashMap() }
                m[d.id] = (m[d.id] ?: 0) + 1
            }
        }
        val avg = if (docs.isEmpty()) 0.0 else totalTokens.toDouble() / docs.size.toDouble()
        return Corpus(lengths, avg, postings, docs.size)
    }

    fun tokenize(text: String): List<String> =
        Regex("[a-z0-9]{2,}").findAll(text.lowercase(Locale.ROOT)).map { it.value }.toList()
}
