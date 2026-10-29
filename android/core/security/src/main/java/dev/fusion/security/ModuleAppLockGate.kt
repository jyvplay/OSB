// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * Clean-room Per-Module App Lock State Machine (`SC-02`) & Privacy Dashboard Event Ledger (`SC-05`).
 *
 * Enforces:
 * - Independent lock states per sensitive module (`vault`, `messages`, `notes`, `files`, `browser_private`).
 * - Immediate relock on screen-off / USB port tamper panic (`lockAllImmediate`) and configurable idle timeout.
 * - Exponential failed-authentication backoff (`1s -> 2s -> 4s -> ... -> 300s`) after 3 failed attempts.
 * - Bounded 24-hour Privacy Dashboard access ledger recording `(moduleId, capability, timestampMs, allowed)`.
 */
class ModuleAppLockGate(
    val idleTimeoutMs: Long = 120_000L,
    val maxLedgerEntries: Int = 512,
) {
    init {
        require(idleTimeoutMs in 5_000L..3_600_000L) { "idleTimeoutMs out of bounds" }
        require(maxLedgerEntries in 16..4_096) { "maxLedgerEntries out of bounds" }
    }

    data class ModuleLockState(
        val moduleId: String,
        val unlocked: Boolean,
        val unlockedAtMs: Long,
        val lastInteractionMs: Long,
        val consecutiveFailures: Int,
        val lockedOutUntilMs: Long,
    )

    data class PrivacyAccessEvent(
        val timestampMs: Long,
        val moduleId: String,
        val capability: String,
        val granted: Boolean,
        val reason: String,
    )

    private val states = LinkedHashMap<String, ModuleLockState>()
    private val ledger = ArrayDeque<PrivacyAccessEvent>()

    fun isUnlocked(moduleId: String, nowMs: Long): Boolean {
        val st = states[moduleId] ?: return false
        if (!st.unlocked) return false
        if (nowMs - st.lastInteractionMs > idleTimeoutMs) {
            states[moduleId] = st.copy(unlocked = false)
            recordAccess(nowMs, moduleId, "MODULE_SESSION", false, "auto-relocked after idle timeout")
            return false
        }
        return true
    }

    fun touchInteraction(moduleId: String, nowMs: Long) {
        val st = states[moduleId] ?: return
        if (st.unlocked && nowMs - st.lastInteractionMs <= idleTimeoutMs) {
            states[moduleId] = st.copy(lastInteractionMs = nowMs)
        }
    }

    fun attemptUnlock(moduleId: String, credentialValid: Boolean, nowMs: Long): ModuleLockState {
        val prev = states[moduleId] ?: ModuleLockState(moduleId, false, 0L, 0L, 0, 0L)
        if (nowMs < prev.lockedOutUntilMs) {
            recordAccess(nowMs, moduleId, "AUTH_UNLOCK", false, "backoff lockout active until ${prev.lockedOutUntilMs}")
            return prev
        }
        return if (credentialValid) {
            val next = ModuleLockState(moduleId, true, nowMs, nowMs, 0, 0L)
            states[moduleId] = next
            recordAccess(nowMs, moduleId, "AUTH_UNLOCK", true, "authenticated")
            next
        } else {
            val fails = prev.consecutiveFailures + 1
            val penaltyMs = if (fails < 3) 0L else minOf(300_000L, 1_000L shl minOf(9, fails - 3))
            val next = prev.copy(
                unlocked = false,
                consecutiveFailures = fails,
                lockedOutUntilMs = nowMs + penaltyMs,
            )
            states[moduleId] = next
            recordAccess(nowMs, moduleId, "AUTH_UNLOCK", false, "invalid credential (fail #$fails)")
            next
        }
    }

    fun lockAllImmediate(nowMs: Long, triggerReason: String = "screen_off") {
        for ((id, st) in states.entries) {
            if (st.unlocked) {
                states[id] = st.copy(unlocked = false)
                recordAccess(nowMs, id, "IMMEDIATE_LOCK", true, triggerReason)
            }
        }
    }

    fun recordAccess(nowMs: Long, moduleId: String, capability: String, granted: Boolean, reason: String) {
        if (ledger.size >= maxLedgerEntries) ledger.removeFirst()
        ledger.addLast(PrivacyAccessEvent(nowMs, moduleId, capability, granted, reason))
    }

    fun recentDashboardEvents(sinceMs: Long = 0L): List<PrivacyAccessEvent> =
        ledger.filter { it.timestampMs >= sinceMs }
}
