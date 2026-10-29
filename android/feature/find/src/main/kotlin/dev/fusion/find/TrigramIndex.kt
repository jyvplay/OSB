// SPDX-License-Identifier: Apache-2.0
package dev.fusion.find

/**
 * Clean-room Russ Cox / Zoekt-inspired Case-Folded 3-Gram Inverted Code & Document Search Index.
 *
 * Guarantee (zero false negatives):
 * For every indexed document `d` whose lowercase text contains `needle.lowercase()` (`len >= 3`),
 * `d` MUST contain EVERY 3-gram of `needle.lowercase()`, so intersecting sorted posting lists
 * narrows candidate documents by orders of magnitude before exact verification — with **zero**
 * missed hits!
 */
object TrigramIndex {
    const val SCOPE =
        "Zero-false-negative case-folded 3-gram inverted index with sorted posting-list intersection and exact-offset verifier; full Thompson NFA regex trigrams out of scope."

    const val MAX_DOCS = 16_384
    const val MAX_DOC_CHARS = 262_144

    data class Hit(
        val docId: String,
        val offsets: List<Int>,
    )

    class Index internal constructor(
        private val docs: Map<String, String>,
        private val lowerDocs: Map<String, String>,
        private val postings: Map<String, IntArray>,
        private val docTable: List<String>,
    ) {
        val documentCount: Int get() = docs.size
        val trigramCount: Int get() = postings.size

        fun searchLiteral(query: String, caseSensitive: Boolean = false): List<Hit> {
            require(query.isNotEmpty()) { "search query must not be empty" }
            val qLower = query.lowercase()
            val candidateOrdinals: IntArray = if (qLower.length < 3) {
                IntArray(docTable.size) { it }
            } else {
                val tris = extractUniqueTrigrams(qLower)
                // Intersect starting from shortest posting list first!
                val lists = tris.map { postings[it] ?: return emptyList() }.sortedBy { it.size }
                var acc = lists.first()
                for (k in 1 until lists.size) {
                    acc = intersectSorted(acc, lists[k])
                    if (acc.isEmpty()) return emptyList()
                }
                acc
            }

            val hits = ArrayList<Hit>()
            for (ord in candidateOrdinals) {
                val id = docTable[ord]
                val hay = if (caseSensitive) docs.getValue(id) else lowerDocs.getValue(id)
                val needle = if (caseSensitive) query else qLower
                val offs = findAllOffsets(hay, needle)
                if (offs.isNotEmpty()) hits.add(Hit(id, offs))
            }
            return hits
        }

        /** Searches OR-alternation of literal phrases (`"alpha" OR "beta"`), unioning posting candidates. */
        fun searchAnyLiteral(phrases: List<String>, caseSensitive: Boolean = false): Set<String> {
            require(phrases.isNotEmpty()) { "phrases must not be empty" }
            val matchedDocs = LinkedHashSet<String>()
            for (p in phrases) {
                for (h in searchLiteral(p, caseSensitive)) matchedDocs.add(h.docId)
            }
            return matchedDocs
        }
    }

    fun build(documents: Map<String, String>): Index {
        require(documents.size <= MAX_DOCS) { "index exceeds $MAX_DOCS documents" }
        val sortedIds = documents.keys.sorted()
        val docsCopy = LinkedHashMap<String, String>(sortedIds.size)
        val lowerCopy = LinkedHashMap<String, String>(sortedIds.size)
        val rawPostings = HashMap<String, MutableList<Int>>()

        for ((ord, id) in sortedIds.withIndex()) {
            val body = documents.getValue(id)
            require(body.length <= MAX_DOC_CHARS) { "document '$id' exceeds $MAX_DOC_CHARS chars" }
            val low = body.lowercase()
            docsCopy[id] = body
            lowerCopy[id] = low
            for (tri in extractUniqueTrigrams(low)) {
                rawPostings.getOrPut(tri) { ArrayList() }.add(ord)
            }
        }
        val compact = HashMap<String, IntArray>(rawPostings.size)
        for ((tri, list) in rawPostings) compact[tri] = list.toIntArray()
        return Index(docsCopy, lowerCopy, compact, sortedIds)
    }

    internal fun extractUniqueTrigrams(lower: String): Set<String> {
        if (lower.length < 3) return emptySet()
        val set = LinkedHashSet<String>(lower.length - 2)
        for (i in 0..lower.length - 3) {
            set.add(lower.substring(i, i + 3))
        }
        return set
    }

    private fun intersectSorted(a: IntArray, b: IntArray): IntArray {
        val out = IntArray(minOf(a.size, b.size))
        var i = 0; var j = 0; var k = 0
        while (i < a.size && j < b.size) {
            val va = a[i]; val vb = b[j]
            when {
                va == vb -> { out[k++] = va; i++; j++ }
                va < vb -> i++
                else -> j++
            }
        }
        return if (k == out.size) out else out.copyOf(k)
    }

    private fun findAllOffsets(hay: String, needle: String): List<Int> {
        val out = ArrayList<Int>()
        var idx = hay.indexOf(needle, 0)
        while (idx >= 0) {
            out.add(idx)
            idx = hay.indexOf(needle, idx + 1)
        }
        return out
    }
}
