// SPDX-License-Identifier: Apache-2.0
package dev.fusion.a11y

/**
 * Production-grade semantic focus-order & spatial reading-order engine.
 */
class A11yFocusOrderEngine private constructor() {
    companion object {
        data class FocusNode(val id: String, val top: Int, val left: Int, val focusable: Boolean)

        fun readingOrder(nodes: List<FocusNode>): List<String> =
            nodes.filter { it.focusable }
                .sortedWith(compareBy<FocusNode> { it.top }.thenBy { it.left }.thenBy { it.id })
                .map { it.id }

        fun nextFocus(nodes: List<FocusNode>, currentId: String?): String? {
            val ordered = readingOrder(nodes)
            if (ordered.isEmpty()) return null
            if (currentId == null) return ordered.first()
            val idx = ordered.indexOf(currentId)
            return if (idx < 0) ordered.first() else ordered[(idx + 1) % ordered.size]
        }
    }
}
