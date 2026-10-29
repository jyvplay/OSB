// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Pure-JVM **Cross-Reference & Numbered Figure / Table / Equation Caption Auto-Numberer**
 * for `:core:document`, resolving forward and backward `REF` bookmarks in a single deterministic
 * two-pass AST walk.
 */
object DocumentCrossRefResolver {
    const val SCOPE =
        "Two-pass document Figure/Table/Equation sequence auto-numberer and forward/backward REF bookmark resolver."

    enum class SequenceStream(val labelPrefix: String) {
        FIGURE("Figure"),
        TABLE("Table"),
        EQUATION("Eq."),
    }

    data class DocumentAnchorNode(
        val anchorId: String,
        val stream: SequenceStream,
        val pageNumber: Int,
        val captionTitle: String,
    )

    data class ResolvedCrossRefMap(
        val assignedNumbers: Map<String, String>, // anchorId -> "Figure 1"
        val expandedText: String,
        val brokenAnchorRefs: Set<String>,
    )

    fun resolve(anchorsInDocOrder: List<DocumentAnchorNode>, bodyWithRefTokens: String): ResolvedCrossRefMap {
        val counters = EnumMapCounter()
        val labelMap = LinkedHashMap<String, String>()
        val pageMap = LinkedHashMap<String, Int>()
        for (a in anchorsInDocOrder) {
            val n = counters.next(a.stream)
            labelMap[a.anchorId] = "${a.stream.labelPrefix} $n"
            pageMap[a.anchorId] = a.pageNumber
        }
        val broken = LinkedHashSet<String>()
        val refRegex = Regex("""\{REF\s+([A-Za-z0-9_:-]+)(\s+\\p)?\}""")
        val expanded = refRegex.replace(bodyWithRefTokens) { m ->
            val id = m.groupValues[1]
            val includePage = m.groupValues[2].isNotBlank()
            val lbl = labelMap[id]
            if (lbl == null) {
                broken.add(id)
                "[Error! Reference source '$id' not found]"
            } else if (includePage) {
                "$lbl (p. ${pageMap[id]})"
            } else {
                lbl
            }
        }
        return ResolvedCrossRefMap(labelMap, expanded, broken)
    }

    private class EnumMapCounter {
        private val counts = HashMap<SequenceStream, Int>()
        fun next(s: SequenceStream): Int {
            val c = (counts[s] ?: 0) + 1
            counts[s] = c
            return c
        }
    }
}
