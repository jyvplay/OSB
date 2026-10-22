// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/** One CUE track: number, optional tags, absolute INDEX 01 in CD frames. */
data class CueTrack(val number: Int, val title: String?, val performer: String?, val index01Frames: Long)

/** Parsed sheet: source file plus ordered tracks. */
data class CueSheet(val file: String, val tracks: List<CueTrack>)

/**
 * CUE sheet subset: one FILE line, TRACK nn AUDIO entries with TITLE /
 * PERFORMER / INDEX 01 (MM:SS:FF at 75 fps), REM comments ignored.
 * Global TITLE/PERFORMER before TRACK 1 are validated and ignored. Track
 * numbers strictly ascend from 1 and INDEX 01 frames strictly increase;
 * anything else (other INDEX numbers are skipped, unknown keywords throw)
 * is refused. Feeds chapter marks; audio decoding is outside this kernel.
 * Caps: 1000 lines, 99 tracks, 256-char quoted strings.
 */
object CueSheetParser {
    const val MAX_LINES = 1000
    const val MAX_TRACKS = 99
    const val MAX_QUOTED = 256
    const val FPS = 75L

    fun parse(text: String): CueSheet {
        val lines = text.split('\n')
        require(lines.size <= MAX_LINES) { "over $MAX_LINES lines" }
        var file: String? = null
        val tracks = mutableListOf<CueTrack>()
        var number = 0
        var title: String? = null
        var performer: String? = null
        var index01: Long? = null
        fun flush() {
            if (number == 0) return
            val idx = index01 ?: throw IllegalArgumentException("track $number missing INDEX 01")
            if (tracks.isNotEmpty()) {
                require(number == tracks.last().number + 1) { "track numbers must ascend from 1" }
                require(idx > tracks.last().index01Frames) { "INDEX 01 must increase" }
            } else {
                require(number == 1) { "first track must be 1" }
            }
            tracks += CueTrack(number, title, performer, idx)
            title = null; performer = null; index01 = null
        }
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("REM")) continue
            val keyword = line.substringBefore(' ')
            val rest = line.substringAfter(' ', "")
            when (keyword) {
                "FILE" -> {
                    require(file == null) { "duplicate FILE" }
                    require(tracks.isEmpty() && number == 0) { "FILE must lead" }
                    file = quoted(rest, true)
                }
                "TRACK" -> {
                    require(file != null) { "TRACK before FILE" }
                    val parts = rest.trim().split(Regex("\\s+"))
                    require(parts.size == 2 && parts[1] == "AUDIO") { "only TRACK nn AUDIO admitted" }
                    require(parts[0].length == 2 && parts[0].all { it.isDigit() }) { "bad track number" }
                    require(tracks.size < MAX_TRACKS) { "over $MAX_TRACKS tracks" }
                    flush()
                    number = parts[0].toInt()
                }
                "TITLE" -> {
                    // Global TITLE (before TRACK 1) is validated and ignored.
                    val t = quoted(rest, false)
                    if (number > 0) title = t
                }
                "PERFORMER" -> {
                    // Global PERFORMER (before TRACK 1) is validated and ignored.
                    val p = quoted(rest, false)
                    if (number > 0) performer = p
                }
                "INDEX" -> {
                    require(number > 0) { "INDEX before TRACK" }
                    val parts = rest.trim().split(Regex("\\s+"))
                    require(parts.size == 2) { "bad INDEX" }
                    if (parts[0] == "01") {
                        require(index01 == null) { "duplicate INDEX 01" }
                        index01 = frames(parts[1])
                    } else {
                        require(parts[0].all { it.isDigit() }) { "bad INDEX number" }
                    }
                }
                else -> throw IllegalArgumentException("unknown keyword $keyword")
            }
        }
        require(file != null) { "missing FILE" }
        flush()
        require(tracks.isNotEmpty()) { "no tracks" }
        return CueSheet(file!!, tracks)
    }

    private fun quoted(rest: String, allowSuffix: Boolean): String {
        val t = rest.trim()
        require(t.startsWith('"')) { "bad quoted string" }
        val end = t.indexOf('"', 1)
        require(end > 1) { "bad quoted string" }
        val tail = t.substring(end + 1).trim()
        if (allowSuffix) {
            require(tail.isNotEmpty() && tail.all { it.isLetterOrDigit() }) { "bad FILE type" }
        } else {
            require(tail.isEmpty()) { "trailing input after quote" }
        }
        val inner = t.substring(1, end)
        require(inner.length <= MAX_QUOTED) { "quoted over $MAX_QUOTED chars" }
        return inner
    }

    private fun frames(mmssff: String): Long {
        val p = mmssff.split(':')
        require(p.size == 3 && p.all { it.isNotEmpty() && it.all { c -> c.isDigit() } }) { "bad time $mmssff" }
        val mm = p[0].toLong()
        val ss = p[1].toLong()
        val ff = p[2].toLong()
        require(ss in 0..59 && ff in 0..74) { "ss/ff out of range in $mmssff" }
        val total = Math.addExact(Math.multiplyExact(Math.addExact(Math.multiplyExact(mm, 60), ss), FPS), ff)
        require(total in 0..FPS * 60 * 60 * 10) { "time over 10h in $mmssff" }
        return total
    }
}
