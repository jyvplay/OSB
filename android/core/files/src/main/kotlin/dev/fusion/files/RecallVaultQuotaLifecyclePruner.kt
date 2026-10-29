// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

/**
 * Storage-quota & pinned-archive lifecycle pruner for **Recall** vaults (`RecallVaultDirectoryDelineator`),
 * reclaiming space via age-weighted size eviction while strictly protecting pinned/starred items
 * and active in-progress recordings.
 */
object RecallVaultQuotaLifecyclePruner {
    const val SCOPE =
        "Recall vault byte-budget lifecycle pruner with pinned-archive immunity, active-session lock, and deterministic LRU-size eviction."

    data class VaultEntry(
        val id: String,
        val folderPath: String,
        val sizeBytes: Long,
        val lastAccessedEpochSec: Long,
        val pinned: Boolean,
        val activeRecording: Boolean = false,
    )

    data class PrunePlan(
        val keptIds: List<String>,
        val evictedIds: List<String>,
        val bytesBefore: Long,
        val bytesAfter: Long,
        val quotaSatisfied: Boolean,
    )

    fun planEviction(entries: List<VaultEntry>, maxQuotaBytes: Long, nowEpochSec: Long): PrunePlan {
        require(maxQuotaBytes >= 0L)
        val bytesBefore = entries.sumOf { it.sizeBytes.coerceAtLeast(0L) }
        if (bytesBefore <= maxQuotaBytes) {
            return PrunePlan(entries.map { it.id }, emptyList(), bytesBefore, bytesBefore, true)
        }
        val candidates = entries
            .filter { !it.pinned && !it.activeRecording }
            .sortedWith(
                compareByDescending<VaultEntry> {
                    val ageSec = (nowEpochSec - it.lastAccessedEpochSec).coerceAtLeast(1L)
                    ageSec * (it.sizeBytes.coerceAtLeast(1024L) / 1024L)
                }.thenBy { it.id }
            )

        val evicted = LinkedHashSet<String>()
        var remaining = bytesBefore
        for (c in candidates) {
            if (remaining <= maxQuotaBytes) break
            evicted.add(c.id)
            remaining -= c.sizeBytes.coerceAtLeast(0L)
        }
        val kept = entries.filter { it.id !in evicted }.map { it.id }
        return PrunePlan(kept, evicted.toList(), bytesBefore, remaining, remaining <= maxQuotaBytes)
    }
}
