package dev.fusion.filesui

import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

data class ListingItem(val id: String, val name: String, val directory: Boolean, val bytes: Long?)
enum class ListingOrder { NAME, LARGEST }
/** Bounded metadata query, not provider enumeration, identity verification or file mutation. */
object DirectoryQuery {
    fun select(input: List<ListingItem>, query: String = "", order: ListingOrder = ListingOrder.NAME): List<ListingItem> {
        require(input.size<=5000 && query.length<=128)
        fun unicode(s:String) { Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(s)) }
        unicode(query)
        var units=0L;val ids=HashSet<String>()
        for(e in input) {
            require(e.id.length in 1..4096 && e.name.length in 1..1024 && (e.bytes==null || e.bytes>=0))
            require(ids.add(e.id));units+=e.id.length+e.name.length;require(units<=2*1024*1024)
            unicode(e.id);unicode(e.name)
        }
        val q=query.lowercase(Locale.ROOT)
        val names=input.associate { it.id to it.name.lowercase(Locale.ROOT) }
        return input.filter { names.getValue(it.id).contains(q) }.sortedWith(Comparator { a,b ->
            if(a.directory!=b.directory) return@Comparator if(a.directory) -1 else 1
            if(order==ListingOrder.LARGEST && !a.directory) {
                val c=when { a.bytes==null && b.bytes!=null -> 1; a.bytes!=null && b.bytes==null -> -1
                    a.bytes!=null && b.bytes!=null -> b.bytes.compareTo(a.bytes); else -> 0 }
                if(c!=0) return@Comparator c
            }
            val natural=natural(names.getValue(a.id),names.getValue(b.id))
            if(natural!=0) natural else a.name.compareTo(b.name).takeIf { it!=0 } ?: a.id.compareTo(b.id)
        })
    }
    /** Decimal runs compare by significant length, never parse an unbounded integer. */
    private fun natural(a:String,b:String):Int {
        var i=0;var j=0
        while(i<a.length && j<b.length) {
            if(a[i] in '0'..'9' && b[j] in '0'..'9') {
                val ai=i;val bj=j
                while(i<a.length && a[i] in '0'..'9') i++
                while(j<b.length && b[j] in '0'..'9') j++
                var x=ai;var y=bj
                while(x<i && a[x]=='0') x++
                while(y<j && b[y]=='0') y++
                if(i-x!=j-y) return (i-x).compareTo(j-y)
                for(k in 0 until i-x) if(a[x+k]!=b[y+k]) return a[x+k].compareTo(b[y+k])
                if(i-ai!=j-bj) return (i-ai).compareTo(j-bj)
            } else { if(a[i]!=b[j]) return a[i].compareTo(b[j]);i++;j++ }
        }
        return (a.length-i).compareTo(b.length-j)
    }
}
