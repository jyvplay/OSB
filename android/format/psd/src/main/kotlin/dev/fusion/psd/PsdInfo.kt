package dev.fusion.psd

import java.io.InputStream

/**
 * PSD header parser only. No layer decode, no composite, no export.
 * Counts as source-present infrastructure, not a finished PSD feature.
 */
data class PsdHeader(val channels: Int, val height: Int, val width: Int, val depth: Int, val colorMode: Int)

object PsdInfo {
    fun read(input: InputStream): Result<PsdHeader> = runCatching {
        val header = input.readNBytes(26)
        require(header.size == 26) { "File too small" }
        require(String(header, 0, 4, Charsets.US_ASCII) == "8BPS") { "Not a PSD/PSB" }
        val version = ((header[4].toInt() and 0xff) shl 8) or (header[5].toInt() and 0xff)
        require(version == 1) { "PSB (version 2) is not supported in this build" }
        val channels = u16(header, 12)
        val height = u32(header, 14)
        val width = u32(header, 18)
        val depth = u16(header, 22)
        val colorMode = u16(header, 24)
        PsdHeader(channels, height, width, depth, colorMode)
    }

    private fun u16(b: ByteArray, o: Int): Int = ((b[o].toInt() and 0xff) shl 8) or (b[o + 1].toInt() and 0xff)
    private fun u32(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xff) shl 24) or ((b[o + 1].toInt() and 0xff) shl 16) or
            ((b[o + 2].toInt() and 0xff) shl 8) or (b[o + 3].toInt() and 0xff)
}
