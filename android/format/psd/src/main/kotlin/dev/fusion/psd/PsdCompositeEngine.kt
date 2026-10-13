package dev.fusion.psd

import java.io.InputStream

/**
 * Clean-room PSD parser and composite decoder (FF-08).
 * Pure Kotlin/Java, zero GPL libraries.
 *
 * The decode core is deliberately free of `android.graphics` so it runs in
 * plain JVM unit tests. [PsdRaster] holds the finished ARGB pixels; the thin
 * Android wrapper that turns them into a `Bitmap` lives in `PsdBitmaps.kt`.
 *
 * Supported:
 *  - 8BPS version 1, 8-bit channel depth, RGB colour mode
 *  - compression 0 (raw planar) and 1 (PackBits RLE)
 *  - 3 channels (RGB) or 4 (RGBA); extra channels are ignored
 *
 * Not supported (and reported as a clear failure, never silently wrong):
 *  - PSB (version 2), 16/32-bit depth, CMYK/Lab/Indexed, ZIP compression
 *  - layer trees, masks, adjustment layers, smart objects, export
 */
data class PsdRaster(
    val width: Int,
    val height: Int,
    val channels: Int,
    /** Row-major ARGB_8888 pixels, length == width * height. */
    val argb: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PsdRaster) return false
        return width == other.width && height == other.height &&
            channels == other.channels && argb.contentEquals(other.argb)
    }

    override fun hashCode(): Int {
        var r = width
        r = 31 * r + height
        r = 31 * r + channels
        r = 31 * r + argb.contentHashCode()
        return r
    }
}

object PsdCompositeEngine {

    class PsdParseException(message: String) : Exception(message)

    /** Hard ceiling so a malformed header cannot trigger a huge allocation. */
    const val MAX_PIXELS = 64_000_000 // 64 MP

    /**
     * Decodes the flattened composite image of a PSD into ARGB pixels.
     * Pure JVM — safe to call from unit tests.
     */
    fun decodeRaster(input: InputStream): Result<PsdRaster> = runCatching {
        val header = input.readNBytes(26)
        if (header.size < 26) throw PsdParseException("File is too small to be a PSD")
        if (String(header, 0, 4, Charsets.US_ASCII) != "8BPS") {
            throw PsdParseException("Not a Photoshop file (missing 8BPS signature)")
        }

        val version = u16(header, 4)
        if (version != 1) throw PsdParseException("PSB (version $version) is not supported; only PSD v1")

        val channels = u16(header, 12)
        val height = u32(header, 14)
        val width = u32(header, 18)
        val depth = u16(header, 22)
        val colorMode = u16(header, 24)

        if (depth != 8) throw PsdParseException("Only 8-bit channels are supported (found $depth-bit)")
        if (colorMode != 3) throw PsdParseException("Only RGB colour mode is supported (found mode $colorMode)")
        if (width <= 0 || height <= 0) throw PsdParseException("Invalid dimensions ${width}x$height")
        if (channels < 3) throw PsdParseException("Expected at least 3 channels, found $channels")

        val pixelCount = width.toLong() * height.toLong()
        if (pixelCount > MAX_PIXELS) {
            throw PsdParseException("Image is too large to decode safely ($width x $height)")
        }

        // Skip Color Mode Data, Image Resources, and Layer & Mask sections.
        skipLengthPrefixedBlock(input)
        skipLengthPrefixedBlock(input)
        skipLengthPrefixedBlock(input)

        val compressionBytes = input.readNBytes(2)
        if (compressionBytes.size < 2) throw PsdParseException("Truncated before compression marker")
        val compression = u16(compressionBytes, 0)

        val usableChannels = if (channels >= 4) 4 else 3
        val planeSize = width * height
        val planes = Array(usableChannels) { ByteArray(planeSize) }

        when (compression) {
            0 -> readRawPlanes(input, planes, planeSize)
            1 -> readRlePlanes(input, planes, channels, usableChannels, width, height)
            else -> throw PsdParseException(
                "Unsupported compression $compression (ZIP-compressed PSDs are not supported)"
            )
        }

        PsdRaster(width, height, usableChannels, assembleArgb(planes, planeSize, usableChannels))
    }

    private fun readRawPlanes(input: InputStream, planes: Array<ByteArray>, planeSize: Int) {
        for (plane in planes) {
            val read = input.readNBytes(plane, 0, planeSize)
            if (read < planeSize) throw PsdParseException("Truncated uncompressed channel data")
        }
    }

    private fun readRlePlanes(
        input: InputStream,
        planes: Array<ByteArray>,
        totalChannels: Int,
        usableChannels: Int,
        width: Int,
        height: Int
    ) {
        // Scanline byte counts for EVERY channel, even ones we will not keep.
        val tableLength = totalChannels * height * 2
        val table = input.readNBytes(tableLength)
        if (table.size < tableLength) throw PsdParseException("Truncated RLE scanline table")

        val lengths = IntArray(totalChannels * height) { u16(table, it * 2) }

        for (channel in 0 until totalChannels) {
            for (row in 0 until height) {
                val scanlineLength = lengths[channel * height + row]
                val packed = input.readNBytes(scanlineLength)
                if (packed.size < scanlineLength) {
                    throw PsdParseException("Truncated RLE data in channel $channel row $row")
                }
                // Channels beyond those we keep still have to be consumed.
                if (channel < usableChannels) {
                    unpackBits(packed, planes[channel], row * width, width)
                }
            }
        }
    }

    private fun assembleArgb(planes: Array<ByteArray>, planeSize: Int, channels: Int): IntArray {
        val red = planes[0]
        val green = planes[1]
        val blue = planes[2]
        val alpha = if (channels > 3) planes[3] else null
        val argb = IntArray(planeSize)
        for (i in 0 until planeSize) {
            val a = alpha?.let { it[i].toInt() and 0xFF } ?: 0xFF
            argb[i] = (a shl 24) or
                ((red[i].toInt() and 0xFF) shl 16) or
                ((green[i].toInt() and 0xFF) shl 8) or
                (blue[i].toInt() and 0xFF)
        }
        return argb
    }

    /**
     * PackBits decoder.
     * n in  0..127  -> copy the next n+1 bytes literally
     * n in -127..-1 -> repeat the next byte 1-n times
     * n == -128     -> no-op
     */
    internal fun unpackBits(src: ByteArray, dst: ByteArray, dstStart: Int, count: Int): Int {
        var srcIndex = 0
        var dstIndex = dstStart
        val end = (dstStart + count).coerceAtMost(dst.size)

        while (srcIndex < src.size && dstIndex < end) {
            val n = src[srcIndex++].toInt()
            when {
                n >= 0 -> {
                    var remaining = n + 1
                    while (remaining > 0 && srcIndex < src.size && dstIndex < end) {
                        dst[dstIndex++] = src[srcIndex++]
                        remaining--
                    }
                }
                n > -128 -> {
                    if (srcIndex >= src.size) break
                    val value = src[srcIndex++]
                    var remaining = 1 - n
                    while (remaining > 0 && dstIndex < end) {
                        dst[dstIndex++] = value
                        remaining--
                    }
                }
                // n == -128 is a documented no-op in the PackBits spec.
            }
        }
        return dstIndex
    }

    private fun skipLengthPrefixedBlock(input: InputStream) {
        val lengthBytes = input.readNBytes(4)
        if (lengthBytes.size < 4) throw PsdParseException("Truncated section header")
        val length = u32(lengthBytes, 0).toLong()
        var skipped = 0L
        while (skipped < length) {
            val n = input.skip(length - skipped)
            if (n <= 0) {
                // skip() can legitimately return 0; fall back to reading.
                if (input.read() == -1) throw PsdParseException("Truncated section body")
                skipped++
            } else {
                skipped += n
            }
        }
    }

    private fun u16(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or
            ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or
            (b[o + 3].toInt() and 0xFF)
}
