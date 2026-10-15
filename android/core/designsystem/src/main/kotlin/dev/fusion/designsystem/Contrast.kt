package dev.fusion.designsystem

import kotlin.math.pow

/** Opaque 8-bit sRGB math only. Not rendered/alpha/HDR contrast or an AX certificate. */
object Contrast {
    fun luminance(rgb:Int):Double {
        require(rgb in 0..0xffffff)
        fun channel(shift:Int):Double {
            val s=((rgb ushr shift) and 255)/255.0
            return if(s<=0.04045) s/12.92 else ((s+0.055)/1.055).pow(2.4)
        }
        return 0.2126*channel(16)+0.7152*channel(8)+0.0722*channel(0)
    }
    fun ratio(a:Int,b:Int):Double {
        val x=luminance(a);val y=luminance(b)
        return (maxOf(x,y)+0.05)/(minOf(x,y)+0.05)
    }
    /** Keep a sufficient preferred color, else choose the stronger black/white candidate, else unavailable. */
    fun foreground(preferred:Int,background:Int,minimum:Double=4.5):Int? {
        require(minimum.isFinite() && minimum in 1.0..21.0)
        if(ratio(preferred,background)>=minimum) return preferred
        val best=if(ratio(0,background)>=ratio(0xffffff,background)) 0 else 0xffffff
        return best.takeIf { ratio(it,background)>=minimum }
    }
}
