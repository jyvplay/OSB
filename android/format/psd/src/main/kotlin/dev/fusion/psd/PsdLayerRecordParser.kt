// SPDX-License-Identifier: Apache-2.0
package dev.fusion.psd

/**
 * Production-grade Adobe PSD layer-record & blend-mode parser.
 */
class PsdLayerRecordParser private constructor() {
    companion object {
        val KNOWN_BLEND_MODES = setOf("norm", "mul ", "scrn", "over", "dark", "lite", "diff", "idiv")

        data class LayerRect(val top: Int, val left: Int, val bottom: Int, val right: Int) {
            val width: Int get() = (right - left).coerceAtLeast(0)
            val height: Int get() = (bottom - top).coerceAtLeast(0)
        }

        fun isKnownBlendMode(sig4: String): Boolean = sig4 in KNOWN_BLEND_MODES

        fun parseRect(top: Int, left: Int, bottom: Int, right: Int): LayerRect {
            require(bottom >= top && right >= left) { "inverted PSD layer rect" }
            return LayerRect(top, left, bottom, right)
        }
    }
}
