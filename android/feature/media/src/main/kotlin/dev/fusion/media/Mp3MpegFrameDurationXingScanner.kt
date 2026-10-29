// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/**
 * ISO/IEC 11172-3 (MPEG-1) & ISO/IEC 13818-3 (MPEG-2) Audio Layer III (`.mp3`) frame-header and
 * `Xing` / `Info` VBR header scanner computing exact sample duration without native codecs.
 */
object Mp3MpegFrameDurationXingScanner {
    const val SCOPE =
        "MPEG-1/2 Audio Layer III 32-bit syncword frame scanner and Xing/Info VBR frame-count inspector for Recall music archives."

    private val BITRATES_V1_L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
    private val BITRATES_V2_L3 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)
    private val SAMPLE_RATES_V1 = intArrayOf(44100, 48000, 32000, 0)
    private val SAMPLE_RATES_V2 = intArrayOf(22050, 24000, 16000, 0)

    data class Mp3Inspection(
        val mpegVersionLabel: String,
        val sampleRateHz: Int,
        val channels: Int,
        val frameCount: Int,
        val totalSamples: Long,
        val durationMillis: Long,
        val isXingVbr: Boolean,
    )

    fun inspectMp3(bytes: ByteArray): Mp3Inspection {
        require(bytes.size >= 4) { "Truncated MP3 buffer" }
        var pos = skipId3v2IfPresent(bytes)
        var frames = 0
        var firstRate = 0
        var firstCh = 2
        var firstVer = "MPEG-1"
        var samplesPerFrame = 1152
        var xingFrames: Int? = null

        while (pos + 4 <= bytes.size) {
            if ((bytes[pos].toInt() and 0xFF) != 0xFF || (bytes[pos + 1].toInt() and 0xE0) != 0xE0) {
                pos++
                continue
            }
            val b1 = bytes[pos + 1].toInt() and 0xFF
            val b2 = bytes[pos + 2].toInt() and 0xFF
            val b3 = bytes[pos + 3].toInt() and 0xFF
            val verBits = (b1 ushr 3) and 0x03
            val layerBits = (b1 ushr 1) and 0x03
            val brIdx = (b2 ushr 4) and 0x0F
            val srIdx = (b2 ushr 2) and 0x03
            val pad = (b2 ushr 1) and 0x01
            val chMode = (b3 ushr 6) and 0x03
            if (layerBits != 1 || brIdx == 0 || brIdx == 15 || srIdx == 3 || verBits == 1) {
                pos++; continue
            }
            val isV1 = (verBits == 3)
            val kbps = if (isV1) BITRATES_V1_L3[brIdx] else BITRATES_V2_L3[brIdx]
            val sr = if (isV1) SAMPLE_RATES_V1[srIdx] else SAMPLE_RATES_V2[srIdx]
            val spf = if (isV1) 1152 else 576
            val frameLen = if (isV1) (144 * kbps * 1000) / sr + pad else (72 * kbps * 1000) / sr + pad
            if (frameLen < 24 || pos + frameLen > bytes.size) break

            if (frames == 0) {
                firstRate = sr
                firstCh = if (chMode == 3) 1 else 2
                firstVer = if (isV1) "MPEG-1" else "MPEG-2"
                samplesPerFrame = spf
                val sideInfoLen = if (isV1) (if (firstCh == 1) 17 else 32) else (if (firstCh == 1) 9 else 17)
                val xingOff = pos + 4 + sideInfoLen
                if (xingOff + 12 <= pos + frameLen) {
                    val tag = String(bytes, xingOff, 4, Charsets.US_ASCII)
                    if (tag == "Xing" || tag == "Info") {
                        val flags = readU32Be(bytes, xingOff + 4)
                        if ((flags and 0x01) != 0) xingFrames = readU32Be(bytes, xingOff + 8)
                    }
                }
            }
            frames++
            pos += frameLen
        }
        require(frames > 0 && firstRate > 0) { "No valid MPEG Layer III audio frames found" }
        val effectiveFrames = xingFrames ?: frames
        val totalSamples = effectiveFrames.toLong() * samplesPerFrame
        return Mp3Inspection(
            mpegVersionLabel = firstVer,
            sampleRateHz = firstRate,
            channels = firstCh,
            frameCount = effectiveFrames,
            totalSamples = totalSamples,
            durationMillis = (totalSamples * 1000L) / firstRate,
            isXingVbr = xingFrames != null,
        )
    }

    private fun skipId3v2IfPresent(b: ByteArray): Int {
        if (b.size >= 10 && b[0] == 'I'.code.toByte() && b[1] == 'D'.code.toByte() && b[2] == '3'.code.toByte()) {
            val synch = ((b[6].toInt() and 0x7F) shl 21) or
                ((b[7].toInt() and 0x7F) shl 14) or
                ((b[8].toInt() and 0x7F) shl 7) or
                (b[9].toInt() and 0x7F)
            return (10 + synch).coerceAtMost(b.size)
        }
        return 0
    }

    private fun readU32Be(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
}
