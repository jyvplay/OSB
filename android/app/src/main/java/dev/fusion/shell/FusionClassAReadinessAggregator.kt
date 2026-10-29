package dev.fusion.shell

/**
 * Clean-room Runtime-Honest Class-A Readiness & Testable-Subset Ledger Aggregator (Apache-2.0).
 *
 * Strictly enforces `H-` (Runtime Honesty): never reports full-spec `Class A` acceptance (`0/50`)
 * unless ALL 7 mandatory gates (`hostPureJvm`, `androidSdk37Compile`, `emulatorOrPhysicalDevice`,
 * `talkBackAccessibility`, `surfaceFlingerFlagSecure`, `elf16KbPageInstall`, `permissiveLicenseAudit`)
 * are verified true!
 */
object FusionClassAReadinessAggregator {
    const val SCOPE =
        "Runtime-honest 7-gate Class-A vs 50/50 host-verified testable-subset aggregator enforcing strict 0% inflation rule"

    data class ModuleGateRecord(
        val gradlePath: String,
        val hostPureJvmVerified: Boolean,
        val permissiveLicenseCleanRoom: Boolean,
        val androidSdk37Compiled: Boolean = false,
        val physicalOrEmulatorDeviceTested: Boolean = false,
        val talkBackAxVerified: Boolean = false,
        val surfaceFlingerFlagSecureVerified: Boolean = false,
        val elf16KbPageAlignedApkInstalled: Boolean = false,
    ) {
        val isFullClassAAccepted: Boolean
            get() = hostPureJvmVerified &&
                permissiveLicenseCleanRoom &&
                androidSdk37Compiled &&
                physicalOrEmulatorDeviceTested &&
                talkBackAxVerified &&
                surfaceFlingerFlagSecureVerified &&
                elf16KbPageAlignedApkInstalled
    }

    data class ReadinessSummary(
        val totalModules: Int,
        val fullClassACount: Int,
        val fullClassAPercent: Double,
        val hostTestableSubsetCount: Int,
        val hostTestableSubsetPercent: Double,
        val externalBlockedGates: List<String>,
    )

    fun summarize(records: List<ModuleGateRecord>): ReadinessSummary {
        require(records.isNotEmpty())
        val n = records.size
        val classACount = records.count { it.isFullClassAAccepted }
        val hostCount = records.count { it.hostPureJvmVerified && it.permissiveLicenseCleanRoom }
        val blockers = buildList {
            if (records.any { !it.androidSdk37Compiled }) add("ANDROID_SDK_37_AAPT2_D8_R8")
            if (records.any { !it.physicalOrEmulatorDeviceTested }) add("PHYSICAL_OR_EMULATOR_ANDROID_DEVICE")
            if (records.any { !it.talkBackAxVerified }) add("TALKBACK_ACCESSIBILITY_SERVICE_LIVE")
            if (records.any { !it.surfaceFlingerFlagSecureVerified }) add("SURFACEFLINGER_FLAG_SECURE_HARDWARE")
            if (records.any { !it.elf16KbPageAlignedApkInstalled }) add("ELF_16KB_PAGE_ALIGNED_APK_INSTALL")
        }
        return ReadinessSummary(
            totalModules = n,
            fullClassACount = classACount,
            fullClassAPercent = (classACount * 100.0) / n,
            hostTestableSubsetCount = hostCount,
            hostTestableSubsetPercent = (hostCount * 100.0) / n,
            externalBlockedGates = blockers,
        )
    }
}
