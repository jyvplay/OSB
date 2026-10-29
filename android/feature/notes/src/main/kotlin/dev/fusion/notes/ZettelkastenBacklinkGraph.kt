// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * Pure-JVM **Zettelkasten Bidirectional `[[WikiLink]]` Graph Backlink, Orphan-Note, and
 * PageRank Centrality Analyzer** for `:feature:notes`.
 */
object ZettelkastenBacklinkGraph {
    const val SCOPE =
        "Bidirectional [[WikiLink]] extractor, reverse backlink index, orphan-note detector, and iterative PageRank hub scorer."

    data class NoteGraphAnalysis(
        val backlinksByTarget: Map<String, Set<String>>,
        val orphanNotes: Set<String>,
        val pageRankScores: Map<String, Double>,
    )

    fun analyze(notesBySlug: Map<String, String>, damping: Double = 0.85, iterations: Int = 25): NoteGraphAnalysis {
        val linkRegex = Regex("""\[\[([^\]|#]+)(?:[|#][^\]]*)?\]\]""")
        val outgoing = LinkedHashMap<String, Set<String>>()
        val incoming = LinkedHashMap<String, MutableSet<String>>()
        for (slug in notesBySlug.keys) incoming[slug] = LinkedHashSet()

        for ((slug, body) in notesBySlug) {
            val targets = linkRegex.findAll(body)
                .map { it.groupValues[1].trim() }
                .filter { it in notesBySlug && it != slug }
                .toSet()
            outgoing[slug] = targets
            for (t in targets) incoming.getValue(t).add(slug)
        }

        val orphans = notesBySlug.keys.filter {
            outgoing[it].isNullOrEmpty() && incoming[it].isNullOrEmpty()
        }.toSet()

        val n = notesBySlug.size
        var ranks = notesBySlug.keys.associateWith { if (n > 0) 1.0 / n else 0.0 }
        if (n > 0) {
            repeat(iterations) {
                val sinkSum = notesBySlug.keys.filter { outgoing[it].isNullOrEmpty() }.sumOf { ranks.getValue(it) }
                val next = LinkedHashMap<String, Double>(n)
                for (u in notesBySlug.keys) {
                    val inSum = incoming.getValue(u).sumOf { v ->
                        ranks.getValue(v) / outgoing.getValue(v).size.toDouble()
                    }
                    next[u] = (1.0 - damping) / n + damping * (inSum + sinkSum / n)
                }
                ranks = next
            }
        }
        return NoteGraphAnalysis(incoming, orphans, ranks)
    }
}
