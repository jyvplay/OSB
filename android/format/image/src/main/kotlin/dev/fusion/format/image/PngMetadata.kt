// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.image

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.zip.CRC32
import java.util.zip.Inflater

/** Narrow, bounded new-copy transform. NOT a malware sanitizer, anonymous image, or arbitrary PNG decoder.
 * Preserves encoded RGB/RGBA8 samples, transparency and admitted sRGB colour chunks. Visual identity,
 * steganography/encoding fingerprints remain; removing EXIF/physical-size data can change presentation.
 */
object PngMetadata {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_CHUNKS = 1024
    const val MAX_SIDE = 4096
    const val MAX_PIXELS = 4_194_304L
    private val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    private val standardChroma = longArrayOf(31270, 32900, 64000, 33000, 30000, 60000, 15000, 6000)
    private val refusedRendering = setOf("iCCP", "cICP", "mDCV", "cLLI", "sBIT", "bKGD", "acTL", "fcTL", "fdAT")

    class Report internal constructor(
        val width: Int, val height: Int, val channels: Int, val inputBytes: Int, val outputBytes: Int,
        removed: List<String>, val inputSha256: String, val outputSha256: String,
    ) {
        val removedChunkTypes: List<String> = Collections.unmodifiableList(ArrayList(removed))
        val removedBytes: Int get() = inputBytes - outputBytes
        val limit = "Only non-interlaced8-bit RGB/RGBA, admitted sRGB colour fields, <=2MiB/4096-side/4M pixels. Not anonymity, exploit safety, visual/EXIF orientation, printing or arbitrary-profile fidelity."
    }
    class Copy internal constructor(bytes: ByteArray, val report: Report) {
        private val owned = bytes.copyOf()
        fun bytes(): ByteArray = owned.copyOf()
    }
    private data class Plan(val width: Int, val height: Int, val channels: Int, val output: ByteArray, val compressed: ByteArray, val removed: List<String>)
    private fun u32(data: ByteArray, at: Int): Long = ((data[at].toLong() and 255) shl 24) or
        ((data[at+1].toLong() and 255) shl 16) or ((data[at+2].toLong() and 255) shl 8) or (data[at+3].toLong() and 255)
    private fun u16(data: ByteArray, at: Int): Int = ((data[at].toInt() and 255) shl 8) or (data[at+1].toInt() and 255)
    private fun sha(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun plan(data: ByteArray): Plan {
        require(data.size in 45..MAX_BYTES) { "PNG byte budget/framing refused" }
        require(data.copyOfRange(0,8).contentEquals(signature)) { "Not a PNG signature" }
        var at=8; var chunks=0; var width=0; var height=0; var channels=0
        var header=false; var idat=false; var idatEnded=false; var end=false
        val singleton=HashSet<String>(); val removed=ArrayList<String>()
        val output=ByteArrayOutputStream(data.size); output.write(signature)
        val compressed=ByteArrayOutputStream()
        while (at < data.size) {
            require(++chunks<=MAX_CHUNKS && data.size-at>=12) { "PNG chunk count/framing refused" }
            val length=u32(data,at)
            require(length<=MAX_BYTES && at.toLong()+length+12<=data.size) { "PNG chunk length refused" }
            val n=length.toInt(); val body=at+8; val next=body+n+4
            val typeBytes=data.copyOfRange(at+4,at+8)
            require(typeBytes.all { (it.toInt() in 65..90) || (it.toInt() in 97..122) } && typeBytes[2].toInt() and 32 == 0) { "PNG chunk-name/reserved bit refused" }
            val type=typeBytes.toString(Charsets.US_ASCII)
            val crc=CRC32().apply { update(data,at+4,n+4) }.value
            require(crc==u32(data,body+n)) { "PNG CRC mismatch" }
            require(header || type=="IHDR") { "IHDR must be first" }
            if (idat && type!="IDAT") idatEnded=true
            var keep=true
            when (type) {
                "IHDR" -> {
                    require(!header && at==8 && n==13) { "Duplicate/invalid IHDR" }
                    val w=u32(data,body); val h=u32(data,body+4)
                    require(w in 1..MAX_SIDE.toLong() && h in 1..MAX_SIDE.toLong() && w*h<=MAX_PIXELS) { "PNG pixel/dimension budget refused" }
                    require(data[body+8].toInt()==8 && data[body+9].toInt() in setOf(2,6) && data[body+10].toInt()==0 && data[body+11].toInt()==0 && data[body+12].toInt()==0) { "Only non-interlaced8-bit RGB/RGBA admitted" }
                    width=w.toInt();height=h.toInt();channels=if(data[body+9].toInt()==6) 4 else 3;header=true
                }
                "IDAT" -> {
                    require(!idatEnded) { "IDAT must be consecutive" }
                    idat=true; compressed.write(data,body,n)
                }
                "IEND" -> {
                    require(n==0 && idat && compressed.size()>0 && !end && next==data.size) { "Invalid IEND/missing image/trailing bytes" }
                    end=true
                }
                "sRGB" -> {
                    require(!idat && singleton.add(type) && n==1 && (data[body].toInt() and 255) in 0..3) { "Invalid/duplicate sRGB" }
                }
                "gAMA" -> {
                    require(!idat && singleton.add(type) && n==4 && u32(data,body)==45455L) { "Only canonical sRGB gamma admitted" }
                }
                "cHRM" -> {
                    require(!idat && singleton.add(type) && n==32 && standardChroma.indices.all { u32(data,body+4*it)==standardChroma[it] }) { "Only canonical sRGB chromaticities admitted" }
                }
                "tRNS" -> {
                    require(!idat && singleton.add(type) && channels==3 && n==6 && (0..2).all { u16(data,body+it*2)<=255 }) { "Invalid/duplicate transparency" }
                }
                else -> {
                    require(type !in refusedRendering) { "Animation/profile/significant-bit/background mode not admitted" }
                    require(typeBytes[0].toInt() and 32 != 0) { "Unknown/palette critical chunk refused" }
                    if(type=="pHYs") require(!idat && singleton.add(type) && n==9 && u32(data,body)>0 && u32(data,body)==u32(data,body+4) && (data[body+8].toInt() and 255) in 0..1) { "Non-square/invalid physical-pixel metadata refused" }
                    // No decompression or interpretation of removed text/EXIF/private metadata.
                    keep=false;removed.add(type)
                }
            }
            if(keep) output.write(data,at,next-at)
            at=next
        }
        require(header && idat && end) { "Incomplete PNG" }
        return Plan(width,height,channels,output.toByteArray(),compressed.toByteArray(),removed)
    }

    /** Fully consumes the single zlib stream with a fixed8KiB buffer, exact row budget and valid PNG filters. */
    private fun scanlines(plan: Plan) {
        val stride=plan.width.toLong()*plan.channels+1
        val expected=stride*plan.height
        val inflater=Inflater()
        var total=0L;var nextFilter=0L
        val buffer=ByteArray(8192)
        try {
            inflater.setInput(plan.compressed)
            while(!inflater.finished()) {
                val count=inflater.inflate(buffer)
                require(total+count<=expected) { "Inflated image exceeds declared scanlines" }
                while(nextFilter<total+count) {
                    require((buffer[(nextFilter-total).toInt()].toInt() and 255) in 0..4) { "Invalid PNG filter" }
                    nextFilter+=stride
                }
                total+=count
                if(count==0 && !inflater.finished()) throw IllegalArgumentException("Incomplete/dictionary/no-progress zlib stream")
            }
            require(total==expected && inflater.remaining==0) { "Wrong decoded length or extra zlib data" }
        } catch (bad: java.util.zip.DataFormatException) {
            throw IllegalArgumentException("Invalid PNG zlib stream",bad)
        } finally { inflater.end() }
    }

    fun clean(input: ByteArray): Copy {
        require(input.size<=MAX_BYTES) { "PNG input cap" }
        val source=input.copyOf();val p=plan(source);scanlines(p)
        val output=p.output
        // A clean result must be structurally stable on a second pass; no metadata or hidden trailing payload remains in this admitted grammar.
        val checked=plan(output)
        check(checked.removed.isEmpty() && checked.output.contentEquals(output) && checked.compressed.contentEquals(p.compressed))
        return Copy(output,Report(p.width,p.height,p.channels,source.size,output.size,p.removed,sha(source),sha(output)))
    }

    /** Minimal non-inflating parent proof: output must be exactly the admitted retained chunks of the source.
     * This is not independent decoder verification or a guarantee against a Byzantine worker accepting bad deflate.
     */
    data class Derivative(val width:Int,val height:Int,val channels:Int,val removedChunks:Int)
    fun derivative(input: ByteArray, output: ByteArray): Derivative? {
        require(input.size<=MAX_BYTES && output.size<=MAX_BYTES) { "PNG proof byte cap" }
        val source=input.copyOf();val candidate=output.copyOf()
        val p=plan(source);val q=plan(candidate)
        if(!q.removed.isEmpty() || !p.output.contentEquals(candidate) || !p.compressed.contentEquals(q.compressed)) return null
        return Derivative(q.width,q.height,q.channels,p.removed.size)
    }
    fun verifyDerivative(input: ByteArray, output: ByteArray): Boolean = derivative(input,output)!=null
}
