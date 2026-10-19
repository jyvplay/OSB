// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.format.image.PngMetadata
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Deflater

/** Separate consumer of actual production classes. Generated PNG fixtures are parser inputs, not native UI evidence. */
object PngChecks {
    private var checks=0
    private fun ok(b:Boolean, why:String="") { checks++;check(b) {"PNG check$checks: $why"} }
    private fun refuse(f:()->Unit) { checks++;check(runCatching(f).isFailure) {"Invalid PNG accepted at$checks"} }
    private val sig=byteArrayOf(137.toByte(),80,78,71,13,10,26,10)
    private fun chunk(type:String,data:ByteArray):ByteArray {
        val code=type.toByteArray(Charsets.US_ASCII);val crc=CRC32().apply {update(code);update(data)}.value.toInt()
        return ByteBuffer.allocate(data.size+12).putInt(data.size).put(code).put(data).putInt(crc).array()
    }
    private fun zlib(data:ByteArray):ByteArray {
        val d=Deflater();val out=ByteArrayOutputStream();val buf=ByteArray(2048)
        try {d.setInput(data);d.finish();while(!d.finished()){val n=d.deflate(buf);check(n>0);out.write(buf,0,n)}} finally {d.end()}
        return out.toByteArray()
    }
    private fun ihdr(w:Int,h:Int,type:Int=6,depth:Int=8,interlace:Int=0)=chunk("IHDR",ByteBuffer.allocate(13).putInt(w).putInt(h).put(depth.toByte()).put(type.toByte()).put(0).put(0).put(interlace.toByte()).array())
    private fun file(w:Int=3,h:Int=2,type:Int=6,metadata:Boolean=true,encoded:ByteArray?=null):ByteArray {
        val channels=if(type==6)4 else 3
        val raw=ByteArray((w*channels+1)*h)
        for(y in 0 until h) for(x in 0 until w*channels) raw[y*(w*channels+1)+1+x]=((x*31+y*17) and 255).toByte()
        val compressed=encoded ?: zlib(raw)
        val split=compressed.size/2
        return sig+ihdr(w,h,type)+chunk("sRGB",byteArrayOf(0))+
            (if(metadata)chunk("tEXt","Author\u0000R25-private-metadata-marker".toByteArray())+chunk("eXIf",byteArrayOf(73,73,42,0,8,0,0,0,0,0,0,0,0,0)) else byteArrayOf())+
            chunk("IDAT",compressed.copyOfRange(0,split))+chunk("IDAT",compressed.copyOfRange(split,compressed.size))+
            (if(metadata)chunk("iTXt","Comment\u0000\u0000\u0000\u0000\u0000private-comment".toByteArray()) else byteArrayOf())+chunk("IEND",byteArrayOf())
    }
    @JvmStatic fun main(args:Array<String>) {
        if(args.firstOrNull()=="clean") {
            require(args.size==3)
            val input=Path.of(args[1]);val output=Path.of(args[2])
            require(!Files.isSymbolicLink(input) && Files.size(input)<=PngMetadata.MAX_BYTES && !Files.exists(output))
            val bytes=Files.readAllBytes(input);val copy=PngMetadata.clean(bytes)
            check(PngMetadata.verifyDerivative(bytes,copy.bytes()))
            Files.write(output,copy.bytes(),StandardOpenOption.CREATE_NEW)
            println("{\"width\":${copy.report.width},\"height\":${copy.report.height},\"input_bytes\":${copy.report.inputBytes},\"output_bytes\":${copy.report.outputBytes},\"removed\":${copy.report.removedChunkTypes.size},\"input_sha256\":\"${copy.report.inputSha256}\",\"output_sha256\":\"${copy.report.outputSha256}\"}")
            return
        }
        for(w in 1..25) for(h in 1..17) for(type in listOf(2,6)) {
            val source=file(w,h,type);val before=source.copyOf();val copy=PngMetadata.clean(source)
            ok(source.contentEquals(before));ok(copy.report.width==w && copy.report.height==h)
            ok(copy.report.removedChunkTypes==listOf("tEXt","eXIf","iTXt"));ok(copy.report.removedBytes>0)
            ok(PngMetadata.verifyDerivative(source,copy.bytes()))
            ok(PngMetadata.clean(copy.bytes()).bytes().contentEquals(copy.bytes()))
            val changed=copy.bytes();changed.fill(0);ok(!copy.bytes().contentEquals(changed))
            refuse {(copy.report.removedChunkTypes as MutableList).clear()}
        }
        val normal=file()
        for(n in normal.indices) refuse {PngMetadata.clean(normal.copyOf(n))}
        for(i in normal.indices step 3) {val bad=normal.copyOf();bad[i]=(bad[i].toInt() xor 1).toByte();refuse{PngMetadata.clean(bad)}}
        refuse{PngMetadata.clean(normal+byteArrayOf(0))}
        refuse{PngMetadata.clean(ByteArray(PngMetadata.MAX_BYTES+1))}
        for((w,h) in listOf(0 to 1,1 to 0,4097 to 1,4096 to 4096,Int.MAX_VALUE to Int.MAX_VALUE))
            refuse {PngMetadata.clean(sig+ihdr(w,h)+chunk("IDAT",zlib(byteArrayOf(0,0,0,0,0)))+chunk("IEND",byteArrayOf()))}
        for(type in listOf(0,1,3,4,5,7)) refuse {PngMetadata.clean(sig+ihdr(1,1,type)+chunk("IDAT",zlib(byteArrayOf(0,0,0,0,0)))+chunk("IEND",byteArrayOf()))}
        for(depth in listOf(1,2,4,16)) refuse {PngMetadata.clean(sig+ihdr(1,1,6,depth)+chunk("IDAT",zlib(byteArrayOf(0,0,0,0,0)))+chunk("IEND",byteArrayOf()))}
        refuse {PngMetadata.clean(sig+ihdr(1,1,6,8,1)+chunk("IDAT",zlib(byteArrayOf(0,0,0,0,0)))+chunk("IEND",byteArrayOf()))}
        val image=zlib(byteArrayOf(0,10,20,30,255.toByte()))
        for(name in listOf("ABCD","PLTE","acTL","fcTL","fdAT","iCCP","cICP","mDCV","cLLI","sBIT","bKGD","abcD")) {
            refuse{PngMetadata.clean(sig+ihdr(1,1)+chunk(name,byteArrayOf())+chunk("IDAT",image)+chunk("IEND",byteArrayOf()))}
        }
        refuse{PngMetadata.clean(file(1,1,encoded=image+image))}
        refuse{PngMetadata.clean(file(1,1,encoded=zlib(ByteArray(65536))))}
        refuse{PngMetadata.clean(file(1,1,encoded=zlib(byteArrayOf(5,0,0,0,0))))}
        refuse{PngMetadata.clean(file(1,1,encoded=zlib(byteArrayOf(0,0,0,0))))}
        refuse{PngMetadata.clean(sig+ihdr(1,1)+chunk("IDAT",image.copyOfRange(0,4))+chunk("tEXt",byteArrayOf())+chunk("IDAT",image.copyOfRange(4,image.size))+chunk("IEND",byteArrayOf()))}
        refuse{PngMetadata.clean(sig+ihdr(1,1)+ihdr(1,1)+chunk("IDAT",image)+chunk("IEND",byteArrayOf()))}
        refuse{PngMetadata.clean(sig+ihdr(1,1)+chunk("gAMA",ByteBuffer.allocate(4).putInt(100000).array())+chunk("IDAT",image)+chunk("IEND",byteArrayOf()))}
        val many=ByteArrayOutputStream().apply {write(sig);write(ihdr(1,1));repeat(1024){write(chunk("tEXt",byteArrayOf()))};write(chunk("IDAT",image));write(chunk("IEND",byteArrayOf()))}.toByteArray()
        refuse{PngMetadata.clean(many)}
        val boundary=PngMetadata.clean(file(4096,1024,metadata=false))
        ok(boundary.report.width==4096 && boundary.report.height==1024)
        val small=file(1,1,metadata=false)
        val exact=sig+chunk("IHDR",small.copyOfRange(16,29))+chunk("stEg",ByteArray(PngMetadata.MAX_BYTES-small.size-12))+small.copyOfRange(33,small.size)
        ok(exact.size==PngMetadata.MAX_BYTES)
        ok(PngMetadata.clean(exact).report.removedChunkTypes==listOf("stEg"))
        val maxChunks=ByteArrayOutputStream().apply {write(sig);write(ihdr(1,1));repeat(PngMetadata.MAX_CHUNKS-3){write(chunk("tEXt",byteArrayOf()))};write(chunk("IDAT",image));write(chunk("IEND",byteArrayOf()))}.toByteArray()
        ok(PngMetadata.clean(maxChunks).report.removedChunkTypes.size==PngMetadata.MAX_CHUNKS-3)
        val clean=PngMetadata.clean(normal).bytes()
        ok(!PngMetadata.verifyDerivative(normal,normal))
        val different=PngMetadata.clean(file(4,2)).bytes();ok(!PngMetadata.verifyDerivative(normal,different))
        val d=Files.createTempDirectory("r25-png-real-files-")
        try {
            val input=d.resolve("source.png");Files.write(input,normal)
            val copy=d.resolve("copy.png");Files.write(copy,clean,StandardOpenOption.CREATE_NEW)
            ok(Files.readAllBytes(input).contentEquals(normal));ok(PngMetadata.verifyDerivative(Files.readAllBytes(input),Files.readAllBytes(copy)))
        } finally {Files.walk(d).use{paths->paths.sorted(Comparator.reverseOrder()).forEach{Files.delete(it)}}}
        println("R25 PNG PASS: $checks production-kernel assertions. Real files/pixel-data preservation/bounds only; NOT native UI, arbitrary image sanitization or anonymity.")
    }
}
