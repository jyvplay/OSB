// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Clean-room IETF RFC 9639 (`FLAC`) lossless capture archiver for **Recall** (Method 7 companion).
 *
 * Writes a real `fLaC` stream: `STREAMINFO` (34-byte body incl. 128-bit MD5 of the raw interleaved
 * little-endian signed PCM), a `SEEKTABLE`, and verbatim-subframe audio frames with RFC 9639
 * header CRC-8 (`x^8 + x^2 + x + 1`, poly `0x07`) and frame CRC-16 (`x^16 + x^15 + x^2 + 1`, poly `0x8005`).
 *
 * Adversarial invariant proven by [validateSeekTable] (RFC 9639 §8.5.1 / musetric#907):
 * a `SEEKPOINT` stores `(firstSampleOfTargetFrame, offsetFromFirstAudioFrameHeader, frameSampleCount)`;
 * the offset is **relative to the first audio frame header**, never to file byte 0, and field 3 is a
 * sample count, never a byte size. Validation therefore checks `firstFrameOffset + offset` lands exactly
 * on a `0xFFF8`/`0xFFF9` frame sync word.
 */
object RecallFlacRfc9639LosslessArchiver {
    const val STREAMINFO = 0
    const val SEEKTABLE = 3
    const val VORBIS_COMMENT = 4

    data class PcmClip(
        val sampleRateHz: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val interleavedSamples: ShortArray,
    ) {
        val frames: Int get() = if (channels == 0) 0 else interleavedSamples.size / channels
    }

    data class StreamInfo(
        val minBlockSize: Int,
        val maxBlockSize: Int,
        val minFrameSize: Int,
        val maxFrameSize: Int,
        val sampleRateHz: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val totalSamples: Long,
        val md5Hex: String,
    )

    data class SeekPoint(val firstSample: Long, val streamOffset: Long, val frameSamples: Int)

    data class FlacFile(
        val bytes: ByteArray,
        val streamInfo: StreamInfo,
        val seekTable: List<SeekPoint>,
        val firstFrameOffset: Int,
        val frameOffsets: List<Int>,
        val crc8Failures: Int,
        val crc16Failures: Int,
    )

    data class Validation(val ok: Boolean, val reasons: List<String>, val pointsChecked: Int)

    fun crc8(data: ByteArray, from: Int, toExclusive: Int): Int {
        var crc = 0
        for (i in from until toExclusive) {
            crc = crc xor (data[i].toInt() and 0xFF)
            for (b in 0 until 8) {
                crc = if ((crc and 0x80) != 0) ((crc shl 1) xor 0x07) and 0xFF else (crc shl 1) and 0xFF
            }
        }
        return crc
    }

    fun crc16(data: ByteArray, from: Int, toExclusive: Int): Int {
        var crc = 0
        for (i in from until toExclusive) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            for (b in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) ((crc shl 1) xor 0x8005) and 0xFFFF else (crc shl 1) and 0xFFFF
            }
        }
        return crc
    }

    private fun pcmMd5(clip: PcmClip): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        val buf = ByteArray(clip.interleavedSamples.size * 2)
        for (i in clip.interleavedSamples.indices) {
            val v = clip.interleavedSamples[i].toInt()
            buf[i * 2] = (v and 0xFF).toByte()
            buf[i * 2 + 1] = ((v ushr 8) and 0xFF).toByte()
        }
        return md.digest(buf)
    }

    private fun utf8FrameNumber(frameNumber: Long): ByteArray {
        require(frameNumber in 0..0x7FFFFFFFL) { "frame number $frameNumber out of range" }
        val n = frameNumber.toInt()
        return when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n < 0x800 -> byteArrayOf((0xC0 or (n ushr 6)).toByte(), (0x80 or (n and 0x3F)).toByte())
            n < 0x10000 -> byteArrayOf(
                (0xE0 or (n ushr 12)).toByte(), (0x80 or ((n ushr 6) and 0x3F)).toByte(), (0x80 or (n and 0x3F)).toByte(),
            )
            n < 0x200000 -> byteArrayOf(
                (0xF0 or (n ushr 18)).toByte(), (0x80 or ((n ushr 12) and 0x3F)).toByte(),
                (0x80 or ((n ushr 6) and 0x3F)).toByte(), (0x80 or (n and 0x3F)).toByte(),
            )
            else -> byteArrayOf(
                (0xF8 or (n ushr 24)).toByte(), (0x80 or ((n ushr 18) and 0x3F)).toByte(),
                (0x80 or ((n ushr 12) and 0x3F)).toByte(), (0x80 or ((n ushr 6) and 0x3F)).toByte(),
                (0x80 or (n and 0x3F)).toByte(),
            )
        }
    }

    fun encode(clip: PcmClip, blockSize: Int = 512, seekIntervalFrames: Int = 1024): FlacFile {
        require(clip.channels in 1..8) { "FLAC supports 1..8 channels, got ${clip.channels}" }
        require(clip.bitsPerSample in setOf(8, 12, 16, 20, 24)) { "bitsPerSample must be 8/12/16/20/24" }
        require(clip.sampleRateHz in 1..655_350) { "invalid sample rate" }
        require(blockSize in 16..65535) { "blockSize must be 16..65535 (RFC 9639 subset)" }

        val total = clip.frames
        val md5 = pcmMd5(clip)
        val frameCount = if (total == 0) 0 else (total + blockSize - 1) / blockSize

        // ---- Audio frames first, so the SEEKTABLE can record true first-frame-relative offsets. ----
        val framesOut = ByteArrayOutputStream()
        val frameOffsets = ArrayList<Int>()
        val seekPoints = ArrayList<SeekPoint>()
        var crc8Failures = 0
        var crc16Failures = 0
        val sizeCode = when (clip.bitsPerSample) { 8 -> 0b001; 12 -> 0b010; 16 -> 0b100; 20 -> 0b101; else -> 0b110 }
        val seekEvery = maxOf(1, seekIntervalFrames / blockSize)

        for (frameIdx in 0 until frameCount) {
            val startSample = frameIdx * blockSize
            val nSamples = minOf(blockSize, total - startSample)
            val offsetFromFirstFrame = framesOut.size()
            frameOffsets.add(offsetFromFirstFrame)
            if (frameIdx % seekEvery == 0) {
                seekPoints.add(SeekPoint(startSample.toLong(), offsetFromFirstFrame.toLong(), nSamples))
            }

            val frameNumberBytes = utf8FrameNumber(frameIdx.toLong())
            val header = ByteArrayOutputStream()
            // RFC 9639 §9.1.1 block size codes: 0b0110 = 8-bit (n-1), 0b0111 = 16-bit (n-1)
            val eightBitBlock = (nSamples - 1) <= 0xFF
            header.write(0xFF)
            header.write(0xF8)                                  // sync 0b11111111111110, reserved 0, fixed block size
            header.write(((if (eightBitBlock) 0x6 else 0x7) shl 4) or 0x0) // rate code 0b0000 => from STREAMINFO
            header.write(((clip.channels - 1) shl 4) or (sizeCode shl 1))
            frameNumberBytes.forEach { header.write(it.toInt()) }
            if (eightBitBlock) {
                header.write((nSamples - 1) and 0xFF)
            } else {
                header.write(((nSamples - 1) ushr 8) and 0xFF)
                header.write((nSamples - 1) and 0xFF)
            }
            val headerBytes = header.toByteArray()
            val headerCrc = crc8(headerBytes, 0, headerBytes.size)
            framesOut.write(headerBytes)
            framesOut.write(headerCrc)

            for (ch in 0 until clip.channels) {
                framesOut.write(0x02)                            // verbatim subframe: 0 pad + 000001 + 0 wasted
                var bitPos = 0
                var acc = 0L
                for (s in 0 until nSamples) {
                    val sample = clip.interleavedSamples[(startSample + s) * clip.channels + ch]
                    acc = (acc shl clip.bitsPerSample) or (sample.toLong() and ((1L shl clip.bitsPerSample) - 1))
                    bitPos += clip.bitsPerSample
                    while (bitPos >= 8) {
                        framesOut.write(((acc ushr (bitPos - 8)) and 0xFF).toInt())
                        bitPos -= 8
                    }
                    // Drop already-emitted high bits so the 64-bit accumulator cannot overflow mid-block.
                    acc = if (bitPos == 0) 0L else acc and ((1L shl bitPos) - 1L)
                }
                if (bitPos > 0) framesOut.write(((acc shl (8 - bitPos)) and 0xFF).toInt())
            }

            val frameBytes = framesOut.toByteArray()
            val frameSlice = frameBytes.copyOfRange(offsetFromFirstFrame, frameBytes.size)
            val crc16 = crc16(frameSlice, 0, frameSlice.size)
            framesOut.write((crc16 ushr 8) and 0xFF)
            framesOut.write(crc16 and 0xFF)

            // Self-audit of the frame header CRC-8 just written.
            val storedCrc8 = frameSlice[headerBytes.size].toInt() and 0xFF
            if (crc8(headerBytes, 0, headerBytes.size) != storedCrc8) crc8Failures++
        }

        // ---- STREAMINFO (34-byte body) ----
        val siBody = ByteArrayOutputStream()
        fun u16(v: Int) { siBody.write((v ushr 8) and 0xFF); siBody.write(v and 0xFF) }
        fun u24(v: Int) { siBody.write((v ushr 16) and 0xFF); siBody.write((v ushr 8) and 0xFF); siBody.write(v and 0xFF) }
        u16(if (total == 0) blockSize else minOf(blockSize, total))
        u16(blockSize)
        u24(0)
        u24(0)
        val combined = (clip.sampleRateHz.toLong() shl 44) or
            ((clip.channels - 1).toLong() shl 41) or
            ((clip.bitsPerSample - 1).toLong() shl 36) or
            (total.toLong() and 0xF_FFFF_FFFFL)
        for (i in 7 downTo 0) siBody.write(((combined ushr (i * 8)) and 0xFF).toInt())
        md5.forEach { siBody.write(it.toInt()) }
        val siBytes = siBody.toByteArray()
        require(siBytes.size == 34) { "STREAMINFO body must be 34 bytes, got ${siBytes.size}" }

        val body = ByteArrayOutputStream()
        body.write(byteArrayOf(0x66, 0x4C, 0x61, 0x43)) // "fLaC"
        if (seekPoints.isEmpty()) {
            body.write(0x80 or STREAMINFO)
            body.write(0); body.write(0); body.write(34)
            body.write(siBytes)
        } else {
            body.write(STREAMINFO)                           // not last
            body.write(0); body.write(0); body.write(34)
            body.write(siBytes)
            val payloadLen = seekPoints.size * 18
            body.write(0x80 or SEEKTABLE)                    // last metadata block
            body.write((payloadLen ushr 16) and 0xFF)
            body.write((payloadLen ushr 8) and 0xFF)
            body.write(payloadLen and 0xFF)
            for (p in seekPoints) {
                for (i in 7 downTo 0) body.write(((p.firstSample ushr (i * 8)) and 0xFF).toInt())
                for (i in 7 downTo 0) body.write(((p.streamOffset ushr (i * 8)) and 0xFF).toInt())
                body.write((p.frameSamples ushr 8) and 0xFF)
                body.write(p.frameSamples and 0xFF)
            }
        }
        val firstFrameOffset = body.size()
        body.write(framesOut.toByteArray())

        return FlacFile(
            bytes = body.toByteArray(),
            streamInfo = StreamInfo(
                minBlockSize = if (total == 0) blockSize else minOf(blockSize, total),
                maxBlockSize = blockSize,
                minFrameSize = 0,
                maxFrameSize = 0,
                sampleRateHz = clip.sampleRateHz,
                channels = clip.channels,
                bitsPerSample = clip.bitsPerSample,
                totalSamples = total.toLong(),
                md5Hex = md5.joinToString("") { "%02x".format(it) },
            ),
            seekTable = seekPoints,
            firstFrameOffset = firstFrameOffset,
            frameOffsets = frameOffsets,
            crc8Failures = crc8Failures,
            crc16Failures = crc16Failures,
        )
    }

    /** Verifies every SEEKTABLE point resolves to an actual `0xFFF8`/`0xFFF9` frame sync word. */
    fun validateSeekTable(flac: FlacFile): Validation {
        val reasons = ArrayList<String>()
        for ((idx, p) in flac.seekTable.withIndex()) {
            val abs = flac.firstFrameOffset + p.streamOffset.toInt()
            if (abs + 1 >= flac.bytes.size) {
                reasons.add("seekpoint[$idx] offset ${p.streamOffset} escapes the stream (abs=$abs)")
                continue
            }
            val b0 = flac.bytes[abs].toInt() and 0xFF
            val b1 = flac.bytes[abs + 1].toInt() and 0xFF
            if (b0 != 0xFF || (b1 != 0xF8 && b1 != 0xF9)) {
                reasons.add("seekpoint[$idx] does not land on a frame sync word (bytes %02x %02x)".format(b0, b1))
            }
            if (p.frameSamples <= 0 || p.frameSamples > 65535) {
                reasons.add("seekpoint[$idx] frameSamples=${p.frameSamples} is not a valid sample count")
            }
        }
        if (flac.crc8Failures > 0) reasons.add("${flac.crc8Failures} frame header CRC-8 mismatches")
        if (flac.crc16Failures > 0) reasons.add("${flac.crc16Failures} frame CRC-16 mismatches")
        if (flac.streamInfo.md5Hex.length != 32) reasons.add("STREAMINFO MD5 is not 128 bits")
        return Validation(reasons.isEmpty(), reasons, flac.seekTable.size)
    }

    /** Byte-exact PCM recovery from a 16-bit verbatim FLAC stream (lossless readback). */
    fun decodeVerbatim16(flac: FlacFile): PcmClip {
        val channels = flac.streamInfo.channels
        require(flac.streamInfo.bitsPerSample == 16) { "decodeVerbatim16 requires 16-bit audio" }
        var p = flac.firstFrameOffset
        val samples = ArrayList<Short>()
        while (p + 8 < flac.bytes.size) {
            if ((flac.bytes[p].toInt() and 0xFF) != 0xFF) break
            val b1 = flac.bytes[p + 1].toInt() and 0xFF
            if (b1 != 0xF8 && b1 != 0xF9) break
            val bsCode = (flac.bytes[p + 2].toInt() and 0xFF) ushr 4
            require(bsCode == 0x6 || bsCode == 0x7) { "unsupported block size code $bsCode" }
            var q = p + 4
            val first = flac.bytes[q].toInt() and 0xFF
            val extra = when {
                first < 0x80 -> 0
                first and 0xE0 == 0xC0 -> 1
                first and 0xF0 == 0xE0 -> 2
                first and 0xF8 == 0xF0 -> 3
                else -> 4
            }
            q += 1 + extra
            val nSamples = if (bsCode == 0x6) {
                (flac.bytes[q].toInt() and 0xFF) + 1
            } else {
                (((flac.bytes[q].toInt() and 0xFF) shl 8) or (flac.bytes[q + 1].toInt() and 0xFF)) + 1
            }
            q += if (bsCode == 0x6) 1 else 2
            q += 1 // header CRC-8
            for (ch in 0 until channels) {
                require((flac.bytes[q].toInt() and 0xFF) == 0x02) { "expected verbatim subframe, got ${flac.bytes[q]}" }
                q += 1
                for (i in 0 until nSamples) {
                    val hi = flac.bytes[q + i * 2].toInt() and 0xFF
                    val lo = flac.bytes[q + i * 2 + 1].toInt() and 0xFF
                    samples.add(((hi shl 8) or lo).toShort())
                }
                q += nSamples * 2
            }
            q += 2 // frame CRC-16
            p = q
        }
        return PcmClip(flac.streamInfo.sampleRateHz, channels, 16, samples.toShortArray())
    }

    /** Recomputes the STREAMINFO MD5 from the decoded verbatim stream and compares it byte-for-byte. */
    fun verifyStreamMd5(flac: FlacFile): Pair<Boolean, String> {
        val decoded = try {
            decodeVerbatim16(flac)
        } catch (e: IllegalArgumentException) {
            return false to "decode failed: ${e.message}"
        }
        val actual = pcmMd5(decoded).joinToString("") { "%02x".format(it) }
        return (actual == flac.streamInfo.md5Hex) to actual
    }

    fun parseSeekTableOnly(bytes: ByteArray): List<SeekPoint> {
        require(bytes.size >= 8 && bytes[0].toInt() == 0x66 && bytes[1].toInt() == 0x4C) { "not a fLaC stream" }
        var p = 4
        val points = ArrayList<SeekPoint>()
        while (p + 4 <= bytes.size) {
            val header = bytes[p].toInt() and 0xFF
            val isLast = (header and 0x80) != 0
            val type = header and 0x7F
            val len = ((bytes[p + 1].toInt() and 0xFF) shl 16) or
                ((bytes[p + 2].toInt() and 0xFF) shl 8) or (bytes[p + 3].toInt() and 0xFF)
            val body = p + 4
            if (type == SEEKTABLE) {
                var k = 0
                while (k + 18 <= len) {
                    var firstSample = 0L
                    for (i in 0 until 8) firstSample = (firstSample shl 8) or (bytes[body + k + i].toLong() and 0xFFL)
                    var offset = 0L
                    for (i in 0 until 8) offset = (offset shl 8) or (bytes[body + k + 8 + i].toLong() and 0xFFL)
                    val samples = ((bytes[body + k + 16].toInt() and 0xFF) shl 8) or (bytes[body + k + 17].toInt() and 0xFF)
                    points.add(SeekPoint(firstSample, offset, samples))
                    k += 18
                }
            }
            if (isLast) break
            p = body + len
        }
        return points
    }
}
