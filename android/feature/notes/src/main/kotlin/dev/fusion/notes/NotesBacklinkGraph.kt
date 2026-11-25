// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * Production-grade bi-directional wiki-link (`[[target|alias]]`) &
 * backlink graph indexer with orphan detection and cycle-safe BFS reachability.
 */
class NotesBacklinkGraph private constructor() {
    companion object {
        private val LINK_REGEX = Regex("\\[\\[([^\\]|]+)(?:\\|[^\\]]*)?\\]\\]")

        fun normalizeSlug(raw: String): String =
            raw.trim().lowercase().replace(Regex("\\s+"), "-")

        fun extractLinks(markdown: String): List<String> =
            LINK_REGEX.findAll(markdown).map { normalizeSlug(it.groupValues[1]) }.distinct().toList()

        fun create(): NotesBacklinkGraph = NotesBacklinkGraph()
    }

    private val forward = LinkedHashMap<String, Set<String>>()
    private val backward = LinkedHashMap<String, MutableSet<String>>()

    fun indexNote(slug: String, markdown: String) {
        val norm = normalizeSlug(slug)
        val oldTargets = forward[norm].orEmpty()
        for (t in oldTargets) {
            backward[t]?.remove(norm)
        }
        val targets = extractLinks(markdown).toSet()
        forward[norm] = targets
        backward.getOrPut(norm) { LinkedHashSet() }
        for (t in targets) {
            backward.getOrPut(t) { LinkedHashSet() }.add(norm)
        }
    }

    fun backlinksOf(slug: String): Set<String> = backward[normalizeSlug(slug)].orEmpty().toSet()

    fun orphanNotes(): Set<String> =
        forward.keys.filter { k -> forward[k].orEmpty().isEmpty() && backward[k].orEmpty().isEmpty() }.toSet()

    fun isReachable(fromSlug: String, toSlug: String): Boolean {
        val start = normalizeSlug(fromSlug)
        val goal = normalizeSlug(toSlug)
        if (start == goal) return start in forward
        val visited = HashSet<String>()
        val queue = ArrayDeque<String>()
        visited.add(start)
        queue.addLast(start)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (nxt in forward[cur].orEmpty()) {
                if (nxt == goal) return true
                if (visited.add(nxt)) queue.addLast(nxt)
            }
        }
        return false
    }
}
