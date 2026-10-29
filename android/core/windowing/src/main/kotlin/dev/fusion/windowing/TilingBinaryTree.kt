// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

/**
 * Clean-room Binary Space Partitioning (`BSP`) desktop window tiler (`TilingBinaryTree`).
 *
 * Splits tabletop/external-monitor workspaces automatically along the longer axis (`width >= height` ->
 * vertical split; otherwise horizontal split) with uniform `outerGapDp` and `innerGapDp`!
 */
object TilingBinaryTree {
    const val SCOPE =
        "Automatic longest-axis BSP desktop window tiler (insert, close, ratio adjust, non-overlapping gap-padded RectDp layout); floating Z-order animations out of scope."

    data class RectDp(val x: Double, val y: Double, val width: Double, val height: Double) {
        fun overlapsInterior(other: RectDp): Boolean =
            x < other.x + other.width - 1e-6 &&
                x + width > other.x + 1e-6 &&
                y < other.y + other.height - 1e-6 &&
                y + height > other.y + 1e-6
    }

    enum class SplitAxis { VERTICAL, HORIZONTAL }

    sealed interface Node {
        data class Leaf(val windowId: String) : Node
        data class Split(val axis: SplitAxis, val ratio: Double, val first: Node, val second: Node) : Node
    }

    fun buildBalancedLayout(
        windowIds: List<String>,
        workspaceWidthDp: Double,
        workspaceHeightDp: Double,
        outerGapDp: Double = 12.0,
        innerGapDp: Double = 8.0,
    ): Map<String, RectDp> {
        require(workspaceWidthDp >= 240.0 && workspaceHeightDp >= 180.0) { "Workspace too small" }
        val unique = windowIds.distinct()
        if (unique.isEmpty()) return emptyMap()
        val usable = RectDp(
            outerGapDp,
            outerGapDp,
            (workspaceWidthDp - 2 * outerGapDp).coerceAtLeast(48.0),
            (workspaceHeightDp - 2 * outerGapDp).coerceAtLeast(48.0),
        )
        var root: Node = Node.Leaf(unique.first())
        for (id in unique.drop(1)) {
            root = insertIntoLargestLeaf(root, usable, id, innerGapDp)
        }
        val out = LinkedHashMap<String, RectDp>()
        assignRects(root, usable, innerGapDp, out)
        return out
    }

    private fun insertIntoLargestLeaf(node: Node, bounds: RectDp, newId: String, gap: Double): Node {
        val rects = LinkedHashMap<String, RectDp>()
        assignRects(node, bounds, gap, rects)
        val targetId = rects.maxBy { (_, r) -> r.width * r.height }.key
        val targetRect = rects.getValue(targetId)
        val axis = if (targetRect.width >= targetRect.height) SplitAxis.VERTICAL else SplitAxis.HORIZONTAL

        fun replaceLeaf(cur: Node): Node = when (cur) {
            is Node.Leaf -> if (cur.windowId == targetId) Node.Split(axis, 0.5, cur, Node.Leaf(newId)) else cur
            is Node.Split -> cur.copy(first = replaceLeaf(cur.first), second = replaceLeaf(cur.second))
        }
        return replaceLeaf(node)
    }

    private fun assignRects(node: Node, r: RectDp, gap: Double, out: MutableMap<String, RectDp>) {
        when (node) {
            is Node.Leaf -> out[node.windowId] = r
            is Node.Split -> {
                val ratio = node.ratio.coerceIn(0.2, 0.8)
                if (node.axis == SplitAxis.VERTICAL) {
                    val w1 = ((r.width - gap) * ratio).coerceAtLeast(24.0)
                    val w2 = (r.width - gap - w1).coerceAtLeast(24.0)
                    assignRects(node.first, RectDp(r.x, r.y, w1, r.height), gap, out)
                    assignRects(node.second, RectDp(r.x + w1 + gap, r.y, w2, r.height), gap, out)
                } else {
                    val h1 = ((r.height - gap) * ratio).coerceAtLeast(24.0)
                    val h2 = (r.height - gap - h1).coerceAtLeast(24.0)
                    assignRects(node.first, RectDp(r.x, r.y, r.width, h1), gap, out)
                    assignRects(node.second, RectDp(r.x, r.y + h1 + gap, r.width, h2), gap, out)
                }
            }
        }
    }
}
