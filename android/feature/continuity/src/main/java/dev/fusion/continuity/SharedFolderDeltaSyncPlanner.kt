// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

/**
 * Clean-room Paired-Device Shared Folder Content-Addressed Delta Sync & Non-Destructive Conflict
 * Twin Planner (`CT-05`).
 */
object SharedFolderDeltaSyncPlanner {
    const val SCOPE =
        "Pure-JVM paired-device shared folder SHA-256 manifest delta planner with vector-version concurrent edit detection and non-destructive conflict twin naming (CT-05)."

    data class FileEntryState(
        val relPath: String,
        val sha256Hex: String,
        val baseSha256Hex: String?, // Common ancestor digest at last sync
        val modifiedEpochSec: Long,
        val originDeviceId: String,
    )

    enum class SyncActionKind { NO_OP, PUSH_LOCAL_TO_PEER, PULL_PEER_TO_LOCAL, PRESERVE_BOTH_CONFLICT_TWIN }

    data class SyncOperation(
        val relPath: String,
        val kind: SyncActionKind,
        val conflictTwinRelPath: String? = null,
    )

    fun planDeltaSync(
        localManifest: Map<String, FileEntryState>,
        peerManifest: Map<String, FileEntryState>,
    ): List<SyncOperation> {
        val allPaths = (localManifest.keys + peerManifest.keys).toSortedSet()
        val ops = ArrayList<SyncOperation>()
        for (path in allPaths) {
            require(!path.startsWith("/") && ".." !in path.split('/')) { "unsafe sync path '$path'" }
            val loc = localManifest[path]
            val rem = peerManifest[path]
            when {
                loc != null && rem == null -> ops.add(SyncOperation(path, SyncActionKind.PUSH_LOCAL_TO_PEER))
                loc == null && rem != null -> ops.add(SyncOperation(path, SyncActionKind.PULL_PEER_TO_LOCAL))
                loc != null && rem != null -> {
                    if (loc.sha256Hex == rem.sha256Hex) {
                        ops.add(SyncOperation(path, SyncActionKind.NO_OP))
                    } else if (rem.sha256Hex == loc.baseSha256Hex) {
                        ops.add(SyncOperation(path, SyncActionKind.PUSH_LOCAL_TO_PEER))
                    } else if (loc.sha256Hex == rem.baseSha256Hex) {
                        ops.add(SyncOperation(path, SyncActionKind.PULL_PEER_TO_LOCAL))
                    } else {
                        // True concurrent modification on both peers -> preserve BOTH!
                        val dot = path.lastIndexOf('.')
                        val twin = if (dot > 0) {
                            "${path.substring(0, dot)}.conflict-${rem.originDeviceId}-${rem.modifiedEpochSec}${path.substring(dot)}"
                        } else {
                            "$path.conflict-${rem.originDeviceId}-${rem.modifiedEpochSec}"
                        }
                        ops.add(SyncOperation(path, SyncActionKind.PRESERVE_BOTH_CONFLICT_TWIN, twin))
                    }
                }
            }
        }
        return ops
    }
}
