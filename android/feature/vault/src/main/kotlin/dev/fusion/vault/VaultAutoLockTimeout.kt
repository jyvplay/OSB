// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

/**
 * Bounded vault auto-lock timeout evaluator.
 */
class VaultAutoLockTimeout private constructor() {
    companion object {
        data class LockState(val locked: Boolean, val remainingMs: Long)

        fun evaluate(lastActivityMs: Long, nowMs: Long, timeoutMs: Long): LockState {
            require(lastActivityMs >= 0L && nowMs >= lastActivityMs) { "invalid timestamps" }
            require(timeoutMs > 0L) { "timeoutMs must be positive" }
            val elapsed = nowMs - lastActivityMs
            val rem = timeoutMs - elapsed
            return if (rem <= 0L) LockState(true, 0L) else LockState(false, rem)
        }
    }
}
