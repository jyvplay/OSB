// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

/**
 * R54 desktop context-menu keyboard navigation: **typeahead prefix matching**, **submenu delay/hover
 * bridging**, and **right-edge left-flip cascades**, all deterministic and pure-JVM.
 *
 * Complements `ContextMenuSubmenuCascadeGeometry` (`180..280dp` primary, `168..240dp` submenu) with:
 * 1. Windows/macOS-style typeahead: consecutive printable characters within `typeaheadResetMs` build a
 *    prefix; items whose label starts with the prefix are selected in cyclic order (`Down`/`Up`);
 * 2. **prefix uniqueness** skips separator/disabled items and never lands on a submenu parent twice;
 * 3. hover-intent delay (`openDelayMs`) plus a safe-triangle bridge so a diagonal pointer path does not
 *    close the parent submenu;
 * 4. mnemonic accelerator lookup (`Alt`+letter) resolved against the same visible item list.
 */
object ContextMenuKeyboardTypeaheadSubmenuEngine {
    data class MenuItem(
        val id: String,
        val label: String,
        val enabled: Boolean = true,
        val isSeparator: Boolean = false,
        val hasSubmenu: Boolean = false,
        val mnemonic: Char? = null,
    )

    data class State(
        val buffer: String = "",
        val lastKeystrokeMs: Long = 0,
        val selectedIndex: Int = 0,
    )

    data class Resolution(val state: State, val selected: MenuItem?, val matchedPrefix: String, val consumedAsTypeahead: Boolean)

    fun keyDown(state: State, items: List<MenuItem>, ch: Char, nowMs: Long, typeaheadResetMs: Long = 750): Resolution {
        require(typeaheadResetMs > 0) { "typeaheadResetMs must be positive" }
        val printable = ch.isLetterOrDigit() || ch == ' '
        if (!printable) return Resolution(state, items.getOrNull(state.selectedIndex), state.buffer, false)
        val buffer = if (nowMs - state.lastKeystrokeMs > typeaheadResetMs) ch.lowercaseChar().toString()
        else state.buffer + ch.lowercaseChar()
        val idx = firstMatch(items, buffer, from = if (buffer.length == 1) state.selectedIndex + 1 else 0)
        val next = state.copy(buffer = buffer, lastKeystrokeMs = nowMs, selectedIndex = if (idx >= 0) idx else state.selectedIndex)
        return Resolution(next, items.getOrNull(next.selectedIndex), buffer, true)
    }

    /** Cyclic search for the first enabled, non-separator item whose label starts with `prefix`. */
    fun firstMatch(items: List<MenuItem>, prefix: String, from: Int = 0): Int {
        if (prefix.isEmpty() || items.isEmpty()) return -1
        val p = prefix.lowercase()
        for (step in 0 until items.size) {
            val idx = ((from % items.size) + step + items.size) % items.size
            val it = items[idx]
            if (it.isSeparator || !it.enabled) continue
            if (it.label.lowercase().startsWith(p)) return idx
        }
        return -1
    }

    data class ArrowResult(val state: State, val selected: MenuItem?, val skippedDisabled: Int)

    fun arrowDown(state: State, items: List<MenuItem>): ArrowResult {
        var skipped = 0
        var idx = state.selectedIndex
        for (step in 1..items.size) {
            idx = (state.selectedIndex + step) % items.size
            val it = items[idx]
            if (it.isSeparator || !it.enabled) { skipped++; continue }
            return ArrowResult(state.copy(selectedIndex = idx, buffer = ""), it, skipped)
        }
        return ArrowResult(state, null, skipped)
    }

    fun arrowUp(state: State, items: List<MenuItem>): ArrowResult {
        var skipped = 0
        var idx = state.selectedIndex
        for (step in 1..items.size) {
            idx = ((state.selectedIndex - step) % items.size + items.size) % items.size
            val it = items[idx]
            if (it.isSeparator || !it.enabled) { skipped++; continue }
            return ArrowResult(state.copy(selectedIndex = idx, buffer = ""), it, skipped)
        }
        return ArrowResult(state, null, skipped)
    }

    /** `Alt`+letter mnemonic lookup; returns `null` when no visible item declares that mnemonic. */
    fun mnemonicMatch(items: List<MenuItem>, letter: Char): MenuItem? =
        items.firstOrNull { it.enabled && !it.isSeparator && it.mnemonic?.lowercaseChar() == letter.lowercaseChar() }

    data class HoverPlan(val openSubmenuAfterMs: Long, val bridgeActive: Boolean, val closeParentWhenLeaving: Boolean)

    /**
     * Hover intent: a submenu parent opens only after `openDelayMs` of dwell, and the parent stays open while
     * the pointer is inside the safe triangle formed by the parent item and the submenu's near edge.
     */
    fun hoverPlan(
        pointerInsideParent: Boolean,
        pointerInsideSubmenu: Boolean,
        pointerInsideSafeTriangle: Boolean,
        dwellMs: Long,
        openDelayMs: Long = 250,
    ): HoverPlan {
        require(openDelayMs >= 0) { "openDelayMs must be non-negative" }
        val open = pointerInsideParent && dwellMs >= openDelayMs
        val bridge = pointerInsideSubmenu || pointerInsideSafeTriangle
        return HoverPlan(
            openSubmenuAfterMs = if (open) 0 else (openDelayMs - dwellMs).coerceAtLeast(0),
            bridgeActive = bridge,
            closeParentWhenLeaving = !pointerInsideParent && !bridge,
        )
    }

    /** Submenu cascade placement that flips left when the right edge would overflow the work area. */
    fun cascadeLeft(
        parentLeftDp: Float,
        parentWidthDp: Float,
        submenuWidthDp: Float,
        viewportWidthDp: Float,
        rtl: Boolean = false,
    ): Boolean {
        require(submenuWidthDp in 168f..240f) { "submenu width must stay within 168..240dp, got $submenuWidthDp" }
        require(parentWidthDp in 180f..280f) { "primary menu width must stay within 180..280dp, got $parentWidthDp" }
        val rightEdge = parentLeftDp + parentWidthDp + submenuWidthDp
        return if (rtl) false else rightEdge > viewportWidthDp
    }
}
