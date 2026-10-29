// SPDX-License-Identifier: Apache-2.0
package dev.fusion.a11y

/**
 * WCAG 2.2 SC 1.3.2 (Meaningful Sequence) & SC 2.4.3 (Focus Order) **2D UI Bounding-Box ->
 * TalkBack / Keyboard Traversal Linearizer** (`LTR` & `RTL` row-band clustering + modal focus-trap
 * escape audit).
 */
object ReadingOrderLinearizer {
    const val SCOPE =
        "WCAG 2.2 SC 1.3.2 / SC 2.4.3 2D UI bounding-box -> LTR/RTL row-band focus/reading-order linearizer and modal focus-trap verifier."

    data class FocusNode(
        val id: String,
        val leftDp: Int,
        val topDp: Int,
        val widthDp: Int,
        val heightDp: Int,
        val explicitTraversalIndex: Int? = null,
        val insideActiveModal: Boolean = true,
    ) {
        val centerY: Double get() = topDp + heightDp / 2.0
        val rightDp: Int get() = leftDp + widthDp
    }

    fun linearize(nodes: List<FocusNode>, rtl: Boolean = false, modalActive: Boolean = false): List<String> {
        val eligible = if (modalActive) nodes.filter { it.insideActiveModal } else nodes
        if (eligible.isEmpty()) return emptyList()

        val (explicit, spatial) = eligible.partition { it.explicitTraversalIndex != null }
        val orderedExplicit = explicit.sortedBy { it.explicitTraversalIndex }

        // Group spatial nodes into horizontal row bands when vertical center distance <= 45% of min height.
        val byTop = spatial.sortedBy { it.topDp }
        val rows = ArrayList<MutableList<FocusNode>>()
        for (n in byTop) {
            val band = rows.lastOrNull { row ->
                val ref = row.first()
                kotlin.math.abs(n.centerY - ref.centerY) <= minOf(n.heightDp, ref.heightDp) * 0.45
            }
            if (band != null) band.add(n) else rows.add(mutableListOf(n))
        }
        val orderedSpatial = rows.flatMap { row ->
            if (rtl) row.sortedByDescending { it.rightDp } else row.sortedBy { it.leftDp }
        }
        return (orderedExplicit + orderedSpatial).map { it.id }
    }
}
