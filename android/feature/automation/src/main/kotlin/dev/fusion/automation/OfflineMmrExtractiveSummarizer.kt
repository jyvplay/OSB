// SPDX-License-Identifier: Apache-2.0
package dev.fusion.automation

import kotlin.math.sqrt

/**
 * Pure-JVM **Offline Extractive TextRank / Maximal Marginal Relevance (`MMR`) Document & Transcript
 * Summarizer** for `:feature:ai` (zero network/cloud inference calls).
 */
object OfflineMmrExtractiveSummarizer {
    const val SCOPE =
        "Offline Maximal Marginal Relevance (MMR, Carbonell & Goldstein 1998) extractive sentence summarizer balancing TF relevance against inter-sentence cosine redundancy."

    data class SummarySentence(
        val originalIndex: Int,
        val text: String,
        val mmrScore: Double,
    )

    fun summarize(documentText: String, maxSentences: Int = 3, lambdaRelevance: Double = 0.72): List<SummarySentence> {
        require(maxSentences >= 1 && lambdaRelevance in 0.1..0.95)
        val rawSentences = documentText.split(Regex("""(?<=[.!?])\s+"""))
            .map { it.trim() }
            .filter { it.length >= 12 }
        if (rawSentences.size <= maxSentences) {
            return rawSentences.mapIndexed { i, s -> SummarySentence(i, s, 1.0) }
        }
        val vectors = rawSentences.map { termFreqVector(it) }
        val docCentroid = LinkedHashMap<String, Double>()
        for (v in vectors) for ((k, c) in v) docCentroid[k] = (docCentroid[k] ?: 0.0) + c

        val selected = ArrayList<SummarySentence>()
        val remaining = rawSentences.indices.toMutableSet()
        while (selected.size < maxSentences && remaining.isNotEmpty()) {
            var bestIdx = -1
            var bestScore = Double.NEGATIVE_INFINITY
            for (cand in remaining) {
                val rel = cosine(vectors[cand], docCentroid)
                val maxSimToSelected = if (selected.isEmpty()) 0.0
                else selected.maxOf { cosine(vectors[cand], vectors[it.originalIndex]) }
                val score = lambdaRelevance * rel - (1.0 - lambdaRelevance) * maxSimToSelected
                if (score > bestScore) {
                    bestScore = score
                    bestIdx = cand
                }
            }
            if (bestIdx < 0) break
            remaining.remove(bestIdx)
            selected.add(SummarySentence(bestIdx, rawSentences[bestIdx], bestScore))
        }
        return selected.sortedBy { it.originalIndex }
    }

    private fun termFreqVector(text: String): Map<String, Double> {
        val m = LinkedHashMap<String, Double>()
        Regex("[A-Za-z0-9]{3,}").findAll(text.lowercase()).forEach { tok ->
            m[tok.value] = (m[tok.value] ?: 0.0) + 1.0
        }
        return m
    }

    private fun cosine(a: Map<String, Double>, b: Map<String, Double>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var dot = 0.0
        for ((k, va) in a) dot += va * (b[k] ?: 0.0)
        val na = sqrt(a.values.sumOf { it * it })
        val nb = sqrt(b.values.sumOf { it * it })
        return if (na == 0.0 || nb == 0.0) 0.0 else dot / (na * nb)
    }
}
