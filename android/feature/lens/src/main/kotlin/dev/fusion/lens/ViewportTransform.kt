package dev.fusion.lens

data class LensPoint(val x:Double,val y:Double)
enum class ViewportMode { FIT, CROP }
/** Continuous image-edge coordinates, clockwise quarter turns then horizontal mirror.
 * Not CameraX sensor/metering integration or a virtual camera. No integer pixel sampling.
 */
class ViewportTransform(val width:Int,val height:Int,val viewportWidth:Int,val viewportHeight:Int,
                        val turns:Int=0,val mirror:Boolean=false,val mode:ViewportMode=ViewportMode.FIT) {
    init { require(listOf(width,height,viewportWidth,viewportHeight).all { it in 1..32768 });require(turns in 0..3) }
    private val rw=if(turns%2==0) width.toDouble() else height.toDouble()
    private val rh=if(turns%2==0) height.toDouble() else width.toDouble()
    private val scale=if(mode==ViewportMode.FIT) minOf(viewportWidth/rw,viewportHeight/rh) else maxOf(viewportWidth/rw,viewportHeight/rh)
    private val dx=(viewportWidth-rw*scale)/2
    private val dy=(viewportHeight-rh*scale)/2
    fun forward(p:LensPoint):LensPoint {
        require(p.x.isFinite() && p.y.isFinite() && p.x in 0.0..width.toDouble() && p.y in 0.0..height.toDouble())
        val r=when(turns) { 0 -> p;1 -> LensPoint(height-p.y,p.x);2 -> LensPoint(width-p.x,height-p.y);else -> LensPoint(p.y,width-p.x) }
        return LensPoint((if(mirror) rw-r.x else r.x)*scale+dx,r.y*scale+dy)
    }
    /** Null for letterbox locations. Crop inverse returns only the visible source subset. */
    fun inverse(p:LensPoint):LensPoint? {
        require(p.x.isFinite() && p.y.isFinite() && p.x in 0.0..viewportWidth.toDouble() && p.y in 0.0..viewportHeight.toDouble())
        var x=(p.x-dx)/scale;val y=(p.y-dy)/scale
        if(x < -1e-9 || y < -1e-9 || x>rw+1e-9 || y>rh+1e-9) return null
        x=x.coerceIn(0.0,rw);if(mirror) x=rw-x
        val yy=y.coerceIn(0.0,rh)
        return when(turns) { 0 -> LensPoint(x,yy);1 -> LensPoint(yy,height-x);2 -> LensPoint(width-x,height-yy);else -> LensPoint(width-yy,x) }
    }
}
