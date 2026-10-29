// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Clean-room RIFF `WAVE` 16-bit PCM Voice Recorder Encoder/Decoder + Waveform RMS/Peak Envelope
 * (`MD-04`) and Audio Output/Input Route Priority Selector (`MD-06`).
 */
object VoiceRecorderWavWaveformCodec {
    const val SCOPE =
        "Pure-JVM RIFF WAVE 16-bit little-endian PCM voice recorder codec, RMS/peak waveform envelope builder (MD-04), and deterministic audio devices panel route selector (MD-06)."

    enum class AudioEndpointKind { USB_AUDIO_DAC, WIRED_HEADSET, BLUETOOTH_LE_AUDIO, BLUETOOTH_A2DP, BUILTIN_SPEAKER }

    data class AudioEndpoint(val id: String, val name: String, val kind: AudioEndpointKind, val connected: Boolean)
    data class WaveformBin(val peakNorm: Double, val rmsNorm: Double)

    fun encodePcm16MonoWav(sampleRateHz: Int, samples: ShortArray): ByteArray {
        require(sampleRateHz in 8_000..96_000 && samples.isNotEmpty()) { "invalid WAV parameters" }
        val dataBytes = samples.size * 2
        val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataBytes)
        buf.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16) // PCM fmt chunk size
        buf.putShort(1) // AudioFormat = 1 (PCM)
        buf.putShort(1) // NumChannels = 1 (mono)
        buf.putInt(sampleRateHz)
        buf.putInt(sampleRateHz * 2) // ByteRate
        buf.putShort(2) // BlockAlign
        buf.putShort(16) // BitsPerSample
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataBytes)
        for (s in samples) buf.putShort(s)
        val bos = ByteArrayOutputStream(44 + dataBytes)
        bos.write(buf.array())
        return bos.toByteArray()
    }

    fun decodePcm16MonoWav(wavBytes: ByteArray): Pair<Int, ShortArray> {
        require(wavBytes.size >= 44) { "truncated WAV header" }
        val buf = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        val riff = ByteArray(4); buf.get(riff)
        buf.int
        val wave = ByteArray(4); buf.get(wave)
        require(String(riff, Charsets.US_ASCII) == "RIFF" && String(wave, Charsets.US_ASCII) == "WAVE") {
            "invalid RIFF/WAVE magic"
        }
        buf.position(24)
        val sampleRate = buf.int
        buf.position(40)
        val dataLen = buf.int
        require(dataLen >= 2 && 44 + dataLen <= wavBytes.size && dataLen % 2 == 0) { "invalid WAV data chunk size" }
        val samples = ShortArray(dataLen / 2) { buf.short }
        return sampleRate to samples
    }

    fun computeWaveformEnvelope(samples: ShortArray, bucketCount: Int): List<WaveformBin> {
        require(samples.isNotEmpty() && bucketCount in 1..512) { "invalid waveform bucket request" }
        val step = maxOf(1, samples.size / bucketCount)
        return (0 until bucketCount).map { b ->
            val start = (b * step).coerceAtMost(samples.lastIndex)
            val end = ((b + 1) * step).coerceAtMost(samples.size)
            var peak = 0
            var sqSum = 0.0
            val cnt = (end - start).coerceAtLeast(1)
            for (i in start until end) {
                val a = abs(samples[i].toInt())
                if (a > peak) peak = a
                val norm = samples[i] / 32768.0
                sqSum += norm * norm
            }
            WaveformBin((peak / 32768.0).coerceIn(0.0, 1.0), sqrt(sqSum / cnt).coerceIn(0.0, 1.0))
        }
    }

    fun selectActiveOutputRoute(endpoints: List<AudioEndpoint>, userPinnedId: String? = null): AudioEndpoint {
        val connected = endpoints.filter { it.connected }
        require(connected.isNotEmpty()) { "no connected audio endpoints" }
        if (userPinnedId != null) {
            connected.firstOrNull { it.id == userPinnedId }?.let { return it }
        }
        return connected.minBy { it.kind.ordinal }
    }
}
