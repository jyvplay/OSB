// SPDX-License-Identifier: Apache-2.0
package dev.fusion.psd

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Clean-room walker of the PSD v1 Layer Records table inside the Layer & Mask Information
 * section that [PsdCompositeEngine] skips (complements [PsdResources]).
 *
 * Parses the binary Adobe Photoshop Layer Info payload (`[layerInfoLength: u32]` optional or
 * direct `[layerCount: i16]` + `abs(layerCount)` layer records):
 * - Signed 16-bit `layerCount` (`< 0` signals first alpha channel holds merged transparency),
 * - Bounding rectangle `(top, left, bottom, right)`,
 * - Channel count (`1..56`) + `(channelId: i16, channelDataLength: u32)` table,
 * - `'8BIM'` blend-mode signature + 4-byte ASCII blend key (`norm`, `mult`, `scrn`, `over`, ...),
 * - `opacity` (`0..255`), `clipping` (`0=base, 1=non-base`), `flags` (`transparencyProtected`,
 *   `visible` = `(flags & 0x02) == 0`),
 * - Extra-data block skipping layer mask & blending ranges to decode the 4-byte-padded Pascal
 *   `name`.
 */
object PsdLayerRecords {
    const val SCOPE =
        "PSD v1 Layer Info header & layer-record metadata walker (bounds, channel descriptors, 8BIM blend mode, opacity, visibility, 4-byte padded Pascal name); per-layer pixel decompression and PSB 64-bit lengths out of scope."

    const val MAX_SECTION_BYTES = 16 * 1024 * 1024
    const val MAX_LAYERS = 1_024
    const val MAX_CHANNELS_PER_LAYER = 56

    data class ChannelRef(val channelId: Int, val byteLength: Long)

    data class LayerRecord(
        val name: String,
        val top: Int,
        val left: Int,
        val bottom: Int,
        val right: Int,
        val blendMode: String,
        val opacity: Int,
        val nonBaseClipping: Boolean,
        val visible: Boolean,
        val transparencyProtected: Boolean,
        val channels: List<ChannelRef>,
    ) {
        val width: Int get() = (right - left).coerceAtLeast(0)
        val height: Int get() = (bottom - top).coerceAtLeast(0)
    }

    data class LayerTable(
        val mergedAlphaFirstChannel: Boolean,
        val layers: List<LayerRecord>,
    )

    fun parseLayerInfo(bytes: ByteArray): LayerTable {
        require(bytes.size in 2..MAX_SECTION_BYTES) {
            "PSD layer-info payload size ${bytes.size} outside 2..$MAX_SECTION_BYTES"
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val rawCount = buf.short.toInt()
        val mergedAlpha = rawCount < 0
        val count = kotlin.math.abs(rawCount)
        require(count <= MAX_LAYERS) { "PSD layer count $count exceeds $MAX_LAYERS" }

        val layers = ArrayList<LayerRecord>(count)
        for (idx in 0 until count) {
            require(buf.remaining() >= 18) { "truncated PSD layer[$idx] header" }
            val top = buf.int
            val left = buf.int
            val bottom = buf.int
            val right = buf.int
            require(bottom >= top && right >= left) {
                "inverted PSD layer[$idx] bounds ($left,$top)..($right,$bottom)"
            }

            val chanCount = buf.short.toInt() and 0xFFFF
            require(chanCount in 1..MAX_CHANNELS_PER_LAYER) {
                "PSD layer[$idx] channel count $chanCount outside 1..$MAX_CHANNELS_PER_LAYER"
            }
            require(buf.remaining() >= chanCount * 6 + 16) { "truncated PSD layer[$idx] channel table" }
            val channels = ArrayList<ChannelRef>(chanCount)
            for (c in 0 until chanCount) {
                val cid = buf.short.toInt()
                val clen = buf.int.toLong() and 0xFFFF_FFFFL
                require(clen >= 2L) { "PSD layer[$idx] channel[$c] length $clen < 2 (compression marker missing)" }
                channels.add(ChannelRef(cid, clen))
            }

            val sig = ByteArray(4).also { buf.get(it) }.toString(StandardCharsets.US_ASCII)
            require(sig == "8BIM") { "bad PSD layer[$idx] blend signature '$sig'" }
            val blend = ByteArray(4).also { buf.get(it) }.toString(StandardCharsets.US_ASCII)
            require(blend.all { it.code in 0x20..0x7E }) { "non-ASCII blend mode in layer[$idx]" }

            val opacity = buf.get().toInt() and 0xFF
            val clipping = buf.get().toInt() and 0xFF
            require(clipping == 0 || clipping == 1) { "invalid PSD clipping byte $clipping in layer[$idx]" }
            val flags = buf.get().toInt() and 0xFF
            buf.get() // filler byte

            val extraLen = buf.int
            require(extraLen >= 12 && buf.remaining() >= extraLen) {
                "invalid PSD layer[$idx] extraDataLength $extraLen"
            }
            val extraEnd = buf.position() + extraLen

            val maskLen = buf.int
            require(maskLen in setOf(0, 20, 36) && buf.position() + maskLen <= extraEnd) {
                "invalid PSD layer[$idx] mask data length $maskLen"
            }
            buf.position(buf.position() + maskLen)

            require(buf.position() + 4 <= extraEnd) { "truncated blending-ranges length in layer[$idx]" }
            val blendRangesLen = buf.int
            require(blendRangesLen >= 0 && blendRangesLen % 8 == 0 && buf.position() + blendRangesLen < extraEnd) {
                "invalid PSD layer[$idx] blending-ranges length $blendRangesLen"
            }
            buf.position(buf.position() + blendRangesLen)

            val nameLen = buf.get().toInt() and 0xFF
            require(buf.position() + nameLen <= extraEnd) { "truncated Pascal layer name in layer[$idx]" }
            val nameBytes = ByteArray(nameLen).also { buf.get(it) }
            val name = nameBytes.toString(StandardCharsets.ISO_8859_1)
            val paddedNameTotal = ((1 + nameLen + 3) / 4) * 4
            val padSkip = paddedNameTotal - (1 + nameLen)
            require(buf.position() + padSkip <= extraEnd) { "truncated Pascal 4-byte pad in layer[$idx]" }
            buf.position(extraEnd) // advance past any remaining tagged blocks (`8BIM`/`8B64` `luni`, etc.)

            layers.add(
                LayerRecord(
                    name = name,
                    top = top,
                    left = left,
                    bottom = bottom,
                    right = right,
                    blendMode = blend,
                    opacity = opacity,
                    nonBaseClipping = clipping == 1,
                    visible = (flags and 0x02) == 0,
                    transparencyProtected = (flags and 0x01) != 0,
                    channels = channels,
                ),
            )
        }
        return LayerTable(mergedAlpha, layers)
    }
}
