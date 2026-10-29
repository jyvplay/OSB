// SPDX-License-Identifier: Apache-2.0
package dev.fusion.storage

import java.util.zip.CRC32

/**
 * Pure-JVM **SQLite Write-Ahead Log (`-wal` `0x377f0682` / `0x377f0683`) Frame Checksum &
 * Salt Continuity Verifier** for `:core:database`.
 */
object SqliteWalFrameIntegrityVerifier {
    const val SCOPE =
        "SQLite WAL frame salt continuity, monotonic commit-boundary, and CRC32 frame integrity verifier."

    data class WalFrameRecord(
        val frameIndex: Int,
        val pageNumber: Int,
        val dbSizeAfterCommitPages: Int, // >0 marks commit frame
        val salt1: Int,
        val salt2: Int,
        val pageBytes: ByteArray,
        val recordedCrc32: Long,
    )

    data class WalRecoveryPlan(
        val validCommittedFrames: List<WalFrameRecord>,
        val uncommittedTailDropped: Int,
        val corruptionReason: String?,
    )

    fun computeFrameCrc(pageNumber: Int, salt1: Int, salt2: Int, pageBytes: ByteArray): Long {
        val crc = CRC32()
        crc.update(intBytes(pageNumber))
        crc.update(intBytes(salt1))
        crc.update(intBytes(salt2))
        crc.update(pageBytes)
        return crc.value
    }

    fun verifyLog(expectedSalt1: Int, expectedSalt2: Int, frames: List<WalFrameRecord>): WalRecoveryPlan {
        val validPrefix = ArrayList<WalFrameRecord>()
        var corruption: String? = null
        for (f in frames) {
            if (f.salt1 != expectedSalt1 || f.salt2 != expectedSalt2) {
                corruption = "SALT_MISMATCH_AT_FRAME_${f.frameIndex}"
                break
            }
            val expectedCrc = computeFrameCrc(f.pageNumber, f.salt1, f.salt2, f.pageBytes)
            if (expectedCrc != f.recordedCrc32) {
                corruption = "CRC_MISMATCH_AT_FRAME_${f.frameIndex}"
                break
            }
            validPrefix.add(f)
        }
        val lastCommitIdx = validPrefix.indexOfLast { it.dbSizeAfterCommitPages > 0 }
        if (lastCommitIdx < 0) {
            return WalRecoveryPlan(emptyList(), validPrefix.size, corruption)
        }
        val committed = validPrefix.subList(0, lastCommitIdx + 1).toList()
        return WalRecoveryPlan(committed, validPrefix.size - committed.size, corruption)
    }

    private fun intBytes(v: Int): ByteArray = byteArrayOf(
        ((v ushr 24) and 0xFF).toByte(),
        ((v ushr 16) and 0xFF).toByte(),
        ((v ushr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte(),
    )
}
