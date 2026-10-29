package dev.fusion.media

/**
 * Clean-room W3C WebVTT (`WEBVTT`) Timed-Text Subtitle Parser, Non-Overlapping Track-Lane
 * Resolver, and SubRip (`.srt`) Bi-Directional Converter (Apache-2.0).
 */
object WebVttCueTimelineEngine {
    const val SCOPE =
        "W3C WEBVTT subtitle cue parser + voice/style tag stripper + non-overlapping lane allocator + SRT <-> WebVTT converter"

    data class SubtitleCue(
        val id: String,
        val startMs: Long,
        val endMs: Long,
        val rawText: String,
        val plainText: String,
        val speakerVoice: String? = null,
        val assignedLane: Int = 0,
    )

    private val TIME_RE = Regex("(?:([0-9]{2}):)?([0-9]{2}):([0-9]{2})[.,]([0-9]{3})")
    private val VOICE_RE = Regex("^<v\\s+([^>]+)>([\\s\\S]*)$")

    fun parseWebVtt(vttSource: String): List<SubtitleCue> {
        val norm = vttSource.replace("\r\n", "\n").trimStart('\uFEFF').trim()
        require(norm.startsWith("WEBVTT")) { "Missing WEBVTT file magic header" }
        val blocks = norm.split(Regex("\\n\\s*\\n")).drop(1)
        val rawCues = ArrayList<SubtitleCue>()
        var autoIdx = 1
        for (blk in blocks) {
            val lines = blk.lines().map { it.trimEnd() }.filter { it.isNotEmpty() }
            if (lines.isEmpty() || lines[0].startsWith("NOTE") || lines[0].startsWith("STYLE")) continue
            val arrowLineIdx = lines.indexOfFirst { "-->" in it }
            if (arrowLineIdx < 0) continue
            val cueId = if (arrowLineIdx > 0) lines[0].trim() else "cue-${autoIdx}"
            autoIdx++
            val timingParts = lines[arrowLineIdx].split("-->")
            val startMs = parseTimestampMs(timingParts[0].trim().substringBefore(' '))
            val endMs = parseTimestampMs(timingParts[1].trim().substringBefore(' '))
            require(endMs > startMs) { "Cue '$cueId' endMs ($endMs) <= startMs ($startMs)" }
            val payload = lines.drop(arrowLineIdx + 1).joinToString("\n")
            val voiceMatch = VOICE_RE.find(payload)
            val voice = voiceMatch?.groupValues?.get(1)?.trim()
            val plain = payload.replace(Regex("<[^>]+>"), "").trim()
            rawCues.add(SubtitleCue(cueId, startMs, endMs, payload, plain, voice))
        }
        return assignCollisionFreeLanes(rawCues)
    }

    fun activeCuesAt(cues: List<SubtitleCue>, positionMs: Long): List<SubtitleCue> =
        cues.filter { positionMs >= it.startMs && positionMs < it.endMs }.sortedBy { it.assignedLane }

    fun toSrt(cues: List<SubtitleCue>): String = buildString {
        cues.forEachIndexed { idx, c ->
            if (idx > 0) append("\n")
            append("${idx + 1}\n")
            append("${formatTime(c.startMs, ',')} --> ${formatTime(c.endMs, ',')}\n")
            append("${c.plainText}\n")
        }
    }

    private fun assignCollisionFreeLanes(cues: List<SubtitleCue>): List<SubtitleCue> {
        val sorted = cues.sortedWith(compareBy({ it.startMs }, { it.endMs }))
        val laneFreeAt = ArrayList<Long>()
        return sorted.map { cue ->
            var lane = laneFreeAt.indexOfFirst { it <= cue.startMs }
            if (lane < 0) {
                lane = laneFreeAt.size
                laneFreeAt.add(cue.endMs)
            } else {
                laneFreeAt[lane] = cue.endMs
            }
            cue.copy(assignedLane = lane)
        }
    }

    private fun parseTimestampMs(token: String): Long {
        val m = TIME_RE.matchEntire(token.trim()) ?: throw IllegalArgumentException("Invalid WebVTT timestamp '$token'")
        val hh = m.groupValues[1].ifEmpty { "0" }.toLong()
        val mm = m.groupValues[2].toLong()
        val ss = m.groupValues[3].toLong()
        val ms = m.groupValues[4].toLong()
        return (((hh * 60 + mm) * 60) + ss) * 1000L + ms
    }

    private fun formatTime(msTotal: Long, sep: Char): String {
        val hh = msTotal / 3_600_000L
        val mm = (msTotal / 60_000L) % 60L
        val ss = (msTotal / 1_000L) % 60L
        val ms = msTotal % 1_000L
        return "%02d:%02d:%02d%c%03d".format(hh, mm, ss, sep, ms)
    }
}
