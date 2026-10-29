package dev.fusion.perf

/**
 * Clean-room Android `ComponentCallbacks2` (`TRIM_MEMORY_*`) Deterministic Subsystem Cache
 * Eviction Planner (Apache-2.0).
 */
object MemoryPressureTrimBudgeter {
    const val SCOPE =
        "ComponentCallbacks2 TRIM_MEMORY_UI_HIDDEN..COMPLETE deterministic cache eviction planner preserving dirty unsaved buffers"

    const val TRIM_UI_HIDDEN = 20
    const val TRIM_RUNNING_MODERATE = 5
    const val TRIM_RUNNING_LOW = 10
    const val TRIM_RUNNING_CRITICAL = 15
    const val TRIM_COMPLETE = 80

    data class PoolEntry(
        val subsystem: String,
        val bytesAllocated: Long,
        val hasUnsavedDirtyEdits: Boolean = false,
        val priority: Int = 5, // 1 = lowest (evict first, e.g. thumbnails), 10 = highest
    )

    data class EvictionPlan(
        val evictedSubsystems: List<String>,
        val bytesReclaimed: Long,
        val bytesRetained: Long,
    )

    fun planEviction(level: Int, pools: List<PoolEntry>): EvictionPlan {
        val targetFraction = when (level) {
            TRIM_RUNNING_MODERATE -> 0.25
            TRIM_UI_HIDDEN -> 0.40
            TRIM_RUNNING_LOW -> 0.60
            TRIM_RUNNING_CRITICAL -> 0.85
            TRIM_COMPLETE -> 1.00
            else -> 0.0
        }
        val totalBytes = pools.sumOf { it.bytesAllocated }
        val goalReclaim = (totalBytes * targetFraction).toLong()
        var reclaimed = 0L
        val evicted = ArrayList<String>()

        // NEVER evict dirty unsaved user documents; evict lowest priority & largest clean caches first!
        val candidates = pools.filter { !it.hasUnsavedDirtyEdits }
            .sortedWith(compareBy<PoolEntry> { it.priority }.thenByDescending { it.bytesAllocated })
        for (c in candidates) {
            if (reclaimed >= goalReclaim && level != TRIM_COMPLETE) break
            evicted.add(c.subsystem)
            reclaimed += c.bytesAllocated
        }
        return EvictionPlan(evicted, reclaimed, totalBytes - reclaimed)
    }
}
