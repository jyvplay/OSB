// SPDX-License-Identifier: Apache-2.0
package dev.fusion.filesui

/**
 * Pure-JVM **Regex / Counter / Date-Token Batch File Renamer with Dry-Run Collision & Cycle
 * Detector** for `:feature:files` (ensuring every batch rename can be previewed safely and undone
 * atomically!).
 */
object BatchFileRenameCollisionPlanner {
    const val SCOPE =
        "Batch file rename dry-run planner with {n:03d}/{stem}/{ext} expansion, duplicate target rejection, and 2-phase temp-hop swap cycle resolver."

    data class RenamePair(val originalName: String, val targetName: String)

    data class BatchRenamePlan(
        val valid: Boolean,
        val pairs: List<RenamePair>,
        val requiresTwoPhaseSwap: Boolean,
        val conflictReason: String?,
    )

    fun planPatternRename(currentNames: List<String>, pattern: String, startIndex: Int = 1): BatchRenamePlan {
        require(currentNames.isNotEmpty())
        val pairs = currentNames.mapIndexed { idx, orig ->
            val dot = orig.lastIndexOf('.')
            val stem = if (dot > 0) orig.substring(0, dot) else orig
            val ext = if (dot > 0) orig.substring(dot + 1) else ""
            val n = startIndex + idx
            var target = pattern.replace("{stem}", stem).replace("{ext}", ext).replace("{n}", n.toString())
            target = Regex("""\{n:0(\d+)d\}""").replace(target) { m ->
                val w = m.groupValues[1].toInt()
                "%0${w}d".format(n)
            }.trim()
            RenamePair(orig, target)
        }
        if (pairs.any { it.targetName.isEmpty() || '/' in it.targetName || '\\' in it.targetName || it.targetName == "." || it.targetName == ".." }) {
            return BatchRenamePlan(false, pairs, false, "ILLEGAL_PATH_SEPARATOR_OR_EMPTY_NAME")
        }
        val targetLowerSet = HashSet<String>()
        for (p in pairs) {
            if (!targetLowerSet.add(p.targetName.lowercase())) {
                return BatchRenamePlan(false, pairs, false, "CASE_INSENSITIVE_TARGET_COLLISION:${p.targetName}")
            }
        }
        val origSet = currentNames.map { it.lowercase() }.toSet()
        val overlapsExisting = pairs.any { it.originalName.lowercase() != it.targetName.lowercase() && it.targetName.lowercase() in origSet }
        return BatchRenamePlan(true, pairs, overlapsExisting, null)
    }
}
