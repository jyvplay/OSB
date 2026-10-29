// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Clean-room Tails-Mod Lightweight Amnesic Mode cadence, 60-second hard-deadline shutdown wipe,
 * and RAM-only low-footprint posture engine.
 *
 * Grounded in verified `2026-10-26` Tails 7.x architecture (`Tails 7.14` 60-second graceful
 * shutdown hard deadline + `Tails 7.15.1` `2026-10-23` emergency security release + 2026
 * Firefox-synchronized 14-day release cadence):
 * 1. [auditCadence]: enforces emergency version revocation (e.g., pre-`7.15.1` builds vulnerable
 *    to `CVE-2026-113470`), detects clock-rollback attacks, and flags update-channel freeze
 *    attacks when a biweekly (`14d`) amnesic image ages past `28` days.
 * 2. [planShutdown]: state machine enforcing immediate zero-wait wipe on emergency triggers
 *    (`USB_YANKED`, `PANIC_CHORD`) and a strict `60_000 ms` graceful ceiling before forced
 *    secret-slot zeroization + poweroff when user-space services hang.
 * 3. [auditFootprint]: verifies lightweight RAM-only amnesic invariants (`swapBytes == 0`,
 *    `coreDumpsDisabled`, `suidDumpable == 0`, `persistentRwMounts == 0`, `rssMiB <= maxRssMiB`).
 */
object TailsCadence {
    const val SCOPE =
        "Tails 7.x 14-day release-cadence + emergency-revocation gate, 60s hard-deadline shutdown wipe state machine, and RAM-only low-footprint posture check; kernel kexec/poison-page execution lives in OS integration."

    const val EXPECTED_CADENCE_DAYS = 14L
    const val UPDATE_DUE_DAYS = 16L
    const val FREEZE_ATTACK_DAYS = 28L
    const val SHUTDOWN_HARD_DEADLINE_MS = 60_000L
    const val DEFAULT_LIGHTWEIGHT_RSS_MIB = 256

    enum class CadenceStatus {
        CURRENT,
        UPDATE_DUE,
        STALE_FREEZE_RISK,
        EMERGENCY_REVOKED,
        CLOCK_ROLLBACK_ANOMALY,
    }

    enum class ShutdownTrigger { GRACEFUL_EXIT, PANIC_BUTTON, USB_MEDIA_REMOVED }

    enum class ShutdownAction {
        AWAIT_GRACEFUL_SERVICES,
        COMPLETE_CLEAN_WIPE_POWEROFF,
        DEADLINE_FORCED_WIPE_POWEROFF,
        IMMEDIATE_EMERGENCY_WIPE_POWEROFF,
    }

    data class CadenceReport(
        val status: CadenceStatus,
        val ageDays: Long,
        val blockNetworkUntilUpdated: Boolean,
    )

    data class ShutdownDecision(
        val action: ShutdownAction,
        val remainingBudgetMs: Long,
        val orderedSteps: List<String>,
    )

    data class FootprintSignals(
        val swapTotalBytes: Long,
        val coreDumpsDisabled: Boolean,
        val suidDumpable: Int,
        val persistentReadWriteMounts: Int,
        val residentMemoryMiB: Int,
        val maxResidentBudgetMiB: Int = DEFAULT_LIGHTWEIGHT_RSS_MIB,
    ) {
        init {
            require(swapTotalBytes >= 0L) { "swapTotalBytes must be >= 0" }
            require(suidDumpable in 0..2) { "fs.suid_dumpable $suidDumpable outside 0..2" }
            require(persistentReadWriteMounts >= 0) { "persistentReadWriteMounts must be >= 0" }
            require(residentMemoryMiB >= 0 && maxResidentBudgetMiB > 0) { "invalid memory budget" }
        }
    }

    data class FootprintReport(
        val amnesicCompliant: Boolean,
        val lightweightCompliant: Boolean,
        val violations: List<String>,
    )

    fun auditCadence(
        installedVersion: String,
        releaseEpochDay: Long,
        currentEpochDay: Long,
        emergencyRevokedVersions: Set<String> = emptySet(),
    ): CadenceReport {
        val ver = installedVersion.trim()
        require(ver.isNotEmpty()) { "installedVersion must not be blank" }
        require(releaseEpochDay > 0L && currentEpochDay > 0L) { "epoch days must be > 0" }

        if (ver in emergencyRevokedVersions) {
            return CadenceReport(CadenceStatus.EMERGENCY_REVOKED, currentEpochDay - releaseEpochDay, true)
        }
        val age = currentEpochDay - releaseEpochDay
        if (age < 0L) {
            return CadenceReport(CadenceStatus.CLOCK_ROLLBACK_ANOMALY, age, true)
        }
        return when {
            age <= UPDATE_DUE_DAYS -> CadenceReport(CadenceStatus.CURRENT, age, false)
            age <= FREEZE_ATTACK_DAYS -> CadenceReport(CadenceStatus.UPDATE_DUE, age, false)
            else -> CadenceReport(CadenceStatus.STALE_FREEZE_RISK, age, true)
        }
    }

    fun planShutdown(
        trigger: ShutdownTrigger,
        elapsedMs: Long,
        gracefulServicesStopped: Boolean,
    ): ShutdownDecision {
        require(elapsedMs >= 0L) { "elapsedMs $elapsedMs must be >= 0" }
        val wipeSteps = listOf(
            "drop_net_interfaces_and_tor_circuits",
            "zeroize_vault_and_session_key_slots",
            "unmount_ephemeral_tmpfs",
            "sync_and_poweroff_memwipe",
        )
        if (trigger == ShutdownTrigger.PANIC_BUTTON || trigger == ShutdownTrigger.USB_MEDIA_REMOVED) {
            return ShutdownDecision(
                action = ShutdownAction.IMMEDIATE_EMERGENCY_WIPE_POWEROFF,
                remainingBudgetMs = 0L,
                orderedSteps = wipeSteps,
            )
        }
        if (gracefulServicesStopped) {
            return ShutdownDecision(
                action = ShutdownAction.COMPLETE_CLEAN_WIPE_POWEROFF,
                remainingBudgetMs = (SHUTDOWN_HARD_DEADLINE_MS - elapsedMs).coerceAtLeast(0L),
                orderedSteps = wipeSteps,
            )
        }
        return if (elapsedMs >= SHUTDOWN_HARD_DEADLINE_MS) {
            ShutdownDecision(
                action = ShutdownAction.DEADLINE_FORCED_WIPE_POWEROFF,
                remainingBudgetMs = 0L,
                orderedSteps = listOf("sigkill_hung_services_at_60s_deadline") + wipeSteps,
            )
        } else {
            ShutdownDecision(
                action = ShutdownAction.AWAIT_GRACEFUL_SERVICES,
                remainingBudgetMs = SHUTDOWN_HARD_DEADLINE_MS - elapsedMs,
                orderedSteps = listOf("await_service_drain"),
            )
        }
    }

    fun auditFootprint(signals: FootprintSignals): FootprintReport {
        val violations = ArrayList<String>()
        if (signals.swapTotalBytes != 0L) {
            violations.add("swap_active:${signals.swapTotalBytes}")
        }
        if (!signals.coreDumpsDisabled) {
            violations.add("core_dumps_enabled")
        }
        if (signals.suidDumpable != 0) {
            violations.add("fs_suid_dumpable:${signals.suidDumpable}")
        }
        if (signals.persistentReadWriteMounts > 0) {
            violations.add("persistent_rw_mounts:${signals.persistentReadWriteMounts}")
        }
        val amnesicOk = violations.isEmpty()
        val lightOk = signals.residentMemoryMiB <= signals.maxResidentBudgetMiB
        if (!lightOk) {
            violations.add("rss_over_budget:${signals.residentMemoryMiB}>${signals.maxResidentBudgetMiB}MiB")
        }
        return FootprintReport(amnesicOk, lightOk, violations)
    }
}
