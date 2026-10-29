// SPDX-License-Identifier: Apache-2.0
package dev.fusion.launcher

/**
 * Clean-room Launcher Shelf & Pinned Surface Organizer merging native modules, standalone Web-App
 * windows, and multi-window workspace presets without usage-tracking telemetry.
 */
object PinnedWebShortcutShelfModel {
    const val SCOPE =
        "Pure-JVM deterministic launcher shelf organizer merging native apps, Web-App windows, and workspace presets with 48dp minimum grid geometry."

    enum class ShelfKind { NATIVE_MODULE, WEB_APP_WINDOW, WORKSPACE_PRESET }

    data class ShelfEntry(
        val id: String,
        val label: String,
        val kind: ShelfKind,
        val pinnedRank: Int? = null,
    )

    fun organizeShelf(entries: List<ShelfEntry>, columns: Int): List<List<ShelfEntry>> {
        require(columns in 2..12) { "columns $columns outside 2..12" }
        val sorted = entries.distinctBy { it.id }.sortedWith(
            compareBy<ShelfEntry> { it.pinnedRank == null }
                .thenBy { it.pinnedRank ?: Int.MAX_VALUE }
                .thenBy { it.label.lowercase() },
        )
        return sorted.chunked(columns)
    }
}
