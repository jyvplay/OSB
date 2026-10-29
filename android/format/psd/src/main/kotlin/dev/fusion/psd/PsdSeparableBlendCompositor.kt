// SPDX-License-Identifier: Apache-2.0
package dev.fusion.psd

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure-JVM W3C Compositing & Adobe Photoshop **Separable Layer Blend Mode Compositor**
 * (`NORMAL`, `MULTIPLY`, `SCREEN`, `OVERLAY`, `DARKEN`, `LIGHTEN`, `DIFFERENCE`) with Porter-Duff
 * `SRC_OVER` straight-alpha unpremultiplication for `:feature:psd`.
 */
object PsdSeparableBlendCompositor {
    const val SCOPE =
        "Photoshop/W3C separable RGBA layer blend compositor (NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, DIFFERENCE) with exact straight-alpha Porter-Duff compositing."

    enum class BlendMode { NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, DIFFERENCE }

    fun compositePixelArgb(backdropArgb: Int, sourceArgb: Int, mode: BlendMode, layerOpacity: Double = 1.0): Int {
        val op = layerOpacity.coerceIn(0.0, 1.0)
        val ab = ((backdropArgb ushr 24) and 0xFF) / 255.0
        val asrc = (((sourceArgb ushr 24) and 0xFF) / 255.0) * op
        if (asrc <= 0.0) return backdropArgb
        if (ab <= 0.0) {
            val outA = (asrc * 255.0).roundToInt().coerceIn(0, 255)
            return (outA shl 24) or (sourceArgb and 0x00FFFFFF)
        }

        val rb = ((backdropArgb ushr 16) and 0xFF) / 255.0
        val gb = ((backdropArgb ushr 8) and 0xFF) / 255.0
        val bb = (backdropArgb and 0xFF) / 255.0

        val rs = ((sourceArgb ushr 16) and 0xFF) / 255.0
        val gs = ((sourceArgb ushr 8) and 0xFF) / 255.0
        val bs = (sourceArgb and 0xFF) / 255.0

        val ao = asrc + ab * (1.0 - asrc)
        fun ch(cb: Double, cs: Double): Int {
            val bVal = blendChannel(cb, cs, mode)
            // W3C Compositing 1 §9.1: Co = as*(1-ab)*Cs + as*ab*B(Cb,Cs) + (1-as)*ab*Cb; C_straight = Co / ao
            val co = asrc * (1.0 - ab) * cs + asrc * ab * bVal + (1.0 - asrc) * ab * cb
            return ((co / ao) * 255.0).roundToInt().coerceIn(0, 255)
        }
        val outA = (ao * 255.0).roundToInt().coerceIn(0, 255)
        return (outA shl 24) or (ch(rb, rs) shl 16) or (ch(gb, gs) shl 8) or ch(bb, bs)
    }

    private fun blendChannel(cb: Double, cs: Double, mode: BlendMode): Double = when (mode) {
        BlendMode.NORMAL -> cs
        BlendMode.MULTIPLY -> cb * cs
        BlendMode.SCREEN -> cb + cs - cb * cs
        BlendMode.OVERLAY -> if (cb <= 0.5) 2.0 * cb * cs else 1.0 - 2.0 * (1.0 - cb) * (1.0 - cs)
        BlendMode.DARKEN -> min(cb, cs)
        BlendMode.LIGHTEN -> maxOf(cb, cs)
        BlendMode.DIFFERENCE -> kotlin.math.abs(cb - cs)
    }
}
