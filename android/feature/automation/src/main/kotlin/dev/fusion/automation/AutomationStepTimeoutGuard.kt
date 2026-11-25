// SPDX-License-Identifier: Apache-2.0
package dev.fusion.automation

/**
 * Bounded automation step timeout clamper [100, 60_000] ms.
 */
class AutomationStepTimeoutGuard private constructor() {
    companion object {
        const val MIN_MS = 100L
        const val MAX_MS = 60_000L

        fun clampMs(timeoutMs: Long): Long = timeoutMs.coerceIn(MIN_MS, MAX_MS)
    }
}
