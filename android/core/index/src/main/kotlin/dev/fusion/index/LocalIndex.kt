package dev.fusion.index

/** Tiny honest local index: substring + prefix ranking. No FTS, no embeddings yet. */
data class Doc(val id: String, val title: String, val body: String, val source: String)
data class Hit(val doc: Doc, val score: Int)

class LocalIndex {
    private val docs = linkedMapOf<String, Doc>()
    fun upsert(d: Doc) { docs[d.id] = d }
    fun remove(id: String) { docs.remove(id) }
    fun size(): Int = docs.size
    fun search(q: String, limit: Int = 20): List<Hit> {
        val s = q.trim().lowercase()
        if (s.isEmpty()) return docs.values.take(limit).map { Hit(it, 0) }
        return docs.values.mapNotNull { d ->
            var score = 0
            val t = d.title.lowercase(); val b = d.body.lowercase()
            if (t.contains(s)) score += 10
            if (t.startsWith(s)) score += 6
            if (b.contains(s)) score += 3
            if (score > 0) Hit(d, score) else null
        }.sortedByDescending { it.score }.take(limit)
    }
}
