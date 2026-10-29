// SPDX-License-Identifier: Apache-2.0
package dev.fusion.filesui

/**
 * **Dual-Pane Independent vs Linked View-Mode Persistence Store** (`LIST`, `GRID`, `COLUMNS`) for
 * Fusion Files Commander (`FilesScreen.kt`): remembers per-directory preferred view mode (e.g.,
 * `/Pictures` -> `GRID`, `/Projects` -> `COLUMNS`, `/Documents` -> `LIST`) across Left & Right panes!
 */
object DualPaneViewModeSyncStore {
    const val SCOPE =
        "Dual-pane independent/linked LIST/GRID/COLUMNS view-mode store with per-folder heuristic defaults and folder memory."

    enum class PaneViewMode { LIST, GRID, COLUMNS }

    data class DualPaneViewState(
        val leftPath: String,
        val rightPath: String,
        val leftMode: PaneViewMode,
        val rightMode: PaneViewMode,
        val syncBothPanes: Boolean = false,
        val folderMemory: Map<String, PaneViewMode> = emptyMap(),
    )

    fun initialForPaths(leftPath: String, rightPath: String, syncBothPanes: Boolean = false): DualPaneViewState {
        val lm = heuristicDefaultMode(leftPath)
        val rm = if (syncBothPanes) lm else heuristicDefaultMode(rightPath)
        return DualPaneViewState(leftPath, rightPath, lm, rm, syncBothPanes, mapOf(leftPath to lm, rightPath to rm))
    }

    fun setPaneMode(state: DualPaneViewState, isLeftPane: Boolean, mode: PaneViewMode): DualPaneViewState {
        val updatedMem = LinkedHashMap(state.folderMemory)
        if (isLeftPane || state.syncBothPanes) updatedMem[state.leftPath] = mode
        if (!isLeftPane || state.syncBothPanes) updatedMem[state.rightPath] = mode
        return state.copy(
            leftMode = if (isLeftPane || state.syncBothPanes) mode else state.leftMode,
            rightMode = if (!isLeftPane || state.syncBothPanes) mode else state.rightMode,
            folderMemory = updatedMem,
        )
    }

    fun navigatePane(state: DualPaneViewState, isLeftPane: Boolean, newPath: String): DualPaneViewState {
        val remembered = state.folderMemory[newPath] ?: heuristicDefaultMode(newPath)
        return if (isLeftPane) {
            val eff = if (state.syncBothPanes) state.rightMode else remembered
            state.copy(leftPath = newPath, leftMode = eff)
        } else {
            val eff = if (state.syncBothPanes) state.leftMode else remembered
            state.copy(rightPath = newPath, rightMode = eff)
        }
    }

    fun heuristicDefaultMode(path: String): PaneViewMode {
        val lower = path.lowercase()
        return when {
            listOf("/pictures", "/photos", "/dcim", "/recall", "/wallpapers").any { it in lower } -> PaneViewMode.GRID
            listOf("/projects", "/src", "/workspace", "/code").any { it in lower } -> PaneViewMode.COLUMNS
            else -> PaneViewMode.LIST
        }
    }
}
