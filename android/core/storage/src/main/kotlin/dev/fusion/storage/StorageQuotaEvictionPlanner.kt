// SPDX-License-Identifier: Apache-2.0
package dev.fusion.storage

/**
 * Bounded LRU storage quota eviction planner. Given items ordered from
 * least-recently-used to most-recently-used, selects the prefix of item IDs
 * to evict to free at least [neededBytes].
 */
class StorageQuotaEvictionPlanner private constructor() {
    companion object {
        const val MAX_ITEMS = 10_000

        data class CachedItem(val id: String, val sizeBytes: Long)

        fun planEvictions(lruOrderedItems: List<CachedItem>, neededBytes: Long): List<String> {
            require(lruOrderedItems.size <= MAX_ITEMS) { "too many items" }
            require(neededBytes >= 0L) { "negative neededBytes" }
            if (neededBytes == 0L) return emptyList()
            val evicted = mutableListOf<String>()
            var freed = 0L
            for (item in lruOrderedItems) {
                require(item.sizeBytes >= 0L) { "negative item size" }
                evicted.add(item.id)
                freed += item.sizeBytes
                if (freed >= neededBytes) break
            }
            return evicted
        }
    }
}
