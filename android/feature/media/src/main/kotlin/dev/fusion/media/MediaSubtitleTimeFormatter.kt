// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/**
 * Bounded WebVTT `HH:MM:SS.mmm` timestamp formatter.
 */
class MediaSubtitleTimeFormatter private constructor() {
    companion object {
        fun formatVtt(ms: Long): String {
            require(ms >= 0L) { "negative ms" }
            val hours = ms / 3_600_000L
            val mins = (ms % 3_600_000L) / 60_000L
            val secs = (ms % 60_000L) / 1_000L
            val millis = ms % 1_000L
            return "%02d:%02d:%02d.%03d".format(java.util.Locale.US, hours, mins, secs, millis)
        }
    }
}
