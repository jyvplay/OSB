// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * **Recall Method 3 (Medium-Complex `<canvas>` / MSE / WebAudio Siphoner):**
 * Records procedural or custom-player browser media (`blob:` MediaSource streams, HTML5 `<canvas>`
 * animations, WebAudio `AudioWorklet` synthesizers/streams) into:
 * 1. Bit-exact `RIFF/WAVE` 16-bit PCM `.wav` with deterministic triangular-PDF (`TPDF`) dither & `[-1, +1]` saturation;
 * 2. IETF RFC 7845 `OggS` `.opus` stream (`OpusHead` + `OpusTags` + Ogg CRC-32 `0x04C11DB7` audio pages);
 * 3. Lossless RFC 2083 `PNG` (`\x89PNG` `IHDR` + zlib `IDAT` + `IEND`) RGBA8 `<canvas>`/video frames.
 */
object RecallTier3CanvasWebAudioSiphoner {
    const val SCOPE =
        "Recall Method 3 WebAudio Float32 PCM -> TPDF-dithered 16-bit RIFF/WAVE .wav, RFC 7845 OggS Opus container muxer with Ogg CRC-32 0x04C11DB7, and lossless RGBA8 <canvas> keyframe PNG encoder."

    fun siphonWebAudioFloatToWav(
        interleavedSamples: FloatArray,
        sampleRateHz: Int = 48_000,
        channels: Int = 2,
        ditherSeed: Long = 0x524543414C4CL,
    ): ByteArray {
        require(sampleRateHz in 8_000..192_000 && channels in 1..8) { "Invalid sampleRate/channels" }
        require(interleavedSamples.isNotEmpty() && interleavedSamples.size % channels == 0) {
            "Interleaved float sample count (${interleavedSamples.size}) must be a positive multiple of channels ($channels)"
        }
        val pcmBytes = ByteArray(interleavedSamples.size * 2)
        var state = ditherSeed
        fun nextUniformMinusHalfToHalf(): Double {
            // SplitMix64 deterministic PRNG for clean TPDF dither without external state
            state += -0x61c8864680b583ebL
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            z = z xor (z ushr 31)
            return ((z ushr 11).toDouble() / (1L shl 53).toDouble()) - 0.5
        }

        val pcmBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (raw in interleavedSamples) {
            val finite = if (raw.isFinite()) raw.toDouble().coerceIn(-1.0, 1.0) else 0.0
            val tpdf = nextUniformMinusHalfToHalf() + nextUniformMinusHalfToHalf() // [-1, +1] LSB TPDF
            val scaled = (finite * 32767.0 + tpdf * 0.5).coerceIn(-32768.0, 32767.0)
            pcmBuf.putShort(kotlin.math.round(scaled).toInt().toShort())
        }

        val byteRate = sampleRateHz * channels * 2
        val blockAlign = (channels * 2).toShort()
        val riffPayloadSize = 36 + pcmBytes.size
        return ByteBuffer.allocate(44 + pcmBytes.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(riffPayloadSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)             // PCM fmt chunk size
            putShort(1)            // WAVE_FORMAT_PCM = 1
            putShort(channels.toShort())
            putInt(sampleRateHz)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(16)           // bitsPerSample = 16
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmBytes.size)
            put(pcmBytes)
        }.array()
    }

    fun muxOggOpusStream(
        opusPackets: List<ByteArray>,
        channels: Int = 2,
        inputSampleRateHz: Int = 48_000,
        samplesPerPacketAt48k: Int = 960, // 20 ms @ 48 kHz
        serialNumber: Int = 0x52434C33,
    ): ByteArray {
        require(opusPackets.isNotEmpty() && channels in 1..2) { "Invalid Opus packet list or channel count" }
        val out = ByteArrayOutputStream()
        var seqNo = 0

        // Page 0 (BOS = 0x02): RFC 7845 §5.1 OpusHead (19 bytes)
        val opusHead = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("OpusHead".toByteArray(Charsets.US_ASCII))
            put(1.toByte()) // version = 1
            put(channels.toByte())
            putShort(312) // pre-skip (6.5 ms @ 48kHz)
            putInt(inputSampleRateHz)
            putShort(0) // output gain 0 dB
            put(0.toByte()) // channel mapping family 0
        }.array()
        out.write(buildOggPage(headerType = 0x02, granulePos = 0L, serial = serialNumber, pageSeq = seqNo++, packet = opusHead))

        // Page 1: RFC 7845 §5.2 OpusTags (zero identifying vendor/user PII leak)
        val vendor = "FusionRecall".toByteArray(Charsets.UTF_8)
        val opusTags = ByteBuffer.allocate(8 + 4 + vendor.size + 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("OpusTags".toByteArray(Charsets.US_ASCII))
            putInt(vendor.size)
            put(vendor)
            putInt(0) // 0 user comments
        }.array()
        out.write(buildOggPage(headerType = 0x00, granulePos = 0L, serial = serialNumber, pageSeq = seqNo++, packet = opusTags))

        var granule = 0L
        opusPackets.forEachIndexed { idx, pkt ->
            require(pkt.isNotEmpty() && pkt.size <= 65025) { "Opus packet size ${pkt.size} out of bounds" }
            granule += samplesPerPacketAt48k.toLong()
            val flags = if (idx == opusPackets.lastIndex) 0x04 else 0x00 // EOS = 0x04 on final page
            out.write(buildOggPage(headerType = flags, granulePos = granule, serial = serialNumber, pageSeq = seqNo++, packet = pkt))
        }
        return out.toByteArray()
    }

    fun encodeCanvasRgbaToPng(width: Int, height: Int, rgbaPixels: ByteArray): ByteArray {
        require(width in 1..4096 && height in 1..4096 && rgbaPixels.size == width * height * 4) {
            "Invalid RGBA8 canvas buffer (${width}x${height}, bytes=${rgbaPixels.size})"
        }
        val rawScanlines = ByteArray(height * (1 + width * 4))
        for (y in 0 until height) {
            val dstRow = y * (1 + width * 4)
            rawScanlines[dstRow] = 0 // filter type 0 (None)
            System.arraycopy(rgbaPixels, y * width * 4, rawScanlines, dstRow + 1, width * 4)
        }
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        deflater.setInput(rawScanlines)
        deflater.finish()
        val idatOut = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            if (n > 0) idatOut.write(buf, 0, n)
        }
        deflater.end()

        val ihdr = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(width); putInt(height)
            put(8); put(6); put(0); put(0); put(0) // 8-bit RGBA
        }.array()

        val png = ByteArrayOutputStream()
        png.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        png.write(pngChunk("IHDR", ihdr))
        png.write(pngChunk("IDAT", idatOut.toByteArray()))
        png.write(pngChunk("IEND", ByteArray(0)))
        return png.toByteArray()
    }

    private fun pngChunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        return ByteBuffer.allocate(12 + data.size).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(data.size)
            put(typeBytes)
            put(data)
            putInt(crc.value.toInt())
        }.array()
    }

    private fun buildOggPage(headerType: Int, granulePos: Long, serial: Int, pageSeq: Int, packet: ByteArray): ByteArray {
        val segValues = ArrayList<Byte>()
        var rem = packet.size
        while (rem >= 255) {
            segValues.add(255.toByte())
            rem -= 255
        }
        segValues.add(rem.toByte())
        val headerSize = 27 + segValues.size
        val page = ByteArray(headerSize + packet.size)
        val bb = ByteBuffer.wrap(page).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("OggS".toByteArray(Charsets.US_ASCII))
        bb.put(0.toByte()) // stream_structure_version
        bb.put(headerType.toByte())
        bb.putLong(granulePos)
        bb.putInt(serial)
        bb.putInt(pageSeq)
        bb.putInt(0) // checksum placeholder at offset 22..25
        bb.put(segValues.size.toByte())
        for (sv in segValues) bb.put(sv)
        bb.put(packet)

        val crc = oggCrc32(page)
        ByteBuffer.wrap(page, 22, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(crc)
        return page
    }

    fun oggCrc32(data: ByteArray): Int {
        var crc = 0
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xFF) shl 24)
            repeat(8) {
                crc = if ((crc and -0x80000000) != 0) (crc shl 1) xor 0x04C11DB7 else (crc shl 1)
            }
        }
        return crc
    }
}
