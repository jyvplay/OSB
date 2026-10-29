// SPDX-License-Identifier: Apache-2.0
package dev.fusion.storage

/**
 * Pure-JVM **Quota-Aware Segmented LRU (`SLRU` Probation + Protected) Cache Eviction Planner**
 * for `:core:storage` and Recall's local media archive disk-headroom guard.
 */
object SegmentedLruStorageQuotaGovernor {
    const val SCOPE =
        "Segmented LRU (probation + protected) storage quota governor preserving pinned & multi-viewed Recall/Office items under low-disk pressure."

    data class StoredObjectMeta(
        val key: String,
        val byteSize: Long,
        val accessCount: Int,
        val lastAccessEpochMs: Long,
        val userPinned: Boolean = false,
    )

    data class EvictionPlan(
        val evictedKeysInOrder: List<String>,
        val freedBytes: Long,
        val remainingBytes: Long,
    )

    fun planEviction(objects: List<StoredObjectMeta>, maxQuotaBytes: Long): EvictionPlan {
        require(maxQuotaBytes >= 0L)
        var currentBytes = objects.sumOf { it.byteSize }
        if (currentBytes <= maxQuotaBytes) return EvictionPlan(emptyList(), 0L, currentBytes)

        // Probation (accessCount <= 1) evicted strictly before Protected (accessCount >= 2); pinned never evicted
        val candidates = objects.filterNot { it.userPinned }.sortedWith(
            compareBy<StoredObjectMeta> { it.accessCount >= 2 }
                .thenBy { it.lastAccessEpochMs }
                .thenByDescending { it.byteSize }
        )
        val evicted = ArrayList<String>()
        var freed = 0L
        for (c in candidates) {
            if (currentBytes <= maxQuotaBytes) break
            evicted.add(c.key)
            currentBytes -= c.byteSize
            freed += c.byteSize
        }
        return EvictionPlan(evicted, freed, currentBytes)
    }
}
