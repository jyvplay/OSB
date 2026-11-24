// SPDX-License-Identifier: Apache-2.0
package dev.fusion.a11y

/**
 * Bounded focus traversal order auditor. Validates that focusable elements
 * are visited in a logical order consistent with DOM/logical order, detects
 * out-of-order focus jumps, and enforces a maximum traversal chain length.
 * Does not manipulate actual focus — it audits a supplied sequence of
 * element IDs and reports violations.
 *
 * Declared narrowings: at most [MAX_ELEMENTS] elements; element IDs at most
 * [MAX_ID_LEN] chars matching [ID_RE]; traversal chain at most
 * [MAX_CHAIN_LEN] hops; out-of-order jumps are reported, not silently
 * corrected; no real View/AccessibilityNodeInfo is consulted.
 */
class FocusTraversalAuditor private constructor(
    private val elements: LinkedHashMap<String, ElementInfo>
) {
    companion object {
        const val MAX_ELEMENTS = 4096
        const val MAX_ID_LEN = 256
        const val MAX_CHAIN_LEN = 1024
        private val ID_RE = Regex("[a-zA-Z0-9_-]{1,64}")

        data class ElementInfo(
            val id: String,
            val logicalIndex: Int,
            val focusable: Boolean
        )

        data class AuditResult(
            val valid: Boolean,
            val outOfOrderJumps: List<Pair<String, String>>,
            val chainLength: Int,
            violations: List<String>
        )

        fun empty(): FocusTraversalAuditor = FocusTraversalAuditor(LinkedHashMap())
    }

    fun addElement(id: String, logicalIndex: Int, focusable: Boolean) {
        require(ID_RE.matches(id)) { "invalid element id: $id" }
        require(id.length <= MAX_ID_LEN) { "id too long" }
        require(logicalIndex >= 0) { "negative logical index" }
        require(elements.size < MAX_ELEMENTS || elements.containsKey(id)) {
            "too many elements: $MAX_ELEMENTS"
        }
        elements[id] = ElementInfo(id, logicalIndex, focusable)
    }

    fun audit(traversal: List<String>): AuditResult {
        require(traversal.size <= MAX_CHAIN_LEN) { "traversal exceeds $MAX_CHAIN_LEN hops" }
        val violations = mutableListOf<String>()
        val outOfOrder = mutableListOf<Pair<String, String>>()
        var lastLogicalIndex = -1
        var chainLen = 0

        for (i in traversal.indices) {
            val id = traversal[i]
            require(ID_RE.matches(id)) { "invalid traversal id: $id" }
            val info = elements[id] ?: {
                violations.add("unknown element at position $i: $id")
                null
            }()
            if (info != null) {
                if (info.focusable) {
                    chainLen++
                    if (info.logicalIndex < lastLogicalIndex) {
                        outOfOrder.add(lastId to id)
                        violations.add("out-of-order jump: $lastId -> $id")
                    }
                    lastLogicalIndex = info.logicalIndex
                }
                lastId = id
            }
        }

        val valid = violations.isEmpty()
        return AuditResult(valid, outOfOrder.toList(), chainLen, violations.toList())
    }

    fun elementCount(): Int = elements.size
    fun focusableCount(): Int = elements.values.count { it.focusable }
    fun clear() = elements.clear()

    private var lastId: String = ""
}
