// SPDX-License-Identifier: Apache-2.0
package dev.fusion.filesui

import java.security.MessageDigest

/**
 * Clean-room Storage Overview, Exact Duplicate-File Clusterer (`FM-07`), Forge Peek Metadata
 * Formatter (`FM-04`), and Cloud/Placeholder Sync Badge Resolver (`FM-09`).
 *
 * Clusters duplicates safely in two stages (exact byte-size bucket -> full SHA-256 content digest)
 * so files with equal size or same filename are NEVER falsely marked duplicate without matching SHA-256!
 */
object DuplicateFileClusterer {
    enum class CloudResidencyBadge { LOCAL_PINNED, CLOUD_PLACEHOLDER_OFFLINE, SYNCING_IN_FLIGHT, CONFLICT_COPY }

    data class ScannedFile(
        val id: String,
        val name: String,
        val sizeBytes: Long,
        val sha256Hex: String?, // computed lazily only for size-collision buckets > 1
        val isCloudPlaceholderZeroBlocks: Boolean = false,
        val syncInFlight: Boolean = false,
    )

    data class DuplicateCluster(
        val sha256Hex: String,
        val eachSizeBytes: Long,
        val files: List<ScannedFile>,
    ) {
        val reclaimableBytes: Long get() = eachSizeBytes * (files.size - 1).coerceAtLeast(0)
    }

    data class StorageAuditSummary(
        val totalFiles: Int,
        val totalBytes: Long,
        val largestFiles: List<ScannedFile>,
        val duplicateClusters: List<DuplicateCluster>,
        val totalReclaimableDuplicateBytes: Long,
    )

    fun analyzeDirectory(files: List<ScannedFile>, topLargeLimit: Int = 5): StorageAuditSummary {
        val totalBytes = files.sumOf { it.sizeBytes.coerceAtLeast(0L) }
        val largest = files.filter { it.sizeBytes > 0L }
            .sortedWith(compareByDescending<ScannedFile> { it.sizeBytes }.thenBy { it.name.lowercase() })
            .take(topLargeLimit.coerceIn(1, 50))

        val sizeBuckets = files.filter { it.sizeBytes > 0L && it.sha256Hex != null }
            .groupBy { it.sizeBytes }
            .values
            .filter { it.size >= 2 }

        val clusters = ArrayList<DuplicateCluster>()
        for (bucket in sizeBuckets) {
            val byHash = bucket.groupBy { it.sha256Hex!!.lowercase() }
            for ((sha, group) in byHash) {
                if (group.size >= 2) {
                    clusters.add(DuplicateCluster(sha, group.first().sizeBytes, group.sortedBy { it.name }))
                }
            }
        }
        clusters.sortByDescending { it.reclaimableBytes }
        return StorageAuditSummary(
            totalFiles = files.size,
            totalBytes = totalBytes,
            largestFiles = largest,
            duplicateClusters = clusters,
            totalReclaimableDuplicateBytes = clusters.sumOf { it.reclaimableBytes },
        )
    }

    fun badgeFor(file: ScannedFile): CloudResidencyBadge = when {
        file.syncInFlight -> CloudResidencyBadge.SYNCING_IN_FLIGHT
        Regex("""\(conflict|\(restored \d+\)""", RegexOption.IGNORE_CASE).containsMatchIn(file.name) ->
            CloudResidencyBadge.CONFLICT_COPY
        file.isCloudPlaceholderZeroBlocks -> CloudResidencyBadge.CLOUD_PLACEHOLDER_OFFLINE
        else -> CloudResidencyBadge.LOCAL_PINNED
    }

    fun formatForgePeekHeadline(name: String, sizeBytes: Long, isDirectory: Boolean): String {
        if (isDirectory) return "Folder • $name"
        val ext = name.substringAfterLast('.', "").lowercase()
        val kind = when (ext) {
            "pdf" -> "Isolated PDF Preview"
            "docx", "odt", "md", "txt" -> "Writer Document Peek"
            "xlsx", "ods", "csv" -> "Spreadsheet Peek"
            "png", "jpg", "jpeg", "webp", "heic", "psd", "ai" -> "Image/Graphic Peek"
            "zip", "7z", "tar", "gz" -> "Guarded Archive Peek"
            "mp3", "flac", "wav", "mp4", "mkv" -> "Media Peek"
            else -> "Binary Metadata Peek"
        }
        val humanSize = when {
            sizeBytes >= 1024L * 1024L -> "%.2f MiB".format(java.util.Locale.US, sizeBytes / (1024.0 * 1024.0))
            sizeBytes >= 1024L -> "%.1f KiB".format(java.util.Locale.US, sizeBytes / 1024.0)
            else -> "$sizeBytes B"
        }
        return "$kind • $humanSize"
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
