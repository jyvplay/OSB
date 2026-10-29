// SPDX-License-Identifier: Apache-2.0
package dev.fusion.filesui

/**
 * Deterministic Batch File Copy/Move Collision Planner & Subtree-Cycle Guard.
 *
 * Guarantees:
 * - Zero silent overwrites under [Strategy.AUTO_RENAME]: allocates `"stem (1).ext"`, `"stem (2).ext"`,
 *   preserving multi-part archive suffixes (`.tar.gz`, `.tar.xz`, `.tar.zst`) and leading dotfiles
 *   (`.gitignore` -> `.gitignore (1)`).
 * - Case-insensitive collision tracking by default (safe across Android `/sdcard` FAT32/exFAT/FUSE,
 *   macOS APFS, and Windows SMB shares).
 * - Categorically refuses moving/copying a directory into itself or any of its own descendants!
 */
object PathConflict {
    const val SCOPE =
        "Case-aware batch file rename collision resolver (compound .tar.gz suffixes, dotfile preservation, subtree-into-self refusal); POSIX renameat2 RENAME_NOREPLACE syscalls out of scope."

    private val COMPOUND_SUFFIXES = listOf(".tar.gz", ".tar.xz", ".tar.bz2", ".tar.zst")

    enum class Strategy { AUTO_RENAME, SKIP_CONFLICTS, FAIL_ON_CONFLICT }

    data class PlannedOp(
        val sourceName: String,
        val resolvedTargetName: String,
        val wasRenamed: Boolean,
    )

    fun verifyNotSubtreeMove(sourceDirCanonical: String, destinationDirCanonical: String) {
        val src = normalizeAbs(sourceDirCanonical)
        val dst = normalizeAbs(destinationDirCanonical)
        require(dst != src && !dst.startsWith("$src/")) {
            "cannot move or copy directory '$src' into itself or its descendant '$dst'"
        }
    }

    fun planBatch(
        existingNamesInDest: Collection<String>,
        incomingNames: List<String>,
        strategy: Strategy = Strategy.AUTO_RENAME,
        caseInsensitive: Boolean = true,
    ): List<PlannedOp> {
        fun keyOf(name: String): String = if (caseInsensitive) name.lowercase() else name

        val occupied = HashSet<String>(existingNamesInDest.size + incomingNames.size)
        for (e in existingNamesInDest) {
            validateLeafName(e)
            occupied.add(keyOf(e))
        }

        val out = ArrayList<PlannedOp>(incomingNames.size)
        for (raw in incomingNames) {
            validateLeafName(raw)
            val k0 = keyOf(raw)
            if (k0 !in occupied) {
                occupied.add(k0)
                out.add(PlannedOp(raw, raw, wasRenamed = false))
                continue
            }
            when (strategy) {
                Strategy.SKIP_CONFLICTS -> continue
                Strategy.FAIL_ON_CONFLICT ->
                    throw IllegalArgumentException("destination conflict for '$raw'")
                Strategy.AUTO_RENAME -> {
                    val (stem, ext) = splitStemAndExt(raw)
                    var counter = 1
                    var candidate: String
                    while (true) {
                        require(counter <= 10_000) { "exhausted rename suffix budget for '$raw'" }
                        candidate = "$stem ($counter)$ext"
                        if (occupied.add(keyOf(candidate))) break
                        counter++
                    }
                    out.add(PlannedOp(raw, candidate, wasRenamed = true))
                }
            }
        }
        return out
    }

    internal fun splitStemAndExt(name: String): Pair<String, String> {
        val lower = name.lowercase()
        for (comp in COMPOUND_SUFFIXES) {
            if (lower.endsWith(comp) && name.length > comp.length) {
                val cut = name.length - comp.length
                return name.substring(0, cut) to name.substring(cut)
            }
        }
        val dot = name.lastIndexOf('.')
        // Leading dotfile like ".bashrc" has dot == 0 -> treat entire name as stem!
        return if (dot <= 0 || dot == name.lastIndex) {
            name to ""
        } else {
            name.substring(0, dot) to name.substring(dot)
        }
    }

    private fun validateLeafName(name: String) {
        require(name.isNotBlank() && name != "." && name != "..") { "invalid filename '$name'" }
        require('/' !in name && '\\' !in name && '\u0000' !in name) {
            "leaf filename '$name' must not contain path separators or NUL"
        }
    }

    private fun normalizeAbs(p: String): String {
        val clean = p.trim().replace('\\', '/').trimEnd('/')
        require(clean.startsWith("/")) { "expected canonical absolute path starting with '/', got '$p'" }
        require(clean.split('/').none { it == ".." }) { "non-canonical '..' in '$p'" }
        return if (clean.isEmpty()) "/" else clean
    }
}
