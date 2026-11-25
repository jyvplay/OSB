// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

/**
 * Production-grade POSIX PAX extended header parser & safe extraction planner.
 * Parses `length key=value\n` PAX records, applies path/size overrides,
 * and enforces path-traversal, symlink/hardlink, and total-quota bounds.
 */
class TarPaxExtractorPlanner private constructor() {
    companion object {
        const val MAX_PAX_BYTES = 65_536
        const val MAX_ENTRIES = 10_000

        data class TarEntryInput(
            val rawPath: String,
            val sizeBytes: Long,
            val typeFlag: Char,
            val paxRecords: Map<String, String> = emptyMap()
        )

        data class PlannedEntry(
            val safePath: String,
            val sizeBytes: Long
        )

        fun parsePaxRecords(paxText: String): Map<String, String> {
            require(paxText.length <= MAX_PAX_BYTES) { "PAX header too large" }
            val out = LinkedHashMap<String, String>()
            var pos = 0
            while (pos < paxText.length) {
                val space = paxText.indexOf(' ', pos)
                require(space > pos) { "malformed PAX record length at $pos" }
                val recLen = paxText.substring(pos, space).toIntOrNull()
                    ?: throw IllegalArgumentException("invalid PAX length")
                require(recLen > 0 && pos + recLen <= paxText.length) { "PAX record out of bounds" }
                val record = paxText.substring(space + 1, pos + recLen)
                require(record.endsWith('\n')) { "PAX record missing trailing newline" }
                val body = record.substring(0, record.length - 1)
                val eq = body.indexOf('=')
                require(eq > 0) { "PAX record missing '='" }
                out[body.substring(0, eq)] = body.substring(eq + 1)
                pos += recLen
            }
            return out
        }

        fun planExtraction(entries: List<TarEntryInput>, maxTotalBytes: Long): List<PlannedEntry> {
            require(entries.size <= MAX_ENTRIES) { "too many entries" }
            require(maxTotalBytes >= 0L) { "negative quota" }
            val plan = mutableListOf<PlannedEntry>()
            var total = 0L
            for (e in entries) {
                if (e.typeFlag == '2' || e.typeFlag == '1') {
                    throw IllegalArgumentException("symlinks/hardlinks refused: ${e.rawPath}")
                }
                val resolvedPath = e.paxRecords["path"] ?: e.rawPath
                val resolvedSize = e.paxRecords["size"]?.toLongOrNull() ?: e.sizeBytes
                require(resolvedSize >= 0L) { "negative entry size" }
                val safe = ArchiveEntryPathNormalizer.normalize(resolvedPath)
                total += resolvedSize
                require(total <= maxTotalBytes) { "archive exceeds quota: $total > $maxTotalBytes" }
                plan.add(PlannedEntry(safe, resolvedSize))
            }
            return plan
        }
    }
}
