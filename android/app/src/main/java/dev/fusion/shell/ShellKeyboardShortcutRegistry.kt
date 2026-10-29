// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

/**
 * Pure-JVM **Fusion Workspace Command-Palette Quick-Action Router** for `:app` (`dev.fusion.shell`),
 * binding keyboard shortcuts (`Ctrl+Shift+R` -> Recall toggle, `Ctrl+1/2/3` -> List/Grid/Columns,
 * `Ctrl+Z` -> Bottom-Right Undo Stack) without collisions!
 */
object ShellKeyboardShortcutRegistry {
    const val SCOPE =
        "Collision-free Fusion Shell desktop/tablet keyboard shortcut registry binding Recall, View-Modes (List/Grid/Columns), and Undo/Redo."

    data class ShortcutBinding(
        val actionId: String,
        val chordCanonical: String, // e.g. "CTRL+SHIFT+R"
        val description: String,
    )

    val DEFAULT_BINDINGS: List<ShortcutBinding> = listOf(
        ShortcutBinding("recall.toggle_auto_record", "CTRL+SHIFT+R", "Toggle Recall automatic browser media archiver"),
        ShortcutBinding("recall.open_vault", "CTRL+ALT+R", "Open Recall archived media folders"),
        ShortcutBinding("files.view_list", "CTRL+1", "Switch active directory to List view"),
        ShortcutBinding("files.view_grid", "CTRL+2", "Switch active directory to Grid view"),
        ShortcutBinding("files.view_columns", "CTRL+3", "Switch active directory to Miller Columns view"),
        ShortcutBinding("edit.undo", "CTRL+Z", "Undo most recent action from bottom-right stack"),
        ShortcutBinding("edit.redo", "CTRL+SHIFT+Z", "Redo most recently undone action"),
    )

    fun validateNoChordCollisions(bindings: List<ShortcutBinding>): Set<String> {
        val seen = HashSet<String>()
        val collisions = LinkedHashSet<String>()
        for (b in bindings) {
            val norm = b.chordCanonical.uppercase().trim()
            if (!seen.add(norm)) collisions.add(norm)
        }
        return collisions
    }
}
