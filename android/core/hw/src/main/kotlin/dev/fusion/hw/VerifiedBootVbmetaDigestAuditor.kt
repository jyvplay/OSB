// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Hardware Shield `#28` — **Android Verified Boot 2.0 (`AVB0`) `AvbVBMetaImageHeader` Binary
 * Auditor & Anti-Rollback Enforcer**.
 *
 * Parses the 256-byte big-endian `AvbVBMetaImageHeader` (`magic = "AVB0"`, `algorithm_type` at `+28`,
 * `rollback_index` at `+112`, `flags` at `+120`), rejecting boot states with `HASHTREE_DISABLED` (`bit 0`)
 * or `VERIFICATION_DISABLED` (`bit 1`) or rollback downgrades below the hardware fuse floor!
 */
object VerifiedBootVbmetaDigestAuditor {
    const val SCOPE =
        "Hardware Shield #28: Android Verified Boot 2.0 256-byte big-endian AvbVBMetaImageHeader ('AVB0') flags & rollback_index auditor."

    const val AVB_VBMETA_IMAGE_HEADER_SIZE = 256
    const val FLAG_HASHTREE_DISABLED = 1
    const val FLAG_VERIFICATION_DISABLED = 2

    data class VbmetaAuditVerdict(
        val validMagic: Boolean,
        val avbMajorVersion: Int,
        val algorithmType: Int,
        val rollbackIndex: Long,
        val flags: Int,
        val trustedGreenState: Boolean,
        val failureReason: String?,
    )

    fun buildSyntheticVbmetaHeader(
        algorithmType: Int = 2, // AVB_ALGORITHM_TYPE_SHA256_RSA4096
        rollbackIndex: Long = 1730000000L,
        flags: Int = 0,
        releaseString: String = "avbtool 1.3.0",
    ): ByteArray {
        val buf = ByteBuffer.allocate(AVB_VBMETA_IMAGE_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        buf.put("AVB0".toByteArray(Charsets.US_ASCII)) // 0..3
        buf.putInt(1) // 4..7 required_libavb_version_major
        buf.putInt(3) // 8..11 required_libavb_version_minor
        buf.position(28)
        buf.putInt(algorithmType) // 28..31
        buf.position(112)
        buf.putLong(rollbackIndex) // 112..119
        buf.putInt(flags) // 120..123
        buf.position(128)
        val relBytes = releaseString.toByteArray(Charsets.US_ASCII)
        buf.put(relBytes, 0, relBytes.size.coerceAtMost(47))
        return buf.array()
    }

    fun auditVbmetaHeader(headerBytes: ByteArray, hardwareFuseMinRollback: Long): VbmetaAuditVerdict {
        if (headerBytes.size < AVB_VBMETA_IMAGE_HEADER_SIZE) {
            return VbmetaAuditVerdict(false, 0, 0, 0L, 0, false, "Truncated vbmeta header (<256B)")
        }
        val magic = String(headerBytes, 0, 4, Charsets.US_ASCII)
        if (magic != "AVB0") {
            return VbmetaAuditVerdict(false, 0, 0, 0L, 0, false, "Invalid vbmeta magic '$magic'")
        }
        val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN)
        val major = buf.getInt(4)
        val alg = buf.getInt(28)
        val rollback = buf.getLong(112)
        val flags = buf.getInt(120)

        val problem = when {
            (flags and FLAG_VERIFICATION_DISABLED) != 0 -> "VERIFICATION_DISABLED flag set in vbmeta (+120)"
            (flags and FLAG_HASHTREE_DISABLED) != 0 -> "HASHTREE_DISABLED dm-verity bypass flag set in vbmeta (+120)"
            alg == 0 -> "Unsigned AVB_ALGORITHM_TYPE_NONE (0) rejected"
            rollback < hardwareFuseMinRollback -> "Rollback downgrade $rollback < fuse floor $hardwareFuseMinRollback"
            else -> null
        }
        return VbmetaAuditVerdict(true, major, alg, rollback, flags, problem == null, problem)
    }
}
