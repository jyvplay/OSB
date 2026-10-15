package dev.fusion.testing

import dev.fusion.filesui.*
import dev.fusion.designsystem.Contrast
import dev.fusion.a11y.*
import dev.fusion.alerts.*
import dev.fusion.settingsui.*
import dev.fusion.ooxml.DecimalSheet
import java.math.BigDecimal

object R14Checks {
    private var checks=0
    private fun eq(a:Any?,b:Any?) { checks++;check(a==b) { "#$checks $a != $b" } }
    private fun no(f:()->Unit) { checks++;try { f() } catch(_:IllegalArgumentException) { return } catch(_:java.nio.charset.CharacterCodingException) { return } catch(_:PreferenceConflict) { return };error("Attack accepted #$checks") }
    @JvmStatic fun main(args:Array<String>) {
        val items=listOf(ListingItem("a","item10",false,10),ListingItem("b","item2",false,2),ListingItem("c","item02",false,null),ListingItem("d","z",true,0))
        eq(listOf("d","b","c","a"),DirectoryQuery.select(items).map { it.id })
        eq(listOf("d","a","b","c"),DirectoryQuery.select(items,order=ListingOrder.LARGEST).map { it.id })
        eq(listOf("b"),DirectoryQuery.select(items,"ITEM2").map { it.id })
        eq(4,items.size);no { DirectoryQuery.select(items+items[0]) };no { DirectoryQuery.select(listOf(items[0].copy(name="\uD800"))) }
        no { DirectoryQuery.select(List(5001){items[0].copy(id=it.toString())}) }
        no { DirectoryQuery.select(listOf(items[0].copy(bytes=-1))) };no { DirectoryQuery.select(items,"x".repeat(129)) }
        eq("y",DirectoryQuery.select(listOf(ListingItem("x","9".repeat(200),false,0),ListingItem("y","1".repeat(199),false,0))).first().id)
        eq(21.0,Contrast.ratio(0,0xffffff));eq(1.0,Contrast.ratio(0x123456,0x123456))
        eq(0,Contrast.foreground(0xffffff,0xffffff));eq(null,Contrast.foreground(0x777777,0x777777,7.0))
        no { Contrast.luminance(-1) };no { Contrast.foreground(0,0,Double.NaN) };no { Contrast.foreground(0,0,21.1) }
        val boxes=listOf(FocusBox("o",0.0,0.0,10.0,10.0),FocusBox("far",100.0,0.0,110.0,10.0),FocusBox("diagonal",11.0,20.0,21.0,30.0),FocusBox("disabled",11.0,0.0,21.0,10.0,false))
        eq("far",SpatialFocus.next(boxes,"o",FocusDirection.RIGHT));eq(null,SpatialFocus.next(boxes,"o",FocusDirection.LEFT))
        eq("diagonal",SpatialFocus.next(boxes,"o",FocusDirection.DOWN))
        no { SpatialFocus.next(boxes,"unknown",FocusDirection.UP) };no { SpatialFocus.next(boxes+boxes[0],"o",FocusDirection.UP) }
        no { SpatialFocus.next(listOf(boxes[0].copy(left=Double.NaN)),"o",FocusDirection.UP) }
        no { SpatialFocus.next(listOf(boxes[0].copy(right=0.0)),"o",FocusDirection.UP) }
        eq("a",SpatialFocus.next(listOf(boxes[0],boxes[1].copy(id="b"),boxes[1].copy(id="a")),"o",FocusDirection.RIGHT))
        val ledger=NoticeLedger(2,100)
        eq(listOf("a"),ledger.post(PublicNotice("a","title","body",0),0).retained.map { it.id })
        ledger.post(PublicNotice("b","","body",1),1)
        eq(listOf("a"),ledger.post(PublicNotice("c","c","",2),2).evictedIds)
        eq(listOf("c"),ledger.redact("b").retained.map { it.id })
        ledger.advance(3);no { ledger.advance(2) };no { ledger.post(PublicNotice("c","old","",1),3) }
        eq("c",ledger.advance(101).retained.single().id);eq(emptyList<PublicNotice>(),ledger.advance(102).retained)
        no { ledger.post(PublicNotice("x","future","",104),103) }
        ledger.post(PublicNotice("x","public","",103),103);eq(emptyList<PublicNotice>(),ledger.redact("x").retained)
        ledger.post(PublicNotice("x","public","",103),103)
        no { ledger.post(PublicNotice("x","\uD800","",103),103) };eq("public",ledger.advance(103).retained.single().title)
        eq(listOf("x"),ledger.clear().evictedIds);no { NoticeLedger(0) };no { NoticeLedger(retentionMillis=0) }
        // Patch attack: a clock rollback cannot prevent privacy removal or clear.
        val privacy=NoticeLedger();privacy.post(PublicNotice("id","public","",100),100)
        no { privacy.advance(99) };eq(emptyList<PublicNotice>(),privacy.redact("id").retained)
        privacy.post(PublicNotice("id","public","",100),100);no { privacy.advance(0) }
        eq(emptyList<PublicNotice>(),privacy.clear().retained)
        val base=mutableMapOf("density" to "balanced","theme" to "dark")
        val desired=mutableMapOf("density" to "touch","theme" to "dark")
        val patch=SettingsPatch.plan(base,desired);base["density"]="mutated";desired["density"]="mutated"
        val current=mapOf("density" to "balanced","theme" to "light","other" to "kept")
        val changed=patch.apply(current)
        eq(mapOf("density" to "touch","theme" to "light","other" to "kept"),changed);eq(changed,patch.apply(changed))
        no { patch.apply(current+ ("density" to "compact")) };eq("balanced",current["density"])
        val deletion=SettingsPatch.plan(mapOf("a" to "b"),emptyMap());eq(emptyMap<String,String>(),deletion.apply(mapOf("a" to "b")))
        no { deletion.apply(mapOf("a" to "c")) };no { SettingsPatch.plan(mapOf("bad key" to "x"),emptyMap()) }
        no { SettingsPatch.plan(emptyMap(),mapOf("a" to "\uD800")) }
        eq(BigDecimal("3"),DecimalSheet.evaluate(mapOf("A1" to "1","A2" to "=A1+2")).getValue("A2"))
        eq(BigDecimal("7"),DecimalSheet.evaluate(mapOf("A1" to "=1+2*3")).getValue("A1"))
        eq(BigDecimal("9"),DecimalSheet.evaluate(mapOf("A1" to "=(1+2)*3")).getValue("A1"))
        eq(BigDecimal("-4"),DecimalSheet.evaluate(mapOf("A1" to "=--2*-2")).getValue("A1"))
        for(s in listOf("=1/0","=SUM(A1)","=A2","=1+","=1e3","=1;2","=\n1","=1.2.3","="+"(".repeat(17)+"1"+")".repeat(17),"1".repeat(35))) no { DecimalSheet.evaluate(mapOf("A1" to s)) }
        no { DecimalSheet.evaluate(mapOf("A1" to "=A2","A2" to "=A1")) }
        no { DecimalSheet.evaluate(mapOf("DY1" to "1")) };no { DecimalSheet.evaluate(mapOf("A1001" to "1")) }
        no { DecimalSheet.evaluate((1..18).associate { "A$it" to if(it==1) "1" else "=A${it-1}" }) }
        eq(17,DecimalSheet.evaluate((1..17).associate { "A$it" to if(it==1) "1" else "=A${it-1}" }).size)
        no { DecimalSheet.evaluate((1..257).associate { "A$it" to "1" }) }
        // Regression: numeric equality does not imply BigDecimal.equals representation equality.
        eq(BigDecimal("6.053E+4"),DecimalSheet.evaluate(mapOf("A1" to "=60528+2")).getValue("A1"))
        eq(BigDecimal.ZERO,DecimalSheet.evaluate(mapOf("A1" to "=-0.00")).getValue("A1"))
        // Boundary and cross-component attacks, not additional claimed features.
        eq(5000,DirectoryQuery.select(List(5000){ListingItem(it.toString(),"x",false,null)}).size)
        no { DirectoryQuery.select(List(2200){ListingItem(it.toString(),"x".repeat(1024),false,null)}) }
        no { DirectoryQuery.select(listOf(items[0].copy(name=""))) }
        // Patch/global-compatibility attack: primary black must not leak onto a custom dark shelf container.
        eq(true,Contrast.ratio(0,0x303A39)<4.5)
        eq(true,Contrast.ratio(requireNotNull(Contrast.foreground(0xE4E4E7,0x303A39)),0x303A39)>=4.5)
        val maximumBoxes=List(512){FocusBox("n$it",it*20.0,0.0,it*20.0+10,10.0)}
        eq("n1",SpatialFocus.next(maximumBoxes,"n0",FocusDirection.RIGHT))
        no { SpatialFocus.next(maximumBoxes+FocusBox("extra",0.0,0.0,1.0,1.0),"n0",FocusDirection.RIGHT) }
        no { SpatialFocus.next(listOf(boxes[0].copy(visible=false)),"o",FocusDirection.RIGHT) }
        val maximumLedger=NoticeLedger()
        repeat(256) { maximumLedger.post(PublicNotice("n$it","t","",0),0) }
        eq(256,maximumLedger.advance(0).retained.size)
        eq(256,maximumLedger.clear().evictedIds.size)
        val endClock=NoticeLedger(retentionMillis=1)
        endClock.post(PublicNotice("last","t","",Long.MAX_VALUE),Long.MAX_VALUE)
        eq(1,endClock.advance(Long.MAX_VALUE).retained.size);eq(0,endClock.redact("last").retained.size)
        val full=List(64){"k$it" to "v".repeat(1000)}.toMap()
        eq(full,SettingsPatch.plan(emptyMap(),full).apply(emptyMap()))
        no { SettingsPatch.plan(emptyMap(),full).apply(mapOf("unrelated" to "keep")) }
        val conflictInput=mutableMapOf("a" to "old","b" to "conflict")
        no { SettingsPatch.plan(mapOf("a" to "old","b" to "base"),mapOf("a" to "new","b" to "new")).apply(conflictInput) }
        eq("old",conflictInput["a"])
        eq(BigDecimal.ONE,DecimalSheet.evaluate(mapOf("A1" to ("="+" ".repeat(510)+"1"))).getValue("A1"))
        no { DecimalSheet.evaluate(mapOf("A1" to ("="+" ".repeat(511)+"1"))) }
        eq(BigDecimal.ONE,DecimalSheet.evaluate(mapOf("A1" to ("="+"(".repeat(16)+"1"+")".repeat(16)))).getValue("A1"))
        // Memoization must not permit a dependency graph deeper than16 through an earlier cached node.
        val tooDeep=(1..18).associate { "A$it" to if(it==1) "1" else "=A${it-1}" }
        no { DecimalSheet.evaluate(tooDeep.toList().reversed().toMap()) }
        val random=java.util.Random(140014)
        repeat(300) {
            val rgb=random.nextInt(0x1000000);val fg=requireNotNull(Contrast.foreground(rgb,rgb))
            eq(true,Contrast.ratio(fg,rgb)>=4.5);eq(Contrast.ratio(rgb,fg),Contrast.ratio(fg,rgb))
            val shuffled=items.shuffled(kotlin.random.Random(it));eq(DirectoryQuery.select(items),DirectoryQuery.select(shuffled))
            eq("far",SpatialFocus.next(boxes.shuffled(kotlin.random.Random(it)),"o",FocusDirection.RIGHT))
            val n=random.nextInt(100000);eq(BigDecimal(n+2).stripTrailingZeros(),DecimalSheet.evaluate(mapOf("A1" to "=$n+2")).getValue("A1"))
            val next="v$n";val p=SettingsPatch.plan(mapOf("key" to "old"),mapOf("key" to next));eq(next,p.apply(mapOf("key" to "old")).getValue("key"))
        }
        println("R14: $checks assertions PASS across six actual production kernels. Not full modules or Android/Office/AX acceptance.")
    }
}
