// SPDX-License-Identifier: Apache-2.0
package dev.fusion.launcher

/**
 * Pure-JVM **Launcher & All-Apps View-Mode (`LIST` / `GRID` / `COLUMNS`) Shelf Layout Planner**,
 * ensuring Fusion's three custom view-mode buttons behave consistently across Files, Recall, and Launcher!
 */
object LauncherViewModeShelfPlanner {
    const val SCOPE =
        "Launcher List / Grid / Category-Columns layout planner and compact quick-action menu descriptors."

    enum class ShelfMode { LIST, GRID, COLUMNS }

    data class AppTileSpec(val id: String, val title: String, val category: String)

    data class PlannedLauncherSection(
        val headerTitle: String,
        val items: List<AppTileSpec>,
        val columnsPerRow: Int,
    )

    fun organize(apps: List<AppTileSpec>, mode: ShelfMode, viewportWidthDp: Double): List<PlannedLauncherSection> {
        val sorted = apps.sortedBy { it.title.lowercase() }
        return when (mode) {
            ShelfMode.LIST -> listOf(PlannedLauncherSection("All Applications (List)", sorted, columnsPerRow = 1))
            ShelfMode.GRID -> {
                val cols = (viewportWidthDp / 112.0).toInt().coerceIn(3, 8)
                listOf(PlannedLauncherSection("All Applications (Grid)", sorted, columnsPerRow = cols))
            }
            ShelfMode.COLUMNS -> {
                sorted.groupBy { it.category.ifBlank { "Workspace" } }
                    .entries.sortedBy { it.key }
                    .map { (cat, list) -> PlannedLauncherSection(cat, list, columnsPerRow = 1) }
            }
        }
    }
}
