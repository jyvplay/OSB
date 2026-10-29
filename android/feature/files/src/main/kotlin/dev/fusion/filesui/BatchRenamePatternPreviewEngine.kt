package dev.fusion.filesui

/**
 * Clean-room Conflict-Free Batch File Rename Pattern Preview & Two-Phase Swap Planner (Apache-2.0).
 *
 * Supports `{stem}`, `{ext}`, `{num:03}` zero-padded counters, rejects duplicate target collisions
 * or illegal `/` / `..` characters BEFORE touching disk, and emits temporary `.swap` steps when an
 * in-place rename cycle (`a.txt -> b.txt`, `b.txt -> a.txt`) occurs!
 */
object BatchRenamePatternPreviewEngine {
    const val SCOPE =
        "Batch file rename template engine ({stem}, {ext}, {num:03}) + duplicate-collision detector + cycle-safe swap planner"

    data class RenamePair(val sourceName: String, val targetName: String)

    data class RenamePlan(
        val valid: Boolean,
        val pairs: List<RenamePair>,
        val atomicExecutionSteps: List<RenamePair>,
        val errors: List<String>,
    )

    fun previewTemplate(sourceNames: List<String>, template: String, startIndex: Int = 1): RenamePlan {
        val pairs = sourceNames.mapIndexed { idx, src ->
            val dot = src.lastIndexOf('.')
            val stem = if (dot > 0) src.substring(0, dot) else src
            val ext = if (dot > 0) src.substring(dot + 1) else ""
            val n = startIndex + idx
            var rendered = template.replace("{stem}", stem).replace("{ext}", ext)
            rendered = Regex("\\{num(?::0([1-9]))?\\}").replace(rendered) { m ->
                val width = m.groupValues[1].toIntOrNull() ?: 1
                n.toString().padStart(width, '0')
            }
            RenamePair(src, rendered)
        }
        return validateAndSchedule(pairs)
    }

    fun validateAndSchedule(pairs: List<RenamePair>): RenamePlan {
        val errors = mutableListOf<String>()
        val seenLowerTargets = HashSet<String>()
        for (p in pairs) {
            val t = p.targetName.trim()
            if (t.isEmpty() || '/' in t || '\\' in t || t == "." || t == "..") {
                errors.add("ILLEGAL_TARGET_NAME:${p.targetName}")
            }
            if (!seenLowerTargets.add(t.lowercase())) {
                errors.add("COLLIDING_TARGET_NAME:${p.targetName}")
            }
        }
        if (errors.isNotEmpty()) return RenamePlan(false, pairs, emptyList(), errors)

        // Schedule renames safely even when source & target sets overlap (e.g., A->B, B->A)
        val noOpsRemoved = pairs.filter { it.sourceName != it.targetName }
        val sourceSet = noOpsRemoved.map { it.sourceName }.toHashSet()
        val steps = ArrayList<RenamePair>()
        val tempStaged = ArrayList<Pair<String, String>>()
        for ((idx, p) in noOpsRemoved.withIndex()) {
            if (p.targetName in sourceSet) {
                val tmp = ".__fusion_rename_tmp_${idx}__"
                steps.add(RenamePair(p.sourceName, tmp))
                tempStaged.add(tmp to p.targetName)
            } else {
                steps.add(p)
            }
        }
        for ((tmp, finalTarget) in tempStaged) {
            steps.add(RenamePair(tmp, finalTarget))
        }
        return RenamePlan(true, pairs, steps, emptyList())
    }
}
