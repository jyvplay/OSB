package dev.fusion.settings

/** App appearance only. Never a change to Android, host OS, permissions or session consent. */
enum class ProfessionalPalette(val id:String,val title:String,val dark:Boolean) {
    OFFICE_BLUE("office_blue","Office Blue",false), STUDIO_GRAPHITE("studio_graphite","Studio Graphite",true),
    STUDIO_SILVER("studio_silver","Studio Silver",false)
}
enum class DesktopLayout(val id:String,val title:String) { BUSINESS("business","Business"), ART("art","Art") }
enum class DesktopScene(val id:String,val title:String) { TABLETOP("tabletop","Tabletop"), FLAT("flat","Flat") }
data class Appearance(val palette:ProfessionalPalette=ProfessionalPalette.OFFICE_BLUE,
                      val layout:DesktopLayout=DesktopLayout.BUSINESS,val dockZoom:Boolean=true,val scene:DesktopScene=DesktopScene.TABLETOP)
object AppearancePreferences {
    const val PALETTE="theme";const val LAYOUT="layout";const val ZOOM="dock_zoom";const val SCENE="desktop_scene"
    val keys:Set<String> = java.util.Collections.unmodifiableSet(setOf(PALETTE,LAYOUT,ZOOM,SCENE))
    /** Legacy reads are nondestructive; unknown values fall back rather than rewriting the store. */
    fun read(values:Map<String,String>):Appearance = Appearance(
        ProfessionalPalette.entries.find {it.id==values[PALETTE]} ?: when(values[PALETTE]) {
            "dark"->ProfessionalPalette.STUDIO_GRAPHITE;else->ProfessionalPalette.OFFICE_BLUE
        }, DesktopLayout.entries.find {it.id==values[LAYOUT]} ?: DesktopLayout.BUSINESS,
        values[ZOOM]!="false", DesktopScene.entries.find {it.id==values[SCENE]} ?: DesktopScene.TABLETOP)
    fun canonical(changes:Map<String,String>):Boolean = changes.isNotEmpty() && changes.all {(id,value)->when(id) {
        PALETTE->ProfessionalPalette.entries.any {it.id==value}
        LAYOUT->DesktopLayout.entries.any {it.id==value}
        ZOOM->value=="true" || value=="false"
        SCENE->DesktopScene.entries.any {it.id==value}
        else->false
    }}
    fun encode(value:Appearance):Map<String,String> = java.util.Collections.unmodifiableMap(mapOf(PALETTE to value.palette.id,LAYOUT to value.layout.id,ZOOM to value.dockZoom.toString(),SCENE to value.scene.id))
}
