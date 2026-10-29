package dev.fusion.index

import java.util.Locale

/**
 * Clean-room Character 3-Gram Jaccard Pre-Filter + Optimal String Alignment / Damerau-Levenshtein
 * (`transposition = 1` edit!) Typo-Tolerant Spell Suggester (Apache-2.0).
 */
object TrigramFuzzySpellSuggester {
    const val SCOPE =
        "Character 3-gram inverted pre-filter + Damerau-Levenshtein adjacent-transposition spell suggester"

    data class Suggestion(val term: String, val editDistance: Int, val jaccardSimilarity: Double)

    class Dictionary(vocabulary: Collection<String>) {
        private val terms = vocabulary.map { it.lowercase(Locale.ROOT).trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        private val trigramsByTerm = terms.associateWith(::trigrams)

        fun suggest(misspelled: String, maxEditDistance: Int = 2, topK: Int = 5): List<Suggestion> {
            val q = misspelled.lowercase(Locale.ROOT).trim()
            if (q.isEmpty()) return emptyList()
            val qTri = trigrams(q)
            return terms.mapNotNull { cand ->
                if (kotlin.math.abs(cand.length - q.length) > maxEditDistance) return@mapNotNull null
                val cTri = trigramsByTerm.getValue(cand)
                val inter = qTri.count { it in cTri }
                val union = (qTri.size + cTri.size - inter).coerceAtLeast(1)
                val jacc = inter.toDouble() / union.toDouble()
                val dist = damerauLevenshtein(q, cand)
                if (dist <= maxEditDistance) Suggestion(cand, dist, jacc) else null
            }.sortedWith(
                compareBy<Suggestion> { it.editDistance }
                    .thenByDescending { it.jaccardSimilarity }
                    .thenBy { it.term },
            ).take(topK)
        }
    }

    fun damerauLevenshtein(a: String, b: String): Int {
        val m = a.length; val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var best = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    best = minOf(best, dp[i - 2][j - 2] + 1)
                }
                dp[i][j] = best
            }
        }
        return dp[m][n]
    }

    private fun trigrams(s: String): Set<String> {
        val padded = "^$s$"
        if (padded.length < 3) return setOf(padded)
        return (0..padded.length - 3).map { padded.substring(it, it + 3) }.toSet()
    }
}
