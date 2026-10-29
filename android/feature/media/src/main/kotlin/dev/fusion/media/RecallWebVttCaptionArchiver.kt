// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/**
 * Pure-JVM **W3C WebVTT (`WEBVTT`) Timed Caption & Chapter Cue Parser + Timestamp Synchronizer**
 * for **Recall**, archiving sidecar subtitles alongside recorded browser videos and feeding clean
 * plain-text cue transcripts into `RecallMediaTranscriptSearchIndex`.
 */
object RecallWebVttCaptionArchiver {
    const val SCOPE =
        "W3C WebVTT timed subtitle & chapter parser, millisecond cue normalizer, and HTML/voice-span stripper for Recall video archives."

    data class VttCue(
        val cueId: String?,
        val startMs: Long,
        val endMs: Long,
        val plainText: String,
    )

    fun parseWebVtt(vttContent: String): List<VttCue> {
        val normalized = vttContent.removePrefix("\uFEFF").replace("\r\n", "\n")
        require(normalized.trimStart().startsWith("WEBVTT")) { "Missing WEBVTT magic header" }
        val blocks = normalized.split(Regex("\n{2,}")).drop(1)
        val cues = ArrayList<VttCue>()
        for (blk in blocks) {
            val lines = blk.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty() || lines[0].startsWith("NOTE") || lines[0].startsWith("STYLE")) continue
            val arrowLineIdx = lines.indexOfFirst { "-->" in it }
            if (arrowLineIdx < 0) continue
            val cueId = if (arrowLineIdx > 0) lines[arrowLineIdx - 1] else null
            val timingLine = lines[arrowLineIdx]
            val parts = timingLine.split("-->")
            if (parts.size < 2) continue
            val startMs = parseTimestampMs(parts[0].trim())
            val endToken = parts[1].trim().substringBefore(' ')
            val endMs = parseTimestampMs(endToken)
            if (endMs <= startMs) continue
            val rawPayload = lines.subList(arrowLineIdx + 1, lines.size).joinToString(" ")
            val clean = rawPayload.replace(Regex("<[^>]+>"), "").trim()
            if (clean.isNotEmpty()) cues.add(VttCue(cueId, startMs, endMs, clean))
        }
        return cues
    }

    fun toPlainTranscript(cues: List<VttCue>): String =
        cues.joinToString(" ") { it.plainText }

    fun parseTimestampMs(token: String): Long {
        val m = Regex("""^(?:(\d+):)?(\d{2}):(\d{2})\.(\d{3})$""").matchEntire(token)
            ?: throw IllegalArgumentException("Malformed WebVTT timestamp: '$token'")
        val hrs = m.groupValues[1].toLongOrNull() ?: 0L
        val mins = m.groupValues[2].toLong()
        val secs = m.groupValues[3].toLong()
        val ms = m.groupValues[4].toLong()
        return (((hrs * 60L + mins) * 60L) + secs) * 1000L + ms
    }
}
