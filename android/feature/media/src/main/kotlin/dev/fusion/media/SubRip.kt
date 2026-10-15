package dev.fusion.media

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Strict bounded SubRip subset. Literal text only; no HTML, positioning, playback or network. */
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)
class SubRip private constructor(private val cues: List<SubtitleCue>) {
    fun cues(): List<SubtitleCue> = cues.toList()
    /** Half-open intervals [start,end); simultaneous cues retain source order. */
    fun at(positionMs: Long): List<SubtitleCue> {
        require(positionMs >= 0)
        return cues.filter { positionMs >= it.startMs && positionMs < it.endMs }
    }
    /** Canonical LF, sequential indices; preserves text, not original bytes/newline style. */
    fun encode(): ByteArray = cues.mapIndexed { i, c -> "${i + 1}\n${stamp(c.startMs)} --> ${stamp(c.endMs)}\n${c.text}\n" }
        .joinToString("\n").toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }
    companion object {
        const val MAX_BYTES = 1024 * 1024
        const val MAX_CUES = 4096
        private val timing = Regex("(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3}) --> (\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})")
        private fun stamp(ms: Long): String {
            fun pad(n: Long, width: Int) = n.toString().padStart(width, '0')
            return "${pad(ms / 3600000, 2)}:${pad(ms / 60000 % 60, 2)}:${pad(ms / 1000 % 60, 2)},${pad(ms % 1000, 3)}"
        }
        /** Consumed-byte bound, not a provider wall-time guarantee. Caller owns the stream. */
        fun read(input: java.io.InputStream): SubRip {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - out.size()))
                if (count < 0) break
                require(count > 0) { "Nonprogressing subtitle stream" }
                out.write(buffer, 0, count)
                require(out.size() <= MAX_BYTES) { "Subtitle exceeds 1 MiB" }
            }
            return parse(out.toByteArray())
        }
        fun parse(bytes: ByteArray): SubRip {
            require(bytes.size <= MAX_BYTES)
            var text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
            text = text.replace("\r\n", "\n")
            require('\r' !in text && text.none { it < ' ' && it != '\n' && it != '\t' })
            if (text.isEmpty()) return SubRip(emptyList())
            val lines = text.split('\n'); val out = mutableListOf<SubtitleCue>(); var i = 0
            while (i < lines.size) {
                while (i < lines.size && lines[i].isEmpty()) i++
                if (i == lines.size) break
                require(out.size < MAX_CUES)
                // Consecutive numbering avoids ambiguous duplicate identity; nonsequential variants are refused.
                require(lines[i++] == (out.size + 1).toString()) { "Expected consecutive cue index" }
                require(i < lines.size)
                val match = timing.matchEntire(lines[i++]) ?: error("Unsupported subtitle timing")
                fun time(offset: Int): Long {
                    val h = match.groupValues[offset].toLong(); val m = match.groupValues[offset + 1].toLong()
                    val s = match.groupValues[offset + 2].toLong(); val ms = match.groupValues[offset + 3].toLong()
                    require(m < 60 && s < 60)
                    return h * 3600000 + m * 60000 + s * 1000 + ms
                }
                val start = time(1); val end = time(5); require(start < end)
                val content = mutableListOf<String>(); var chars = 0
                while (i < lines.size && lines[i].isNotEmpty()) {
                    val line = lines[i++]; chars += line.length + 1
                    require(chars <= 8192 && content.size < 32)
                    content.add(line)
                }
                require(content.isNotEmpty())
                out.add(SubtitleCue(start, end, content.joinToString("\n")))
            }
            return SubRip(out.toList()).also { it.encode() }
        }
    }
}
