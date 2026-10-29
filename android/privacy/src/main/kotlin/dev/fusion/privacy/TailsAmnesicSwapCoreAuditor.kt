// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Pure-JVM **Tails-Light Amnesic Swap / Core-Dump / Hibernation Leak Auditor** for `:privacy`,
 * verifying that sensitive Tails-mode sessions enforce `RLIMIT_CORE = 0`, unencrypted-swap refusal
 * (`zram` ephemeral key or zero swap), and RAM-backed tmpfs scratch mounts before unlocking secrets.
 */
object TailsAmnesicSwapCoreAuditor {
    const val SCOPE =
        "Tails-7.x-style amnesic memory posture auditor verifying RLIMIT_CORE=0, zero unencrypted block swap, and ephemeral tmpfs scratch."

    data class HostMemoryPosture(
        val rlimitCoreBytes: Long,
        val activeSwapDevices: List<SwapDeviceEntry>,
        val scratchMountFsType: String, // tmpfs, ramfs, ext4, f2fs
        val kernelKexecDisabled: Boolean,
    )

    data class SwapDeviceEntry(
        val path: String,
        val encryptedWithEphemeralKey: Boolean,
    )

    data class AmnesicAuditVerdict(
        val amnesicCompliant: Boolean,
        val violations: List<String>,
    )

    fun audit(posture: HostMemoryPosture): AmnesicAuditVerdict {
        val violations = ArrayList<String>()
        if (posture.rlimitCoreBytes != 0L) {
            violations.add("CORE_DUMPS_ENABLED_RLIMIT_${posture.rlimitCoreBytes}")
        }
        for (sw in posture.activeSwapDevices) {
            if (!sw.encryptedWithEphemeralKey) {
                violations.add("UNENCRYPTED_PERSISTENT_SWAP_${sw.path}")
            }
        }
        val fs = posture.scratchMountFsType.lowercase()
        if (fs != "tmpfs" && fs != "ramfs") {
            violations.add("NON_VOLATILE_SCRATCH_FS_$fs")
        }
        if (!posture.kernelKexecDisabled) {
            violations.add("KEXEC_COLD_REBOOT_VECTOR_OPEN")
        }
        return AmnesicAuditVerdict(violations.isEmpty(), violations)
    }
}
