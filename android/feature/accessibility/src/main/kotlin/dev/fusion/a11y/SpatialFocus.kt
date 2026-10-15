package dev.fusion.a11y

data class FocusBox(val id:String,val left:Double,val top:Double,val right:Double,val bottom:Double,val enabled:Boolean=true,val visible:Boolean=true)
enum class FocusDirection { LEFT, RIGHT, UP, DOWN }
/** Own bounded directional policy; no Android focus request or input injection is performed. */
object SpatialFocus {
    fun next(boxes:List<FocusBox>,current:String,direction:FocusDirection):String? {
        require(boxes.size<=512 && boxes.map { it.id }.distinct().size==boxes.size)
        for(b in boxes) {
            require(b.id.matches(Regex("[A-Za-z0-9_.-]{1,128}")))
            require(listOf(b.left,b.top,b.right,b.bottom).all { it.isFinite() && it in -1000000.0..1000000.0 })
            require(b.left<b.right && b.top<b.bottom)
        }
        val origin=boxes.singleOrNull { it.id==current } ?: throw IllegalArgumentException("Unknown current focus")
        require(origin.enabled && origin.visible)
        fun x(b:FocusBox)=(b.left+b.right)/2
        fun y(b:FocusBox)=(b.top+b.bottom)/2
        val horizontal=direction==FocusDirection.LEFT || direction==FocusDirection.RIGHT
        val forward=direction==FocusDirection.RIGHT || direction==FocusDirection.DOWN
        fun ahead(b:FocusBox):Boolean {
            val delta=if(horizontal) x(b)-x(origin) else y(b)-y(origin)
            return if(forward) delta>0 else delta<0
        }
        fun overlaps(b:FocusBox)=if(horizontal) minOf(b.bottom,origin.bottom)>maxOf(b.top,origin.top)
            else minOf(b.right,origin.right)>maxOf(b.left,origin.left)
        fun distance(b:FocusBox):Double { val dx=x(b)-x(origin);val dy=y(b)-y(origin);return dx*dx+dy*dy }
        return boxes.filter { it.id!=current && it.enabled && it.visible && ahead(it) }
            .minWithOrNull(compareBy<FocusBox> { if(overlaps(it)) 0 else 1 }.thenBy { distance(it) }.thenBy { it.id })?.id
    }
}
