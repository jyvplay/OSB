// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

/**
 * Bounded vault clipboard auto-clear delay clamper [5_000, 300_000] ms.
 */
class VaultClipboardClearDelay private constructor() {
    companion object {
        const val MIN_MS = 5_000L
        const val MAX_MS = 300_000L

        fun clampMs(delayMs: Long): Long = delayMs.coerceIn(MIN_MS, MAX_MS)
    }
}
