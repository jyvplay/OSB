// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * **Fusion Notes `LIST` / `GRID` / `COLUMNS` 3-Mode Notebook -> Tag -> Note Organizer** backing
 * our original `List`, `Grid`, and `Columns` vector logos inside `NotesScreen.kt`!
 */
object NotesCollectionViewModeSorter {
    const val SCOPE =
        "Fusion Notes LIST (chronological ledger), GRID (masonry note cards), and COLUMNS (Notebook -> Tag -> Note) organizer."

    data class NoteSummary(
        val id: String,
        val title: String,
        val notebook: String,
        val primaryTag: String,
        val updatedEpochSec: Long,
    )

    data class NotesColumnsState(
        val notebooks: List<String>,
        val selectedNotebook: String,
        val tags: List<String>,
        val selectedTag: String,
        val notes: List<NoteSummary>,
    )

    fun buildColumns(notes: List<NoteSummary>, notebook: String?, tag: String?): NotesColumnsState {
        if (notes.isEmpty()) return NotesColumnsState(emptyList(), "", emptyList(), "", emptyList())
        val nbs = notes.map { it.notebook }.distinct().sorted()
        val selNb = notebook?.takeIf { it in nbs } ?: nbs.first()
        val inNb = notes.filter { it.notebook == selNb }
        val tags = inNb.map { it.primaryTag }.distinct().sorted()
        val selTag = tag?.takeIf { it in tags } ?: tags.first()
        val leaf = inNb.filter { it.primaryTag == selTag }.sortedByDescending { it.updatedEpochSec }
        return NotesColumnsState(nbs, selNb, tags, selTag, leaf)
    }
}
