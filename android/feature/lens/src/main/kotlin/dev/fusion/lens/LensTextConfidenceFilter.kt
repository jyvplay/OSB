// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

/**
 * Bounded OCR text block confidence filter.
 */
class LensTextConfidenceFilter private constructor() {
    companion object {
        data class OcrBlock(val text: String, val confidence: Double)

        fun filter(blocks: List<OcrBlock>, minConfidence: Double): List<OcrBlock> {
            require(minConfidence in 0.0..1.0) { "minConfidence out of bounds" }
            return blocks.filter { it.confidence >= minConfidence && it.text.trim().isNotEmpty() }
        }
    }
}
