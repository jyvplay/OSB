package dev.fusion.format.image

import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Clean-room Björn Ottosson `Oklab` Perceptual Color Space Converter, Deterministic K-Means++
 * Palette Quantizer, and WCAG 2.2 Relative Luminance Contrast Optimizer (Apache-2.0).
 */
object OklabPerceptualPaletteQuantizer {
    const val SCOPE =
        "Bjorn Ottosson sRGB <-> Oklab perceptual color space + deterministic k-means++ palette quantizer + WCAG 2.2 AAA foreground solver"

    data class Oklab(val l: Double, val a: Double, val b: Double) {
        fun deltaE(other: Oklab): Double {
            val dl = l - other.l; val da = a - other.a; val db = b - other.b
            return sqrt(dl * dl + da * da + db * db)
        }
    }

    fun srgbToOklab(rgb24: Int): Oklab {
        val r = srgbChannelToLinear((rgb24 ushr 16) and 0xFF)
        val g = srgbChannelToLinear((rgb24 ushr 8) and 0xFF)
        val b = srgbChannelToLinear(rgb24 and 0xFF)

        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)

        return Oklab(
            l = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            b = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    fun oklabToSrgb(lab: Oklab): Int {
        val l_ = lab.l + 0.3963377774 * lab.a + 0.2158037573 * lab.b
        val m_ = lab.l - 0.1055613458 * lab.a - 0.0638541728 * lab.b
        val s_ = lab.l - 0.0894841775 * lab.a - 1.2914855480 * lab.b

        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_

        val r = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val b = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        return (linearToSrgbByte(r) shl 16) or (linearToSrgbByte(g) shl 8) or linearToSrgbByte(b)
    }

    fun wcagContrastRatio(rgbA: Int, rgbB: Int): Double {
        val lumA = relativeLuminance(rgbA)
        val lumB = relativeLuminance(rgbB)
        return (max(lumA, lumB) + 0.05) / (min(lumA, lumB) + 0.05)
    }

    /** Adjusts `Oklab.l` while preserving hue/chroma `(a, b)` until WCAG contrast vs [bgRgb24] >= [minRatio]. */
    fun ensureAccessibleForeground(fgRgb24: Int, bgRgb24: Int, minRatio: Double = 7.0): Int {
        if (wcagContrastRatio(fgRgb24, bgRgb24) >= minRatio) return fgRgb24
        val lab = srgbToOklab(fgRgb24)
        val goLighter = relativeLuminance(bgRgb24) < 0.18
        var lo = if (goLighter) lab.l else 0.0
        var hi = if (goLighter) 1.0 else lab.l
        var best = if (goLighter) 0xFFFFFF else 0x000000
        repeat(28) {
            val mid = 0.5 * (lo + hi)
            val cand = oklabToSrgb(Oklab(mid, lab.a * 0.85, lab.b * 0.85))
            if (wcagContrastRatio(cand, bgRgb24) >= minRatio) {
                best = cand
                if (goLighter) hi = mid else lo = mid
            } else {
                if (goLighter) lo = mid else hi = mid
            }
        }
        return best
    }

    /** Deterministic farthest-first + Lloyd iterations in perceptual Oklab space. */
    fun quantizePalette(pixelsRgb24: IntArray, k: Int, iterations: Int = 8): List<Int> {
        require(pixelsRgb24.isNotEmpty() && k in 1..64)
        val pts = pixelsRgb24.map(::srgbToOklab)
        val centroids = ArrayList<Oklab>(k)
        centroids.add(pts[0])
        while (centroids.size < k) {
            val farthest = pts.maxBy { p -> centroids.minOf { c -> p.deltaE(c) } }
            centroids.add(farthest)
        }
        repeat(iterations) {
            val sumsL = DoubleArray(k); val sumsA = DoubleArray(k); val sumsB = DoubleArray(k)
            val counts = IntArray(k)
            for (p in pts) {
                var bestIdx = 0; var bestD = Double.MAX_VALUE
                for (cIdx in 0 until k) {
                    val d = p.deltaE(centroids[cIdx])
                    if (d < bestD) { bestD = d; bestIdx = cIdx }
                }
                sumsL[bestIdx] += p.l; sumsA[bestIdx] += p.a; sumsB[bestIdx] += p.b
                counts[bestIdx]++
            }
            for (cIdx in 0 until k) {
                if (counts[cIdx] > 0) {
                    centroids[cIdx] = Oklab(sumsL[cIdx] / counts[cIdx], sumsA[cIdx] / counts[cIdx], sumsB[cIdx] / counts[cIdx])
                }
            }
        }
        return centroids.map(::oklabToSrgb)
    }

    private fun relativeLuminance(rgb24: Int): Double =
        0.2126 * srgbChannelToLinear((rgb24 ushr 16) and 0xFF) +
            0.7152 * srgbChannelToLinear((rgb24 ushr 8) and 0xFF) +
            0.0722 * srgbChannelToLinear(rgb24 and 0xFF)

    private fun srgbChannelToLinear(u8: Int): Double {
        val c = u8 / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun linearToSrgbByte(lin: Double): Int {
        val c = lin.coerceIn(0.0, 1.0)
        val s = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055
        return (s * 255.0).roundToInt().coerceIn(0, 255)
    }
}
