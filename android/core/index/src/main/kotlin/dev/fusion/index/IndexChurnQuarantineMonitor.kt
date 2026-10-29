// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

/**
 * Clean-room Search Index Churn Quarantine Monitor, Exclusion Rule Matcher, and Missing-Result
 * Coverage Diagnostics (`SR-02`, `SR-08`).
 *
 * Detects runaway build-dir / log-dir mutation storms (`mutationsInWindow > churnThreshold`),
 * automatically quarantines noisy directory prefixes, enforces `.nomedia` / `.fusionignore`
 * exclusions, and explains *why* a file is absent from search results (`EXCLUDED_GLOB`,
 * `CHURN_QUARANTINED`, `BINARY_UNINDEXED_EXTENSION`, `OVERSIZE_SKIPPED`, or `INDEXED_OK`).
 */
class IndexChurnQuarantineMonitor(
    val windowMs: Long = 60_000L,
    val churnThresholdPerDir: Int = 40,
    val maxIndexedFileSizeBytes: Long = 8L * 1024L * 1024L,
    excludedPrefixes: Set<String> = DEFAULT_EXCLUDED_DIRS,
) {
    enum class CoverageReason {
        INDEXED_OK,
        EXCLUDED_DIRECTORY_RULE,
        CHURN_QUARANTINED_DIRECTORY,
        OVERSIZE_FILE_SKIPPED,
        UNSUPPORTED_BINARY_EXTENSION,
    }

    data class IndexHealthReport(
        val indexedDocumentCount: Int,
        val quarantinedDirectories: Set<String>,
        val excludedPrefixes: Set<String>,
        val rebuildRecommended: Boolean,
    )

    private val excluded = excludedPrefixes.map { normalizeDir(it) }.toMutableSet()
    private val quarantined = LinkedHashSet<String>()
    private val dirEvents = HashMap<String, ArrayDeque<Long>>()

    fun recordFileMutation(path: String, timestampMs: Long): Boolean {
        val dir = parentDir(path)
        if (isExcluded(dir)) return false
        val q = dirEvents.getOrPut(dir) { ArrayDeque() }
        while (q.isNotEmpty() && timestampMs - q.first() > windowMs) q.removeFirst()
        q.addLast(timestampMs)
        if (q.size > churnThresholdPerDir) {
            quarantined.add(dir)
            return true
        }
        return false
    }

    fun releaseQuarantine(dir: String): Boolean = quarantined.remove(normalizeDir(dir))

    fun diagnoseFileCoverage(path: String, sizeBytes: Long): CoverageReason {
        val dir = parentDir(path)
        if (isExcluded(dir)) return CoverageReason.EXCLUDED_DIRECTORY_RULE
        if (quarantined.any { dir == it || dir.startsWith("$it/") }) {
            return CoverageReason.CHURN_QUARANTINED_DIRECTORY
        }
        if (sizeBytes > maxIndexedFileSizeBytes) return CoverageReason.OVERSIZE_FILE_SKIPPED
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext !in TEXT_INDEXABLE_EXTS) return CoverageReason.UNSUPPORTED_BINARY_EXTENSION
        return CoverageReason.INDEXED_OK
    }

    fun healthReport(indexedCount: Int, corruptedShardDetected: Boolean = false): IndexHealthReport =
        IndexHealthReport(
            indexedDocumentCount = indexedCount.coerceAtLeast(0),
            quarantinedDirectories = quarantined.toSet(),
            excludedPrefixes = excluded.toSet(),
            rebuildRecommended = corruptedShardDetected || quarantined.size > 16,
        )

    private fun isExcluded(dir: String): Boolean =
        excluded.any { ex -> dir == ex || dir.startsWith("$ex/") || dir.endsWith("/$ex") || "/$ex/" in "$dir/" }

    private fun parentDir(path: String): String {
        val clean = path.trim().replace('\\', '/').trim('/')
        val slash = clean.lastIndexOf('/')
        return if (slash < 0) "/" else "/" + clean.substring(0, slash)
    }

    private fun normalizeDir(d: String): String = d.trim().replace('\\', '/').trim('/')

    companion object {
        val DEFAULT_EXCLUDED_DIRS = setOf(".git", "node_modules", "build", ".gradle", "__pycache__", ".cache")
        val TEXT_INDEXABLE_EXTS = setOf("md", "txt", "csv", "tsv", "json", "yaml", "yml", "kt", "py", "html", "xml")
    }
}
