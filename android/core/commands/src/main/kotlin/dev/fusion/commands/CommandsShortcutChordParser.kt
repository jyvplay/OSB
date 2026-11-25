// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * Bounded keyboard shortcut chord canonicalizer (`Shift+Ctrl+P` -> `ctrl+shift+p`).
 */
class CommandsShortcutChordParser private constructor() {
    companion object {
        private val MODS = setOf("ctrl", "alt", "shift", "meta")

        fun canonicalize(chord: String): String {
            val parts = chord.split('+').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            require(parts.isNotEmpty()) { "empty chord" }
            val mods = parts.filter { it in MODS }.toSortedSet()
            val keys = parts.filter { it !in MODS }
            require(keys.size == 1) { "chord must have exactly one primary key" }
            return (mods.toList() + keys).joinToString("+")
        }
    }
}
