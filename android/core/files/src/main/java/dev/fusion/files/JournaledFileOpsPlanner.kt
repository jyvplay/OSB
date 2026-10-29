// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

import java.security.MessageDigest

/**
 * Clean-room Crash-Recoverable Write-Ahead File Operations Journal (`FM-03`), New-File Template
 * Catalog (`FM-13`), Cross-Window `ClipData` URI Grant Scoper (`FM-14`), and Cached Folder-Size
 * Rollup (`FM-17`).
 */
object JournaledFileOpsPlanner {
    enum class OpKind { COPY, MOVE, RENAME }
    enum class ConflictPolicy { FAIL_CLOSED, KEEP_BOTH_SUFFIX, SKIP_EXISTING }
    enum class EntryState { PLANNED, IN_PROGRESS, VERIFIED_COMMITTED, SKIPPED_CONFLICT, ROLLED_BACK }

    data class JournalEntry(
        val opId: String,
        val kind: OpKind,
        val sourcePath: String,
        val targetPath: String,
        val resolvedTargetPath: String,
        val expectedSha256Hex: String,
        val state: EntryState,
    )

    fun planBatch(
        ops: List<Triple<OpKind, String, String>>,
        sourceHashesHex: Map<String, String>,
        existingTargets: Set<String>,
        conflictPolicy: ConflictPolicy = ConflictPolicy.KEEP_BOTH_SUFFIX,
    ): List<JournalEntry> {
        val claimedTargets = existingTargets.toMutableSet()
        return ops.mapIndexed { idx, (kind, src, dst) ->
            require(!src.contains("..") && !dst.contains("..")) { "path traversal '..' refused in file journal" }
            val sha = sourceHashesHex[src] ?: throw IllegalArgumentException("missing pre-flight SHA-256 for '$src'")
            val resolved = if (dst !in claimedTargets) {
                claimedTargets.add(dst)
                dst
            } else {
                when (conflictPolicy) {
                    ConflictPolicy.FAIL_CLOSED -> throw IllegalStateException("target conflict on '$dst'")
                    ConflictPolicy.SKIP_EXISTING -> dst
                    ConflictPolicy.KEEP_BOTH_SUFFIX -> disambiguatePath(dst, claimedTargets)
                }
            }
            val initState = if (dst in existingTargets && conflictPolicy == ConflictPolicy.SKIP_EXISTING) {
                EntryState.SKIPPED_CONFLICT
            } else {
                EntryState.PLANNED
            }
            JournalEntry("op-${idx + 1}", kind, src, dst, resolved, sha.lowercase(), initState)
        }
    }

    fun verifyDestinationAndCommit(entry: JournalEntry, writtenBytes: ByteArray): JournalEntry {
        val actualSha = MessageDigest.getInstance("SHA-256")
            .digest(writtenBytes).joinToString("") { "%02x".format(it) }
        return if (actualSha == entry.expectedSha256Hex) {
            entry.copy(state = EntryState.VERIFIED_COMMITTED)
        } else {
            entry.copy(state = EntryState.ROLLED_BACK)
        }
    }

    fun buildUndoOp(entry: JournalEntry): Triple<OpKind, String, String>? {
        if (entry.state != EntryState.VERIFIED_COMMITTED) return null
        return when (entry.kind) {
            OpKind.MOVE -> Triple(OpKind.MOVE, entry.resolvedTargetPath, entry.sourcePath)
            OpKind.RENAME -> Triple(OpKind.RENAME, entry.resolvedTargetPath, entry.sourcePath)
            OpKind.COPY -> null // Reversing copy requires explicit user Trash consent, never silent delete
        }
    }

    val NEW_FILE_TEMPLATES: Map<String, Pair<String, String>> = mapOf(
        "markdown_note" to ("Untitled.md" to "# Untitled\n\n"),
        "plain_text" to ("Untitled.txt" to ""),
        "csv_sheet" to ("Untitled.csv" to "ColumnA,ColumnB,ColumnC\n"),
        "shell_script" to ("script.sh" to "#!/usr/bin/env sh\nset -eu\n"),
    )

    fun computeFolderSizes(fileSizesByRelativePath: Map<String, Long>): Map<String, Long> {
        val totals = LinkedHashMap<String, Long>()
        totals["/"] = 0L
        for ((rawPath, bytes) in fileSizesByRelativePath) {
            require(bytes >= 0L) { "negative file size for '$rawPath'" }
            val parts = rawPath.trim('/').split('/').filter { it.isNotEmpty() }
            totals["/"] = (totals["/"] ?: 0L) + bytes
            var prefix = ""
            for (i in 0 until parts.size - 1) {
                prefix += "/${parts[i]}"
                totals[prefix] = (totals[prefix] ?: 0L) + bytes
            }
        }
        return totals
    }

    /** Enforces read-only temporary URI grant flags (`FLAG_GRANT_READ_URI_PERMISSION = 1`) on cross-window drops (`FM-14`). */
    fun sanitizeCrossWindowDropGrantFlags(requestedFlags: Int, allowWriteGrant: Boolean = false): Int {
        val readBit = 0x01
        val writeBit = 0x02
        val allowedMask = if (allowWriteGrant) (readBit or writeBit) else readBit
        // Never allow persistable (0x40) or prefix (0x80) escalations via drag-and-drop!
        return (requestedFlags and allowedMask) or readBit
    }

    private fun disambiguatePath(dst: String, claimed: MutableSet<String>): String {
        val slash = dst.lastIndexOf('/')
        val dir = if (slash >= 0) dst.substring(0, slash + 1) else ""
        val name = if (slash >= 0) dst.substring(slash + 1) else dst
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (true) {
            val cand = "$dir$stem ($n)$ext"
            if (cand !in claimed) {
                claimed.add(cand)
                return cand
            }
            n++
        }
    }
}
