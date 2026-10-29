// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

/**
 * Clean-room App Lifecycle, Own-Data Uninstall Assistant (`AP-02`), Pro APK Pre-Install Hygiene
 * Verifier (`AP-03`), Update-Source Inventory (`AP-04`), and Own-Process Startup Ledger (`AP-05`
 * non-Shizuku own-app subset).
 */
object AppInventoryLifecycleAdvisor {
    const val SCOPE =
        "Pure-JVM own-data uninstall storage breakdown (AP-02), APK SDK/16KB/signature pre-install auditor (AP-03), update-channel inventory (AP-04), and own-startup ledger (AP-05 subset)."

    data class OwnDataSlice(val bucket: String, val bytes: Long, val exportableBeforeUninstall: Boolean)

    data class UninstallPlan(
        val totalBytes: Long,
        val exportableBytes: Long,
        val recommendedExportOrder: List<String>,
    )

    data class ApkInstallCandidate(
        val packageName: String,
        val versionCode: Long,
        val targetSdk: Int,
        val nativeLibs16KbAligned: Boolean,
        val signingCertSha256: String,
        val existingSigningCertSha256: String?,
        val existingVersionCode: Long?,
    )

    data class ApkInstallVerdict(
        val permitted: Boolean,
        val warningsAndBlocks: List<String>,
    )

    data class InstalledAppRecord(
        val packageName: String,
        val installerPackage: String?,
        val versionName: String,
    )

    fun buildOwnDataUninstallPlan(slices: List<OwnDataSlice>): UninstallPlan {
        require(slices.all { it.bytes >= 0L }) { "negative byte slice" }
        val exportable = slices.filter { it.exportableBeforeUninstall }.sortedByDescending { it.bytes }
        return UninstallPlan(
            totalBytes = slices.sumOf { it.bytes },
            exportableBytes = exportable.sumOf { it.bytes },
            recommendedExportOrder = exportable.map { it.bucket },
        )
    }

    fun auditApkCandidate(c: ApkInstallCandidate): ApkInstallVerdict {
        val issues = ArrayList<String>()
        var block = false
        if (!c.nativeLibs16KbAligned) {
            issues.add("BLOCK_16KB_ELF_PAGE_MISALIGNED")
            block = true
        }
        if (c.existingSigningCertSha256 != null &&
            !c.existingSigningCertSha256.equals(c.signingCertSha256, ignoreCase = true)
        ) {
            issues.add("BLOCK_SIGNATURE_MISMATCH_CONFLICT")
            block = true
        }
        if (c.existingVersionCode != null && c.versionCode < c.existingVersionCode) {
            issues.add("BLOCK_VERSION_DOWNGRADE")
            block = true
        }
        if (c.targetSdk < 34) {
            issues.add("WARN_LEGACY_TARGET_SDK_${c.targetSdk}")
        }
        return ApkInstallVerdict(!block, issues)
    }

    fun resolveUpdateDeepLink(rec: InstalledAppRecord): String = when (rec.installerPackage) {
        "org.fdroid.fdroid" -> "fdroid.app://details?id=${rec.packageName}"
        "dev.imranr.obtainium" -> "obtainium://app/${rec.packageName}"
        "com.android.vending" -> "market://details?id=${rec.packageName}"
        else -> "package:${rec.packageName}"
    }
}
