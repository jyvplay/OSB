// SPDX-License-Identifier: Apache-2.0
package dev.fusion.find

/**
 * Clean-room Command Palette Word-Boundary / Acronym / Consecutive-Run Fuzzy Scorer with
 * deterministic tie-breaking.
 */
object CommandPaletteFuzzyActionRanker {
    const val SCOPE =
        "Pure-JVM word-boundary, camelCase, acronym, and consecutive-run fuzzy command ranker."

    data class ActionCandidate(val id: String, val title: String, val category: String)
    data class RankedAction(val candidate: ActionCandidate, val score: Int, val matchedIndices: List<Int>)

    fun rank(query: String, candidates: List<ActionCandidate>, limit: Int = 20): List<RankedAction> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return candidates.sortedBy { it.title.lowercase() }.take(limit).map { RankedAction(it, 1, emptyList()) }
        val hits = ArrayList<RankedAction>()
        for (c in candidates) {
            val res = scoreMatch(q, c.title) ?: continue
            hits.add(RankedAction(c, res.first, res.second))
        }
        return hits.sortedWith(
            compareByDescending<RankedAction> { it.score }
                .thenBy { it.candidate.title.length }
                .thenBy { it.candidate.id },
        ).take(limit.coerceIn(1, 200))
    }

    private fun scoreMatch(qLower: String, target: String): Pair<Int, List<Int>>? {
        val tLower = target.lowercase()
        val matched = ArrayList<Int>(qLower.length)
        var qi = 0
        var score = 0
        var prevMatchIdx = -2
        for (ti in tLower.indices) {
            if (qi < qLower.length && tLower[ti] == qLower[qi]) {
                val isBoundary = ti == 0 || !target[ti - 1].isLetterOrDigit() ||
                    (target[ti].isUpperCase() && target[ti - 1].isLowerCase())
                score += 10
                if (isBoundary) score += 18
                if (ti == prevMatchIdx + 1) score += 14
                if (ti == qi) score += 8
                matched.add(ti)
                prevMatchIdx = ti
                qi++
            }
        }
        return if (qi == qLower.length) score to matched else null
    }
}
