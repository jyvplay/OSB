// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

/**
 * Clean-room Cross-Window / Cross-App `ClipData` Drag-and-Drop Scoped URI Grant Ledger (`FM-14`)
 * enforcing least-privilege read/write grant flags, path-traversal refusal, and deterministic
 * revocation upon drop completion or timeout.
 */
object CrossWindowClipUriGrantor {
    const val SCOPE =
        "Pure-JVM cross-window/app drag-drop ClipData URI grant ledger with least-privilege flags, root-confinement validation, and post-drop revocation (FM-14)."

    const val FLAG_GRANT_READ_URI_PERMISSION = 0x00000001
    const val FLAG_GRANT_WRITE_URI_PERMISSION = 0x00000002

    data class DragUriGrant(
        val sessionId: String,
        val sourceWindowId: String,
        val targetPackageOrWindow: String,
        val contentUris: List<String>,
        val modeFlags: Int,
        val expiresAtElapsedMs: Long,
        val revoked: Boolean = false,
    )

    class GrantLedger(private val grantTtlMs: Long = 15_000L) {
        private val active = LinkedHashMap<String, DragUriGrant>()

        fun beginDragGrant(
            sessionId: String,
            sourceWindowId: String,
            targetPackageOrWindow: String,
            contentUris: List<String>,
            allowWrite: Boolean,
            nowElapsedMs: Long,
        ): DragUriGrant {
            require(contentUris.isNotEmpty() && contentUris.size <= 128) { "invalid drag item count" }
            for (u in contentUris) {
                require(u.startsWith("content://dev.fusion.files.documents/")) {
                    "refusing non-Fusion or file:// drag URI '$u'"
                }
                require(!u.contains("..") && !u.contains("%2e%2e", ignoreCase = true)) {
                    "refusing path-traversal drag URI '$u'"
                }
            }
            val flags = FLAG_GRANT_READ_URI_PERMISSION or (if (allowWrite) FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            val g = DragUriGrant(
                sessionId = sessionId,
                sourceWindowId = sourceWindowId,
                targetPackageOrWindow = targetPackageOrWindow,
                contentUris = contentUris,
                modeFlags = flags,
                expiresAtElapsedMs = nowElapsedMs + grantTtlMs,
            )
            active[sessionId] = g
            return g
        }

        fun completeOrSweep(sessionId: String?, nowElapsedMs: Long): Int {
            var revokedCount = 0
            for ((id, g) in active.entries) {
                if (!g.revoked && (id == sessionId || nowElapsedMs >= g.expiresAtElapsedMs)) {
                    active[id] = g.copy(revoked = true)
                    revokedCount++
                }
            }
            return revokedCount
        }

        fun isGrantValid(sessionId: String, uri: String, nowElapsedMs: Long): Boolean {
            val g = active[sessionId] ?: return false
            return !g.revoked && nowElapsedMs < g.expiresAtElapsedMs && uri in g.contentUris
        }
    }
}
