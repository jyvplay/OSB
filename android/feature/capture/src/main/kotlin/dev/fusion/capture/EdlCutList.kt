// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

import java.util.Locale

/**
 * Clean-room Non-Destructive Edit Decision List (`EDL` / CMX 3600 conceptual subset)
 * Frame-Accurate Timeline Trimmer & SMPTE Non-Drop Timecode Converter.
 *
 * Guarantees frame conservation:
 * - Splitting any clip at `timelineFrame` preserves `before.totalFrames == after.totalFrames`!
 * - Ripple-deleting `[cutStartFrame, cutEndFrame)` removes exact `cutEndFrame - cutStartFrame`
 *   frames and re-packs downstream timeline offsets with zero gaps or overlaps!
 */
object EdlCutList {
    const val SCOPE =
        "Frame-accurate non-destructive EDL timeline splicer (SMPTE HH:MM:SS:FF non-drop timecode, splitAt, rippleDelete, CMX-style export); NTSC 29.97 drop-frame and audio crossfades out of scope."

    private val ALLOWED_FPS = setOf(24, 25, 30, 50, 60)
    private val TC_RE = Regex("""^(\d{2}):(\d{2}):(\d{2}):(\d{2})$""")

    data class SourceClip(
        val sourceId: String,
        val inFrame: Long,
        val outFrameExclusive: Long,
    ) {
        init {
            require(sourceId.isNotBlank()) { "sourceId must not be blank" }
            require(inFrame >= 0L && outFrameExclusive > inFrame) {
                "invalid clip range [$inFrame, $outFrameExclusive)"
            }
        }

        val durationFrames: Long get() = outFrameExclusive - inFrame
    }

    data class TimelinePlacement(
        val eventIndex: Int,
        val clip: SourceClip,
        val recordInFrame: Long,
        val recordOutFrameExclusive: Long,
    )

    class Timeline(val fps: Int, val clips: List<SourceClip>) {
        init {
            require(fps in ALLOWED_FPS) { "unsupported non-drop frame rate $fps (allowed: $ALLOWED_FPS)" }
        }

        val totalFrames: Long get() = clips.sumOf { it.durationFrames }

        fun placements(): List<TimelinePlacement> {
            var cursor = 0L
            return clips.mapIndexed { idx, c ->
                val p = TimelinePlacement(idx + 1, c, cursor, cursor + c.durationFrames)
                cursor += c.durationFrames
                p
            }
        }

        fun splitAt(timelineFrame: Long): Timeline {
            require(timelineFrame in 0L..totalFrames) { "timelineFrame $timelineFrame outside 0..$totalFrames" }
            if (timelineFrame == 0L || timelineFrame == totalFrames) return this
            val next = ArrayList<SourceClip>(clips.size + 1)
            var cursor = 0L
            for (c in clips) {
                val end = cursor + c.durationFrames
                if (timelineFrame in (cursor + 1) until end) {
                    val cutOffset = timelineFrame - cursor
                    next.add(SourceClip(c.sourceId, c.inFrame, c.inFrame + cutOffset))
                    next.add(SourceClip(c.sourceId, c.inFrame + cutOffset, c.outFrameExclusive))
                } else {
                    next.add(c)
                }
                cursor = end
            }
            return Timeline(fps, next)
        }

        fun rippleDelete(cutStartFrame: Long, cutEndExclusive: Long): Timeline {
            require(cutStartFrame in 0L until cutEndExclusive && cutEndExclusive <= totalFrames) {
                "invalid rippleDelete range [$cutStartFrame, $cutEndExclusive) for totalFrames=$totalFrames"
            }
            val splitBoth = this.splitAt(cutStartFrame).splitAt(cutEndExclusive)
            val kept = ArrayList<SourceClip>()
            for (p in splitBoth.placements()) {
                if (p.recordOutFrameExclusive <= cutStartFrame || p.recordInFrame >= cutEndExclusive) {
                    kept.add(p.clip)
                }
            }
            return Timeline(fps, kept)
        }

        fun formatCmx3600(title: String): String = buildString {
            append("TITLE: ${title.trim()}\nFCM: NON-DROP FRAME\n")
            for (p in placements()) {
                append(
                    String.format(
                        Locale.ROOT,
                        "%03d  %-8s V     C        %s %s %s %s\n",
                        p.eventIndex,
                        p.clip.sourceId.take(8),
                        framesToSmpte(p.clip.inFrame, fps),
                        framesToSmpte(p.clip.outFrameExclusive, fps),
                        framesToSmpte(p.recordInFrame, fps),
                        framesToSmpte(p.recordOutFrameExclusive, fps),
                    ),
                )
            }
        }
    }

    fun framesToSmpte(frame: Long, fps: Int): String {
        require(fps in ALLOWED_FPS && frame >= 0L) { "invalid frame=$frame or fps=$fps" }
        val ff = (frame % fps).toInt()
        val totalSec = frame / fps
        val ss = (totalSec % 60).toInt()
        val mm = ((totalSec / 60) % 60).toInt()
        val hh = (totalSec / 3600)
        require(hh <= 99L) { "SMPTE hours overflow ($hh > 99)" }
        return String.format(Locale.ROOT, "%02d:%02d:%02d:%02d", hh, mm, ss, ff)
    }

    fun smpteToFrames(tc: String, fps: Int): Long {
        require(fps in ALLOWED_FPS) { "unsupported fps $fps" }
        val m = TC_RE.matchEntire(tc.trim()) ?: throw IllegalArgumentException("invalid SMPTE timecode '$tc'")
        val hh = m.groupValues[1].toLong()
        val mm = m.groupValues[2].toInt()
        val ss = m.groupValues[3].toInt()
        val ff = m.groupValues[4].toInt()
        require(mm in 0..59 && ss in 0..59 && ff in 0 until fps) {
            "SMPTE field out of range in '$tc' for ${fps}fps"
        }
        return (((hh * 60L + mm) * 60L + ss) * fps) + ff
    }
}
