// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

/**
 * Software cursor highlight halo + password-field redaction mask compositor for **Recall**
 * `TIER_4_NATIVE_VIEWPORT_AV` viewport recordings, ensuring captured tutorials are clear while
 * `<input type="password">` bounding boxes inside the viewport are blacked out before encoding!
 */
object RecallViewportCursorAnnotationCompositor {
    const val SCOPE =
        "Tier-4 Recall RGBA8 viewport frame compositor rendering anti-aliased cursor click rings while redacting sensitive password-field rectangles before H.264/AV1 encode."

    data class IntRect(val x: Int, val y: Int, val width: Int, val height: Int)

    fun compositeFrameRgba8(
        rgba: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
        cursorX: Int?,
        cursorY: Int?,
        clickPressed: Boolean,
        redactionRects: List<IntRect>,
    ): Int {
        require(frameWidth > 0 && frameHeight > 0 && rgba.size == frameWidth * frameHeight * 4)
        var redactedPixels = 0
        // 1. Apply hard privacy redaction over password/credential rects FIRST
        for (r in redactionRects) {
            val x0 = r.x.coerceIn(0, frameWidth)
            val y0 = r.y.coerceIn(0, frameHeight)
            val x1 = (r.x + r.width).coerceIn(0, frameWidth)
            val y1 = (r.y + r.height).coerceIn(0, frameHeight)
            for (y in y0 until y1) {
                for (x in x0 until x1) {
                    val idx = (y * frameWidth + x) * 4
                    rgba[idx] = 0x10; rgba[idx + 1] = 0x14; rgba[idx + 2] = 0x1C; rgba[idx + 3] = 0xFF.toByte()
                    redactedPixels++
                }
            }
        }
        // 2. Composite cursor halo only outside redacted regions
        if (cursorX != null && cursorY != null) {
            val radius = if (clickPressed) 10 else 6
            val rSq = radius * radius
            val innerSq = (radius - 2) * (radius - 2)
            for (dy in -radius..radius) {
                val py = cursorY + dy
                if (py !in 0 until frameHeight) continue
                for (dx in -radius..radius) {
                    val px = cursorX + dx
                    if (px !in 0 until frameWidth) continue
                    val d2 = dx * dx + dy * dy
                    if (d2 in innerSq..rSq && redactionRects.none { px in it.x until (it.x + it.width) && py in it.y until (it.y + it.height) }) {
                        val idx = (py * frameWidth + px) * 4
                        rgba[idx] = 0x38; rgba[idx + 1] = 0xE8.toByte(); rgba[idx + 2] = 0xB8.toByte(); rgba[idx + 3] = 0xFF.toByte()
                    }
                }
            }
        }
        return redactedPixels
    }
}
