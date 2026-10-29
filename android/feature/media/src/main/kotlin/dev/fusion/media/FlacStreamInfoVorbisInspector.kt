// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure-JVM **RFC 9639 (`FLAC` `fLaC`) `STREAMINFO` & Vorbis-Comment (`METADATA_BLOCK_VORBIS_COMMENT`)
 * Lossless Audio Inspector & Tag Builder** for Recall's lossless music capture pipeline.
 */
object FlacStreamInfoVorbisInspector {
    const val SCOPE =
        "IETF RFC 9639 FLAC fLaC magic, 34-byte STREAMINFO sample-rate/channels/PCM-MD5 parser, and little-endian Vorbis Comment builder."

    data class FlacStreamSummary(
        val sampleRateHz: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val totalSamples: Long,
        val md5Hex: String,
    )

    fun parseStreamInfo(flacBytes: ByteArray): FlacStreamSummary {
        require(flacBytes.size >= 42 && String(flacBytes, 0, 4, Charsets.US_ASCII) == "fLaC") {
            "Not a valid RFC 9639 fLaC stream"
        }
        val blockType = flacBytes[4].toInt() and 0x7F
        val blockLen = ((flacBytes[5].toInt() and 0xFF) shl 16) or
            ((flacBytes[6].toInt() and 0xFF) shl 8) or
            (flacBytes[7].toInt() and 0xFF)
        require(blockType == 0 && blockLen == 34) { "First FLAC metadata block must be 34-byte STREAMINFO" }

        // Bytes 18..25 (offset 8 + 10 = 18) pack: 20-bit sampleRate | 3-bit (channels-1) | 5-bit (bps-1) | 36-bit totalSamples
        val packed = ByteBuffer.wrap(flacBytes, 18, 8).order(ByteOrder.BIG_ENDIAN).long
        val sampleRate = ((packed ushr 44) and 0xFFFFFL).toInt()
        val channels = (((packed ushr 41) and 0x7L).toInt()) + 1
        val bps = (((packed ushr 36) and 0x1FL).toInt()) + 1
        val totalSamples = packed and 0xFFFFFFFFFL
        val md5 = flacBytes.copyOfRange(26, 42).joinToString("") { "%02x".format(it) }
        return FlacStreamSummary(sampleRate, channels, bps, totalSamples, md5)
    }

    fun buildVorbisCommentBlock(vendor: String, tags: Map<String, String>, isLastMetadataBlock: Boolean = true): ByteArray {
        val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
        val entries = tags.entries.map { "${it.key.uppercase()}=${it.value}".toByteArray(Charsets.UTF_8) }
        val payloadLen = 4 + vendorBytes.size + 4 + entries.sumOf { 4 + it.size }
        val payload = ByteBuffer.allocate(payloadLen).order(ByteOrder.LITTLE_ENDIAN)
        payload.putInt(vendorBytes.size)
        payload.put(vendorBytes)
        payload.putInt(entries.size)
        for (e in entries) {
            payload.putInt(e.size)
            payload.put(e)
        }
        val header = ByteArray(4)
        header[0] = ((if (isLastMetadataBlock) 0x80 else 0x00) or 0x04).toByte() // type 4 = VORBIS_COMMENT
        header[1] = ((payloadLen ushr 16) and 0xFF).toByte()
        header[2] = ((payloadLen ushr 8) and 0xFF).toByte()
        header[3] = (payloadLen and 0xFF).toByte()
        return header + payload.array()
    }
}
