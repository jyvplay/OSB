// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settings

/**
 * Bounded 3-tier settings override layer: userOverride > policyDefault > systemDefault.
 */
class SettingsOverrideLayer private constructor() {
    companion object {
        const val MAX_KEYS = 1024
        const val MAX_KEY_LEN = 128
        const val MAX_VAL_LEN = 1024
        private val KEY_RE = Regex("[a-z][a-z0-9_.-]{0,127}")

        fun empty(): SettingsOverrideLayer = SettingsOverrideLayer()
    }

    private val systemDefaults = LinkedHashMap<String, String>()
    private val policyDefaults = LinkedHashMap<String, String>()
    private val userOverrides = LinkedHashMap<String, String>()

    private fun validateKV(key: String, value: String) {
        require(key.length <= MAX_KEY_LEN && KEY_RE.matches(key)) { "invalid key: $key" }
        require(value.length <= MAX_VAL_LEN) { "value too long" }
    }

    fun setSystemDefault(key: String, value: String) {
        validateKV(key, value)
        require(systemDefaults.size < MAX_KEYS || systemDefaults.containsKey(key)) { "too many keys" }
        systemDefaults[key] = value
    }

    fun setPolicyDefault(key: String, value: String) {
        validateKV(key, value)
        require(policyDefaults.size < MAX_KEYS || policyDefaults.containsKey(key)) { "too many keys" }
        policyDefaults[key] = value
    }

    fun setUserOverride(key: String, value: String) {
        validateKV(key, value)
        require(userOverrides.size < MAX_KEYS || userOverrides.containsKey(key)) { "too many keys" }
        userOverrides[key] = value
    }

    fun clearUserOverride(key: String): Boolean = userOverrides.remove(key) != null

    fun resolve(key: String): String? =
        userOverrides[key] ?: policyDefaults[key] ?: systemDefaults[key]
}
