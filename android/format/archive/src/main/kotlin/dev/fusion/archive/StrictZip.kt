package dev.fusion.archive
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Inflater

/** Deliberately restricted ZIP reader: no extraction paths, ZIP64, descriptors, encryption, comments or gaps.
 * Caller owns stable input during call. Sizes are checked against actual inflate, not trusted metadata.
 */
object StrictZip {
    const val MAX_INPUT=2*1024*1024;const val MAX_PART=1024*1024;const val MAX_TOTAL=2*1024*1024
    private data class Entry(val name:String,val flags:Int,val method:Int,val crc:Long,val packed:Int,val plain:Int,val local:Int)
    fun read(input:ByteArray,allowed:Set<String>):Map<String,ByteArray> {
        require(input.size in 22..MAX_INPUT && allowed.size in 1..16)
        fun u16(p:Int):Int {require(p>=0 && p+2<=input.size);return (input[p].toInt() and 255) or ((input[p+1].toInt() and 255) shl 8)}
        fun u32(p:Int):Long {require(p>=0 && p.toLong()+4<=input.size);return (0..3).fold(0L) {v,i->v or ((input[p+i].toLong() and 255) shl (8*i))}}
        fun size(p:Int)=u32(p).also {require(it<=MAX_INPUT)}.toInt()
        fun ascii(p:Int,n:Int):String {require(p>=0 && n in 1..256 && p+n<=input.size);return String(input.copyOfRange(p,p+n),Charsets.US_ASCII).also {s->require(s.all {it.code in 33..126} && s in allowed)}}
        fun extra(p:Int,n:Int) {var at=p;require(n>=0 && p+n<=input.size);while(at<p+n) {require(at+4<=p+n);require(u16(at)!=1);val len=u16(at+2);at+=4;require(at+len<=p+n);at+=len}}
        val end=input.size-22
        require(u32(end)==0x06054b50L && u16(end+4)==0 && u16(end+6)==0 && u16(end+20)==0)
        val count=u16(end+10);require(count in 1..16 && u16(end+8)==count)
        val central=size(end+16);val centralSize=size(end+12);require(central.toLong()+centralSize==end.toLong())
        var pos=central;val entries=ArrayList<Entry>();val names=HashSet<String>();var total=0L
        repeat(count) {
            require(pos+46<=end && u32(pos)==0x02014b50L)
            require(u16(pos+6)<=20 && u16(pos+34)==0)
            val flags=u16(pos+8);val method=u16(pos+10);require(flags and 0xf7ff==0 && method in setOf(0,8))
            val packed=size(pos+20);val plain=size(pos+24);require(plain<=MAX_PART);total+=plain;require(total<=MAX_TOTAL)
            val nl=u16(pos+28);val el=u16(pos+30);val cl=u16(pos+32);require(cl==0 && pos.toLong()+46+nl+el<=end)
            val name=ascii(pos+46,nl);require(names.add(name));extra(pos+46+nl,el)
            val mode=(u32(pos+38) ushr 16).toInt() and 0xf000;require(mode==0 || mode==0x8000)
            require(u32(pos+38) and 16L==0L)
            entries.add(Entry(name,flags,method,u32(pos+16),packed,plain,size(pos+42)));pos+=46+nl+el
        }
        require(pos==end)
        var offset=0;val output=LinkedHashMap<String,ByteArray>()
        for(e in entries.sortedBy {it.local}) {
            val p=e.local;require(p==offset && p+30<=central && u32(p)==0x04034b50L)
            require(u16(p+4)<=20 && u16(p+6)==e.flags && u16(p+8)==e.method)
            require(u32(p+14)==e.crc && size(p+18)==e.packed && size(p+22)==e.plain)
            val nl=u16(p+26);val el=u16(p+28);val start=p.toLong()+30+nl+el
            require(start+e.packed<=central);require(ascii(p+30,nl)==e.name);extra(p+30+nl,el)
            val bytes=if(e.method==0) {require(e.packed==e.plain);input.copyOfRange(start.toInt(),start.toInt()+e.packed)} else {
                val inflater=Inflater(true);val out=ByteArrayOutputStream();val chunk=ByteArray(8192)
                try {
                    inflater.setInput(input,start.toInt(),e.packed)
                    while(!inflater.finished()) {
                        val n=inflater.inflate(chunk,0,minOf(chunk.size,e.plain-out.size()+1))
                        require(n>0 || inflater.finished()) {"Deflate made no progress"}
                        require(out.size()+n<=e.plain);out.write(chunk,0,n)
                    }
                    require(inflater.remaining==0 && out.size()==e.plain);out.toByteArray()
                } finally {inflater.end()}
            }
            require(CRC32().also {it.update(bytes)}.value==e.crc)
            output[e.name]=bytes;offset=start.toInt()+e.packed
        }
        require(offset==central);return output
    }
}
