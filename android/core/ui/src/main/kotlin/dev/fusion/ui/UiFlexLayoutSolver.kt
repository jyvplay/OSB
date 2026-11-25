// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ui

/**
 * Production-grade 1D Flex/Row layout solver with fixed items,
 * weighted flex items, min-size clamping, and gap spacing.
 */
class UiFlexLayoutSolver private constructor() {
    companion object {
        data class FlexItem(val id: String, val fixedSize: Int = 0, val weight: Double = 0.0, val minSize: Int = 0)
        data class LayoutSpan(val id: String, val start: Int, val size: Int)

        fun solveRow(containerSize: Int, gap: Int, items: List<FlexItem>): List<LayoutSpan> {
            require(containerSize >= 0 && gap >= 0) { "negative layout bounds" }
            if (items.isEmpty()) return emptyList()
            val totalGap = gap * (items.size - 1)
            val fixedSum = items.sumOf { if (it.weight <= 0.0) maxOf(it.fixedSize, it.minSize) else 0 }
            val remaining = (containerSize - totalGap - fixedSum).coerceAtLeast(0)
            val totalWeight = items.sumOf { if (it.weight > 0.0) it.weight else 0.0 }

            var cursor = 0
            val out = mutableListOf<LayoutSpan>()
            for (item in items) {
                val sz = if (item.weight > 0.0 && totalWeight > 0.0) {
                    maxOf(item.minSize, ((remaining * (item.weight / totalWeight))).toInt())
                } else {
                    maxOf(item.fixedSize, item.minSize)
                }
                out.add(LayoutSpan(item.id, cursor, sz))
                cursor += sz + gap
            }
            return out
        }
    }
}
