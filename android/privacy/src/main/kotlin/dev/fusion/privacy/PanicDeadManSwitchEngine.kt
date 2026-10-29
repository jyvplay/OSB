package dev.fusion.privacy

/**
 * Clean-room Monotonic Dead-Man Switch & Emergency Panic-Trigger Policy Engine (Apache-2.0).
 *
 * Evaluates multi-signal physical & authentication telemetry (monotonic `elapsedRealtimeMs` —
 * immune to wall-clock rollback!, failed unlock count, unauthenticated USB hotplug while locked,
 * duress hardware chord) into deterministic vault/session lock & wipe actions.
 */
object PanicDeadManSwitchEngine {
    const val SCOPE =
        "Monotonic elapsed-time dead-man switch + failed-PIN / locked-USB-hotplug / hardware-panic-chord evaluator"

    enum class PanicAction { NO_ACTION, LOCK_VAULT_ONLY, LOCK_AND_PURGE_VOLATILE_RAM, EMERGENCY_AMNESIC_TEARDOWN }

    data class PolicyConfig(
        val idleLockTimeoutMs: Long = 180_000L,      // 3 min
        val idleRamPurgeTimeoutMs: Long = 900_000L,  // 15 min
        val maxFailedAuthBeforePurge: Int = 5,
        val purgeOnLockedUsbDataHotplug: Boolean = true,
    )

    data class TelemetrySample(
        val lastInteractionRealtimeMs: Long,
        val currentRealtimeMs: Long,
        val consecutiveFailedUnlocks: Int = 0,
        val screenLocked: Boolean = false,
        val newUsbDataPeripheralAttached: Boolean = false,
        val emergencyChordPressed: Boolean = false,
    )

    data class PanicVerdict(
        val action: PanicAction,
        val triggerReasons: List<String>,
    )

    fun evaluate(sample: TelemetrySample, config: PolicyConfig = PolicyConfig()): PanicVerdict {
        val reasons = mutableListOf<String>()
        // Negative elapsed delta means monotonic clock anomaly -> fail closed!
        val idleMs = sample.currentRealtimeMs - sample.lastInteractionRealtimeMs
        if (idleMs < 0L) {
            reasons.add("MONOTONIC_CLOCK_ROLLBACK_ANOMALY")
            return PanicVerdict(PanicAction.EMERGENCY_AMNESIC_TEARDOWN, reasons)
        }
        if (sample.emergencyChordPressed) {
            reasons.add("HARDWARE_EMERGENCY_CHORD")
            return PanicVerdict(PanicAction.EMERGENCY_AMNESIC_TEARDOWN, reasons)
        }
        if (sample.consecutiveFailedUnlocks >= config.maxFailedAuthBeforePurge) {
            reasons.add("FAILED_UNLOCK_THRESHOLD_${sample.consecutiveFailedUnlocks}")
        }
        if (config.purgeOnLockedUsbDataHotplug && sample.screenLocked && sample.newUsbDataPeripheralAttached) {
            reasons.add("LOCKED_USB_DATA_HOTPLUG")
        }
        if (idleMs >= config.idleRamPurgeTimeoutMs) {
            reasons.add("IDLE_RAM_PURGE_TIMEOUT_${idleMs}MS")
        }
        if (reasons.isNotEmpty()) {
            return PanicVerdict(PanicAction.LOCK_AND_PURGE_VOLATILE_RAM, reasons)
        }
        if (idleMs >= config.idleLockTimeoutMs) {
            return PanicVerdict(PanicAction.LOCK_VAULT_ONLY, listOf("IDLE_LOCK_TIMEOUT_${idleMs}MS"))
        }
        return PanicVerdict(PanicAction.NO_ACTION, emptyList())
    }
}
