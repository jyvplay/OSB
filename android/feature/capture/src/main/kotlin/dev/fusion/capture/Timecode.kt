// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

/** Non-drop timecode at an integer frame rate: HH:MM:SS:FF. */
data class Timecode(val hours: Int, val minutes: Int, val seconds: Int, val frames: Int, val fps: Int) {
    override fun toString(): String =
        "%02d:%02d:%02d:%02d".format(hours, minutes, seconds, frames)
}

/**
 * Non-drop timecode for capture display. Millis map to frames by truncation
 * against an integer fps from the fixed set {24, 25, 30, 50, 60}; drop-frame
 * (29.97/59.94) is refused outright because silently mislabeling it as
 * non-drop corrupts every downstream duration. Range is [0, 24h); the
 * 24-hour wrap is a refusal, not a rollover, so no recording silently
 * restarts its clock.
 */
object Timecodes {
    const val MAX_MILLIS = 86_400_000L
    val RATES = setOf(24, 25, 30, 50, 60)

    fun validFps(fps: Int) = fps in RATES

    fun fromMillis(millis: Long, fps: Int): Timecode {
        require(validFps(fps))
        require(millis in 0 until MAX_MILLIS)
        val totalFrames = millis * fps / 1000
        val ff = (totalFrames % fps).toInt()
        val totalSeconds = totalFrames / fps
        val ss = (totalSeconds % 60).toInt()
        val totalMinutes = totalSeconds / 60
        val mm = (totalMinutes % 60).toInt()
        val hh = (totalMinutes / 60).toInt()
        return Timecode(hh, mm, ss, ff, fps)
    }

    fun parse(text: String, fps: Int): Timecode {
        require(validFps(fps))
        val p = text.split(":")
        require(p.size == 4 && p.all { it.length == 2 && it.all { c -> c in '0'..'9' } })
        val (hh, mm, ss, ff) = p.map { it.toInt() }
        require(hh in 0..23 && mm in 0..59 && ss in 0..59 && ff in 0 until fps)
        return Timecode(hh, mm, ss, ff, fps)
    }

    fun toMillis(tc: Timecode): Long {
        require(validFps(tc.fps))
        require(tc.hours in 0..23 && tc.minutes in 0..59 && tc.seconds in 0..59 && tc.frames in 0 until tc.fps)
        val totalSeconds = ((tc.hours * 60L + tc.minutes) * 60 + tc.seconds)
        return totalSeconds * 1000 + tc.frames * 1000L / tc.fps
    }
}
