// SPDX-License-Identifier: Apache-2.0
package dev.fusion.filesui

/**
 * Clean-room Dual-Pane Split File Commander + Optional Docked Terminal Pane (`FM-02`) and
 * Concise / Full / Custom Context-Menu Builder (`FM-06`).
 */
object DualPaneContextMenuModel {
    const val SCOPE =
        "Pure-JVM dual-pane split file commander + docked terminal pane reducer (FM-02) and concise/full/custom file context-menu builder (FM-06)."

    enum class MenuVerbosity { CONCISE, FULL, CUSTOM }
    enum class ActiveFocusPane { LEFT, RIGHT, TERMINAL }

    data class CommanderState(
        val dualPaneEnabled: Boolean = false,
        val terminalDrawerVisible: Boolean = false,
        val leftDir: String = "/Documents",
        val rightDir: String = "/Downloads",
        val focusedPane: ActiveFocusPane = ActiveFocusPane.LEFT,
        val splitRatio: Double = 0.50,
    ) {
        val terminalWorkingDir: String
            get() = if (focusedPane == ActiveFocusPane.RIGHT && dualPaneEnabled) rightDir else leftDir
    }

    data class MenuItem(val actionId: String, val label: String, val shortcutHint: String = "", val sectionBreakBefore: Boolean = false)

    fun toggleDualPane(s: CommanderState): CommanderState =
        s.copy(dualPaneEnabled = !s.dualPaneEnabled, focusedPane = ActiveFocusPane.LEFT)

    fun toggleTerminalPane(s: CommanderState): CommanderState =
        s.copy(terminalDrawerVisible = !s.terminalDrawerVisible)

    fun copySelectionAcrossPanes(srcDir: String, dstDir: String, names: List<String>): List<Pair<String, String>> {
        require(srcDir != dstDir) { "source and destination pane directories are identical ('$srcDir')" }
        require(names.isNotEmpty() && names.all { '/' !in it && it != ".." }) { "invalid filenames" }
        return names.map { "${srcDir.trimEnd('/')}/$it" to "${dstDir.trimEnd('/')}/$it" }
    }

    fun buildContextMenu(
        verbosity: MenuVerbosity,
        isDirectory: Boolean,
        customEnabledIds: Set<String> = emptySet(),
    ): List<MenuItem> {
        val concise = buildList {
            add(MenuItem("open", "Open", "Enter"))
            add(MenuItem("rename", "Rename", "F2"))
            add(MenuItem("copy_to_other_pane", "Copy to Other Pane", "F5"))
            add(MenuItem("trash", "Move to Trash", "Del", sectionBreakBefore = true))
        }
        val fullExtra = buildList {
            add(MenuItem("copy_path", "Copy Canonical Path", "Ctrl+Shift+C", sectionBreakBefore = true))
            if (isDirectory) add(MenuItem("open_in_terminal", "Open Terminal Here", "Ctrl+`"))
            add(MenuItem("send_to_beam", "Cast / Send via Beam"))
            add(MenuItem("scrub_metadata_mat2", "Scrub Metadata (mat2)"))
            add(MenuItem("new_from_template", "New File from Template", "Ctrl+N"))
        }
        val all = concise + fullExtra
        return when (verbosity) {
            MenuVerbosity.CONCISE -> concise
            MenuVerbosity.FULL -> all
            MenuVerbosity.CUSTOM -> all.filter { it.actionId in customEnabledIds }.ifEmpty { concise }
        }
    }
}
