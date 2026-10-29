// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/**
 * Clean-room W3C **WebVTT (`WEBVTT`) Subtitle/Caption Parser, Validator & Active-Cue Timeline**.
 *
 * Parses `[HH:]MM:SS.mmm --> [HH:]MM:SS.mmm` cue timings, optional cue IDs, cue positioning
 * settings (`align:center`, `line:85%`), strips unsafe `<script>` markup from cue payloads, and
 * queries active cues at any playback timestamp `positionMs`.
 */
object WebVttCueTimeline {
    const val SCOPE =
        "Clean-room W3C WebVTT caption parser, timestamp validator, script-safe text cleaner, and active-cue interval lookup."

    data class Cue(
        val id: String?,
        val startMs: Long,
        val endMs: Long,
        val settings: Map<String, String>,
        val text: String,
    ) {
        init {
            require(startMs >= 0L && endMs > startMs) { "invalid WebVTT cue interval [$startMs, $endMs)" }
        }
    }

    fun parse(vtt: String): List<Cue> {
        val norm = vtt.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val blocks = norm.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
        require(blocks.isNotEmpty() && (blocks[0] == "WEBVTT" || blocks[0].startsWith("WEBVTT ") || blocks[0].startsWith("WEBVTT\n"))) {
            "missing mandatory WEBVTT header signature"
        }
        val cues = ArrayList<Cue>()
        for (block in blocks.drop(1)) {
            if (block.startsWith("NOTE") || block.startsWith("STYLE") || block.startsWith("REGION")) continue
            val lines = block.lines()
            val arrowLineIdx = lines.indexOfFirst { "-->" in it }
            if (arrowLineIdx < 0) continue
            val cueId = if (arrowLineIdx == 1) lines[0].trim() else null
            val timingLine = lines[arrowLineIdx]
            val arrowParts = timingLine.split("-->")
            require(arrowParts.size == 2) { "malformed WebVTT timing line: '$timingLine'" }
            val startMs = parseTimestampMs(arrowParts[0].trim())
            val rhsTokens = arrowParts[1].trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val endMs = parseTimestampMs(rhsTokens[0])
            val settings = LinkedHashMap<String, String>()
            for (tok in rhsTokens.drop(1)) {
                val colon = tok.indexOf(':')
                if (colon > 0) settings[tok.substring(0, colon)] = tok.substring(colon + 1)
            }
            val rawPayload = lines.drop(arrowLineIdx + 1).joinToString("\n")
            require(!Regex("<\\s*script\\b", RegexOption.IGNORE_CASE).containsMatchIn(rawPayload)) {
                "script tag forbidden in WebVTT cue"
            }
            val cleanText = rawPayload.replace(Regex("</?[biuvc][^>]*>"), "").trim()
            cues.add(Cue(cueId, startMs, endMs, settings, cleanText))
        }
        return cues
    }

    fun activeCuesAt(cues: List<Cue>, positionMs: Long): List<Cue> =
        cues.filter { positionMs in it.startMs until it.endMs }

    fun parseTimestampMs(raw: String): Long {
        val m = Regex("^(?:(\\d{2,}):)?([0-5]\\d):([0-5]\\d)\\.(\\d{3})$").matchEntire(raw)
            ?: throw IllegalArgumentException("invalid WebVTT timestamp '$raw'")
        val hh = m.groupValues[1].ifEmpty { "0" }.toLong()
        val mm = m.groupValues[2].toLong()
        val ss = m.groupValues[3].toLong()
        val ms = m.groupValues[4].toLong()
        return (((hh * 60L + mm) * 60L) + ss) * 1000L + ms
    }
}
