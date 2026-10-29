// SPDX-License-Identifier: Apache-2.0
package dev.fusion.filesui

/**
 * Pure-JVM **Multi-Mode (`LIST` / `GRID` / `COLUMNS`) & Hierarchical Miller-Columns Navigator**
 * for `Files`, `Recall` media folders, and desktop panels.
 */
object MillerColumnsTreeNavigator {
    const val SCOPE =
        "Pure-JVM List / Grid / Miller-Columns hierarchical folder pane navigator with breadcrumb selection trail and leaf-inspector metadata."

    enum class CollectionViewMode { LIST, GRID, COLUMNS }

    data class VfsNode(
        val path: String,
        val name: String,
        val isDirectory: Boolean,
        val byteSize: Long = 0L,
        val children: List<VfsNode> = emptyList(),
    )

    data class ColumnPaneModel(
        val depth: Int,
        val parentPath: String,
        val entries: List<VfsNode>,
        val selectedEntryPath: String?,
    )

    data class ColumnsLayoutSnapshot(
        val panes: List<ColumnPaneModel>,
        val activeLeafPreview: VfsNode?,
    )

    fun computeColumns(root: VfsNode, selectedTrailPaths: List<String>, maxVisibleColumns: Int = 3): ColumnsLayoutSnapshot {
        require(root.isDirectory && maxVisibleColumns in 2..5)
        val panes = ArrayList<ColumnPaneModel>()
        var curDir: VfsNode? = root
        var depth = 0
        var leafPreview: VfsNode? = null

        while (curDir != null && curDir.isDirectory) {
            val selPath = selectedTrailPaths.getOrNull(depth)
            val chosen = curDir.children.firstOrNull { it.path == selPath }
            panes.add(ColumnPaneModel(depth, curDir.path, curDir.children, chosen?.path))
            if (chosen == null) break
            if (chosen.isDirectory) {
                curDir = chosen
                depth++
            } else {
                leafPreview = chosen
                curDir = null
            }
        }
        val visible = if (panes.size > maxVisibleColumns) panes.takeLast(maxVisibleColumns) else panes
        return ColumnsLayoutSnapshot(visible, leafPreview)
    }

    fun computeAdaptiveGridColumnCount(containerWidthDp: Double, minTileWidthDp: Double = 148.0): Int {
        require(containerWidthDp > 0.0 && minTileWidthDp >= 72.0)
        return (containerWidthDp / minTileWidthDp).toInt().coerceIn(2, 8)
    }
}
