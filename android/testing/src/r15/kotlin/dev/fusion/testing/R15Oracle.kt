package dev.fusion.testing

import dev.fusion.lens.*
import dev.fusion.capture.*
import dev.fusion.messages.*
import dev.fusion.vault.*
import dev.fusion.model.*
import dev.fusion.ooxml.*
import java.util.Base64

/** Test-only pipe into real APIs. No device, peer or Office engine is impersonated. */
object R15Oracle {
    @JvmStatic fun main(args:Array<String>) {
        val dec=Base64.getDecoder();val enc=Base64.getEncoder()
        fun text(s:String)=String(dec.decode(s),Charsets.UTF_8)
        fun encoded(b:ByteArray)=enc.encodeToString(b)
        for(line in generateSequence(::readLine)) try {
            val p=line.split('\t')
            println(when(p[0]) {
                "G" -> { val t=ViewportTransform(p[1].toInt(),p[2].toInt(),p[3].toInt(),p[4].toInt(),p[5].toInt(),p[6]=="1",ViewportMode.valueOf(p[7]));val out=t.forward(LensPoint(p[8].toDouble(),p[9].toDouble()));"${out.x},${out.y}" }
                "T" -> { val t=RecordingTimeline();t.start(0);val stamps=p.drop(1).map(String::toLong);for(i in 0 until stamps.size-1 step 2) { t.pause(stamps[i]);t.resume(stamps[i+1]) };val r=t.stop(stamps.last());"${r.elapsedMillis},${r.activeMillis},${r.pausedMillis}" }
                "M" -> encoded(TranscriptArchive.encode(listOf(TranscriptLine("m1",text(p[1]),text(p[2]),p[3].toLong()))))
                "V" -> encoded(VaultEnvelope.seal(dec.decode(p[1]),dec.decode(p[2]),dec.decode(p[3])))
                "O" -> encoded(VaultEnvelope.open(dec.decode(p[1]),dec.decode(p[2]),dec.decode(p[3])))
                "L" -> { val t=LaunchLinks.decode(text(p[1]));"${t.module},${t.workspace},${LaunchLinks.encode(t)}" }
                "S" -> encoded(SheetFrame.execute(SheetFrame.encode(p.drop(1).mapIndexed { i,s -> "A${i+1}" to text(s) }.toMap())))
                else -> error("Unknown operation")
            })
        } catch(_:Exception) { println("REFUSED") }
    }
}
