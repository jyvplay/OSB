// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * Production-grade command palette fuzzy-subsequence scorer &
 * keybinding conflict detector.
 */
class CommandsFuzzyMatcher private constructor() {
    companion object {
        fun scoreSubsequence(query: String, target: String): Int? {
            if (query.isEmpty()) return 0
            val q = query.lowercase()
            val t = target.lowercase()
            var qi = 0
            var score = 0
            var prevMatch = -2
            for (ti in t.indices) {
                if (qi < q.length && t[ti] == q[qi]) {
                    score += 10
                    if (ti == prevMatch + 1) score += 15 // consecutive bonus
                    if (ti == 0 || target[ti - 1] in " _-./") score += 20 // boundary bonus
                    prevMatch = ti
                    qi++
                }
            }
            return if (qi == q.length) score - (target.length - query.length) else null
        }

        fun findChordConflicts(bindings: List<Pair<String, String>>): Map<String, List<String>> {
            val byChord = LinkedHashMap<String, MutableList<String>>()
            for ((cmdId, chord) in bindings) {
                byChord.getOrPut(chord.lowercase().trim()) { mutableListOf() }.add(cmdId)
            }
            return byChord.filterValues { it.size > 1 }
        }
    }
}
