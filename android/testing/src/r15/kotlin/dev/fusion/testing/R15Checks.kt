package dev.fusion.testing

import dev.fusion.lens.*
import dev.fusion.capture.*
import dev.fusion.messages.*
import dev.fusion.vault.*
import dev.fusion.model.*
import dev.fusion.ooxml.*
import java.nio.file.Files
import java.util.zip.CRC32
import java.nio.ByteBuffer

object R15Checks {
    private var checks=0
    private fun eq(a:Any?,b:Any?) { checks++;check(a==b) { "#$checks $a != $b" } }
    private fun close(a:Double,b:Double) { checks++;check(kotlin.math.abs(a-b)<=1e-6) { "#$checks coordinate $a != $b" } }
    private fun no(f:()->Unit) { checks++;try { f() } catch(_:IllegalArgumentException) { return } catch(_:java.io.IOException) { return } catch(_:java.security.GeneralSecurityException) { return };error("Attack accepted #$checks") }
    @JvmStatic fun main(args:Array<String>) {
        for(w in listOf(1,100,32768)) for(h in listOf(1,100,32768)) for(vw in listOf(1,101,32768)) for(vh in listOf(1,101,32768)) for(turn in 0..3) for(mirror in listOf(false,true)) {
            val t=ViewportTransform(w,h,vw,vh,turn,mirror)
            for(point in listOf(LensPoint(0.0,0.0),LensPoint(w.toDouble(),h.toDouble()),LensPoint(w/2.0,h/2.0))) {
                val f=t.forward(point);val back=requireNotNull(t.inverse(LensPoint(f.x.coerceIn(0.0,vw.toDouble()),f.y.coerceIn(0.0,vh.toDouble()))))
                close(point.x,back.x);close(point.y,back.y)
            }
            val crop=ViewportTransform(w,h,vw,vh,turn,mirror,ViewportMode.CROP)
            val p=LensPoint(vw/2.0,vh/2.0);val f=crop.forward(requireNotNull(crop.inverse(p)));close(p.x,f.x);close(p.y,f.y)
        }
        eq(null,ViewportTransform(200,100,100,100).inverse(LensPoint(50.0,1.0)))
        no { ViewportTransform(0,1,1,1) };no { ViewportTransform(1,1,1,1,4) }
        no { ViewportTransform(1,1,1,1).forward(LensPoint(Double.NaN,0.0)) }
        val time=RecordingTimeline();no { time.read(0) };time.start(10);time.pause(20);time.resume(40)
        eq(RecordingTime(40,20,20,false),time.read(50));no { time.pause(49) }
        eq(RecordingTime(50,30,20,true),time.stop(60));eq(time.read(60),time.read(100))
        eq(listOf(RecordingSpan(10,20),RecordingSpan(40,60)),time.completedSpans())
        no { time.resume(101) };no { time.start(101) };no { time.stop(101) }
        val maxTime=RecordingTimeline();maxTime.start(0);eq(Long.MAX_VALUE,maxTime.stop(Long.MAX_VALUE).activeMillis)
        val many=RecordingTimeline();many.start(0);repeat(1024) { many.pause(it.toLong());if(it<1023) many.resume(it.toLong()) }
        no { many.resume(1024) };eq(1024,many.completedSpans().size);eq(true,many.stop(1024).stopped)
        val lines=listOf(TranscriptLine("a","Author🙂","one\ntwo\t<>&",0),TranscriptLine("b","","界",1))
        val frame=TranscriptArchive.encode(lines);eq(lines,TranscriptArchive.decode(frame))
        val file=Files.createTempFile("r15-transcript-",".fmt")
        try { Files.write(file,frame);eq(lines,TranscriptArchive.decode(Files.readAllBytes(file))) } finally { Files.delete(file) }
        eq(emptyList<TranscriptLine>(),TranscriptArchive.decode(TranscriptArchive.encode(emptyList())))
        no { TranscriptArchive.encode(lines+lines[0]) };no { TranscriptArchive.encode(lines.reversed()) }
        no { TranscriptArchive.encode(listOf(lines[0].copy(text="\uD800"))) }
        no { TranscriptArchive.encode(List(257){lines[0].copy(id="m$it")}) }
        no { TranscriptArchive.decode(frame+byteArrayOf(0)) };no { TranscriptArchive.decode(frame.copyOf(11)) }
        for(i in frame.indices) no { TranscriptArchive.decode(frame.copyOf().also { it[i]=(it[i].toInt() xor 1).toByte() }) }
        // CRC is not authentication: an attacker who can rewrite the frame can recompute it.
        val forged=TranscriptArchive.encode(listOf(TranscriptLine("x","","A",0))).copyOf()
        val bodyIndex=8+4+1+4+4;forged[bodyIndex]='B'.code.toByte()
        ByteBuffer.wrap(forged,forged.size-4,4).putInt(CRC32().also { it.update(forged,0,forged.size-4) }.value.toInt())
        eq("B",TranscriptArchive.decode(forged).single().text)
        val key=ByteArray(32){it.toByte()};val context="own-test-context".toByteArray();val plaintext="private bytes\u0000🙂".toByteArray()
        val sealed=VaultEnvelope.seal(plaintext,key,context);eq(true,plaintext.contentEquals(VaultEnvelope.open(sealed,key,context)))
        no { VaultEnvelope.open(sealed,key,byteArrayOf()) };no { VaultEnvelope.open(sealed,ByteArray(32),context) }
        for(i in sealed.indices) no { VaultEnvelope.open(sealed.copyOf().also { it[i]=(it[i].toInt() xor 1).toByte() },key,context) }
        no { VaultEnvelope.seal(byteArrayOf(),ByteArray(16)) };no { VaultEnvelope.seal(ByteArray(VaultEnvelope.MAX_PLAINTEXT+1),key) }
        eq(0,VaultEnvelope.open(VaultEnvelope.seal(byteArrayOf(),key),key).size)
        val max=ByteArray(VaultEnvelope.MAX_PLAINTEXT){(it%251).toByte()};eq(true,max.contentEquals(VaultEnvelope.open(VaultEnvelope.seal(max,key),key)))
        // Replay is intentionally NOT prevented; do not promote an AEAD envelope to freshness/rollback protection.
        eq(true,VaultEnvelope.open(sealed,key,context).contentEquals(VaultEnvelope.open(sealed,key,context)))
        val nonces=HashSet<String>();repeat(100) { val b=VaultEnvelope.seal(plaintext,key);eq(true,nonces.add(b.sliceArray(8..19).joinToString(","))) }
        for(module in listOf("files","editor","notes","calculator","browser","settings")) for(workspace in 1..3) {
            val target=LaunchTarget(module,workspace);eq(target,LaunchLinks.decode(LaunchLinks.encode(target)))
        }
        for(link in listOf("fusion://open/editor?workspace=0","fusion://open/editor?workspace=1#x","fusion://open/editor?workspace=1&workspace=2","FUSION://open/editor?workspace=1","fusion://u@open/editor?workspace=1","fusion://open:80/editor?workspace=1","fusion://open/%65ditor?workspace=1","fusion://open/beam?workspace=1","https://open/editor?workspace=1","fusion://open/editor?workspace=01")) no { LaunchLinks.decode(link) }
        val cells=mapOf("A1" to "2","B1" to "=A1*3+1")
        eq(cells,SheetFrame.decode(SheetFrame.encode(cells)));eq(mapOf("A1" to "2","B1" to "7"),SheetFrame.readResult(SheetFrame.execute(SheetFrame.encode(cells))))
        eq(emptyMap<String,String>(),SheetFrame.readResult(SheetFrame.execute(SheetFrame.encode(emptyMap()))))
        no { SheetFrame.decode(SheetFrame.encode(cells)+byteArrayOf(10)) }
        no { SheetFrame.execute(SheetFrame.encode(mapOf("A1" to "=A1"))) };no { SheetFrame.execute(SheetFrame.encode(mapOf("A1" to "=SUM(A2)"))) }
        no { SheetFrame.readResult("FSR1\nA1\t1.0\n".toByteArray()) }
        no { SheetFrame.readResult("FSR1\nA1\t1\nA1\t2\n".toByteArray()) }
        for(key in listOf("ZZ9999","DY1","DX1001","A0")) {
            no { SheetFrame.readResult("FSR1\n$key\t1\n".toByteArray()) }
            no { SheetFrame.encode(mapOf(key to "1")) }
        }
        eq(mapOf("DX1000" to "1"),SheetFrame.readResult("FSR1\nDX1000\t1\n".toByteArray()))
        // Additional boundary attacks: size validation precedes allocation/crypto and failed calls preserve state.
        eq(256,TranscriptArchive.decode(TranscriptArchive.encode(List(256){TranscriptLine("m$it","","",it.toLong())})).size)
        no { TranscriptArchive.encode(List(256){TranscriptLine("m$it","","x".repeat(8192),it.toLong())}) }
        no { VaultEnvelope.open(sealed+byteArrayOf(0),key,context) }
        no { VaultEnvelope.open(sealed.copyOf(35),key,context) }
        no { VaultEnvelope.seal(plaintext,key,ByteArray(1025)) }
        val replayable=VaultEnvelope.seal("other-resource".toByteArray(),key,context)
        eq("other-resource",VaultEnvelope.open(replayable,key,context).toString(Charsets.UTF_8))
        // Shared context deliberately does not bind a filename/resource identity.
        no { SheetFrame.decode(ByteArray(SheetFrame.MAX_INPUT+1)) }
        no { SheetFrame.readResult(ByteArray(SheetFrame.MAX_OUTPUT+1)) }
        val fullSheet=(1..256).associate { "A$it" to ("1"+"0".repeat(100)) }
        eq(256,SheetFrame.readResult(SheetFrame.execute(SheetFrame.encode(fullSheet))).size)
        no { SheetFrame.decode("FSC1\nA1\tMg\n".toByteArray()) } // Noncanonical Base64 padding.
        no { SheetFrame.decode("FSC1\nA2\tMg==\nA1\tMQ==\n".toByteArray()) }
        no { LaunchLinks.decode("x".repeat(129)) }
        no { LaunchLinks.decode("fusion://open/../editor?workspace=1") }
        no { LaunchLinks.decode("fusion://open/editor?workspace=%31") }
        println("R15: $checks assertions PASS. Six separately compiled production components; no Android/security/Office/full-module acceptance inferred.")
    }
}
