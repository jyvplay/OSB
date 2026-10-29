// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

import java.util.Locale

/**
 * Clean-room SMPTE ST 12-1 non-drop (`HH:MM:SS:FF`) & `29.97` NTSC drop-frame (`HH:MM:SS;FF`)
 * bidirectional frame-number timecode engine (`SmpteTimecode`).
 *
 * Implements the exact SMPTE `29.97` drop-frame rule: frame numbers `00` and `01` are skipped at the
 * start of every minute EXCEPT minutes `00, 10, 20, 30, 40, 50` (`18` dropped frame labels per 10-minute block =
 * `17,982` frames per 10 minutes)!
 */
object SmpteTimecode {
    const val SCOPE =
        "SMPTE ST 12-1 non-drop (24/25/30/60 fps) & 29.97 NTSC drop-frame (HH:MM:SS;FF) bidirectional frame-index converter; LTC biphase audio modulation out of scope."

    enum class Rate(val nominalFps: Int, val dropFrame: Boolean) {
        FPS_24(24, false),
        FPS_25(25, false),
        FPS_30_NDF(30, false),
        FPS_29_97_DF(30, true),
        FPS_60_NDF(60, false),
    }

    fun framesToTimecode(frameNumber: Long, rate: Rate): String {
        require(frameNumber >= 0L) { "Negative frameNumber: $frameNumber" }
        val adjusted = if (!rate.dropFrame) {
            frameNumber
        } else {
            // 17,982 real frames per 10 minutes; 1,798 frames per non-tenth minute.
            val d = frameNumber / 17_982L
            val m = frameNumber % 17_982L
            val extraDrops = if (m < 2L) 0L else 2L * ((m - 2L) / 1_798L)
            frameNumber + 18L * d + extraDrops
        }
        val fps = rate.nominalFps.toLong()
        val ff = adjusted % fps
        val totalSec = adjusted / fps
        val ss = totalSec % 60L
        val mm = (totalSec / 60L) % 60L
        val hh = totalSec / 3600L
        val sep = if (rate.dropFrame) ';' else ':'
        return String.format(Locale.US, "%02d:%02d:%02d%c%02d", hh, mm, ss, sep, ff)
    }

    fun timecodeToFrames(tc: String, rate: Rate): Long {
        val m = Regex("^(\\d{2}):([0-5]\\d):([0-5]\\d)([:;])(\\d{2})$").matchEntire(tc.trim())
            ?: throw IllegalArgumentException("Malformed SMPTE timecode: $tc")
        val hh = m.groupValues[1].toLong()
        val mm = m.groupValues[2].toLong()
        val ss = m.groupValues[3].toLong()
        val sep = m.groupValues[4][0]
        val ff = m.groupValues[5].toLong()
        require(ff < rate.nominalFps) { "Frame field $ff >= nominal ${rate.nominalFps}" }
        if (rate.dropFrame) {
            require(sep == ';') { "Drop-frame timecode requires ';' separator: $tc" }
            require(!(ss == 0L && (mm % 10L) != 0L && (ff == 0L || ff == 1L))) {
                "Illegal skipped drop-frame label: $tc"
            }
            val totalMinutes = hh * 60L + mm
            val dropped = 2L * (totalMinutes - totalMinutes / 10L)
            return (hh * 3600L + mm * 60L + ss) * 30L + ff - dropped
        } else {
            require(sep == ':') { "Non-drop timecode requires ':' separator: $tc" }
            return (hh * 3600L + mm * 60L + ss) * rate.nominalFps.toLong() + ff
        }
    }
}
