package dev.fusion.designsystem

import dev.fusion.settings.DesktopLayout
import dev.fusion.settings.ProfessionalPalette
import kotlin.math.abs

/** Opaque semantic sRGB roles. Paper/image/document pixels are not recolored. */
data class ProfessionalColors(val background:Int,val surface:Int,val raised:Int,val outline:Int,
    val primary:Int,val onPrimary:Int,val text:Int,val muted:Int,val selection:Int,val onSelection:Int) {
    fun textPairs():List<Pair<Int,Int>> = listOf(text to background,text to surface,text to raised,
        muted to background,muted to surface,muted to raised,onPrimary to primary,onSelection to selection,primary to background)
}
object ProfessionalPalettes {
    fun colors(palette:ProfessionalPalette):ProfessionalColors = when(palette) {
        ProfessionalPalette.STUDIO_SILVER->ProfessionalColors(0xe9edf1,0xfafcfe,0xdce2e8,0x5f6d7a,0x2f4965,0xffffff,0x16202a,0x3f4e5d,0xd0dce9,0x1b354d)
        ProfessionalPalette.OFFICE_BLUE->ProfessionalColors(0xf3f6fb,0xffffff,0xe8eef7,0x5e7085,0x174ea6,0xffffff,0x121f30,0x3d4f65,0xd9e7ff,0x103668)
        ProfessionalPalette.STUDIO_GRAPHITE->ProfessionalColors(0x151b24,0x202936,0x2d394a,0x8b9db4,0x80cbb8,0x0f2c28,0xf0f4fa,0xc6d3e4,0x354e66,0xf0f5fa)
    }
}
/** Drawing changes, never hit geometry:64dp slot/32dp glyph leaves room for1.25× magnification. */
object DockMagnification {
    const val MAX_ITEMS=64
    const val SLOT_DP=64;const val GLYPH_DP=32
    fun scale(index:Int,hot:Int?,layout:DesktopLayout,enabled:Boolean):Float {
        require(index in 0 until MAX_ITEMS && (hot==null || hot in 0 until MAX_ITEMS))
        if(layout!=DesktopLayout.ART || !enabled || hot==null) return 1f
        return when(abs(index-hot)) {0->1.25f;1->1.125f;else->1f}
    }
}
