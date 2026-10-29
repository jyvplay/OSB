// SPDX-License-Identifier: Apache-2.0
package dev.fusion.alerts

/**
 * Pure-JVM **Cross-App Activity Timeline & Recall Media Quick-Jump Aggregator** for `:feature:hub`,
 * deduplicating recent Office documents, Browser sessions, and Recall archived media items into a
 * privacy-filtered workspace feed.
 */
object HubWorkspaceFeedAggregator {
    const val SCOPE =
        "Hub workspace activity aggregator merging Office docs, Browser tabs, and Recall media captures while suppressing amnesic/incognito sessions."

    data class ActivityEntry(
        val entryId: String,
        val surface: String, // WRITER, SHEETS, SLIDES, BROWSER, RECALL, FILES
        val title: String,
        val timestampMs: Long,
        val pinned: Boolean = false,
        val fromAmnesicSession: Boolean = false,
    )

    fun buildUnifiedFeed(entries: List<ActivityEntry>, maxItems: Int = 25): List<ActivityEntry> {
        require(maxItems >= 1)
        return entries
            .filterNot { it.fromAmnesicSession }
            .groupBy { "${it.surface}:${it.entryId}" }
            .values
            .map { group -> group.maxBy { it.timestampMs } }
            .sortedWith(
                compareByDescending<ActivityEntry> { it.pinned }
                    .thenByDescending { it.timestampMs }
                    .thenBy { it.entryId }
            )
            .take(maxItems)
    }
}
