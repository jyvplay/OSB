// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

/**
 * Bounded pinned app shortcut ledger.
 */
class AppShortcutPinLedger private constructor(private val maxPins: Int) {
    companion object {
        fun create(maxPins: Int = 32): AppShortcutPinLedger {
            require(maxPins in 1..256) { "maxPins out of bounds" }
            return AppShortcutPinLedger(maxPins)
        }
    }

    private val pinned = mutableListOf<String>()

    fun pin(appId: String): Boolean {
        require(appId.isNotEmpty()) { "empty appId" }
        if (appId in pinned) return true
        if (pinned.size >= maxPins) return false
        pinned.add(appId)
        return true
    }

    fun unpin(appId: String): Boolean = pinned.remove(appId)
    fun list(): List<String> = pinned.toList()
}
