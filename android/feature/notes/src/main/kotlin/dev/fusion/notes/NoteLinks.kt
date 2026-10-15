package dev.fusion.notes

import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Collections

class NoteLinkGraph internal constructor(val outgoing: Map<String,Set<String>>,val revisions: Map<String,NoteRevision>) {
    fun backlinks(target:String): List<String> {
        NoteFiles.validateName(target)
        return Collections.unmodifiableList(outgoing.filter { (name,links) -> name!=target && target in links }.keys.sorted())
    }
    fun unresolved(source:String): Set<String> = Collections.unmodifiableSet((outgoing[source] ?: emptySet()).filterNot { it in outgoing }.toSet())
}

/**
 * Local exact [[note-name]] wiki references, case-sensitive like NoteFiles. Not a CommonMark AST.
 * Backslash-escaped opens, single-line backtick spans, and standalone fenced blocks (0..3 spaces) are excluded.
 * Aliases, Markdown destination links, embedded HTML, indented code and multiline inline-code interpretation are unsupported.
 */
object NoteLinks {
    const val MAX_GRAPH_BYTES=8*1024*1024
    const val MAX_OCCURRENCES=4096
    private fun bytes(text:String): ByteArray {
        require(text.length<=NoteFiles.MAX_BYTES)
        val encoded=Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text))
        require(encoded.remaining()<=NoteFiles.MAX_BYTES)
        return ByteArray(encoded.remaining()).also(encoded::get)
    }
    fun references(text:String): Set<String> { bytes(text);return scan(text).first }
    private fun scan(text:String): Pair<Set<String>,Int> {
        val targets=linkedSetOf<String>();var count=0;var fence:Char?=null;var fenceLength=0
        for(line in text.lineSequence()) {
            val indentation=line.takeWhile { it==' ' }.length
            val stripped=line.drop(indentation)
            val marker=stripped.firstOrNull()
            val run=if(marker=='`' || marker=='~') stripped.takeWhile { it==marker }.length else 0
            if(indentation<=3 && run>=3) {
                if(fence==null) { fence=marker;fenceLength=run;continue }
                if(marker==fence && run>=fenceLength && stripped.drop(run).isBlank()) { fence=null;continue }
            }
            if(fence!=null) continue
            // Precompute next equal-length backtick delimiter. Avoid repeated suffix scans for unmatched delimiters.
            val runs=ArrayList<Pair<Int,Int>>();var p=0
            while(p<line.length) {
                if(line[p]!='`') { p++;continue }
                val start=p;while(p<line.length && line[p]=='`') p++
                runs.add(start to p-start)
            }
            val next=HashMap<Int,Int>();val byLength=HashMap<Int,Int>()
            for((start,length) in runs.asReversed()) { byLength[length]?.let { next[start]=it+length };byLength[length]=start }
            var i=0
            while(i<line.length) {
                if(line[i]=='\\') { i+=2;continue }
                if(line[i]=='`') {
                    val end=next[i]
                    if(end!=null) { i=end;continue }
                    while(i<line.length && line[i]=='`') i++
                    continue
                }
                if(i+1<line.length && line[i]=='[' && line[i+1]=='[') {
                    var j=i+2
                    while(j<line.length && j-i<=66 && (line[j].isLetterOrDigit() || line[j]=='-' || line[j]=='_')) j++
                    if(j>i+2 && j+1<line.length && line[j]==']' && line[j+1]==']') {
                        val name=line.substring(i+2,j)
                        if(runCatching { NoteFiles.validateName(name) }.isFailure) { i=j+2;continue }
                        require(++count<=MAX_OCCURRENCES) { "Wiki-link count limit exceeded" }
                        targets.add(name);i=j+2;continue
                    }
                }
                i++
            }
        }
        return Collections.unmodifiableSet(targets) to count
    }
    fun build(notes: Sequence<NoteSnapshot>): NoteLinkGraph {
        var total=0;var occurrences=0
        val links=linkedMapOf<String,Set<String>>();val revisions=linkedMapOf<String,NoteRevision>()
        for(note in notes) {
            require(links.size<NoteFiles.MAX_NOTES && note.name !in links) { "Duplicate or excess note" }
            NoteFiles.validateName(note.name);require(note.revision.name==note.name)
            val encoded=bytes(note.text);total+=encoded.size;require(total<=MAX_GRAPH_BYTES) { "Backlink inventory exceeds 8 MiB; no partial index published" }
            val digest=MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }
            require(digest==note.revision.sha256) { "Note revision does not describe this text" }
            val (targets,count)=scan(note.text);occurrences+=count
            require(occurrences<=MAX_OCCURRENCES) { "Backlink occurrence budget exceeded" }
            links[note.name]=targets;revisions[note.name]=note.revision
        }
        return NoteLinkGraph(Collections.unmodifiableMap(links),Collections.unmodifiableMap(revisions))
    }
}
