// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Clean-room canonical 44-byte RIFF/WAVE linear PCM codec & RMS dBFS meter (`WavPcmHeader`).
 *
 * Powers `:feature:recorder` zero-metadata offline voice memo export and waveform level metering!
 */
object WavPcmHeader {
    const val SCOPE =
        "Canonical 44-byte RIFF/WAVE PCM16/PCM24/PCM32 header encoder/decoder + PCM16 RMS/peak dBFS & zero-crossing analyzer; BWF/LIST INFO metadata chunks refused/stripped."

    const val HEADER_BYTES = 44
    const val WAVE_FORMAT_PCM = 1

    data class WavFormat(
        val sampleRateHz: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val dataBytes: Int,
    ) {
        init {
            require(sampleRateHz in 8_000..192_000) { "Unsupported sampleRateHz: $sampleRateHz" }
            require(channels in 1..8) { "Unsupported channel count: $channels" }
            require(bitsPerSample in setOf(8, 16, 24, 32)) { "Unsupported PCM bitsPerSample: $bitsPerSample" }
            require(dataBytes >= 0 && dataBytes % blockAlign == 0) { "dataBytes ($dataBytes) not aligned to frame ($blockAlign)" }
        }

        val blockAlign: Int get() = channels * (bitsPerSample / 8)
        val byteRate: Int get() = sampleRateHz * blockAlign
        val frameCount: Int get() = dataBytes / blockAlign
        val durationSeconds: Double get() = frameCount.toDouble() / sampleRateHz.toDouble()
    }

    data class LevelStats(
        val rmsDbfs: Double,
        val peakDbfs: Double,
        val zeroCrossingRate: Double,
        val clippedSamples: Int,
    )

    fun encodePcm16Mono(sampleRateHz: Int, samples: ShortArray): ByteArray {
        val dataBytes = samples.size * 2
        val header = encodeHeader(WavFormat(sampleRateHz, channels = 1, bitsPerSample = 16, dataBytes = dataBytes))
        val out = ByteArray(HEADER_BYTES + dataBytes)
        System.arraycopy(header, 0, out, 0, HEADER_BYTES)
        val bb = ByteBuffer.wrap(out, HEADER_BYTES, dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) bb.putShort(s)
        return out
    }

    fun encodeHeader(fmt: WavFormat): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(StandardCharsets.US_ASCII))
        buf.putInt(36 + fmt.dataBytes)
        buf.put("WAVE".toByteArray(StandardCharsets.US_ASCII))
        buf.put("fmt ".toByteArray(StandardCharsets.US_ASCII))
        buf.putInt(16) // PCM fmt chunk size
        buf.putShort(WAVE_FORMAT_PCM.toShort())
        buf.putShort(fmt.channels.toShort())
        buf.putInt(fmt.sampleRateHz)
        buf.putInt(fmt.byteRate)
        buf.putShort(fmt.blockAlign.toShort())
        buf.putShort(fmt.bitsPerSample.toShort())
        buf.put("data".toByteArray(StandardCharsets.US_ASCII))
        buf.putInt(fmt.dataBytes)
        return buf.array()
    }

    fun decodeHeader(wavBytes: ByteArray): WavFormat {
        require(wavBytes.size >= HEADER_BYTES) { "WAV shorter than 44-byte header (${wavBytes.size})" }
        val buf = ByteBuffer.wrap(wavBytes, 0, HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        val riff = ByteArray(4).also { buf.get(it) }.toString(StandardCharsets.US_ASCII)
        val riffSize = buf.int
        val wave = ByteArray(4).also { buf.get(it) }.toString(StandardCharsets.US_ASCII)
        val fmtTag = ByteArray(4).also { buf.get(it) }.toString(StandardCharsets.US_ASCII)
        val fmtLen = buf.int
        val audioFmt = buf.short.toInt() and 0xFFFF
        val channels = buf.short.toInt() and 0xFFFF
        val sampleRate = buf.int
        val byteRate = buf.int
        val blockAlign = buf.short.toInt() and 0xFFFF
        val bitsPerSample = buf.short.toInt() and 0xFFFF
        val dataTag = ByteArray(4).also { buf.get(it) }.toString(StandardCharsets.US_ASCII)
        val dataSize = buf.int

        require(riff == "RIFF" && wave == "WAVE" && fmtTag == "fmt " && dataTag == "data") {
            "Invalid canonical RIFF/WAVE header tags"
        }
        require(fmtLen == 16 && audioFmt == WAVE_FORMAT_PCM) { "Only canonical 16-byte PCM fmt chunk supported" }
        require(riffSize == 36 + dataSize) { "RIFF chunk size ($riffSize) != 36 + dataSize ($dataSize)" }
        val parsed = WavFormat(sampleRate, channels, bitsPerSample, dataSize)
        require(byteRate == parsed.byteRate && blockAlign == parsed.blockAlign) {
            "Inconsistent WAV byteRate/blockAlign header fields"
        }
        return parsed
    }

    fun analyzePcm16(samples: ShortArray): LevelStats {
        if (samples.isEmpty()) return LevelStats(-120.0, -120.0, 0.0, 0)
        var sumSq = 0.0
        var peak = 0.0
        var clipped = 0
        var crossings = 0
        for (i in samples.indices) {
            val s = samples[i].toInt()
            if (s == Short.MAX_VALUE.toInt() || s == Short.MIN_VALUE.toInt()) clipped++
            val norm = abs(s) / 32768.0
            sumSq += norm * norm
            if (norm > peak) peak = norm
            if (i > 0 && ((samples[i - 1] >= 0 && samples[i] < 0) || (samples[i - 1] < 0 && samples[i] >= 0))) {
                crossings++
            }
        }
        val rms = sqrt(sumSq / samples.size.toDouble())
        val rmsDb = if (rms <= 1e-6) -120.0 else max(-120.0, 20.0 * log10(rms))
        val peakDb = if (peak <= 1e-6) -120.0 else max(-120.0, 20.0 * log10(peak))
        val zcr = if (samples.size <= 1) 0.0 else crossings.toDouble() / (samples.size - 1).toDouble()
        return LevelStats(rmsDb, peakDb, zcr, clipped)
    }
}
