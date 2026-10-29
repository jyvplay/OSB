// SPDX-License-Identifier: Apache-2.0
package dev.fusion.filesui

/**
 * Cycle-Safe Two-Phase **Batch File Rename Planner**.
 *
 * Refuses path traversal (`/`, `\`, `..`), blank/dot-only names, and case-insensitive target
 * collisions (`report.pdf` vs `Report.PDF` on FAT/exFAT/macOS/Windows SAF providers!), while
 * safely breaking permutation cycles (`a.txt -> b.txt`, `b.txt -> a.txt`) via collision-free
 * temporary staging filenames!
 */
object SafeBatchRenamePlanner {
    const val SCOPE =
        "Cycle-safe two-phase batch file rename planner with case-insensitive collision & path-traversal refusal."

    data class AtomicStep(val fromName: String, val toName: String)

    fun planRenames(requested: List<Pair<String, String>>, existingUntouchedNames: Set<String> = emptySet()): List<AtomicStep> {
        require(requested.map { it.first.lowercase() }.distinct().size == requested.size) {
            "duplicate source filenames in batch rename"
        }
        val active = ArrayList<Pair<String, String>>()
        for ((src, dst) in requested) {
            validateSafeFilename(src)
            validateSafeFilename(dst)
            if (src != dst) active.add(src to dst)
        }
        val allFinalLower = HashSet<String>()
        for (u in existingUntouchedNames) {
            validateSafeFilename(u)
            require(allFinalLower.add(u.lowercase())) { "duplicate untouched filename '$u'" }
        }
        for ((_, dst) in requested) {
            require(allFinalLower.add(dst.lowercase())) {
                "case-insensitive destination filename collision on '$dst'"
            }
        }

        // Order simple chains (dst not currently occupied by another active source) first; stage cycles via __fusion_tmp_N__.
        val pending = active.toMutableList()
        val steps = ArrayList<AtomicStep>()
        var tmpSeq = 0
        val occupiedLower = (existingUntouchedNames.map { it.lowercase() } + active.map { it.first.lowercase() }).toMutableSet()

        while (pending.isNotEmpty()) {
            val freeIdx = pending.indexOfFirst { (src, dst) ->
                dst.lowercase() !in occupiedLower || src.equals(dst, ignoreCase = true)
            }
            if (freeIdx >= 0) {
                val (src, dst) = pending.removeAt(freeIdx)
                occupiedLower.remove(src.lowercase())
                occupiedLower.add(dst.lowercase())
                steps.add(AtomicStep(src, dst))
            } else {
                // Cycle detected (`a -> b`, `b -> a`): stage first item to a collision-free temporary name!
                val (src, dst) = pending.removeAt(0)
                var tmpName: String
                do {
                    tmpName = ".__fusion_rename_stage_${tmpSeq++}__"
                } while (tmpName.lowercase() in occupiedLower || tmpName.lowercase() in allFinalLower)
                occupiedLower.remove(src.lowercase())
                occupiedLower.add(tmpName.lowercase())
                steps.add(AtomicStep(src, tmpName))
                pending.add(tmpName to dst)
            }
        }
        return steps
    }

    fun validateSafeFilename(name: String) {
        require(name.isNotBlank() && name != "." && name != "..") { "invalid filename '$name'" }
        require('/' !in name && '\\' !in name && '\u0000' !in name) {
            "path separator or NUL forbidden in filename '$name'"
        }
    }
}
