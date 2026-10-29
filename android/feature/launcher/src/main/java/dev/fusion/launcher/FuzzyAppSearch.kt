// SPDX-License-Identifier: Apache-2.0
package dev.fusion.launcher

import java.util.Locale

/**
 * Clean-room `fzf`/Smith-Waterman-inspired subsequence fuzzy app & command ranker (`FuzzyAppSearch`).
 *
 * Powers `:feature:launcher` instant keyboard/thumb search (`"fs"` -> `"Fusion Spreadsheet"`,
 * `"wtr"` -> `"Writer"`, `"tbr"` -> `"Tor Browser"`):
 * - Case-insensitive ordered subsequence matching with boundary bonuses (`+18` word start,
 *   `+15` CamelCase transition, `+12` consecutive character streak, `+20` exact prefix, `-2` per gap char).
 * - Deterministic tie-breaking by shorter candidate label then lexicographic `id`.
 */
object FuzzyAppSearch {
    const val SCOPE =
        "Subsequence fuzzy launcher ranker (word-boundary/CamelCase/prefix/streak bonuses + gap penalty + matched-index highlight positions); phonetic Soundex out of scope."

    data class Entry(
        val id: String,
        val label: String,
        val category: String = "",
    )

    data class Match(
        val entry: Entry,
        val score: Int,
        val matchedIndices: List<Int>,
    )

    fun search(query: String, entries: List<Entry>, limit: Int = 20): List<Match> {
        require(limit >= 1) { "limit must be >= 1" }
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) {
            return entries.sortedBy { it.label.lowercase(Locale.ROOT) }
                .take(limit)
                .map { Match(it, 0, emptyList()) }
        }
        return entries.mapNotNull { scoreCandidate(q, it) }
            .sortedWith(
                compareByDescending<Match> { it.score }
                    .thenBy { it.entry.label.length }
                    .thenBy { it.entry.id },
            )
            .take(limit)
    }

    fun scoreCandidate(lowerQuery: String, entry: Entry): Match? {
        val text = entry.label
        val lowerText = text.lowercase(Locale.ROOT)
        val n = text.length
        val m = lowerQuery.length
        if (m > n) return null

        // Verify subsequence exists and compute greedy boundary-preferring DP alignment.
        val negInf = -1_000_000
        val dp = Array(m) { IntArray(n) { negInf } }
        val prev = Array(m) { IntArray(n) { -1 } }

        for (j in 0 until n) {
            if (lowerText[j] == lowerQuery[0]) {
                dp[0][j] = 10 + boundaryBonus(text, j) + (if (j == 0) 20 else -j)
            }
        }
        for (i in 1 until m) {
            for (j in i until n) {
                if (lowerText[j] != lowerQuery[i]) continue
                var best = negInf
                var bestP = -1
                for (p in (i - 1) until j) {
                    if (dp[i - 1][p] == negInf) continue
                    val gap = j - p - 1
                    val step = if (gap == 0) 12 + boundaryBonus(text, j) / 2
                    else boundaryBonus(text, j) - gap * 2
                    val cand = dp[i - 1][p] + 10 + step
                    if (cand > best) {
                        best = cand
                        bestP = p
                    }
                }
                dp[i][j] = best
                prev[i][j] = bestP
            }
        }

        var endJ = -1
        var bestFinal = negInf
        for (j in (m - 1) until n) {
            if (dp[m - 1][j] > bestFinal) {
                bestFinal = dp[m - 1][j]
                endJ = j
            }
        }
        if (endJ < 0 || bestFinal <= negInf / 2) return null

        val indices = IntArray(m)
        var curJ = endJ
        for (i in (m - 1) downTo 0) {
            indices[i] = curJ
            curJ = prev[i][curJ]
        }
        return Match(entry, bestFinal, indices.toList())
    }

    private fun boundaryBonus(text: String, idx: Int): Int {
        if (idx == 0) return 18
        val prev = text[idx - 1]
        val cur = text[idx]
        if (prev == ' ' || prev == '-' || prev == '_' || prev == '/') return 18
        if (prev.isLowerCase() && cur.isUpperCase()) return 15
        if (!prev.isDigit() && cur.isDigit()) return 10
        return 0
    }
}
