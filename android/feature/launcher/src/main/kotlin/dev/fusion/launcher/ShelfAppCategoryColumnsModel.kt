// SPDX-License-Identifier: Apache-2.0
package dev.fusion.launcher

/**
 * **Launcher Shelf `LIST` / `GRID` / `COLUMNS` 3-Pane Miller Category -> App -> Quick-Action Model**
 * powering the original `Columns` logo view inside `Shelf.kt`!
 */
object ShelfAppCategoryColumnsModel {
    const val SCOPE =
        "Launcher Shelf 3-column Miller organizer (Column 1 Category -> Column 2 Apps -> Column 3 Quick Actions)."

    data class ShelfAppEntry(
        val appId: String,
        val title: String,
        val category: String,
        val quickActions: List<String>,
    )

    data class ThreeColumnShelfProjection(
        val column1Categories: List<String>,
        val selectedCategory: String,
        val column2Apps: List<ShelfAppEntry>,
        val selectedApp: ShelfAppEntry?,
        val column3QuickActions: List<String>,
    )

    fun projectColumns(
        apps: List<ShelfAppEntry>,
        activeCategory: String?,
        activeAppId: String?,
    ): ThreeColumnShelfProjection {
        if (apps.isEmpty()) return ThreeColumnShelfProjection(emptyList(), "", emptyList(), null, emptyList())
        val categories = apps.map { it.category }.distinct().sorted()
        val selCat = activeCategory?.takeIf { it in categories } ?: categories.first()
        val appsInCat = apps.filter { it.category == selCat }.sortedBy { it.title }
        val selApp = appsInCat.firstOrNull { it.appId == activeAppId } ?: appsInCat.firstOrNull()
        return ThreeColumnShelfProjection(
            column1Categories = categories,
            selectedCategory = selCat,
            column2Apps = appsInCat,
            selectedApp = selApp,
            column3QuickActions = selApp?.quickActions ?: emptyList(),
        )
    }
}
