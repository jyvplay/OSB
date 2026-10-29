// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * Clean-room Keyboard Shortcut Presets, Normalized Chord Canonicalizer, Conflict Detector, and
 * Per-Device Hardware Profile Resolver (`ST-06`).
 */
object ShortcutPresetConflictDetector {
    const val SCOPE =
        "Pure-JVM keyboard shortcut preset catalog, modifier-ordered chord canonicalizer, collision detector, and per-device override resolver (ST-06)."

    data class Binding(val actionId: String, val rawChord: String)

    data class Conflict(val canonicalChord: String, val collidingActions: List<String>)

    data class ValidationReport(
        val resolvedActionByChord: Map<String, String>,
        val conflicts: List<Conflict>,
    )

    private val MOD_ORDER = listOf("META", "CTRL", "ALT", "SHIFT")

    fun canonicalizeChord(raw: String): String {
        val tokens = raw.uppercase().split('+').map { it.trim() }.filter { it.isNotEmpty() }.map {
            when (it) {
                "CMD", "SUPER", "WIN" -> "META"
                "CONTROL" -> "CTRL"
                "OPT", "OPTION" -> "ALT"
                else -> it
            }
        }
        require(tokens.isNotEmpty()) { "empty shortcut chord" }
        val mods = MOD_ORDER.filter { it in tokens }
        val keys = tokens.filterNot { it in MOD_ORDER }
        require(keys.size == 1) { "expected exactly 1 primary key in '$raw', got $keys" }
        return (mods + keys).joinToString("+")
    }

    fun presetBindings(presetName: String): List<Binding> = when (presetName.lowercase()) {
        "standard" -> listOf(
            Binding("palette.open", "Ctrl+K"),
            Binding("window.close", "Ctrl+W"),
            Binding("workspace.next", "Meta+Tab"),
            Binding("terminal.toggle", "Ctrl+`"),
        )
        "emacs" -> listOf(
            Binding("palette.open", "Alt+X"),
            Binding("window.close", "Ctrl+X"),
            Binding("workspace.next", "Ctrl+Tab"),
            Binding("terminal.toggle", "Alt+T"),
        )
        else -> throw IllegalArgumentException("unknown shortcut preset '$presetName'")
    }

    fun resolveWithDeviceOverrides(
        base: List<Binding>,
        deviceOverrides: List<Binding> = emptyList(),
    ): ValidationReport {
        val effectiveByAction = LinkedHashMap<String, String>()
        for (b in base) effectiveByAction[b.actionId] = canonicalizeChord(b.rawChord)
        for (o in deviceOverrides) effectiveByAction[o.actionId] = canonicalizeChord(o.rawChord)

        val actionsByChord = LinkedHashMap<String, MutableList<String>>()
        for ((act, chord) in effectiveByAction) {
            actionsByChord.getOrPut(chord) { ArrayList() }.add(act)
        }
        val conflicts = actionsByChord.filter { it.value.size > 1 }
            .map { Conflict(it.key, it.value.sorted()) }
        val unique = actionsByChord.mapValues { it.value.first() }
        return ValidationReport(unique, conflicts)
    }
}
