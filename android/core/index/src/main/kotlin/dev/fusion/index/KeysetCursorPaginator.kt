// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

/**
 * Pure-JVM **Content-Addressable Keyset Cursor Paginator** (`SEEK` / opaque keyset cursor over
 * `(timestampMs DESC, id ASC)`) for `:core:data`, eliminating `OFFSET` drift during live Recall
 * media inserts or Undo deletions!
 */
object KeysetCursorPaginator {
    const val SCOPE =
        "Drift-free keyset cursor paginator over (timestampMs DESC, itemId ASC) eliminating SQL/list OFFSET skip/duplicate anomalies."

    data class TimestampedItem<T>(
        val itemId: String,
        val timestampMs: Long,
        val payload: T,
    )

    data class CursorPage<T>(
        val items: List<TimestampedItem<T>>,
        val nextCursorToken: String?,
    )

    fun <T> page(
        allItems: List<TimestampedItem<T>>,
        afterCursorToken: String?,
        pageSize: Int,
    ): CursorPage<T> {
        require(pageSize in 1..500) { "pageSize must be 1..500" }
        val sorted = allItems.sortedWith(
            compareByDescending<TimestampedItem<T>> { it.timestampMs }.thenBy { it.itemId }
        )
        val filtered = if (afterCursorToken.isNullOrBlank()) {
            sorted
        } else {
            val parts = afterCursorToken.split('|', limit = 2)
            require(parts.size == 2) { "Malformed cursor token" }
            val cTs = parts[0].toLong()
            val cId = parts[1]
            sorted.filter { it.timestampMs < cTs || (it.timestampMs == cTs && it.itemId > cId) }
        }
        val slice = filtered.take(pageSize)
        val next = if (filtered.size > pageSize) {
            val last = slice.last()
            "${last.timestampMs}|${last.itemId}"
        } else null
        return CursorPage(slice, next)
    }
}
