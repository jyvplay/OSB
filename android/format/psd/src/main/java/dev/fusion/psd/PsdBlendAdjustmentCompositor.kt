// SPDX-License-Identifier: Apache-2.0
package dev.fusion.psd

import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Clean-room PSD separable multi-layer blend compositor (`NORMAL`, `MULTIPLY`, `SCREEN`, `OVERLAY`,
 * `SOFT_LIGHT`, `DIFFERENCE`) + exposure/gamma levels adjustment exporting binary Netpbm `P6` `.ppm`.
 */
object PsdBlendAdjustmentCompositor {
    const val SCOPE =
        "Pure-JVM multi-layer RGBA separable blend-mode & levels adjustment compositor with Netpbm P6 binary raster export."

    enum class BlendMode { NORMAL, MULTIPLY, SCREEN, OVERLAY, SOFT_LIGHT, DIFFERENCE }

    data class LayerBuffer(
        val name: String,
        val width: Int,
        val height: Int,
        val rgba: ByteArray, // 4 bytes per pixel: R, G, B, A
        val mode: BlendMode = BlendMode.NORMAL,
        val opacity: Double = 1.0,
        val visible: Boolean = true,
    ) {
        init {
            require(width in 1..2048 && height in 1..2048 && rgba.size == width * height * 4) {
                "invalid layer dimensions or RGBA byte count"
            }
            require(opacity in 0.0..1.0) { "opacity outside 0..1" }
        }
    }

    fun compositeToRgb(
        width: Int,
        height: Int,
        layersBottomToTop: List<LayerBuffer>,
        exposureStops: Double = 0.0,
        gamma: Double = 1.0,
    ): ByteArray {
        require(width in 1..2048 && height in 1..2048 && gamma in 0.1..5.0) { "invalid canvas/gamma" }
        val pxCount = width * height
        val dstR = DoubleArray(pxCount)
        val dstG = DoubleArray(pxCount)
        val dstB = DoubleArray(pxCount)
        val dstA = DoubleArray(pxCount)

        for (layer in layersBottomToTop) {
            if (!layer.visible || layer.opacity <= 0.0) continue
            require(layer.width == width && layer.height == height) { "layer '${layer.name}' size mismatch" }
            for (i in 0 until pxCount) {
                val off = i * 4
                val sr = (layer.rgba[off].toInt() and 0xFF) / 255.0
                val sg = (layer.rgba[off + 1].toInt() and 0xFF) / 255.0
                val sb = (layer.rgba[off + 2].toInt() and 0xFF) / 255.0
                val sa = ((layer.rgba[off + 3].toInt() and 0xFF) / 255.0) * layer.opacity
                if (sa <= 0.0) continue
                val da = dstA[i]
                val outA = sa + da * (1.0 - sa)
                val br = blendChannel(dstR[i], sr, layer.mode)
                val bg = blendChannel(dstG[i], sg, layer.mode)
                val bb = blendChannel(dstB[i], sb, layer.mode)
                dstR[i] = ((1.0 - da) * sa * sr + (1.0 - sa) * da * dstR[i] + da * sa * br) / outA
                dstG[i] = ((1.0 - da) * sa * sg + (1.0 - sa) * da * dstG[i] + da * sa * bg) / outA
                dstB[i] = ((1.0 - da) * sa * sb + (1.0 - sa) * da * dstB[i] + da * sa * bb) / outA
                dstA[i] = outA
            }
        }

        val gain = 2.0.pow(exposureStops)
        val invGamma = 1.0 / gamma
        val out = ByteArray(pxCount * 3)
        for (i in 0 until pxCount) {
            out[i * 3] = ((dstR[i] * gain).coerceIn(0.0, 1.0).pow(invGamma) * 255.0).roundToInt().toByte()
            out[i * 3 + 1] = ((dstG[i] * gain).coerceIn(0.0, 1.0).pow(invGamma) * 255.0).roundToInt().toByte()
            out[i * 3 + 2] = ((dstB[i] * gain).coerceIn(0.0, 1.0).pow(invGamma) * 255.0).roundToInt().toByte()
        }
        return out
    }

    fun encodePpm(width: Int, height: Int, rgb: ByteArray): ByteArray {
        require(rgb.size == width * height * 3)
        val header = "P6\n$width $height\n255\n".toByteArray(Charsets.US_ASCII)
        val bos = ByteArrayOutputStream(header.size + rgb.size)
        bos.write(header)
        bos.write(rgb)
        return bos.toByteArray()
    }

    private fun blendChannel(b: Double, s: Double, mode: BlendMode): Double = when (mode) {
        BlendMode.NORMAL -> s
        BlendMode.MULTIPLY -> b * s
        BlendMode.SCREEN -> b + s - b * s
        BlendMode.OVERLAY -> if (b <= 0.5) 2.0 * b * s else 1.0 - 2.0 * (1.0 - b) * (1.0 - s)
        BlendMode.SOFT_LIGHT -> if (s <= 0.5) b - (1.0 - 2.0 * s) * b * (1.0 - b) else {
            val d = if (b <= 0.25) ((16.0 * b - 12.0) * b + 4.0) * b else sqrt(b)
            b + (2.0 * s - 1.0) * (d - b)
        }
        BlendMode.DIFFERENCE -> abs(b - s)
    }
}
