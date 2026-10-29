// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

/**
 * **Encrypted Vault `LIST` / `GRID` / `COLUMNS` 3-Mode Secret Organizer** (`Security Tier -> Folder
 * -> Masked Item`) backing our original `List`, `Grid`, and `Columns` logos inside `VaultScreen.kt`.
 */
object VaultItemViewModeOrganizer {
    const val SCOPE =
        "Fusion Vault LIST / GRID / COLUMNS (Category -> Folder -> Masked Secret) 3-mode view organizer."

    data class VaultSecretStub(
        val id: String,
        val label: String,
        val category: String,
        val folder: String,
        val maskedPreview: String = "••••••••••••",
    )

    data class VaultColumnsView(
        val categories: List<String>,
        val activeCategory: String,
        val folders: List<String>,
        val activeFolder: String,
        val items: List<VaultSecretStub>,
    )

    fun organizeColumns(secrets: List<VaultSecretStub>, selectedCategory: String?, selectedFolder: String?): VaultColumnsView {
        if (secrets.isEmpty()) return VaultColumnsView(emptyList(), "", emptyList(), "", emptyList())
        val cats = secrets.map { it.category }.distinct().sorted()
        val cat = selectedCategory?.takeIf { it in cats } ?: cats.first()
        val inCat = secrets.filter { it.category == cat }
        val folders = inCat.map { it.folder }.distinct().sorted()
        val fld = selectedFolder?.takeIf { it in folders } ?: folders.first()
        val leafItems = inCat.filter { it.folder == fld }.sortedBy { it.label }
        return VaultColumnsView(cats, cat, folders, fld, leafItems)
    }
}
