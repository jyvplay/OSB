// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

import kotlin.math.ln

/**
 * Local offline BM25 + media-facet search index for **Recall** recorded browser images, videos,
 * and music tracks (zero cloud calls, indexing page titles, clean URLs, alt-text, ID3/Vorbis artist/album,
 * and WebVTT subtitle cues).
 */
class RecallMediaTranscriptSearchIndex(
    private val k1: Double = 1.4,
    private val b: Double = 0.75,
) {
    companion object {
        const val SCOPE =
            "Offline BM25 + media-kind/folder-mode faceted search index over Recall archived images, videos, music metadata, and WebVTT captions."
    }

    data class IndexedRecallAsset(
        val assetId: String,
        val mediaKind: String, // IMAGE, VIDEO, MUSIC
        val folderPath: String,
        val pageTitle: String,
        val pageUrl: String,
        val transcriptOrTags: String,
    )

    data class SearchHit(
        val asset: IndexedRecallAsset,
        val score: Double,
    )

    private val docs = LinkedHashMap<String, IndexedRecallAsset>()
    private val docTokens = LinkedHashMap<String, Map<String, Int>>()
    private val docLengths = LinkedHashMap<String, Int>()
    private val df = LinkedHashMap<String, Int>()

    fun index(asset: IndexedRecallAsset) {
        if (asset.assetId in docs) remove(asset.assetId)
        val combined = "${asset.pageTitle} ${asset.pageTitle} ${asset.folderPath} ${asset.pageUrl} ${asset.transcriptOrTags}"
        val toks = tokenize(combined)
        val tf = LinkedHashMap<String, Int>()
        for (t in toks) tf[t] = (tf[t] ?: 0) + 1
        docs[asset.assetId] = asset
        docTokens[asset.assetId] = tf
        docLengths[asset.assetId] = toks.size.coerceAtLeast(1)
        for (term in tf.keys) df[term] = (df[term] ?: 0) + 1
    }

    fun remove(assetId: String) {
        val tf = docTokens.remove(assetId) ?: return
        docs.remove(assetId)
        docLengths.remove(assetId)
        for (term in tf.keys) {
            val next = (df[term] ?: 1) - 1
            if (next <= 0) df.remove(term) else df[term] = next
        }
    }

    fun search(query: String, mediaKindFilter: String? = null, limit: Int = 20): List<SearchHit> {
        val qTerms = tokenize(query).distinct()
        if (qTerms.isEmpty() || docs.isEmpty()) return emptyList()
        val n = docs.size.toDouble()
        val avgDl = docLengths.values.sum().toDouble() / n
        val hits = ArrayList<SearchHit>()
        for ((id, asset) in docs) {
            if (mediaKindFilter != null && !asset.mediaKind.equals(mediaKindFilter, ignoreCase = true)) continue
            val tfMap = docTokens[id] ?: continue
            val dl = (docLengths[id] ?: 1).toDouble()
            var score = 0.0
            for (term in qTerms) {
                val f = (tfMap[term] ?: 0).toDouble()
                if (f <= 0.0) continue
                val nq = (df[term] ?: 0).toDouble()
                val idf = ln(1.0 + (n - nq + 0.5) / (nq + 0.5))
                val denom = f + k1 * (1.0 - b + b * (dl / avgDl))
                score += idf * ((f * (k1 + 1.0)) / denom)
            }
            if (score > 0.0) hits.add(SearchHit(asset, score))
        }
        return hits.sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.asset.assetId }).take(limit)
    }

    private fun tokenize(text: String): List<String> =
        Regex("[A-Za-z0-9]{2,}").findAll(text.lowercase()).map { it.value }.toList()
}
