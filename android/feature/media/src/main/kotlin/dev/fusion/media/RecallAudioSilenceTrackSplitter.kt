// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Automatic silence-boundary track splitter + zero-crossing cue cutter + `.cue` sidecar builder
 * for **Recall** continuous music/radio/concert recordings.
 *
 * Scans mono/interleaved PCM16 audio in short analysis windows, identifies sustained silence
 * valleys below `silenceThresholdDbFs`, snaps cut points to the nearest zero-crossing sample to
 * eliminate audible DC/click transients, and emits frame-accurate `.cue` index sheets.
 */
object RecallAudioSilenceTrackSplitter {
    const val SCOPE =
        "Recall continuous stream RMS dBFS silence detector, zero-crossing click-free PCM16 track splitter, and CDRWIN .cue sheet sidecar writer."

    data class TrackSegment(
        val trackNumber: Int,
        val startSample: Int,
        val endSampleExclusive: Int,
        val startMillis: Long,
        val durationMillis: Long,
        val peakAbsSample: Int,
    )

    fun detectAndSplitTracks(
        monoPcm16: ShortArray,
        sampleRateHz: Int = 48_000,
        silenceThresholdDbFs: Double = -42.0,
        minSilenceMillis: Int = 350,
        minTrackMillis: Int = 500,
    ): List<TrackSegment> {
        require(sampleRateHz in 8_000..192_000 && monoPcm16.isNotEmpty())
        val winSamples = (sampleRateHz / 50).coerceAtLeast(64) // 20 ms window
        val minSilenceWins = ((minSilenceMillis * sampleRateHz) / (1000 * winSamples)).coerceAtLeast(1)
        val minTrackSamples = ((minTrackMillis.toLong() * sampleRateHz) / 1000L).toInt().coerceAtLeast(1)

        val cutCandidates = ArrayList<Int>()
        var silentRun = 0
        var runStartWin = 0
        val numWins = monoPcm16.size / winSamples

        for (w in 0 until numWins) {
            var sumSq = 0.0
            val base = w * winSamples
            for (i in 0 until winSamples) {
                val n = monoPcm16[base + i].toDouble() / 32768.0
                sumSq += n * n
            }
            val rms = sqrt(sumSq / winSamples.toDouble())
            val db = if (rms <= 1e-9) -120.0 else 20.0 * log10(rms)
            if (db <= silenceThresholdDbFs) {
                if (silentRun == 0) runStartWin = w
                silentRun++
            } else {
                if (silentRun >= minSilenceWins) {
                    val midSample = ((runStartWin + silentRun / 2) * winSamples).coerceIn(1, monoPcm16.lastIndex)
                    cutCandidates.add(snapToZeroCrossing(monoPcm16, midSample, radius = winSamples / 2))
                }
                silentRun = 0
            }
        }

        val boundaries = ArrayList<Int>()
        boundaries.add(0)
        for (c in cutCandidates) {
            if (c - boundaries.last() >= minTrackSamples && monoPcm16.size - c >= minTrackSamples) {
                boundaries.add(c)
            }
        }
        boundaries.add(monoPcm16.size)

        return (0 until boundaries.lastIndex).map { idx ->
            val s = boundaries[idx]
            val e = boundaries[idx + 1]
            var peak = 0
            for (k in s until e) {
                val a = abs(monoPcm16[k].toInt())
                if (a > peak) peak = a
            }
            TrackSegment(
                trackNumber = idx + 1,
                startSample = s,
                endSampleExclusive = e,
                startMillis = (s.toLong() * 1000L) / sampleRateHz,
                durationMillis = ((e - s).toLong() * 1000L) / sampleRateHz,
                peakAbsSample = peak,
            )
        }
    }

    fun formatCueSheet(albumTitle: String, audioFilename: String, tracks: List<TrackSegment>): String {
        require(tracks.isNotEmpty())
        val sb = StringBuilder()
        sb.append("TITLE \"").append(albumTitle.replace("\"", "'")).append("\"\n")
        sb.append("FILE \"").append(audioFilename.replace("\"", "'")).append("\" WAVE\n")
        for (t in tracks) {
            val totalFrames75 = (t.startMillis * 75L) / 1000L
            val ff = (totalFrames75 % 75L).toInt()
            val totalSec = totalFrames75 / 75L
            val ss = (totalSec % 60L).toInt()
            val mm = (totalSec / 60L).toInt()
            sb.append("  TRACK %02d AUDIO\n".format(t.trackNumber))
            sb.append("    TITLE \"Track %02d\"\n".format(t.trackNumber))
            sb.append("    INDEX 01 %02d:%02d:%02d\n".format(mm, ss, ff))
        }
        return sb.toString()
    }

    private fun snapToZeroCrossing(pcm: ShortArray, center: Int, radius: Int): Int {
        val lo = (center - radius).coerceAtLeast(1)
        val hi = (center + radius).coerceAtMost(pcm.lastIndex)
        var bestIdx = center
        var bestMag = abs(pcm[center].toInt())
        for (i in lo..hi) {
            val prev = pcm[i - 1].toInt()
            val cur = pcm[i].toInt()
            if ((prev <= 0 && cur >= 0) || (prev >= 0 && cur <= 0)) return i
            val mag = abs(cur)
            if (mag < bestMag) { bestMag = mag; bestIdx = i }
        }
        return bestIdx
    }
}
