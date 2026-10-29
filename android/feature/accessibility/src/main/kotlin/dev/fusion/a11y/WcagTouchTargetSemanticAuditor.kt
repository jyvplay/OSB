package dev.fusion.a11y

/**
 * Clean-room WCAG 2.2 Target Size (`>= 48x48 dp` Android Material / `>= 24x24` minimum),
 * Pairwise Non-Overlap Hit-Box, and TalkBack `contentDescription` Semantic Tree Auditor (Apache-2.0).
 */
object WcagTouchTargetSemanticAuditor {
    const val SCOPE =
        "Android 48x48dp touch-target + pairwise 2D hit-box non-overlap + unique TalkBack contentDescription semantic auditor"

    data class InteractiveNode(
        val id: String,
        val leftDp: Double,
        val topDp: Double,
        val widthDp: Double,
        val heightDp: Double,
        val contentDescription: String?,
    )

    data class A11yAuditReport(
        val compliant: Boolean,
        val undersizedIds: List<String>,
        val overlappingPairs: List<Pair<String, String>>,
        val unlabeledOrDuplicateIds: List<String>,
    )

    fun auditInteractiveNodes(nodes: List<InteractiveNode>, minTargetDp: Double = 48.0): A11yAuditReport {
        val undersized = ArrayList<String>()
        val overlaps = ArrayList<Pair<String, String>>()
        val badLabels = ArrayList<String>()
        val seenLabels = HashMap<String, String>()

        for (n in nodes) {
            if (n.widthDp < minTargetDp || n.heightDp < minTargetDp) undersized.add(n.id)
            val lbl = n.contentDescription?.trim().orEmpty()
            if (lbl.isEmpty()) {
                badLabels.add(n.id)
            } else {
                val prev = seenLabels.putIfAbsent(lbl.lowercase(), n.id)
                if (prev != null) badLabels.add(n.id)
            }
        }
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                if (rectsOverlap(nodes[i], nodes[j])) {
                    overlaps.add(nodes[i].id to nodes[j].id)
                }
            }
        }
        return A11yAuditReport(
            compliant = undersized.isEmpty() && overlaps.isEmpty() && badLabels.isEmpty(),
            undersizedIds = undersized,
            overlappingPairs = overlaps,
            unlabeledOrDuplicateIds = badLabels,
        )
    }

    private fun rectsOverlap(a: InteractiveNode, b: InteractiveNode): Boolean =
        a.leftDp < b.leftDp + b.widthDp &&
            a.leftDp + a.widthDp > b.leftDp &&
            a.topDp < b.topDp + b.heightDp &&
            a.topDp + a.heightDp > b.topDp
}
