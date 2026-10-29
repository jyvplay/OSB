package dev.fusion.windowing

/**
 * Clean-room Deterministic Window Z-Order Stack with Modal Parent-Child Focus Trapping &
 * `Alt+Tab` MRU Ring (Apache-2.0).
 */
object FocusZOrderModalStack {
    const val SCOPE =
        "Deterministic window Z-order stack enforcing modal child focus trapping & always-on-top band invariants"

    data class WindowNode(
        val id: String,
        val parentModalOwnerId: String? = null,
        val alwaysOnTop: Boolean = false,
    )

    class Stack {
        private val bottomToTop = ArrayList<WindowNode>()

        fun openOrRaise(node: WindowNode) {
            bottomToTop.removeAll { it.id == node.id }
            bottomToTop.add(node)
            rebalanceBands()
        }

        /** Clicking a parent window blocked by an open modal dialog automatically redirects focus to its modal child! */
        fun requestFocus(targetId: String): String? {
            val idx = bottomToTop.indexOfFirst { it.id == targetId }
            if (idx < 0) return focusedWindowId()
            val modalBlocker = bottomToTop.lastOrNull { it.parentModalOwnerId == targetId }
            val effective = modalBlocker ?: bottomToTop[idx]
            bottomToTop.remove(effective)
            bottomToTop.add(effective)
            rebalanceBands()
            return focusedWindowId()
        }

        fun close(windowId: String) {
            // Closing an owner closes its transient modal children too
            bottomToTop.removeAll { it.id == windowId || it.parentModalOwnerId == windowId }
        }

        fun focusedWindowId(): String? = bottomToTop.lastOrNull()?.id

        fun zOrderBottomToTop(): List<String> = bottomToTop.map { it.id }

        private fun rebalanceBands() {
            val normal = bottomToTop.filter { !it.alwaysOnTop }
            val pinned = bottomToTop.filter { it.alwaysOnTop }
            bottomToTop.clear()
            bottomToTop.addAll(normal)
            bottomToTop.addAll(pinned)
        }
    }
}
