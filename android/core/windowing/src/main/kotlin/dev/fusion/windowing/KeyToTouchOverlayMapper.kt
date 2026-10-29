// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

import kotlin.math.min

/**
 * Clean-room Key-to-Touch Overlay Coordinate Mapper (`WM-14`) and Fusion Window-to-Cast Virtual
 * Render-Surface Viewport Planner (`WM-15`).
 */
object KeyToTouchOverlayMapper {
    const val SCOPE =
        "Pure-JVM normalized key-to-touch tap/WASD-vector overlay mapper (WM-14) and pillarbox/letterbox window-to-cast Surface viewport transform (WM-15)."

    data class KeyBindingAnchor(
        val keyCodeName: String,
        val normX: Double, // 0..1
        val normY: Double, // 0..1
    ) {
        init { require(normX in 0.0..1.0 && normY in 0.0..1.0) }
    }

    data class CastSurfaceTransform(
        val dstX: Int,
        val dstY: Int,
        val dstW: Int,
        val dstH: Int,
        val scale: Double,
        val letterboxed: Boolean,
    )

    fun mapKeyToScreenTap(
        bindings: List<KeyBindingAnchor>,
        pressedKeyName: String,
        surfaceWidthPx: Int,
        surfaceHeightPx: Int,
    ): Pair<Int, Int>? {
        require(surfaceWidthPx >= 64 && surfaceHeightPx >= 64) { "surface < 64px" }
        val hit = bindings.firstOrNull { it.keyCodeName.equals(pressedKeyName.trim(), ignoreCase = true) }
            ?: return null
        val px = (hit.normX * (surfaceWidthPx - 1)).toInt().coerceIn(0, surfaceWidthPx - 1)
        val py = (hit.normY * (surfaceHeightPx - 1)).toInt().coerceIn(0, surfaceHeightPx - 1)
        return px to py
    }

    fun computeWasdJoystickVector(
        wDown: Boolean,
        aDown: Boolean,
        sDown: Boolean,
        dDown: Boolean,
        centerPxX: Int,
        centerPxY: Int,
        radiusPx: Int,
    ): Pair<Int, Int> {
        var dx = (if (dDown) 1.0 else 0.0) - (if (aDown) 1.0 else 0.0)
        var dy = (if (sDown) 1.0 else 0.0) - (if (wDown) 1.0 else 0.0)
        if (dx != 0.0 && dy != 0.0) {
            val invSqrt2 = 0.7071067811865475
            dx *= invSqrt2
            dy *= invSqrt2
        }
        return (centerPxX + (dx * radiusPx).toInt()) to (centerPxY + (dy * radiusPx).toInt())
    }

    fun planWindowToCastSurface(
        windowW: Int,
        windowH: Int,
        castSinkW: Int,
        castSinkH: Int,
    ): CastSurfaceTransform {
        require(windowW >= 1 && windowH >= 1 && castSinkW >= 64 && castSinkH >= 64) { "invalid cast dimensions" }
        val sx = castSinkW.toDouble() / windowW.toDouble()
        val sy = castSinkH.toDouble() / windowH.toDouble()
        val s = min(sx, sy)
        val dw = (windowW * s).toInt().coerceIn(1, castSinkW)
        val dh = (windowH * s).toInt().coerceIn(1, castSinkH)
        val dx = (castSinkW - dw) / 2
        val dy = (castSinkH - dh) / 2
        return CastSurfaceTransform(dx, dy, dw, dh, s, dx > 0 || dy > 0)
    }
}
