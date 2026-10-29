// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

/**
 * Clean-room Read-Only Virtual `DocumentsProvider` Tree & Remote Mount Reconnect Planner (`FM-08`, `FM-10`).
 *
 * Maps archive / SMB / SFTP / WebDAV virtual paths into canonical `documentId = "<mountId>:/clean/path"`
 * identifiers, refuses `..` / NUL / backslash path-traversal probes, synthesizes intermediate virtual
 * directories, and computes jitter-free exponential reconnect backoff with human diagnostic hints.
 */
class ArchiveDocumentsProviderTree(
    val mountId: String,
    entries: List<VirtualNode>,
) {
    data class VirtualNode(
        val relativePath: String,
        val isDirectory: Boolean,
        val sizeBytes: Long,
        val mimeType: String = "application/octet-stream",
    )

    data class ProviderDocRow(
        val documentId: String,
        val displayName: String,
        val isDirectory: Boolean,
        val sizeBytes: Long,
        val mimeType: String,
    )

    private val byDocId = LinkedHashMap<String, ProviderDocRow>()
    private val childrenByParentId = LinkedHashMap<String, MutableList<String>>()

    val rootDocumentId: String = "$mountId:/"

    init {
        require(mountId.matches(Regex("[a-z0-9_-]{2,32}"))) { "invalid mountId '$mountId'" }
        byDocId[rootDocumentId] = ProviderDocRow(rootDocumentId, mountId, true, 0L, "vnd.android.document/directory")
        childrenByParentId[rootDocumentId] = ArrayList()
        for (e in entries) insertEntry(e)
    }

    private fun normalizeRelPath(raw: String): List<String> {
        require('\u0000' !in raw && '\\' !in raw) { "NUL or backslash forbidden in virtual path '$raw'" }
        val segs = raw.trim('/').split('/').filter { it.isNotEmpty() }
        require(segs.none { it == "." || it == ".." }) { "path traversal segment forbidden in '$raw'" }
        return segs
    }

    private fun insertEntry(node: VirtualNode) {
        val segs = normalizeRelPath(node.relativePath)
        if (segs.isEmpty()) return
        var parentId = rootDocumentId
        var accum = ""
        for (i in 0 until segs.size - 1) {
            accum += "/${segs[i]}"
            val dirId = "$mountId:$accum"
            if (dirId !in byDocId) {
                byDocId[dirId] = ProviderDocRow(dirId, segs[i], true, 0L, "vnd.android.document/directory")
                childrenByParentId.getOrPut(parentId) { ArrayList() }.add(dirId)
                childrenByParentId[dirId] = ArrayList()
            }
            parentId = dirId
        }
        val leafPath = "$accum/${segs.last()}"
        val leafId = "$mountId:$leafPath"
        if (leafId !in byDocId) {
            val mime = if (node.isDirectory) "vnd.android.document/directory" else node.mimeType
            byDocId[leafId] = ProviderDocRow(leafId, segs.last(), node.isDirectory, node.sizeBytes.coerceAtLeast(0L), mime)
            childrenByParentId.getOrPut(parentId) { ArrayList() }.add(leafId)
            if (node.isDirectory) childrenByParentId.putIfAbsent(leafId, ArrayList())
        }
    }

    fun queryDocument(documentId: String): ProviderDocRow =
        byDocId[documentId] ?: throw IllegalArgumentException("unknown documentId '$documentId'")

    fun queryChildDocuments(parentDocumentId: String): List<ProviderDocRow> {
        val ids = childrenByParentId[parentDocumentId]
            ?: throw IllegalArgumentException("unknown or non-directory documentId '$parentDocumentId'")
        return ids.map { byDocId.getValue(it) }
            .sortedWith(compareByDescending<ProviderDocRow> { it.isDirectory }.thenBy { it.displayName.lowercase() })
    }

    companion object {
        fun planReconnectBackoffMs(consecutiveFailures: Int, lastErrorCode: String): Pair<Long, String> {
            val clamped = consecutiveFailures.coerceIn(0, 8)
            val delayMs = minOf(60_000L, 500L shl clamped)
            val hint = when (lastErrorCode.uppercase()) {
                "AUTH_FAILED", "SSH_HOST_KEY_MISMATCH" -> "Permanent security/credential failure ($lastErrorCode): auto-reconnect halted until user re-verifies."
                "NETWORK_UNREACHABLE" -> "Interface offline; waiting for validated network callback before retry."
                else -> "Transient mount disconnect ($lastErrorCode); retry scheduled in ${delayMs}ms."
            }
            return delayMs to hint
        }
    }
}
