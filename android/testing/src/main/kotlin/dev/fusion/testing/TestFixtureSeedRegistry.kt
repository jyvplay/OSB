// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

/**
 * Bounded deterministic seed registry for reproducible property tests.
 * Enforces unique test-case IDs and bounded seed values.
 */
class TestFixtureSeedRegistry private constructor() {
    companion object {
        const val MAX_ENTRIES = 2048
        const val MAX_ID_LEN = 128
        private val ID_RE = Regex("[a-zA-Z0-9_.-]{1,128}")

        fun empty(): TestFixtureSeedRegistry = TestFixtureSeedRegistry()
    }

    private val seeds = LinkedHashMap<String, Long>()

    fun register(testId: String, seed: Long) {
        require(testId.length <= MAX_ID_LEN && ID_RE.matches(testId)) { "invalid testId: $testId" }
        require(seeds.size < MAX_ENTRIES || seeds.containsKey(testId)) { "registry full" }
        seeds[testId] = seed
    }

    fun get(testId: String): Long? = seeds[testId]
    fun count(): Int = seeds.size
    fun clear() = seeds.clear()
}
