// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.image

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Clean-room ISO/IEC 14496-12 / ISO/IEC 23008-12 ISOBMFF (`HEIC` / `HEIF` / `AVIF`) container
 * box header inspector (`FF-07`).
 *
 * Parses `ftyp` major/compatible brands, nested `meta` -> `iprp` -> `ipco` -> `ispe` image spatial
 * extents (`widthPx`, `heightPx`), `irot` (`0/90/180/270` rotation), and `imir` mirror axis purely
 * in memory-safe Kotlin without invoking native C/C++ codec parsers on untrusted headers.
 */
object HeicIsomBoxInspector {
    private val SUPPORTED_BRANDS = setOf("heic", "heix", "heim", "heis", "mif1", "msf1", "avif", "avis")

    data class HeicHeaderSummary(
        val majorBrand: String,
        val compatibleBrands: List<String>,
        val widthPx: Int,
        val heightPx: Int,
        val rotationDegrees: Int,
        val mirroredAxis: Int?, // 0 = vertical-axis flip, 1 = horizontal-axis flip
    ) {
        val isSupportedHeifOrAvif: Boolean
            get() = majorBrand in SUPPORTED_BRANDS || compatibleBrands.any { it in SUPPORTED_BRANDS }
    }

    fun inspect(bytes: ByteArray): HeicHeaderSummary {
        require(bytes.size >= 16) { "ISOBMFF stream shorter than 16 bytes" }
        var majorBrand = ""
        val compat = ArrayList<String>()
        var width = 0
        var height = 0
        var rotDeg = 0
        var mirrorAxis: Int? = null

        fun walkBoxes(start: Int, end: Int, depth: Int) {
            require(depth <= 8) { "ISOBMFF box nesting exceeds 8" }
            var pos = start
            while (pos + 8 <= end) {
                val size32 = readU32(bytes, pos)
                val type = String(bytes, pos + 4, 4, StandardCharsets.US_ASCII)
                val (boxSize, headerLen) = when (size32) {
                    1L -> {
                        require(pos + 16 <= end) { "truncated 64-bit box header" }
                        readU64(bytes, pos + 8) to 16
                    }
                    0L -> (end - pos).toLong() to 8
                    else -> size32 to 8
                }
                require(boxSize >= headerLen && pos + boxSize <= end) {
                    "malformed ISOBMFF box '$type' size=$boxSize at offset=$pos"
                }
                val payloadStart = pos + headerLen
                val boxEnd = (pos + boxSize).toInt()
                when (type) {
                    "ftyp" -> {
                        require(payloadStart + 8 <= boxEnd) { "truncated ftyp box" }
                        majorBrand = String(bytes, payloadStart, 4, StandardCharsets.US_ASCII).trim()
                        var p = payloadStart + 8
                        while (p + 4 <= boxEnd) {
                            compat.add(String(bytes, p, 4, StandardCharsets.US_ASCII).trim())
                            p += 4
                        }
                    }
                    "meta" -> {
                        // FullBox: 4-byte version/flags header before child boxes
                        if (payloadStart + 4 < boxEnd) walkBoxes(payloadStart + 4, boxEnd, depth + 1)
                    }
                    "iprp", "ipco" -> walkBoxes(payloadStart, boxEnd, depth + 1)
                    "ispe" -> {
                        // FullBox (4B) + image_width (4B) + image_height (4B)
                        require(payloadStart + 12 <= boxEnd) { "truncated ispe box" }
                        val w = readU32(bytes, payloadStart + 4)
                        val h = readU32(bytes, payloadStart + 8)
                        require(w in 1..65_535 && h in 1..65_535) { "ispe dimensions ${w}x$h out of bounds" }
                        if (w * h > width.toLong() * height) {
                            width = w.toInt()
                            height = h.toInt()
                        }
                    }
                    "irot" -> {
                        require(payloadStart + 1 <= boxEnd) { "truncated irot box" }
                        rotDeg = ((bytes[payloadStart].toInt() and 0x03) * 90)
                    }
                    "imir" -> {
                        require(payloadStart + 1 <= boxEnd) { "truncated imir box" }
                        mirrorAxis = bytes[payloadStart].toInt() and 0x01
                    }
                }
                pos = boxEnd
            }
        }

        walkBoxes(0, bytes.size, 0)
        require(majorBrand.isNotEmpty()) { "missing mandatory ftyp box" }
        return HeicHeaderSummary(majorBrand, compat, width, height, rotDeg, mirrorAxis)
    }

    private fun readU32(b: ByteArray, off: Int): Long =
        ByteBuffer.wrap(b, off, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFF_FFFFL

    private fun readU64(b: ByteArray, off: Int): Long {
        val v = ByteBuffer.wrap(b, off, 8).order(ByteOrder.BIG_ENDIAN).long
        require(v >= 0L) { "uint64 box size overflow" }
        return v
    }
}
