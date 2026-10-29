// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/**
 * Clean-room W3C WebVTT (`WEBVTT`) subtitle/caption parser & timestamp lookup engine (`WebVttCueParser`).
 *
 * Powers `:feature:media` accessible offline captions without external player SDKs:
 * - Validates `WEBVTT` magic header (allowing UTF-8 BOM `\uFEFF`), skips `NOTE` and `STYLE` blocks,
 *   parses both `MM:SS.mmm` and `HH:MM:SS.mmm` cue timings + cue settings (`align:center line:85%`),
 *   strips inline `<v Speaker>` / `<b>` / `<c>` formatting tags while decoding HTML entities, and
 *   answers `activeCuesAt(ms)`!
 */
object WebVttCueParser {
    const val SCOPE =
        "W3C WebVTT caption parser (MM:SS.mmm & HH:MM:SS.mmm timestamps, NOTE/STYLE skipping, cue settings, voice/tag stripping, activeCuesAt query); REGION scroll boxes out of scope."

    data class Cue(
        val id: String?,
        val startMs: Long,
        val endMs: Long,
        val text: String,
        val settings: Map<String, String> = emptyMap(),
    ) {
        init {
            require(startMs >= 0L && endMs > startMs) { "Invalid WebVTT cue interval: $startMs --> $endMs" }
        }
    }

    class Track(val cues: List<Cue>) {
        fun activeCuesAt(positionMs: Long): List<Cue> =
            cues.filter { positionMs >= it.startMs && positionMs < it.endMs }
    }

    private val TIME_RE = Regex("^(?:(\\d{2,}):)?([0-5]\\d):([0-5]\\d)\\.(\\d{3})$")

    fun parse(vttText: String): Track {
        val normalized = vttText.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val blocks = normalized.split(Regex("\n{2,}"))
        require(blocks.isNotEmpty() && blocks.first().trimStart().startsWith("WEBVTT")) {
            "Missing W3C WEBVTT header"
        }

        val cues = mutableListOf<Cue>()
        for (blk in blocks.drop(1)) {
            val lines = blk.lines().map { it.trimEnd() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            val first = lines.first()
            if (first.startsWith("NOTE") || first.startsWith("STYLE") || first.startsWith("REGION")) continue

            val arrowLineIdx = lines.indexOfFirst { it.contains("-->") }
            if (arrowLineIdx < 0) continue

            val cueId = if (arrowLineIdx > 0) lines[0].trim() else null
            val timingLine = lines[arrowLineIdx]
            val arrowPos = timingLine.indexOf("-->")
            val startRaw = timingLine.substring(0, arrowPos).trim()
            val rhsTokens = timingLine.substring(arrowPos + 3).trim().split(Regex("\\s+"))
            val endRaw = rhsTokens.first()
            val settings = linkedMapOf<String, String>()
            for (kv in rhsTokens.drop(1)) {
                val c = kv.indexOf(':')
                if (c > 0 && c < kv.lastIndex) {
                    settings[kv.substring(0, c)] = kv.substring(c + 1)
                }
            }
            val startMs = parseTimestampMs(startRaw)
            val endMs = parseTimestampMs(endRaw)
            val payload = lines.drop(arrowLineIdx + 1).joinToString("\n") { stripVttTags(it) }
            cues.add(Cue(cueId, startMs, endMs, payload, settings))
        }
        return Track(cues.sortedBy { it.startMs })
    }

    fun parseTimestampMs(raw: String): Long {
        val m = TIME_RE.matchEntire(raw.trim())
            ?: throw IllegalArgumentException("Invalid WebVTT timestamp: '$raw'")
        val hours = if (m.groupValues[1].isEmpty()) 0L else m.groupValues[1].toLong()
        val mins = m.groupValues[2].toLong()
        val secs = m.groupValues[3].toLong()
        val millis = m.groupValues[4].toLong()
        return ((hours * 3600L + mins * 60L + secs) * 1000L) + millis
    }

    private fun stripVttTags(line: String): String =
        line.replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
}
