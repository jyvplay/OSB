package dev.fusion.archive

/**
 * Clean-room Archive Decompression-Bomb (`42.zip`) & Traversal Pre-Flight Quota Auditor (Apache-2.0).
 *
 * Evaluates archive central-directory / header metadata BEFORE allocating output buffers:
 * enforces per-entry compression ratio (`<= 100.0x`), cumulative uncompressed byte ceiling
 * (`<= 256 MiB`), max entry count (`<= 10_000`), max path depth (`<= 32`), and zero `..` / NUL paths!
 */
object ArchiveBombQuotaInspector {
    const val SCOPE =
        "Pre-flight zip/tar decompression-bomb quota auditor (ratio <= 100:1, cumulative <= 256 MiB, entry count <= 10,000, path depth <= 32)"

    data class EntryHeaderDescriptor(
        val path: String,
        val compressedBytes: Long,
        val uncompressedBytes: Long,
        val isSymlink: Boolean = false,
    )

    data class QuotaLimits(
        val maxCompressionRatio: Double = 100.0,
        val maxTotalUncompressedBytes: Long = 256L * 1024L * 1024L,
        val maxEntries: Int = 10_000,
        val maxPathDepth: Int = 32,
    )

    data class QuotaAuditResult(
        val safe: Boolean,
        val totalUncompressedBytes: Long,
        val worstEntryRatio: Double,
        val violations: List<String>,
    )

    fun inspect(entries: List<EntryHeaderDescriptor>, limits: QuotaLimits = QuotaLimits()): QuotaAuditResult {
        val violations = mutableListOf<String>()
        if (entries.size > limits.maxEntries) {
            violations.add("ENTRY_COUNT_EXCEEDED_${entries.size}")
        }
        var totalUncomp = 0L
        var worstRatio = 1.0
        val seenLowerPaths = HashSet<String>()

        for (e in entries) {
            if (e.isSymlink) violations.add("SYMLINK_ENTRY_FORBIDDEN:${e.path}")
            if ('\u0000' in e.path || e.path.startsWith("/") || e.path.replace('\\', '/').split('/').any { it == ".." }) {
                violations.add("PATH_TRAVERSAL_OR_NUL:${e.path}")
            }
            val depth = e.path.replace('\\', '/').split('/').count { it.isNotEmpty() }
            if (depth > limits.maxPathDepth) violations.add("PATH_DEPTH_EXCEEDED:${e.path}")
            if (!seenLowerPaths.add(e.path.lowercase())) {
                violations.add("CASE_COLLISION_DUPLICATE_ENTRY:${e.path}")
            }
            if (e.uncompressedBytes < 0L || e.compressedBytes < 0L) {
                violations.add("NEGATIVE_SIZE_HEADER:${e.path}")
                continue
            }
            val ratio = if (e.compressedBytes == 0L) {
                if (e.uncompressedBytes == 0L) 1.0 else Double.POSITIVE_INFINITY
            } else {
                e.uncompressedBytes.toDouble() / e.compressedBytes.toDouble()
            }
            if (ratio > worstRatio) worstRatio = ratio
            if (ratio > limits.maxCompressionRatio && e.uncompressedBytes > 65_536L) {
                violations.add("BOMB_RATIO_EXCEEDED:${e.path}:${"%.1f".format(ratio)}x")
            }
            totalUncomp = (totalUncomp + e.uncompressedBytes).coerceAtLeast(totalUncomp)
            if (totalUncomp > limits.maxTotalUncompressedBytes) {
                violations.add("CUMULATIVE_QUOTA_EXCEEDED:$totalUncomp")
                break
            }
        }
        return QuotaAuditResult(violations.isEmpty(), totalUncomp, worstRatio, violations)
    }
}
