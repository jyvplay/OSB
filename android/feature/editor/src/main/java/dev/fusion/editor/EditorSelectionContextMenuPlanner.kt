// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * **Right-Sized Writer / Editor Selection Context-Menu & Cascading `Format ▸` / `Track Changes ▸`
 * Submenu Planner** (`180..268 dp` width cap — never hogging the editor canvas!).
 */
object EditorSelectionContextMenuPlanner {
    const val SCOPE =
        "Right-sized (180..268dp) Writer/Editor selection right-click menu with cascading Format and Track-Changes submenus."

    data class EditorMenuRow(
        val id: String,
        val label: String,
        val shortcut: String = "",
        val submenu: List<EditorMenuRow> = emptyList(),
    )

    fun planForSelection(hasNonEmptySelection: Boolean, insideTrackedChange: Boolean, readOnlyMode: Boolean): List<EditorMenuRow> {
        return buildList {
            if (!readOnlyMode && hasNonEmptySelection) add(EditorMenuRow("cut", "Cut", "Ctrl+X"))
            if (hasNonEmptySelection) add(EditorMenuRow("copy", "Copy", "Ctrl+C"))
            if (!readOnlyMode) add(EditorMenuRow("paste", "Paste", "Ctrl+V"))
            if (!readOnlyMode && hasNonEmptySelection) {
                add(
                    EditorMenuRow(
                        "format_submenu",
                        "Format Text",
                        submenu = listOf(
                            EditorMenuRow("bold", "Bold", "Ctrl+B"),
                            EditorMenuRow("italic", "Italic", "Ctrl+I"),
                            EditorMenuRow("insert_omml_eq", "Insert OMML Equation"),
                        ),
                    )
                )
            }
            if (!readOnlyMode && insideTrackedChange) {
                add(
                    EditorMenuRow(
                        "track_changes_submenu",
                        "Track Changes",
                        submenu = listOf(
                            EditorMenuRow("accept_change", "Accept Revision"),
                            EditorMenuRow("reject_change", "Reject Revision"),
                        ),
                    )
                )
            }
            add(EditorMenuRow("select_all", "Select All", "Ctrl+A"))
        }
    }
}
