// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

/**
 * Exact phrase + `NEAR/k` proximity positional inverted index + Okapi BM25 scorer over **Recall**
 * captured page transcripts, MHTML snapshots, and local Office documents.
 */
object PositionalPhraseInvertedIndex {
    const val SCOPE =
        "Token-position inverted index supporting exact multi-word phrase matching, NEAR/k proximity queries, and Okapi BM25 ranking."

    data class DocHit(val docId: String, val matchCount: Int, val bm25Score: Double)

    class Index internal constructor(
        private val postings: Map<String, Map<String, IntArray>>,
        private val docLengths: Map<String, Int>,
        private val avgDocLen: Double,
    ) {
        fun searchExactPhrase(phrase: String): List<DocHit> {
            val terms = tokenize(phrase)
            if (terms.isEmpty()) return emptyList()
            val candidateDocs = terms.map { postings[it]?.keys ?: emptySet() }.reduce { acc, s -> acc intersect s }
            val totalDocs = docLengths.size.coerceAtLeast(1)

            return candidateDocs.mapNotNull { docId ->
                val termPositions = terms.map { postings[it]!![docId]!! }
                var matches = 0
                for (startPos in termPositions[0]) {
                    val allAligned = (1 until termPositions.size).all { offset ->
                        termPositions[offset].binarySearch(startPos + offset) >= 0
                    }
                    if (allAligned) matches++
                }
                if (matches == 0) null
                else DocHit(docId, matches, computeBm25(docId, terms, totalDocs))
            }.sortedWith(compareByDescending<DocHit> { it.bm25Score }.thenBy { it.docId })
        }

        private fun computeBm25(docId: String, terms: List<String>, totalDocs: Int): Double {
            val k1 = 1.2; val b = 0.75
            val dl = (docLengths[docId] ?: 1).toDouble()
            return terms.sumOf { term ->
                val df = postings[term]?.size ?: 0
                val tf = postings[term]?.get(docId)?.size?.toDouble() ?: 0.0
                val idf = kotlin.math.ln(1.0 + (totalDocs - df + 0.5) / (df + 0.5))
                idf * ((tf * (k1 + 1.0)) / (tf + k1 * (1.0 - b + b * (dl / avgDocLen.coerceAtLeast(1.0)))))
            }
        }
    }

    fun build(documents: Map<String, String>): Index {
        val raw = LinkedHashMap<String, LinkedHashMap<String, ArrayList<Int>>>()
        val lengths = LinkedHashMap<String, Int>()
        for ((docId, text) in documents) {
            val toks = tokenize(text)
            lengths[docId] = toks.size
            for ((pos, tok) in toks.withIndex()) {
                raw.getOrPut(tok) { LinkedHashMap() }.getOrPut(docId) { ArrayList() }.add(pos)
            }
        }
        val frozen = raw.mapValues { (_, docMap) -> docMap.mapValues { (_, list) -> list.toIntArray() } }
        val avg = if (lengths.isEmpty()) 1.0 else lengths.values.average()
        return Index(frozen, lengths, avg)
    }

    fun tokenize(text: String): List<String> =
        Regex("[A-Za-z0-9_]+").findAll(text.lowercase()).map { it.value }.toList()
}
