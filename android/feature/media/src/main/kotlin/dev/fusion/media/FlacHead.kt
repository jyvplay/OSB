// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/**
 * FLAC stream-header parser pinned to RFC 9639 (December 2024):
 *
 * - A FLAC bitstream begins with the fLaC marker (0x664C6143) followed by
 *   a mandatory streaminfo metadata block (RFC 9639 section 8.2): it
 *   "MUST be present as the first metadata block in the stream".
 * - Each metadata block header (section 8.1) is 4 bytes: 1 bit last-flag,
 *   7 bits type (0 = streaminfo; 127 is forbidden), 24 bits big-endian
 *   length. Streaminfo's body is exactly 34 bytes.
 * - Streaminfo layout: u(16) min block size, u(16) max block size,
 *   u(24) min frame size, u(24) max frame size, u(20) sample rate in Hz,
 *   u(3) channels-1 (1..8), u(5) bits-per-sample-1 (4..32),
 *   u(36) total interchannel samples (0 = unknown), u(128) MD5.
 * - "The minimum block size and the maximum block size MUST be in the
 *   16-65535 range. The minimum block size MUST be equal to or less than
 *   the maximum block size." Sample rate 0 is invalid for a stream with
 *   audio; FLAC supports 1..1048575 Hz.
 *
 * Pure JVM logic over a byte array; refusal is IllegalArgumentException.
 * This parses the header only — no frames, no decoding, no audio claims.
 */
object FlacHead {
    data class StreamInfo(
        val lastMetadataBlock: Boolean,
        val minBlockSize: Int,
        val maxBlockSize: Int,
        val minFrameSize: Int,
        val maxFrameSize: Int,
        val sampleRateHz: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val totalSamples: Long,
        val md5Hex: String,
    ) {
        /** Duration in milliseconds, floor; null when total samples is unknown (0). */
        val durationMs: Long? get() =
            if (totalSamples == 0L) null else totalSamples * 1000 / sampleRateHz
    }

    const val MARKER = 0x664C6143
    const val STREAMINFO_LENGTH = 34

    fun parse(bytes: ByteArray): StreamInfo {
        require(bytes.size >= 4 + 4 + STREAMINFO_LENGTH) { "too short for fLaC + streaminfo: ${bytes.size}" }
        val marker = be32(bytes, 0)
        require(marker == MARKER) { "missing fLaC marker" }
        val head = bytes[4].toInt() and 0xFF
        val last = (head and 0x80) != 0
        val type = head and 0x7F
        require(type != 127) { "metadata block type 127 is forbidden" }
        require(type == 0) { "first metadata block must be streaminfo (type 0), got $type" }
        val length = be24(bytes, 5)
        require(length == STREAMINFO_LENGTH) { "streaminfo length must be 34, got $length" }
        val b = 8 // streaminfo body offset
        val minBlock = be16(bytes, b)
        val maxBlock = be16(bytes, b + 2)
        require(minBlock in 16..65535) { "min block size out of 16..65535: $minBlock" }
        require(maxBlock in 16..65535) { "max block size out of 16..65535: $maxBlock" }
        require(minBlock <= maxBlock) { "min block size exceeds max block size" }
        val minFrame = be24(bytes, b + 4)
        val maxFrame = be24(bytes, b + 7)
        // 64 bits holding: sampleRate(20) channels-1(3) bps-1(5) totalSamples(36)
        var acc = 0L
        for (i in 0 until 8) acc = (acc shl 8) or (bytes[b + 10 + i].toLong() and 0xFF)
        val sampleRate = (acc ushr 44).toInt()
        val channels = ((acc ushr 41).toInt() and 0x7) + 1
        val bps = ((acc ushr 36).toInt() and 0x1F) + 1
        val totalSamples = acc and 0xFFFFFFFFFL // low 36 bits
        require(sampleRate in 1..1048575) { "sample rate out of 1..1048575: $sampleRate" }
        require(bps >= 4) { "bits per sample below 4: $bps" }
        val md5 = StringBuilder(32)
        for (i in 0 until 16) md5.append("%02x".format(bytes[b + 18 + i].toInt() and 0xFF))
        return StreamInfo(last, minBlock, maxBlock, minFrame, maxFrame, sampleRate, channels, bps, totalSamples, md5.toString())
    }

    private fun be16(b: ByteArray, off: Int) = ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)
    private fun be24(b: ByteArray, off: Int) = (be16(b, off) shl 8) or (b[off + 2].toInt() and 0xFF)
    private fun be32(b: ByteArray, off: Int) = (be24(b, off) shl 8) or (b[off + 3].toInt() and 0xFF)
}
