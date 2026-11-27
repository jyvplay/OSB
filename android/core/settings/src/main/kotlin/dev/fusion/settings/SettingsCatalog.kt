// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settings

/**
 * Bounded settings key/value catalog. Keys follow a dotted namespace grammar
 * (lowercase segments separated by dots); values are plain strings bounded in
 * length. The catalog supports get/put/remove/contains with explicit capacity
 * and per-key/per-value limits. No type conversion, no defaults, no change
 * notification — the storage layer applies persistence and notifications.
 *
 * Declared narrowings: at most [MAX_ENTRIES] entries; keys at most
 * [MAX_KEY_LEN] chars matching [KEY_RE]; values at most [MAX_VALUE_LEN] chars;
 * no empty keys; no embedded NUL in keys or values.
 */
class SettingsValueCatalog private constructor(
    private val entries: LinkedHashMap<String, String>
) {
    companion object {
        const val MAX_ENTRIES = 4096
        const val MAX_KEY_LEN = 256
        const val MAX_VALUE_LEN = 4096
        private val KEY_RE = Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*")

        fun empty(): SettingsValueCatalog = SettingsValueCatalog(LinkedHashMap())
    }

    fun put(key: String, value: String) {
        require(KEY_RE.matches(key)) { "invalid settings key: $key" }
        require(key.length <= MAX_KEY_LEN) { "key exceeds $MAX_KEY_LEN chars" }
        require('\u0000' !in value) { "NUL in value" }
        require(value.length <= MAX_VALUE_LEN) { "value exceeds $MAX_VALUE_LEN chars" }
        if (!entries.containsKey(key)) require(entries.size < MAX_ENTRIES) { "catalog full: $MAX_ENTRIES entries" }
        entries[key] = value
    }

    fun get(key: String): String? = entries[key]
    fun contains(key: String): Boolean = entries.containsKey(key)
    fun remove(key: String): Boolean = entries.remove(key) != null
    fun keys(): Set<String> = entries.keys.toSet()
    fun size(): Int = entries.size
    fun clear() = entries.clear()
}
