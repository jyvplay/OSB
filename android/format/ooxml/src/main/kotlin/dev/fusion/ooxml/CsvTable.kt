package dev.fusion.ooxml

import java.io.*
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections
import java.security.MessageDigest

/** Original bounded CSV dialect. No sniffing, formulas, type inference or full Office import. */
class CsvTable private constructor(val rows:List<List<String>>) {
    val columns:Int get()=rows.firstOrNull()?.size ?: 0
    fun project(indices:List<Int>):CsvTable {
        require(indices.isNotEmpty() && indices.size<=MAX_COLUMNS)
        val copy=indices.toList();require(copy.distinct().size==copy.size && copy.all {it in 0 until columns})
        return of(rows.map {row->copy.map {row[it]}})
    }
    /** Complete bounded display; escape controls/bidi formatting ONLY in preview, never in exported cells. */
    fun preview():String=rows.mapIndexed {r,row->
        "Row ${r+1}: "+row.mapIndexed {c,s->
            val shown=buildString {for(ch in s) when(ch) {
                '\\'->append("\\\\");'"'->append("\\\"");'\r'->append("\\r");'\n'->append("\\n");'\t'->append("\\t")
                else->if(Character.getType(ch)==Character.FORMAT.toInt()) append("\\u"+ch.code.toString(16).padStart(4,'0')) else append(ch)
            }}
            "[${c+1}] \"$shown\""
        }.joinToString(" | ")
    }.joinToString("\n")
    fun workbook():WorkbookExport=LiteralWorkbook.export("Selected columns",rows.map {r->r.map {LiteralCell.Text(it)}})
    /** Bounded data-only reply. Parent validates this frame, not the original CSV grammar. */
    fun frame():ByteArray {
        val out=ByteArrayOutputStream()
        DataOutputStream(out).use {d->d.writeInt(0x46435431);d.writeInt(rows.size);d.writeInt(columns)
            rows.forEach {r->r.forEach {s->val b=utf8(s);d.writeInt(b.size);d.write(b)}}}
        return out.toByteArray().also {require(it.size<=MAX_FRAME)}
    }
    companion object {
        const val MAX_INPUT=65536;const val MAX_FRAME=131072
        const val MAX_ROWS=256;const val MAX_COLUMNS=32;const val MAX_CELL=4096
        private val emptyUtf8=ByteArray(0) // No mutable elements; shape is encoded separately.
        fun utf8(s:String):ByteArray {
            require(s.length<=MAX_INPUT)
            if(s.isEmpty()) return emptyUtf8
            return try {val b=Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(s));ByteArray(b.remaining()).also {b.get(it)}}
            catch(e:java.nio.charset.CharacterCodingException) {throw IllegalArgumentException("Malformed Unicode",e)}
        }
        private fun text(b:ByteArray):String=if(b.isEmpty()) "" else try {Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString()}
            catch(e:java.nio.charset.CharacterCodingException) {throw IllegalArgumentException("Malformed UTF8",e)}
        private fun valid(s:String) {require(s.length<=MAX_CELL && s.none {it.isISOControl() && it!='\r' && it!='\n' && it!='\t'})}
        fun of(input:List<List<String>>):CsvTable {
            require(input.size<=MAX_ROWS && input.all {it.size in 1..MAX_COLUMNS})
            val width=input.firstOrNull()?.size ?: 0
            var budget=12L
            val frozen=input.map {r->require(r.size==width);Collections.unmodifiableList(r.map {s->valid(s);budget+=4+utf8(s).size;require(budget<=MAX_FRAME);s})}
            return CsvTable(Collections.unmodifiableList(frozen))
        }
        fun parse(input:ByteArray):CsvTable {
            require(input.size<=MAX_INPUT)
            val raw=text(input);val s=if(raw.startsWith('\uFEFF')) raw.substring(1) else raw
            if(s.isEmpty()) return of(emptyList())
            val rows=ArrayList<List<String>>();val row=ArrayList<String>();val cell=StringBuilder()
            var quoted=false;var afterQuote=false;var atStart=true;var justEnded=false;var i=0
            fun field() {val value=cell.toString();valid(value);require(row.size<MAX_COLUMNS);row.add(value);cell.setLength(0);atStart=true;afterQuote=false}
            fun record() {field();require(rows.size<MAX_ROWS);rows.add(row.toList());row.clear();justEnded=true}
            while(i<s.length) {
                val c=s[i++];justEnded=false
                if(quoted) {
                    if(c=='"') {if(i<s.length && s[i]=='"') {cell.append('"');i++} else {quoted=false;afterQuote=true}}
                    else cell.append(c)
                } else when(c) {
                    '"' -> {require(atStart && !afterQuote);quoted=true;atStart=false}
                    ',' -> field()
                    '\n' -> record()
                    '\r' -> {require(i<s.length && s[i++]=='\n');record()}
                    else -> {require(!afterQuote);cell.append(c);atStart=false}
                }
                require(cell.length<=MAX_CELL)
            }
            require(!quoted)
            if(!justEnded) record()
            return of(rows)
        }
        fun readFrame(bytes:ByteArray):CsvTable {
            require(bytes.size in 12..MAX_FRAME)
            try {DataInputStream(ByteArrayInputStream(bytes)).use {d->
                require(d.readInt()==0x46435431);val n=d.readInt();val w=d.readInt()
                require(n in 0..MAX_ROWS && (if(n==0) w==0 else w in 1..MAX_COLUMNS))
                val rows=List(n) {List(w) {val len=d.readInt();require(len in 0..minOf(MAX_CELL*4,d.available()));val b=ByteArray(len);d.readFully(b);text(b)}}
                require(d.available()==0);return of(rows)
            }} catch(e:EOFException) {throw IllegalArgumentException("Truncated table frame",e)}
        }
    }
}
/** Metadata correlation only. Compromised worker may return plausible wrong values. */
class OwnedCsvRequest(input:ByteArray) {
    private val frozen:ByteArray
    val size:Int get()=frozen.size
    val digest:String
    init {require(input.size<=CsvTable.MAX_INPUT);frozen=input.copyOf();digest=MessageDigest.getInstance("SHA-256").digest(frozen).joinToString("") {"%02x".format(it)}}
    fun bytes()=frozen.copyOf()
    fun accept(size:Int,digest:String,frame:ByteArray):CsvTable {require(size==this.size && digest==this.digest);return CsvTable.readFrame(frame)}
}
