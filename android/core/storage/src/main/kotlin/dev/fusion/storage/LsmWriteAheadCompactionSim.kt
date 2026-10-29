package dev.fusion.storage

/**
 * Clean-room Log-Structured Merge (`LSM`) Tree Engine (`MemTable` + Sorted Immutable `SSTable`
 * Runs + Tombstone Deletion + Size-Tiered Compaction) (Apache-2.0).
 */
object LsmWriteAheadCompactionSim {
    const val SCOPE =
        "LSM-tree MemTable + immutable sorted SSTable runs + tombstone deletion + deterministic compaction"

    private sealed interface ValEntry {
        data class Present(val value: String) : ValEntry
        data object Tombstone : ValEntry
    }

    class Engine(private val memTableFlushThreshold: Int = 4) {
        private val memTable = java.util.TreeMap<String, ValEntry>()
        private val sstablesNewestFirst = ArrayDeque<Map<String, ValEntry>>()

        val sstableCount: Int get() = sstablesNewestFirst.size

        fun put(key: String, value: String) {
            memTable[key] = ValEntry.Present(value)
            if (memTable.size >= memTableFlushThreshold) flushMemTable()
        }

        fun delete(key: String) {
            memTable[key] = ValEntry.Tombstone
            if (memTable.size >= memTableFlushThreshold) flushMemTable()
        }

        fun get(key: String): String? {
            memTable[key]?.let { return (it as? ValEntry.Present)?.value }
            for (sst in sstablesNewestFirst) {
                sst[key]?.let { return (it as? ValEntry.Present)?.value }
            }
            return null
        }

        fun flushMemTable() {
            if (memTable.isEmpty()) return
            sstablesNewestFirst.addFirst(LinkedHashMap(memTable))
            memTable.clear()
        }

        /** Merges all SSTable runs from oldest to newest and purges tombstones at bottom level! */
        fun compactAll(): Int {
            flushMemTable()
            val merged = java.util.TreeMap<String, ValEntry>()
            for (sst in sstablesNewestFirst.reversed()) {
                merged.putAll(sst)
            }
            val liveOnly = LinkedHashMap<String, ValEntry>()
            for ((k, v) in merged) if (v is ValEntry.Present) liveOnly[k] = v
            sstablesNewestFirst.clear()
            if (liveOnly.isNotEmpty()) sstablesNewestFirst.addFirst(liveOnly)
            return liveOnly.size
        }
    }
}
