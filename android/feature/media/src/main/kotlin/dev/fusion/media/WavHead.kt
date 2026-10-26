// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/**
 * WAV/RIFF header reader with cross-field consistency checks.
 *
 * Sits next to [Id3Head] (tag headers) and [SubRip] (cues): nothing reads
 * the RIFF chunk descriptor plus `fmt ` subchunk that open every WAV —
 * audio format, channel count, sample rate, byte rate, block alignment,
 * and bits per sample. Beyond shape validation, the two redundant
 * relations are ENFORCED (byteRate == sampleRate·blockAlign and
 * blockAlign == channels·bitsPerSample/8): a header whose redundant
 * fields disagree is corrupt, and guessing which field to trust would
 * misreport the audio. PCM (1), IEEE float (3), A-law (6), and µ-law (7)
 * are accepted; other format tags are refused, not mislabeled. Reading
 * stops after `fmt ` — the `data` chunk and its samples are outside.
 *
 * Plain Kotlin (ByteArray in, data out), JVM unit-testable.
 */
object WavHead {

    /** Parsed WAV header: format, geometry, and container sizes. */
    data class Header(
        val format: Int,
        val channels: Int,
        val sampleRate: Int,
        val byteRate: Int,
        val blockAlign: Int,
        val bitsPerSample: Int,
        val riffSize: Int,
    )

    /**
     * Parses the WAV header at the start of [bytes] (40 bytes minimum).
     *
     * @throws IllegalArgumentException on short input, bad magics, an
     * unsupported format, out-of-range geometry, or inconsistent
     * redundant fields.
     */
    fun parse(bytes: ByteArray): Header {
        require(bytes.size >= 40) { "short header" }
        require(bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()) { "bad RIFF" }
        val riffSize = u32(bytes, 4)
        require(bytes[8] == 'W'.code.toByte() && bytes[9] == 'A'.code.toByte() &&
            bytes[10] == 'V'.code.toByte() && bytes[11] == 'E'.code.toByte()) { "bad WAVE" }
        require(bytes[12] == 'f'.code.toByte() && bytes[13] == 'm'.code.toByte() &&
            bytes[14] == 't'.code.toByte() && bytes[15] == ' '.code.toByte()) { "bad fmt" }
        require(u32(bytes, 16) >= 16) { "short fmt" }
        val format = u16(bytes, 20)
        require(format == 1 || format == 3 || format == 6 || format == 7) { "bad format $format" }
        val channels = u16(bytes, 22)
        require(channels in 1..8) { "bad channels" }
        val rate = u32(bytes, 24)
        require(rate in 1..384000) { "bad rate" }
        val byteRate = u32(bytes, 28)
        val align = u16(bytes, 32)
        val bits = u16(bytes, 34)
        require(bits == 8 || bits == 16 || bits == 24 || bits == 32) { "bad bits" }
        require(align == channels * bits / 8) { "align mismatch" }
        require(byteRate == rate * align) { "rate mismatch" }
        return Header(format, channels, rate, byteRate, align, bits, riffSize)
    }

    private fun u16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)
}
