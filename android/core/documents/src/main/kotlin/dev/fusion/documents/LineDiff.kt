package dev.fusion.documents

import java.util.Collections

enum class LineChange { SAME, REMOVED, ADDED }
data class DiffLine(val change: LineChange, val text: String)
data class LineComparison(val lines: List<DiffLine>, val added: Int, val removed: Int)

/** Exact bounded line diff, no file writes, normalization, merge or binary/large-file promise. */
object LineDiff {
    const val MAX_LINES = 2048
    const val MAX_CELLS = 1_000_000
    private fun lines(text: String): List<String> {
        TextBytes.encodedSize(text)
        val result=ArrayList<String>()
        var start=0;var i=0
        while(i<text.length) {
            val c=text[i++]
            if(c=='\r' || c=='\n') {
                if(c=='\r' && i<text.length && text[i]=='\n') i++
                result.add(text.substring(start,i));start=i
                require(result.size<=MAX_LINES) { "Diff line budget exceeded; nothing changed" }
            }
        }
        if(start<text.length) result.add(text.substring(start))
        require(result.size<=MAX_LINES) { "Diff line budget exceeded; nothing changed" }
        return result
    }
    fun compare(before: String, after: String): LineComparison {
        val a=lines(before);val b=lines(after)
        val cells=(a.size+1L)*(b.size+1L)
        require(cells<=MAX_CELLS) { "Diff matrix budget exceeded; narrow the comparison" }
        // Intern line identities so the bounded dynamic-programming loop compares integers, not long lines.
        val names=HashMap<String,Int>()
        fun ids(input: List<String>)=input.map { line -> names.getOrPut(line) { names.size } }
        val ai=ids(a);val bi=ids(b)
        val stride=b.size+1;val lengths=IntArray(cells.toInt())
        for(i in a.indices.reversed()) for(j in b.indices.reversed()) {
            lengths[i*stride+j]=if(ai[i]==bi[j]) 1+lengths[(i+1)*stride+j+1]
                else maxOf(lengths[(i+1)*stride+j],lengths[i*stride+j+1])
        }
        val result=ArrayList<DiffLine>();var i=0;var j=0;var added=0;var removed=0
        while(i<a.size || j<b.size) {
            when {
                i<a.size && j<b.size && ai[i]==bi[j] -> { result.add(DiffLine(LineChange.SAME,a[i++]));j++ }
                i<a.size && (j==b.size || lengths[(i+1)*stride+j]>=lengths[i*stride+j+1]) -> {
                    result.add(DiffLine(LineChange.REMOVED,a[i++]));removed++
                }
                else -> { result.add(DiffLine(LineChange.ADDED,b[j++]));added++ }
            }
        }
        return LineComparison(Collections.unmodifiableList(result),added,removed)
    }
}
