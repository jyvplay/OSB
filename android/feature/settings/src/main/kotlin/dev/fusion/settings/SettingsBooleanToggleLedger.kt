// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settings

/**
 * Bounded boolean setting toggle ledger with default fallback & dirty check.
 */
class SettingsBooleanToggleLedger private constructor() {
    companion object {
        fun empty(): SettingsBooleanToggleLedger = SettingsBooleanToggleLedger()
    }

    private val defaults = HashMap<String, Boolean>()
    private val current = HashMap<String, Boolean>()

    fun define(key: String, defaultVal: Boolean) {
        require(key.isNotEmpty()) { "empty key" }
        defaults[key] = defaultVal
        current.putIfAbsent(key, defaultVal)
    }

    fun toggle(key: String): Boolean {
        val cur = current[key] ?: throw IllegalArgumentException("unknown key: $key")
        val next = !cur
        current[key] = next
        return next
    }

    fun isDirty(key: String): Boolean = current[key] != defaults[key]
}
