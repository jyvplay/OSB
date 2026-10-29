// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

/**
 * Pure-JVM **Extended M3U8 (`#EXTM3U` / `#EXTINF`) & JSON Sidecar Manifest Exporter**
 * for **Recall** folders, writing portable local playlist files inside each URL / Page-Title /
 * Custom folder so VLC, mpv, Foobar2000, or Fusion MediaHub can play recorded music & video albums immediately!
 */
object RecallPlaylistSidecarWriter {
    const val SCOPE =
        "Portable #EXTM3U/#EXTINF playlist generator and deterministic sidecar manifest writer for Recall folders."

    data class ArchivedPlaylistTrack(
        val relativeFileName: String,
        val displayTitle: String,
        val durationSeconds: Int,
        val mediaKind: String, // MUSIC or VIDEO
        val tierRecorded: Int,
    )

    fun buildExtM3uPlaylist(folderTitle: String, tracks: List<ArchivedPlaylistTrack>): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        sb.append("#PLAYLIST:").append(folderTitle.replace('\n', ' ').trim()).append('\n')
        for (t in tracks) {
            require(!t.relativeFileName.contains("..") && !t.relativeFileName.startsWith("/")) {
                "Unsafe playlist path '${t.relativeFileName}'"
            }
            val dur = t.durationSeconds.coerceAtLeast(-1)
            val safeTitle = t.displayTitle.replace('\r', ' ').replace('\n', ' ').trim()
            sb.append("#EXTINF:").append(dur).append(',').append(safeTitle)
                .append(" [Recall T").append(t.tierRecorded).append("]\n")
            sb.append(t.relativeFileName).append('\n')
        }
        return sb.toString()
    }
}
