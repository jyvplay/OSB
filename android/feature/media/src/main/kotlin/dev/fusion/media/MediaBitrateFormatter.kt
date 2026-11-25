// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/**
 * Bounded media bitrate formatter (`kbps` / `Mbps`).
 */
class MediaBitrateFormatter private constructor() {
    companion object {
        fun formatBps(bps: Long): String {
            require(bps >= 0L) { "negative bps" }
            return if (bps < 1_000_000L) {
                "${bps / 1_000L} kbps"
            } else {
                "%.1f Mbps".format(java.util.Locale.US, bps / 1_000_000.0)
            }
        }
    }
}
