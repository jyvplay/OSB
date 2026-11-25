// SPDX-License-Identifier: Apache-2.0
package dev.fusion.image

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bounded aspect-preserving image fit calculator.
 */
class ImageAspectRatioFit private constructor() {
    companion object {
        data class Size(val width: Int, val height: Int)

        fun fitInside(srcW: Int, srcH: Int, maxW: Int, maxH: Int): Size {
            require(srcW > 0 && srcH > 0 && maxW > 0 && maxH > 0) { "dimensions must be positive" }
            val scale = min(maxW.toDouble() / srcW.toDouble(), maxH.toDouble() / srcH.toDouble())
            val w = (srcW * scale).roundToInt().coerceIn(1, maxW)
            val h = (srcH * scale).roundToInt().coerceIn(1, maxH)
            return Size(w, h)
        }
    }
}
