// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

import kotlin.math.ln

/**
 * Clean-room Offline Document **BM25F Fielded Full-Text Ranker & Snippet Highlighter**.
 *
 * Ranks local documents across weighted `title` (`3.0x`), `headings` (`2.0x`), and `body` (`1.0x`)
 * fields using Robertson-Zaragoza **BM25F** (`k1 = 1.2, b = 0.75`) and generates clean `[...]`
 * highlighted context snippets without any external search engine or network call.
 */
object OpenSearchSuggestionJson {
    const val SCOPE =
        "Clean-room offline BM25F fielded document ranker (title 3x, headings 2x, body 1x) & snippet highlighter."

    data class IndexedDoc(
        val id: String,
        val title: String,
        val headings: String = "",
        val body: String,
    )

    data class RankedHit(
        val docId: String,
        val score: Double,
        val snippet: String,
    )

    fun search(docs: List<IndexedDoc>, query: String, limit: Int = 10): List<RankedHit> {
        val qTerms = tokenize(query).distinct()
        if (docs.isEmpty() || qTerms.isEmpty() || limit <= 0) return emptyList()

        val weightedTokens = docs.map { d ->
            val t = tokenize(d.title)
            val h = tokenize(d.headings)
            val b = tokenize(d.body)
            val tf = HashMap<String, Double>()
            for (w in t) tf[w] = (tf[w] ?: 0.0) + 3.0
            for (w in h) tf[w] = (tf[w] ?: 0.0) + 2.0
            for (w in b) tf[w] = (tf[w] ?: 0.0) + 1.0
            val vLen = t.size * 3.0 + h.size * 2.0 + b.size * 1.0
            tf to vLen
        }
        val avgDl = (weightedTokens.sumOf { it.second } / docs.size).coerceAtLeast(1.0)
        val k1 = 1.2
        val bParam = 0.75

        val df = qTerms.associateWith { term ->
            weightedTokens.count { (tf, _) -> (tf[term] ?: 0.0) > 0.0 }
        }

        val hits = ArrayList<RankedHit>()
        docs.forEachIndexed { idx, doc ->
            val (tfMap, dl) = weightedTokens[idx]
            var score = 0.0
            for (term in qTerms) {
                val f = tfMap[term] ?: 0.0
                if (f <= 0.0) continue
                val n = df.getValue(term)
                val idf = ln(1.0 + (docs.size - n + 0.5) / (n + 0.5))
                val num = f * (k1 + 1.0)
                val den = f + k1 * (1.0 - bParam + bParam * (dl / avgDl))
                score += idf * (num / den)
            }
            if (score > 0.0) {
                hits.add(RankedHit(doc.id, score, buildSnippet(doc.body.ifBlank { doc.title }, qTerms)))
            }
        }
        return hits.sortedWith(compareByDescending<RankedHit> { it.score }.thenBy { it.docId }).take(limit)
    }

    fun buildSnippet(text: String, queryTerms: List<String>, windowChars: Int = 120): String {
        if (text.isEmpty()) return ""
        val lower = text.lowercase()
        val firstHit = queryTerms.mapNotNull { t -> lower.indexOf(t).takeIf { it >= 0 } }.minOrNull() ?: 0
        val start = (firstHit - windowChars / 3).coerceAtLeast(0)
        val end = (start + windowChars).coerceAtMost(text.length)
        var slice = text.substring(start, end).trim()
        for (t in queryTerms.sortedByDescending { it.length }) {
            if (t.isNotEmpty()) {
                slice = Regex("(?i)\\b(${Regex.escape(t)})\\b").replace(slice, "[$1]")
            }
        }
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + slice + suffix
    }

    private fun tokenize(s: String): List<String> =
        Regex("[A-Za-z0-9]{2,}").findAll(s.lowercase()).map { it.value }.toList()
}
