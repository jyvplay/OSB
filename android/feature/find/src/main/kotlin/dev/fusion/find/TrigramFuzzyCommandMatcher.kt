// SPDX-License-Identifier: Apache-2.0
package dev.fusion.find

/**
 * Character-3-gram Jaccard similarity + acronym/camelCase boundary bonus fuzzy command & file
 * ranker for Fusion Find (`Ctrl+K` / Launcher quick-open).
 */
object TrigramFuzzyCommandMatcher {
    const val SCOPE =
        "Padded character-trigram Jaccard + camelCase/word-boundary acronym fuzzy command palette scorer."

    data class MatchCandidate(val id: String, val title: String, val score: Double)

    fun rankQuery(query: String, commands: List<Pair<String, String>>, topK: Int = 10): List<MatchCandidate> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val qLower = q.lowercase()
        val qTrigrams = trigramsOf(qLower)
        return commands.mapNotNull { (id, title) ->
            val tLower = title.lowercase()
            val tTrigrams = trigramsOf(tLower)
            val inter = (qTrigrams intersect tTrigrams).size.toDouble()
            val union = (qTrigrams union tTrigrams).size.coerceAtLeast(1).toDouble()
            val jaccard = inter / union
            val prefixBonus = if (tLower.startsWith(qLower)) 0.45 else if (qLower in tLower) 0.25 else 0.0
            val acronym = buildAcronym(title).lowercase()
            val acronymBonus = if (acronym.startsWith(qLower)) 0.50 else if (isSubsequence(qLower, tLower)) 0.15 else 0.0
            val total = jaccard + prefixBonus + acronymBonus
            if (total <= 0.08) null else MatchCandidate(id, title, total)
        }.sortedWith(compareByDescending<MatchCandidate> { it.score }.thenBy { it.id }).take(topK)
    }

    private fun trigramsOf(s: String): Set<String> {
        val padded = "  $s "
        val set = LinkedHashSet<String>()
        for (i in 0..(padded.length - 3)) set.add(padded.substring(i, i + 3))
        return set
    }

    private fun buildAcronym(title: String): String = buildString {
        for ((i, ch) in title.withIndex()) {
            if (ch.isLetterOrDigit() && (i == 0 || !title[i - 1].isLetterOrDigit() || ch.isUpperCase())) {
                append(ch)
            }
        }
    }

    private fun isSubsequence(needle: String, haystack: String): Boolean {
        var ni = 0
        for (ch in haystack) {
            if (ni < needle.length && ch == needle[ni]) ni++
        }
        return ni == needle.length
    }
}
