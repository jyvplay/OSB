// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settingsui

/** Named key scope: exact keys plus dot-namespaced prefixes. */
data class PatchScope(val name: String, val prefixes: List<String>, val exact: Set<String>)

/**
 * Scope gate for [SettingsPatch]: splits a planned patch into the deltas a
 * named scope permits and the keys it refuses. A prefix permits its exact
 * key and everything under `prefix.`; [SettingsPatch]'s own key grammar is
 * re-checked so a scope can never widen the key shape. Scopes compose by
 * union at the caller — this kernel decides one scope, never merges them.
 */
object PatchScopes {
    const val MAX_PREFIXES = 32
    const val MAX_EXACT = 128
    private val NAME = Regex("[a-z][a-z0-9]{0,31}")
    private val KEY = Regex("[a-z][a-z0-9_.-]{0,63}")

    fun define(name: String, prefixes: List<String>, exact: Set<String>): PatchScope {
        require(NAME.matches(name))
        require(prefixes.size <= MAX_PREFIXES && exact.size <= MAX_EXACT)
        require(prefixes.all { KEY.matches(it) } && exact.all { KEY.matches(it) })
        require(prefixes.toSet().size == prefixes.size)
        return PatchScope(name, prefixes.toList(), exact.toSet())
    }

    fun permits(scope: PatchScope, key: String): Boolean {
        require(KEY.matches(key))
        if (key in scope.exact) return true
        return scope.prefixes.any { key == it || key.startsWith("$it.") }
    }

    fun split(patch: SettingsPatch, scope: PatchScope): Pair<List<PreferenceDelta>, List<String>> {
        val allowed = ArrayList<PreferenceDelta>()
        val refused = ArrayList<String>()
        for (d in patch.preview()) {
            if (permits(scope, d.key)) allowed.add(d) else refused.add(d.key)
        }
        return allowed.toList() to refused.toList()
    }
}
