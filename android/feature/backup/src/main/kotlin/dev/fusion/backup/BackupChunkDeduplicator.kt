// SPDX-License-Identifier: Apache-2.0
package dev.fusion.backup

/**
 * Bounded content-addressed chunk deduplication tracker.
 */
class BackupChunkDeduplicator private constructor() {
    companion object {
        fun empty(): BackupChunkDeduplicator = BackupChunkDeduplicator()
    }

    private val uniqueSizes = HashMap<String, Long>()
    private var totalBytes = 0L
    private var uniqueBytes = 0L

    fun addChunk(hash: String, sizeBytes: Long) {
        require(hash.isNotEmpty() && sizeBytes >= 0L) { "invalid chunk" }
        totalBytes += sizeBytes
        if (!uniqueSizes.containsKey(hash)) {
            uniqueSizes[hash] = sizeBytes
            uniqueBytes += sizeBytes
        }
    }

    fun dedupRatio(): Double =
        if (totalBytes == 0L) 0.0 else (totalBytes - uniqueBytes).toDouble() / totalBytes.toDouble()
}
