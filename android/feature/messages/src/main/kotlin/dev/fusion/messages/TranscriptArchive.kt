package dev.fusion.messages

import java.io.*
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.CRC32

data class TranscriptLine(val id:String,val author:String,val text:String,val epochMillis:Long)
/** Explicit plaintext transcript codec. CRC detects accidents, NOT malicious edits/authenticity.
 * No system-message access, send operation, attachment handling or transport is implied.
 */
object TranscriptArchive {
    const val MAX_BYTES=2*1024*1024
    private const val MAGIC=0x464d5431
    fun encode(lines:List<TranscriptLine>):ByteArray {
        require(lines.size<=256);val ids=HashSet<String>();var previous=0L
        val bytes=ByteArrayOutputStream();val out=DataOutputStream(bytes);out.writeInt(MAGIC);out.writeInt(lines.size)
        fun text(value:String,max:Int) {
            require(value.length<=max)
            val encoded=Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
            require(bytes.size()+4L+encoded.remaining()+12<=MAX_BYTES)
            val b=ByteArray(encoded.remaining());encoded.get(b);out.writeInt(b.size);out.write(b)
        }
        for(line in lines) {
            require(line.id.matches(Regex("[A-Za-z0-9_-]{1,64}")) && ids.add(line.id))
            require(line.epochMillis>=previous);previous=line.epochMillis
            text(line.id,64);text(line.author,256);text(line.text,8192);out.writeLong(line.epochMillis)
        }
        out.flush();val body=bytes.toByteArray();out.writeInt(CRC32().also { it.update(body) }.value.toInt());out.flush()
        return bytes.toByteArray().also { require(it.size<=MAX_BYTES) }
    }
    fun decode(frame:ByteArray):List<TranscriptLine> {
        require(frame.size in 12..MAX_BYTES)
        val crc=CRC32().also { it.update(frame,0,frame.size-4) }.value
        require(ByteBuffer.wrap(frame,frame.size-4,4).int.toLong() and 0xffffffffL==crc) { "CRC mismatch; not authentication" }
        val stream=DataInputStream(ByteArrayInputStream(frame,0,frame.size-4))
        require(stream.readInt()==MAGIC);val count=stream.readInt();require(count in 0..256)
        fun text(maxBytes:Int):String {
            val length=stream.readInt();require(length in 0..maxBytes && length<=stream.available())
            val b=ByteArray(length);stream.readFully(b)
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString()
        }
        val result=List(count) { TranscriptLine(text(64),text(1024),text(32768),stream.readLong()) }
        require(stream.available()==0 && encode(result).contentEquals(frame)) { "Noncanonical transcript" }
        return result
    }
}
