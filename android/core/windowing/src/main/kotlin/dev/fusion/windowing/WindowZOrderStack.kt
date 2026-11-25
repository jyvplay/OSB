// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

/**
 * Bounded window Z-order stack (top-most window at end of list).
 */
class WindowZOrderStack private constructor() {
    companion object {
        const val MAX_WINDOWS = 256

        fun empty(): WindowZOrderStack = WindowZOrderStack()
    }

    private val stack = mutableListOf<String>()

    fun bringToFront(windowId: String) {
        require(windowId.isNotEmpty()) { "empty windowId" }
        stack.remove(windowId)
        require(stack.size < MAX_WINDOWS) { "too many windows" }
        stack.add(windowId)
    }

    fun remove(windowId: String): Boolean = stack.remove(windowId)

    fun topWindow(): String? = stack.lastOrNull()

    fun orderBottomToTop(): List<String> = stack.toList()
}
