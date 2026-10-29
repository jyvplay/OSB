package dev.fusion.index

import java.util.Locale
import kotlin.math.ln

/**
 * Clean-room Okapi `BM25F` Positional Full-Text Search Index (Apache-2.0) supporting weighted
 * multi-field (`titleBoost = 3.0`, `bodyBoost = 1.0`) ranking, exact `"phrase"` adjacency matching,
 * and `prefix*` term expansion!
 */
object Bm25FtsPositionalIndex {
    const val SCOPE =
        "Okapi BM25F weighted positional inverted index (title/body boosts + exact phrase adjacency + prefix* expansion)"

    data class IndexedDoc(val id: String, val title: String, val body: String)
    data class ScoredHit(val docId: String, val score: Double)

    class Index(
        docs: List<IndexedDoc>,
        private val k1: Double = 1.2,
        private val b: Double = 0.75,
        private val titleBoost: Double = 3.0,
    ) {
        private val docCount = docs.size
        private val pseudoLengths = HashMap<String, Double>()
        private val avgLen: Double
        // term -> docId -> (weightedTf, bodyPositions)
        private val postings = HashMap<String, HashMap<String, Pair<Double, IntArray>>>()

        init {
            var totalLen = 0.0
            for (d in docs) {
                val tTokens = tokenize(d.title)
                val bTokens = tokenize(d.body)
                val pLen = tTokens.size * titleBoost + bTokens.size
                pseudoLengths[d.id] = pLen
                totalLen += pLen

                val tfMap = HashMap<String, Double>()
                val posMap = HashMap<String, ArrayList<Int>>()
                for (tok in tTokens) tfMap[tok] = (tfMap[tok] ?: 0.0) + titleBoost
                for ((idx, tok) in bTokens.withIndex()) {
                    tfMap[tok] = (tfMap[tok] ?: 0.0) + 1.0
                    posMap.getOrPut(tok) { ArrayList() }.add(idx)
                }
                for ((term, wtf) in tfMap) {
                    postings.getOrPut(term) { HashMap() }[d.id] = wtf to (posMap[term]?.toIntArray() ?: IntArray(0))
                }
            }
            avgLen = if (docCount == 0) 1.0 else (totalLen / docCount).coerceAtLeast(1.0)
        }

        fun search(rawQuery: String, limit: Int = 10): List<ScoredHit> {
            if (docCount == 0 || rawQuery.isBlank()) return emptyList()
            val phrases = Regex("\"([^\"]+)\"").findAll(rawQuery).map { tokenize(it.groupValues[1]) }.filter { it.isNotEmpty() }.toList()
            val remainder = rawQuery.replace(Regex("\"[^\"]+\""), " ")
            val rawTerms = remainder.lowercase(Locale.ROOT).split(Regex("[^a-z0-9*]+")).filter { it.isNotEmpty() }

            val scores = HashMap<String, Double>()
            for (rt in rawTerms) {
                val expanded = if (rt.endsWith("*") && rt.length >= 2) {
                    val pref = rt.dropLast(1)
                    postings.keys.filter { it.startsWith(pref) }
                } else {
                    listOf(rt)
                }
                for (term in expanded) {
                    val termDocs = postings[term] ?: continue
                    val df = termDocs.size
                    val idf = ln(1.0 + (docCount - df + 0.5) / (df + 0.5))
                    for ((docId, pair) in termDocs) {
                        val tf = pair.first
                        val dl = pseudoLengths.getValue(docId)
                        val norm = tf * (k1 + 1.0) / (tf + k1 * (1.0 - b + b * (dl / avgLen)))
                        scores[docId] = (scores[docId] ?: 0.0) + idf * norm
                    }
                }
            }

            if (phrases.isNotEmpty()) {
                val phraseCandidates = postings[phrases[0][0]]?.keys.orEmpty()
                for (docId in (scores.keys + phraseCandidates).toSet()) {
                    val allMatch = phrases.all { ph -> matchesExactPhraseInBody(docId, ph) }
                    if (!allMatch) {
                        scores.remove(docId)
                    } else {
                        scores[docId] = (scores[docId] ?: 0.0) + 4.0 * phrases.size
                    }
                }
            }

            return scores.entries
                .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
                .take(limit)
                .map { ScoredHit(it.key, it.value) }
        }

        private fun matchesExactPhraseInBody(docId: String, phraseTokens: List<String>): Boolean {
            val firstPos = postings[phraseTokens[0]]?.get(docId)?.second ?: return false
            if (phraseTokens.size == 1) return firstPos.isNotEmpty()
            return firstPos.any { p0 ->
                (1 until phraseTokens.size).all { offset ->
                    val arr = postings[phraseTokens[offset]]?.get(docId)?.second ?: return@all false
                    arr.binarySearch(p0 + offset) >= 0
                }
            }
        }
    }

    fun tokenize(text: String): List<String> =
        text.lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
}
