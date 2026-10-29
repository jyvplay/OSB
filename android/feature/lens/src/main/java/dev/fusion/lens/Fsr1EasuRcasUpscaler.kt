// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Clean-room CPU Reference Engine of MIT-Licensed AMD FidelityFX Super Resolution 1.0 (`FSR 1.0`):
 * - **Pass 1 (`EASU` — Edge-Adaptive Spatial Upsampling)**: Computes local Sobel gradient
 *   direction $(dx, dy)$ and edge anisotropy length, then evaluates an elliptical 12-tap
 *   approximated Lanczos2 kernel $w(d^2) = (\frac{2}{5} d^2 - 1)^2$ (clamped to $d^2 \le 2.5$)
 *   strictly bounded to the local $2\times 2$ texel `[min, max]` box to prevent ringing!
 * - **Pass 2 (`RCAS` — Robust Contrast-Adaptive Sharpening)**: Evaluates a 5-tap cross
 *   neighborhood $(b, d, e, f, h)$ with adaptive negative lobe weight derived from local headroom
 *   toward `0.0` and `1.0`, controlled by `sharpnessStops` (`0.0` = maximum sharpening, `2.0` =
 *   quarter sharpening).
 *
 * Advances `LN-05` and `MD-05` under permissive MIT mathematical specification.
 */
object Fsr1EasuRcasUpscaler {
    const val SCOPE =
        "Pure-JVM MIT-spec FSR 1.0 two-pass spatial upscaler: Pass 1 EASU directional elliptical Lanczos2 with anti-ringing clamp + Pass 2 RCAS 5-tap contrast-adaptive sharpening (LN-05, MD-05)."

    data class GrayFrame(val width: Int, val height: Int, val luma: DoubleArray) {
        init {
            require(width in 2..4096 && height in 2..4096 && luma.size == width * height) {
                "invalid GrayFrame ${width}x$height (len=${luma.size})"
            }
        }
        fun sampleClamped(x: Int, y: Int): Double {
            val cx = x.coerceIn(0, width - 1)
            val cy = y.coerceIn(0, height - 1)
            return luma[cy * width + cx].coerceIn(0.0, 1.0)
        }
    }

    /**
     * Pass 1: Edge-Adaptive Spatial Upsampling (`EASU`) from `src` (`W_in x H_in`) to `dstW x dstH`.
     */
    fun easuUpscale(src: GrayFrame, dstW: Int, dstH: Int): GrayFrame {
        require(dstW >= src.width && dstH >= src.height && dstW <= 4096 && dstH <= 4096) {
            "target ${dstW}x$dstH must be >= ${src.width}x${src.height}"
        }
        val out = DoubleArray(dstW * dstH)
        val scaleX = src.width.toDouble() / dstW.toDouble()
        val scaleY = src.height.toDouble() / dstH.toDouble()

        // 12-tap cross-footprint offsets around base integer coordinate (ix, iy)
        val tapDx = intArrayOf(0, 1, -1, 0, 1, 2, -1, 0, 1, 2, 0, 1)
        val tapDy = intArrayOf(-1, -1, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2)

        for (oy in 0 until dstH) {
            val sy = (oy + 0.5) * scaleY - 0.5
            val iy = kotlin.math.floor(sy).toInt()
            val fy = sy - iy
            for (ox in 0 until dstW) {
                val sx = (ox + 0.5) * scaleX - 0.5
                val ix = kotlin.math.floor(sx).toInt()
                val fx = sx - ix

                // Core 2x2 neighborhood for gradient & anti-ringing box clamp
                val f = src.sampleClamped(ix, iy)
                val g = src.sampleClamped(ix + 1, iy)
                val j = src.sampleClamped(ix, iy + 1)
                val k = src.sampleClamped(ix + 1, iy + 1)
                val boxMin = min(min(f, g), min(j, k))
                val boxMax = max(max(f, g), max(j, k))

                // Local directional gradient & anisotropy estimate
                val gradX = (g - f + k - j) * 0.5
                val gradY = (j - f + k - g) * 0.5
                val gradMag = sqrt(gradX * gradX + gradY * gradY)
                val dirX: Double
                val dirY: Double
                if (gradMag > 1e-5) {
                    // Tangent along the edge (perpendicular to gradient)
                    dirX = -gradY / gradMag
                    dirY = gradX / gradMag
                } else {
                    dirX = 1.0
                    dirY = 0.0
                }
                val stretch = 1.0 + 0.65 * gradMag.coerceIn(0.0, 1.0)
                val invStretch = 1.0 / stretch

                var wSum = 0.0
                var vSum = 0.0
                for (t in 0 until 12) {
                    val tx = tapDx[t] - fx
                    val ty = tapDy[t] - fy
                    // Rotate into (edge-tangent, edge-normal) elliptical coordinates
                    val along = (tx * dirX + ty * dirY) * invStretch
                    val across = -tx * dirY + ty * dirX
                    val d2 = along * along + across * across
                    if (d2 < 2.25) {
                        val wBase = (0.4 * d2 - 1.0).let { it * it }
                        val lobe = (1.0 - d2 / 2.25)
                        val w = wBase * lobe
                        val sample = src.sampleClamped(ix + tapDx[t], iy + tapDy[t])
                        wSum += w
                        vSum += w * sample
                    }
                }
                val raw = if (wSum > 1e-9) vSum / wSum else (f + g + j + k) * 0.25
                out[oy * dstW + ox] = raw.coerceIn(boxMin, boxMax)
            }
        }
        return GrayFrame(dstW, dstH, out)
    }

    /**
     * Pass 2: Robust Contrast-Adaptive Sharpening (`RCAS`) with `sharpnessStops` in `0.0..2.0`
     * (`0.0` = strongest sharpening; `2.0` = subtle sharpening).
     */
    fun rcasSharpen(frame: GrayFrame, sharpnessStops: Double = 0.2): GrayFrame {
        require(sharpnessStops in 0.0..2.0) { "sharpnessStops $sharpnessStops outside 0.0..2.0" }
        val attenuation = 2.0.pow(-sharpnessStops) // 1.0 at 0 stops, 0.25 at 2 stops
        val out = DoubleArray(frame.luma.size)

        for (y in 0 until frame.height) {
            for (x in 0 until frame.width) {
                val b = frame.sampleClamped(x, y - 1)
                val d = frame.sampleClamped(x - 1, y)
                val e = frame.sampleClamped(x, y)
                val f = frame.sampleClamped(x + 1, y)
                val h = frame.sampleClamped(x, y + 1)

                val mn4 = min(min(b, d), min(f, h))
                val mx4 = max(max(b, d), max(f, h))
                val mn5 = min(mn4, e)
                val mx5 = max(mx4, e)

                //FSR 1.0 RCAS analytically bounded negative lobe weight w in [-0.25, 0.0]
                val hitMin = if (mx4 > 1e-6) mn5 / (4.0 * mx4) else 0.0
                val hitMax = if (1.0 - mn4 > 1e-6) (1.0 - mx5) / (4.0 * mn4 - 4.0) else 0.0
                val lobe = max(-hitMin, hitMax).coerceIn(-0.1875, 0.0) * attenuation
                val denom = 4.0 * lobe + 1.0
                val sharpened = if (abs(denom) > 1e-6) (lobe * (b + d + f + h) + e) / denom else e
                out[y * frame.width + x] = sharpened.coerceIn(0.0, 1.0)
            }
        }
        return GrayFrame(frame.width, frame.height, out)
    }

    fun upscaleTwoPass(src: GrayFrame, dstW: Int, dstH: Int, sharpnessStops: Double = 0.2): GrayFrame =
        rcasSharpen(easuUpscale(src, dstW, dstH), sharpnessStops)
}
