// SPDX-License-Identifier: Apache-2.0
package dev.fusion.filesui

/**
 * **Multi-Selection & Heterogeneous MIME Context-Menu Verb + Cascading Submenu Resolver** for
 * Fusion Files: suppresses single-item-only verbs (`Rename F2`) on multi-select unless Batch Rename
 * is invoked, and attaches compact cascading submenus (`Open With ▸`, `Privacy & Tails Scrub ▸`,
 * `Send to Recall Vault ▸`).
 */
object ContextMenuSelectionVerbResolver {
    const val SCOPE =
        "Single/multi/heterogeneous selection context-menu verb filter and cascading submenu tree builder for Files."

    data class SelectedEntry(val name: String, val isDirectory: Boolean, val mimeType: String)

    data class ContextVerbNode(
        val id: String,
        val label: String,
        val shortcut: String = "",
        val submenuChildren: List<ContextVerbNode> = emptyList(),
    )

    fun resolveMenuTree(selection: List<SelectedEntry>, dualPaneActive: Boolean): List<ContextVerbNode> {
        require(selection.isNotEmpty())
        val single = selection.size == 1
        val anyDir = selection.any { it.isDirectory }
        val allImagesOrDocs = selection.all {
            !it.isDirectory && (it.mimeType.startsWith("image/") || it.mimeType == "application/pdf" || "officedocument" in it.mimeType)
        }

        return buildList {
            if (single) {
                add(ContextVerbNode("open", "Open", "Enter"))
                if (!anyDir) {
                    add(
                        ContextVerbNode(
                            "open_with",
                            "Open With",
                            submenuChildren = listOf(
                                ContextVerbNode("open_writer", "Fusion Writer / Editor"),
                                ContextVerbNode("open_media", "Fusion Media Studio"),
                                ContextVerbNode("open_hex", "Read-Only Inspector"),
                            ),
                        )
                    )
                }
                add(ContextVerbNode("rename", "Rename", "F2"))
            } else {
                add(ContextVerbNode("batch_rename", "Batch Rename (${selection.size} items)", "F2"))
                add(ContextVerbNode("compress_zip", "Compress to .zip Archive"))
            }
            if (dualPaneActive) {
                add(ContextVerbNode("copy_other_pane", "Copy to Other Pane", "F5"))
            }
            if (allImagesOrDocs) {
                add(
                    ContextVerbNode(
                        "privacy_submenu",
                        "Privacy & Tails Scrub",
                        submenuChildren = listOf(
                            ContextVerbNode("mat2_clean", "Scrub Metadata (.cleaned)"),
                            ContextVerbNode("move_to_vault", "Encrypt into Fusion Vault"),
                        ),
                    )
                )
            }
            add(ContextVerbNode("move_trash", "Move to Trash", "Del"))
        }
    }
}
