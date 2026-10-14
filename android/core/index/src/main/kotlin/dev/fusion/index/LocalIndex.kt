package dev.fusion.index

import java.util.Locale

/** Bounded in-memory literal index. No disk crawler, FTS, embeddings or provider access. */
data class Doc(val id: String, val title: String, val body: String, val source: String)
data class Hit(val doc: Doc, val score: Int)

class LocalIndex {
    companion object {
        const val MAX_DOCS=2048
        const val MAX_BODY=65536
        const val MAX_TOTAL_UNITS=8*1024*1024 // UTF-16 code units, not bytes or measured heap.
        const val MAX_QUERY=1024
    }
    private val docs=linkedMapOf<String,Doc>()
    private var units=0
    private fun cost(d:Doc)=d.id.length+d.title.length+d.body.length+d.source.length
    @Synchronized fun upsert(d:Doc) {
        require(d.id.isNotBlank() && d.id.length<=256 && d.title.length<=512 && d.body.length<=MAX_BODY && d.source.isNotBlank() && d.source.length<=1024)
        require(d.id in docs || docs.size<MAX_DOCS)
        val next=units-(docs[d.id]?.let(::cost) ?: 0)+cost(d)
        require(next<=MAX_TOTAL_UNITS) { "Index payload budget exceeded; previous content retained" }
        docs[d.id]=d; units=next
    }
    @Synchronized fun remove(id:String) { docs.remove(id)?.let { units-=cost(it) } }
    @Synchronized fun clear() { docs.clear(); units=0 }
    @Synchronized fun size():Int=docs.size
    @Synchronized fun payloadUnits():Int=units
    @Synchronized fun search(q:String,limit:Int=20):List<Hit> {
        require(q.length<=MAX_QUERY && limit in 0..100)
        val query=q.trim().lowercase(Locale.ROOT)
        if(query.isEmpty()) return docs.values.take(limit).map { Hit(it,0) }
        return docs.values.mapNotNull { d ->
            val title=d.title.lowercase(Locale.ROOT); val body=d.body.lowercase(Locale.ROOT)
            val score=(if(title.contains(query)) 10 else 0)+(if(title.startsWith(query)) 6 else 0)+(if(body.contains(query)) 3 else 0)
            if(score>0) Hit(d,score) else null
        }.sortedByDescending { it.score }.take(limit)
    }
}
