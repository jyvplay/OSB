// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

/**
 * R28 EXECUTED host kernel. Pure trash-eviction policy over entry snapshots.
 *
 * [TrashRepository] refuses every mutation through [FileMutationPolicy]; this ledger
 * computes WHICH entries a future honest implementation must purge and validates
 * restore targets. It touches no filesystem: ordering + validation only.
 */
object TrashLedger {
    const val MAX_ENTRIES = 4096
    const val MAX_NAME = 255
    const val MAX_TOTAL_BYTES = 1L shl 40 // 1 TiB accounting cap; sums saturate, never wrap.
    private val ID = Regex("[A-Za-z0-9_-]{1,64}")

    data class Entry(val id: String, val name: String, val bytes: Long, val trashedAtEpoch: Long, val retainDays: Int) {
        init {
            require(ID.matches(id)) { "Entry id must be 1..64 ASCII word characters" }
            require(name.length in 1..MAX_NAME && '/' !in name && '\u0000' !in name && name != "." && name != "..") {
                "Entry name must be a single 1..255-char path segment"
            }
            require(bytes in 0..MAX_TOTAL_BYTES) { "Byte count out of range" }
            require(trashedAtEpoch in 0..4102444800L) { "trashedAtEpoch out of range" }
            require(retainDays in 1..3650) { "retainDays must be 1..3650" }
        }
        fun expiresAt(): Long = trashedAtEpoch + retainDays * 86400L
    }

    /**
     * Ordered purge list: expired entries oldest-first, then (if still over [maxBytes])
     * largest-first until at/under budget. Deterministic; ties break by id.
     */
    fun purgeOrder(entries: List<Entry>, nowEpoch: Long, maxBytes: Long): List<String> {
        require(entries.size <= MAX_ENTRIES) { "At most $MAX_ENTRIES entries per evaluation" }
        require(entries.map { it.id }.toSet().size == entries.size) { "Duplicate entry ids" }
        require(nowEpoch in 0..4102444800L) { "nowEpoch out of range" }
        require(maxBytes in 0..MAX_TOTAL_BYTES) { "maxBytes out of range" }
        val expired = entries.filter { it.expiresAt() <= nowEpoch }
            .sortedWith(compareBy({ it.expiresAt() }, { it.trashedAtEpoch }, { it.id }))
        val purged = expired.toMutableList()
        var remaining = entries.filter { it !in purged.toSet() }
            .fold(0L) { acc, e -> (acc + e.bytes).coerceAtMost(MAX_TOTAL_BYTES) }
        val over = remaining - maxBytes
        if (over > 0) {
            val bySize = entries.filter { it !in purged.toSet() }
                .sortedWith(compareByDescending<Entry> { it.bytes }.thenBy { it.id })
            for (e in bySize) {
                if (remaining <= maxBytes) break
                purged.add(e)
                remaining -= e.bytes
            }
        }
        return purged.map { it.id }
    }

    /** Validate a restore target: entry exists, destination is a fresh single segment, no live collision. */
    fun validateRestore(entries: List<Entry>, id: String, liveNames: Set<String>, destName: String): Entry {
        require(entries.size <= MAX_ENTRIES) { "At most $MAX_ENTRIES entries per evaluation" }
        val entry = entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown trash entry")
        require(destName.length in 1..MAX_NAME && '/' !in destName && '\u0000' !in destName &&
            destName != "." && destName != "..") { "Destination must be a single 1..255-char segment" }
        require(destName !in liveNames) { "Destination collides with a live file" }
        return entry
    }
}
