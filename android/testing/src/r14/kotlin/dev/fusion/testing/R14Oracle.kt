package dev.fusion.testing

import dev.fusion.filesui.*
import dev.fusion.designsystem.Contrast
import dev.fusion.a11y.*
import dev.fusion.alerts.*
import dev.fusion.settingsui.*
import dev.fusion.ooxml.DecimalSheet
import java.util.Base64

/** Test pipe into actual public APIs, not an Android or Office substitute. */
object R14Oracle {
    @JvmStatic fun main(args:Array<String>) {
        val dec=Base64.getDecoder();val enc=Base64.getEncoder()
        fun text(s:String)=String(dec.decode(s),Charsets.UTF_8)
        fun b64(s:String)=enc.encodeToString(s.toByteArray(Charsets.UTF_8))
        for(line in generateSequence(::readLine)) try {
            val p=line.split('\t')
            println(when(p[0]) {
                "C" -> Contrast.ratio(p[1].toInt(),p[2].toInt()).toString()
                "D" -> DirectoryQuery.select(p.drop(1).mapIndexed { i,s -> ListingItem(i.toString(),text(s),false,i.toLong()) }).joinToString(",") { it.id }
                "F" -> DecimalSheet.evaluate(p.drop(1).mapIndexed { i,s -> "A${i+1}" to text(s) }.toMap()).getValue("A${p.size-1}").toPlainString()
                "S" -> { val q=SettingsPatch.plan(mapOf("target" to text(p[1])),mapOf("target" to text(p[2])));val result=q.apply(mapOf("target" to text(p[1]),"unrelated" to text(p[3])));b64(result.getValue("target"))+","+b64(result.getValue("unrelated")) }
                "N" -> { val now=p[1].toLong();val ledger=NoticeLedger(p[2].toInt(),p[3].toLong());for((i,t) in p.drop(4).withIndex()) ledger.post(PublicNotice(i.toString(),"title","",t.toLong()),now);ledger.advance(now).retained.joinToString(",") { it.id } }
                "G" -> { val boxes=p.drop(2).mapIndexed { i,s -> val c=s.split(',').map(String::toDouble);FocusBox(i.toString(),c[0],c[1],c[2],c[3]) };SpatialFocus.next(boxes,"0",FocusDirection.valueOf(p[1])) ?: "NONE" }
                else -> error("Unknown operation")
            })
        } catch(_:Exception) { println("REFUSED") }
    }
}
