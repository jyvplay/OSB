// SPDX-License-Identifier: Apache-2.0
package dev.fusion.backup

import java.security.MessageDigest

/**
 * Clean-room Monthly Backup Health & Sampled Restore Drill Scheduler (`BK-05`), Two-Way Folder-Sync
 * Conflict-Inbox Planner (`BK-02`), and New-Device Migration Checklist (`BK-04`).
 */
object BackupHealthDrillScheduler {
    const val MONTHLY_DRILL_INTERVAL_MS = 30L * 24L * 3_600_000L

    enum class HealthGrade { HEALTHY_VERIFIED, DRILL_DUE_SOON, DRILL_OVERDUE_OR_MISSING, CORRUPT_ARCHIVE_DETECTED }

    data class HealthAssessment(
        val grade: HealthGrade,
        val daysSinceLastVerifiedRestore: Long?,
        val sampledChunksVerified: Int,
        val summary: String,
    )

    fun evaluateHealth(
        nowEpochMs: Long,
        lastExportEpochMs: Long?,
        lastVerifiedDrillEpochMs: Long?,
        sampledChunkExpectedHashes: Map<Int, String> = emptyMap(),
        sampledChunkBytes: Map<Int, ByteArray> = emptyMap(),
    ): HealthAssessment {
        for ((idx, expectedHex) in sampledChunkExpectedHashes) {
            val bytes = sampledChunkBytes[idx]
                ?: return HealthAssessment(HealthGrade.CORRUPT_ARCHIVE_DETECTED, null, 0, "Missing sampled chunk #$idx during restore drill.")
            val actualHex = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            if (!actualHex.equals(expectedHex, ignoreCase = true)) {
                return HealthAssessment(HealthGrade.CORRUPT_ARCHIVE_DETECTED, null, 0, "Chunk #$idx SHA-256 mismatch during restore drill!")
            }
        }
        val verifiedCount = sampledChunkExpectedHashes.size
        if (lastExportEpochMs == null || lastVerifiedDrillEpochMs == null) {
            return HealthAssessment(HealthGrade.DRILL_OVERDUE_OR_MISSING, null, verifiedCount, "No verified restore drill recorded yet; run a sampled password check.")
        }
        val ageMs = (nowEpochMs - lastVerifiedDrillEpochMs).coerceAtLeast(0L)
        val days = ageMs / (24L * 3_600_000L)
        return when {
            ageMs > MONTHLY_DRILL_INTERVAL_MS ->
                HealthAssessment(HealthGrade.DRILL_OVERDUE_OR_MISSING, days, verifiedCount, "Last restore drill was ${days}d ago (>30d); monthly drill due.")
            ageMs > (MONTHLY_DRILL_INTERVAL_MS * 3) / 4 ->
                HealthAssessment(HealthGrade.DRILL_DUE_SOON, days, verifiedCount, "Backup verified ${days}d ago; next monthly drill due within 7 days.")
            else ->
                HealthAssessment(HealthGrade.HEALTHY_VERIFIED, days, verifiedCount, "Backup healthy • verified ${days}d ago ($verifiedCount sampled chunks OK).")
        }
    }

    enum class SyncAction { NO_OP, COPY_LOCAL_TO_REMOTE, COPY_REMOTE_TO_LOCAL, QUARANTINE_TO_CONFLICT_INBOX }

    /** Two-way sync reconciliation (`BK-02`): concurrent divergent edits NEVER overwrite — both go to Conflict Inbox! */
    fun reconcileSyncItem(
        baseHash: String?,
        localHash: String?,
        remoteHash: String?,
    ): SyncAction = when {
        localHash == remoteHash -> SyncAction.NO_OP
        localHash == baseHash && remoteHash != null -> SyncAction.COPY_REMOTE_TO_LOCAL
        remoteHash == baseHash && localHash != null -> SyncAction.COPY_LOCAL_TO_REMOTE
        else -> SyncAction.QUARANTINE_TO_CONFLICT_INBOX
    }

    data class MigrationCheckItem(val id: String, val label: String, val completed: Boolean)

    fun buildNewDeviceChecklist(
        encryptedNotesExported: Boolean,
        configBundleExported: Boolean,
        userHoldsRecoverySharesOffline: Boolean,
        restoreDrillPassedOnTarget: Boolean,
    ): List<MigrationCheckItem> = listOf(
        MigrationCheckItem("export_notes", "Export AES-256-GCM notes archive", encryptedNotesExported),
        MigrationCheckItem("export_config", "Export SHA-256 signed workspace & shortcut config bundle", configBundleExported),
        MigrationCheckItem("verify_recovery_key", "Confirm user-held passphrase / SLIP-39 shares (no cloud escrow)", userHoldsRecoverySharesOffline),
        MigrationCheckItem("target_drill", "Verify non-destructive restore on target device", restoreDrillPassedOnTarget),
    )
}
