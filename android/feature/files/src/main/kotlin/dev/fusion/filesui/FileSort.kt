// SPDX-License-Identifier: Apache-2.0
package dev.fusion.filesui

/** One row the file list can sort. Extension is derived, never stored. */
data class FileRow(val name: String, val bytes: Long, val modifiedMs: Long, val isDir: Boolean) {
    val ext: String get() {
        if (isDir) return ""
        val i = name.lastIndexOf('.')
        return if (i in 1 until name.length - 1) name.substring(i + 1).lowercase() else ""
    }
}

enum class SortKey { NAME, SIZE, MODIFIED, KIND }

/**
 * Total file-list ordering: the sort key, then case-folded name, then raw
 * name, so the same set always yields the same order regardless of input
 * order. NAME folds case without locale collation; KIND sorts by lowercase
 * extension, then name. Descriptors look like "name:asc" or "size:desc".
 */
object FileSort {
    fun parse(descriptor: String): Pair<SortKey, Boolean> {
        val parts = descriptor.split(":")
        require(parts.size == 2)
        val key = when (parts[0].lowercase()) {
            "name" -> SortKey.NAME; "size" -> SortKey.SIZE
            "modified" -> SortKey.MODIFIED; "kind" -> SortKey.KIND
            else -> throw IllegalArgumentException("bad key")
        }
        val asc = when (parts[1].lowercase()) {
            "asc" -> true; "desc" -> false
            else -> throw IllegalArgumentException("bad direction")
        }
        return key to asc
    }

    fun sort(rows: List<FileRow>, key: SortKey, ascending: Boolean, dirsFirst: Boolean = true): List<FileRow> {
        val base: Comparator<FileRow> = when (key) {
            SortKey.NAME -> compareBy({ it.name.lowercase() }, { it.name })
            SortKey.SIZE -> compareBy<FileRow> { it.bytes }
                .thenBy { it.name.lowercase() }.thenBy { it.name }
            SortKey.MODIFIED -> compareBy<FileRow> { it.modifiedMs }
                .thenBy { it.name.lowercase() }.thenBy { it.name }
            SortKey.KIND -> compareBy({ it.ext }, { it.name.lowercase() }, { it.name })
        }
        val dir = if (ascending) base else base.reversed()
        val full = if (dirsFirst) compareByDescending<FileRow> { it.isDir }.then(dir) else dir
        return rows.sortedWith(full)
    }
}
