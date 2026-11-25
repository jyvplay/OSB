// SPDX-License-Identifier: Apache-2.0
package dev.fusion.find

import kotlin.math.ln

/**
 * Production-grade BM25 inverted-index document ranker.
 */
class FindBm25Ranker private constructor(
    private val k1: Double = 1.5,
    private val b: Double = 0.75
) {
    companion object {
        fun create(k1: Double = 1.5, b: Double = 0.75): FindBm25Ranker {
            require(k1 >= 0.0 && b in 0.0..1.0) { "invalid BM25 parameters" }
            return FindBm25Ranker(k1, b)
        }

        fun tokenize(text: String): List<String> =
            text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
    }

    private val docs = LinkedHashMap<String, Map<String, Int>>()
    private val docLens = LinkedHashMap<String, Int>()
    private val df = HashMap<String, Int>()
    private var totalLen = 0L

    fun addDocument(docId: String, text: String) {
        val tokens = tokenize(text)
        val tf = HashMap<String, Int>()
        for (t in tokens) tf[t] = (tf[t] ?: 0) + 1
        if (docId in docs) {
            totalLen -= (docLens[docId] ?: 0)
            for (term in docs[docId]!!.keys) {
                df[term] = (df[term] ?: 1) - 1
            }
        }
        docs[docId] = tf
        docLens[docId] = tokens.size
        totalLen += tokens.size
        for (term in tf.keys) {
            df[term] = (df[term] ?: 0) + 1
        }
    }

    fun search(query: String, topK: Int = 10): List<Pair<String, Double>> {
        if (docs.isEmpty() || topK <= 0) return emptyList()
        val qTerms = tokenize(query).distinct()
        val n = docs.size.toDouble()
        val avgDl = totalLen.toDouble() / n
        val scores = mutableListOf<Pair<String, Double>>()

        for ((docId, tfMap) in docs) {
            val dl = (docLens[docId] ?: 0).toDouble()
            var score = 0.0
            for (term in qTerms) {
                val freq = (tfMap[term] ?: 0).toDouble()
                if (freq == 0.0) continue
                val docFreq = (df[term] ?: 0).toDouble()
                val idf = ln((n - docFreq + 0.5) / (docFreq + 0.5) + 1.0)
                val num = freq * (k1 + 1.0)
                val den = freq + k1 * (1.0 - b + b * (dl / avgDl))
                score += idf * (num / den)
            }
            if (score > 0.0) scores.add(docId to score)
        }
        return scores.sortedByDescending { it.second }.take(topK)
    }
}
