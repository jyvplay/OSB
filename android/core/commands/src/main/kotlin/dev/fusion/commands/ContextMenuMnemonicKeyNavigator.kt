// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * **Keyboard Arrow / Home / End / Submenu-Cascade & Mnemonic Access-Key Navigator** for
 * `FusionContextMenuPopup` (`WAI-ARIA` Menu Pattern + desktop `&Rename` / first-letter cyclic jump).
 */
object ContextMenuMnemonicKeyNavigator {
    const val SCOPE =
        "WAI-ARIA keyboard Up/Down/Home/End/Right-cascade/Left-close and mnemonic access-key cyclic navigator for context menus."

    data class NavigableMenuItem(
        val id: String,
        val label: String,
        val enabled: Boolean = true,
        val hasSubmenu: Boolean = false,
        val explicitMnemonicChar: Char? = null,
    ) {
        val effectiveMnemonicLower: Char?
            get() = (explicitMnemonicChar ?: label.firstOrNull { it.isLetterOrDigit() })?.lowercaseChar()
    }

    enum class KeyInput { ARROW_DOWN, ARROW_UP, HOME, END, ARROW_RIGHT, ARROW_LEFT, ESCAPE }

    data class MenuFocusState(
        val highlightedIndex: Int,
        val submenuOpenForIndex: Int? = null,
        val dismissed: Boolean = false,
    )

    fun handleNavigationKey(items: List<NavigableMenuItem>, state: MenuFocusState, key: KeyInput): MenuFocusState {
        val enabledIndices = items.indices.filter { items[it].enabled }
        if (enabledIndices.isEmpty()) return state.copy(dismissed = key == KeyInput.ESCAPE)
        return when (key) {
            KeyInput.ESCAPE -> state.copy(submenuOpenForIndex = null, dismissed = state.submenuOpenForIndex == null)
            KeyInput.ARROW_LEFT -> state.copy(submenuOpenForIndex = null)
            KeyInput.ARROW_RIGHT -> {
                val cur = state.highlightedIndex.coerceIn(0, items.lastIndex)
                if (items[cur].enabled && items[cur].hasSubmenu) state.copy(submenuOpenForIndex = cur) else state
            }
            KeyInput.HOME -> MenuFocusState(enabledIndices.first(), null, false)
            KeyInput.END -> MenuFocusState(enabledIndices.last(), null, false)
            KeyInput.ARROW_DOWN -> {
                val next = enabledIndices.firstOrNull { it > state.highlightedIndex } ?: enabledIndices.first()
                MenuFocusState(next, null, false)
            }
            KeyInput.ARROW_UP -> {
                val prev = enabledIndices.lastOrNull { it < state.highlightedIndex } ?: enabledIndices.last()
                MenuFocusState(prev, null, false)
            }
        }
    }

    fun jumpByMnemonicChar(items: List<NavigableMenuItem>, state: MenuFocusState, typedChar: Char): MenuFocusState {
        val target = typedChar.lowercaseChar()
        val matches = items.indices.filter { items[it].enabled && items[it].effectiveMnemonicLower == target }
        if (matches.isEmpty()) return state
        val next = matches.firstOrNull { it > state.highlightedIndex } ?: matches.first()
        return MenuFocusState(next, null, false)
    }
}
