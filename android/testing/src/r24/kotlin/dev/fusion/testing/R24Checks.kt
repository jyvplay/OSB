package dev.fusion.testing

import dev.fusion.settings.*
import dev.fusion.designsystem.*
import dev.fusion.windowing.*

object R24Checks {
    private var checks=0
    private fun ok(value:Boolean) {checks++;check(value) {"R24 failure at check $checks"}}
    private fun refuses(block:()->Unit) {checks++;check(runCatching(block).isFailure)}
    @JvmStatic fun main(args:Array<String>) {
        for(p in ProfessionalPalette.entries) for(l in DesktopLayout.entries) for(z in listOf(false,true)) {
            val a=Appearance(p,l,z);val values=AppearancePreferences.encode(a)
            ok(AppearancePreferences.canonical(values));ok(AppearancePreferences.read(values)==a)
            refuses {(values as MutableMap)["layout"]="evil"}
        }
        ok(AppearancePreferences.read(mapOf("theme" to "dark")).palette==ProfessionalPalette.STUDIO_GRAPHITE)
        ok(AppearancePreferences.read(mapOf("layout" to "ART","theme" to "evil")).layout==DesktopLayout.BUSINESS)
        ok(AppearancePreferences.read(emptyMap())==Appearance())
        for(key in listOf("layout","theme","dock_zoom","permission","unknown")) for(value in listOf("", "ART","business","art","office_blue","studio_graphite","true","false","<script>","dark")) {
            val expected=when(key) {"layout"->value in setOf("business","art");"theme"->value in setOf("office_blue","studio_graphite");"dock_zoom"->value in setOf("true","false");else->false}
            ok(AppearancePreferences.canonical(mapOf(key to value))==expected)
        }
        ok(!AppearancePreferences.canonical(emptyMap()))
        ok(!AppearancePreferences.canonical(mapOf("theme" to "office_blue","layout" to "evil")))
        for(p in ProfessionalPalette.entries) {
            val c=ProfessionalPalettes.colors(p)
            for((fg,bg) in c.textPairs()) ok(Contrast.ratio(fg,bg)>=4.5)
            ok(Contrast.ratio(c.outline,c.background)>=3.0)
            ok(Contrast.ratio(c.outline,c.surface)>=3.0)
        }
        for(i in 0 until 64) for(h in listOf<Int?>(null)+ (0 until 64).toList()) for(l in DesktopLayout.entries) for(z in listOf(false,true)) {
            val scale=DockMagnification.scale(i,h,l,z)
            ok(scale in 1f..1.25f)
            ok(DockMagnification.GLYPH_DP*scale<=DockMagnification.SLOT_DP)
            ok(if(l==DesktopLayout.BUSINESS || !z || h==null) scale==1f else true)
            if(l==DesktopLayout.ART && z && h!=null) ok(if(i==h) scale==1.25f else scale<1.25f)
        }
        refuses {DockMagnification.scale(-1,0,DesktopLayout.ART,true)}
        refuses {DockMagnification.scale(0,64,DesktopLayout.ART,true)}
        var cases=0
        for(w in listOf(1f,48f,120f,239f,240f,360f,600f,1920f)) for(h in listOf(1f,48f,120f,179f,180f,240f,600f))
            for(x in listOf(-1000f,0f,100f,2000f)) for(y in listOf(-20f,0f,180f,2000f))
                for(d in WindowDensity.entries) {
                    val normal=Bounds(x,y,600f,430f);val out=InputPresentation.fit(normal,w,h,d,true)
                    ok(out.finite && out.x>=0 && out.y>=0 && out.width>0 && out.height>0)
                    ok(out.x+out.width<=w+.001f && out.y+out.height<=h+.001f)
                    ok(normal==Bounds(x,y,600f,430f))
                    ok(InputPresentation.fit(normal,w,h,d,false)==normal.constrained(w,h,d));cases++
                }
        refuses {InputPresentation.fit(Bounds(0f,0f,1f,1f),Float.NaN,600f,WindowDensity.TOUCH,true)}
        println("R24 PASS: $checks assertions;8 appearance combinations;16640 dock configurations; $cases input presentations. Opaque tokens/bounded model only, not native/AX/hardware acceptance.")
    }
}
