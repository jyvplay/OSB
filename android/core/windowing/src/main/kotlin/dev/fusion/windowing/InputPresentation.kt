package dev.fusion.windowing

/** Transient rendering only; keyboard insets must not destroy the persisted normal bounds.
 * Tiny viewports can fit a rectangle, not promise an operable full form. Native form regime is separately tested.
 */
object InputPresentation {
    fun fit(normal:Bounds,width:Float,height:Float,density:WindowDensity,keyboard:Boolean):Bounds {
        require(width.isFinite() && height.isFinite() && width>0 && height>0 && normal.finite)
        if(!keyboard) return normal.constrained(width,height,density)
        val w=normal.width.coerceIn(1f,width.coerceAtLeast(1f)).coerceAtMost(width)
        val h=normal.height.coerceIn(1f,height.coerceAtLeast(1f)).coerceAtMost(height)
        return Bounds(normal.x.coerceIn(0f,(width-w).coerceAtLeast(0f)),normal.y.coerceIn(0f,(height-h).coerceAtLeast(0f)),w,h)
    }
}
