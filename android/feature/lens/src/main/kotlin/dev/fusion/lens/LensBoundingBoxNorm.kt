// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

/**
 * Bounded OCR/object bounding-box normalizer and IoU calculator.
 */
class LensBoundingBoxNorm private constructor() {
    companion object {
        data class NormBox(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

        fun normalize(x1: Int, y1: Int, x2: Int, y2: Int, imgW: Int, imgH: Int): NormBox {
            require(imgW > 0 && imgH > 0) { "invalid image dimensions" }
            val nx1 = (x1.toDouble() / imgW).coerceIn(0.0, 1.0)
            val ny1 = (y1.toDouble() / imgH).coerceIn(0.0, 1.0)
            val nx2 = (x2.toDouble() / imgW).coerceIn(0.0, 1.0)
            val ny2 = (y2.toDouble() / imgH).coerceIn(0.0, 1.0)
            require(nx1 <= nx2 && ny1 <= ny2) { "inverted box" }
            return NormBox(nx1, ny1, nx2, ny2)
        }

        fun iou(a: NormBox, b: NormBox): Double {
            val ix1 = maxOf(a.x1, b.x1)
            val iy1 = maxOf(a.y1, b.y1)
            val ix2 = minOf(a.x2, b.x2)
            val iy2 = minOf(a.y2, b.y2)
            val iw = maxOf(0.0, ix2 - ix1)
            val ih = maxOf(0.0, iy2 - iy1)
            val inter = iw * ih
            val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
            val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
            val union = areaA + areaB - inter
            return if (union <= 0.0) 0.0 else inter / union
        }
    }
}
