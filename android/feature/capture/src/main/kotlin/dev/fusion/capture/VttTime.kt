// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

/**
 * WebVTT timestamp and cue-timings parser for recording captions,
 * pinned to the W3C WebVTT grammar (webvtt1 "WebVTT timestamp" /
 * "WebVTT cue timings"):
 *
 * - A timestamp is mm:ss.ttt or hh:mm:ss.ttt. Hours are two OR MORE
 *   digits (9999:00:00.000 is legal); minutes and seconds are exactly
 *   two digits in 00..59; milliseconds are exactly three digits.
 * - Cue timings are: start timestamp, one or more spaces/tabs, the
 *   literal "-->", one or more spaces/tabs, end timestamp, optionally
 *   followed by cue settings. The end must be greater than the start.
 *
 * This is deliberately distinct from this module's SMPTE Timecode
 * kernel (HH:MM:SS:FF at integer frame rates): WebVTT carries
 * dot-separated milliseconds and optional hours and knows nothing about
 * frames. Pure JVM logic; refusal is IllegalArgumentException.
 */
object VttTime {
    data class Cue(val startMs: Long, val endMs: Long, val settings: List<String>)

    fun parseTimestamp(text: String): Long {
        val parts = text.split(':')
        require(parts.size == 2 || parts.size == 3) { "timestamp needs 2 or 3 colon groups" }
        val hours: Long
        val mm: String
        val rest: String
        if (parts.size == 3) {
            require(parts[0].length >= 2 && parts[0].all { it in '0'..'9' }) { "hours must be 2+ digits" }
            hours = parts[0].toLong()
            mm = parts[1]
            rest = parts[2]
        } else {
            hours = 0
            mm = parts[0]
            rest = parts[1]
        }
        require(mm.length == 2 && mm.all { it in '0'..'9' }) { "minutes must be exactly 2 digits" }
        val minutes = mm.toInt()
        require(minutes in 0..59) { "minutes out of 00..59: $minutes" }
        val dot = rest.indexOf('.')
        require(dot == 2) { "seconds must be 2 digits followed by '.'" }
        val ss = rest.substring(0, 2)
        val ttt = rest.substring(3)
        require(ss.all { it in '0'..'9' }) { "non-digit seconds" }
        val seconds = ss.toInt()
        require(seconds in 0..59) { "seconds out of 00..59: $seconds" }
        require(ttt.length == 3 && ttt.all { it in '0'..'9' }) { "milliseconds must be exactly 3 digits" }
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + ttt.toInt()
    }

    fun parseCueTimings(line: String): Cue {
        val arrow = line.indexOf("-->")
        require(arrow > 0) { "cue timings need '-->'" }
        val startText = line.substring(0, arrow).trimEnd(' ', '\t')
        require(startText.length < arrow) { "need whitespace before '-->'" }
        require(!startText.contains(' ') && !startText.contains('\t')) { "garbage before '-->'" }
        val after = line.substring(arrow + 3)
        require(after.startsWith(" ") || after.startsWith("\t")) { "need whitespace after '-->'" }
        val tail = after.trimStart(' ', '\t')
        val fields = tail.split(' ', '\t').filter { it.isNotEmpty() }
        require(fields.isNotEmpty()) { "missing end timestamp" }
        val start = parseTimestamp(startText)
        val end = parseTimestamp(fields[0])
        require(end > start) { "cue end must be greater than start" }
        return Cue(start, end, fields.drop(1))
    }
}
