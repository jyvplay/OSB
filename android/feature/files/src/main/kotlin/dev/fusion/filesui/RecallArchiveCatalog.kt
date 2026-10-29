// SPDX-License-Identifier: Apache-2.0
package dev.fusion.filesui

/**
 * Pure-JVM archive browser, folder-delineation switcher, and deduplication analytics engine
 * for **Recall** inside `:feature:files` and the dedicated Recall workspace.
 */
object RecallArchiveCatalog {
    const val SCOPE =
        "Recall archive catalog & folder-tree organizer across BY_URL_NAME, BY_PAGE_TITLE, and BY_CUSTOM_TEMPLATE folders with per-tier (T1..T4), per-media-kind (IMAGE/VIDEO/MUSIC), and SHA-256 deduplication savings metrics."

    enum class ArchiveGrouping { BY_URL_FOLDER, BY_TITLE_FOLDER, BY_CUSTOM_FOLDER, BY_MEDIA_KIND, BY_TIER }

    data class CatalogRecord(
        val sha256Hex: String,
        val relativePath: String,
        val mediaKind: String, // IMAGE, VIDEO, MUSIC
        val delineationMode: String,
        val tierId: String,
        val byteSize: Long,
        val viewCount: Int,
        val pageUrl: String,
        val pageTitle: String,
    ) {
        val topFolderKey: String
            get() {
                val parts = relativePath.split('/').filter { it.isNotEmpty() }
                return if (parts.size >= 2) "${parts[0]}/${parts[1]}" else relativePath
            }
    }

    data class ArchiveSummary(
        val uniqueAssetCount: Int,
        val totalViewEvents: Int,
        val physicalBytesOnDisk: Long,
        val deduplicatedBytesSaved: Long,
        val countsByKind: Map<String, Int>,
        val countsByTier: Map<String, Int>,
        val groupedFolders: Map<String, List<CatalogRecord>>,
    )

    fun summarize(
        records: List<CatalogRecord>,
        grouping: ArchiveGrouping = ArchiveGrouping.BY_URL_FOLDER,
        filterQuery: String = "",
    ): ArchiveSummary {
        val needle = filterQuery.trim().lowercase()
        val filtered = if (needle.isEmpty()) records else records.filter {
            needle in it.relativePath.lowercase() ||
                needle in it.pageUrl.lowercase() ||
                needle in it.pageTitle.lowercase() ||
                needle in it.mediaKind.lowercase() ||
                needle in it.tierId.lowercase()
        }
        val byKind = LinkedHashMap<String, Int>()
        val byTier = LinkedHashMap<String, Int>()
        var physical = 0L
        var saved = 0L
        var views = 0
        for (r in filtered) {
            physical += r.byteSize
            val extraViews = (r.viewCount - 1).coerceAtLeast(0)
            saved += r.byteSize * extraViews.toLong()
            views += r.viewCount.coerceAtLeast(1)
            byKind[r.mediaKind] = (byKind[r.mediaKind] ?: 0) + 1
            byTier[r.tierId] = (byTier[r.tierId] ?: 0) + 1
        }
        val grouped = filtered.groupBy { r ->
            when (grouping) {
                ArchiveGrouping.BY_URL_FOLDER,
                ArchiveGrouping.BY_TITLE_FOLDER,
                ArchiveGrouping.BY_CUSTOM_FOLDER -> r.topFolderKey
                ArchiveGrouping.BY_MEDIA_KIND -> r.mediaKind
                ArchiveGrouping.BY_TIER -> r.tierId
            }
        }
        return ArchiveSummary(
            uniqueAssetCount = filtered.size,
            totalViewEvents = views,
            physicalBytesOnDisk = physical,
            deduplicatedBytesSaved = saved,
            countsByKind = byKind,
            countsByTier = byTier,
            groupedFolders = grouped,
        )
    }
}
