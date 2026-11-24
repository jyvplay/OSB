// SPDX-License-Identifier: Apache-2.0
package dev.fusion.launcher

/**
 * Bounded app shortcut resolver. Resolves shortcut names to target app IDs
 * through a chain of shortcut mappings, with cycle detection and depth
 * limits. Does not launch apps — the launcher handles that.
 *
 * Declared narrowings: at most [MAX_SHORTCUTS] shortcuts; shortcut names at
 * most [MAX_NAME_LEN] chars matching [NAME_RE]; target IDs at most
 * [MAX_ID_LEN] chars; resolution depth at most [MAX_DEPTH]; self-loops and
 * cycles are refused.
 */
class AppShortcutResolver private constructor(
    private val shortcuts: LinkedHashMap<String, String>
) {
    companion object {
        const val MAX_SHORTCUTS = 512
        const val MAX_NAME_LEN = 128
        const val MAX_ID_LEN = 128
        const val MAX_DEPTH = 16
        private val NAME_RE = Regex("[a-z][a-z0-9_-]{0,63}")

        fun empty(): AppShortcutResolver = AppShortcutResolver(LinkedHashMap())
    }

    fun define(name: String, target: String) {
        require(NAME_RE.matches(name)) { "invalid shortcut name: $name" }
        require(name.length <= MAX_NAME_LEN) { "name too long" }
        require(target.length <= MAX_ID_LEN) { "target too long" }
        require(shortcuts.size < MAX_SHORTCUTS || shortcuts.containsKey(name)) {
            "too many shortcuts: $MAX_SHORTCUTS"
        }
        shortcuts[name] = target
    }

    fun resolve(name: String): String {
        require(NAME_RE.matches(name)) { "invalid shortcut name: $name" }
        val seen = mutableSetOf<String>()
        var current = name
        var depth = 0
        while (true) {
            val target = shortcuts[current] ?: return current
            require(!seen.contains(current)) { "shortcut cycle at $current" }
            require(depth < MAX_DEPTH) { "resolution depth exceeded: $MAX_DEPTH" }
            seen.add(current)
            depth++
            current = target
        }
    }

    fun remove(name: String): Boolean = shortcuts.remove(name) != null

    fun shortcutCount(): Int = shortcuts.size
    fun isDefined(name: String): Boolean = shortcuts.containsKey(name)
    fun clear() = shortcuts.clear()
}
