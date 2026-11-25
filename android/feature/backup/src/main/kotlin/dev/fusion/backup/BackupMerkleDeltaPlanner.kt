// SPDX-License-Identifier: Apache-2.0
package dev.fusion.backup

/**
 * Production-grade incremental Merkle-chunk delta-sync planner.
 */
class BackupMerkleDeltaPlanner private constructor() {
    companion object {
        data class ChunkMeta(val index: Int, val sha256Hex: String, val sizeBytes: Long)

        fun planUploadIndices(
            localChunks: List<ChunkMeta>,
            remoteChunks: List<ChunkMeta>,
            maxDeltaBytes: Long
        ): List<Int> {
            require(maxDeltaBytes >= 0L) { "negative maxDeltaBytes" }
            val remoteByIdx = remoteChunks.associateBy { it.index }
            val toUpload = mutableListOf<Int>()
            var totalBytes = 0L
            for (c in localChunks.sortedBy { it.index }) {
                val r = remoteByIdx[c.index]
                if (r == null || !r.sha256Hex.equals(c.sha256Hex, ignoreCase = true)) {
                    totalBytes += c.sizeBytes
                    require(totalBytes <= maxDeltaBytes) { "delta exceeds budget: $totalBytes > $maxDeltaBytes" }
                    toUpload.add(c.index)
                }
            }
            return toUpload
        }
    }
}
