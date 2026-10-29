// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settingsui

/**
 * Pure-JVM **Predictive Back-Gesture & Multi-Pane Workspace Back-Stack State Machine**
 * for `:core:navigation`, ensuring transient overlays (right-click context menu, Undo detail sheet)
 * dismiss cleanly BEFORE popping document/folder navigation routes!
 */
object PredictiveBackOverlayStack {
    const val SCOPE =
        "Layered predictive back-gesture router prioritizing transient context-menu/modal dismissal before route back-stack pop."

    data class NavigationFrame(
        val routeHistory: List<String>,
        val contextMenuOpen: Boolean = false,
        val activeModalId: String? = null,
    ) {
        init { require(routeHistory.isNotEmpty()) { "routeHistory must retain >=1 root destination" } }
    }

    enum class BackConsumptionLayer { DISMISSED_CONTEXT_MENU, DISMISSED_MODAL, POPPED_ROUTE, AT_ROOT_EXIT_PROMPT }

    fun dispatchBack(frame: NavigationFrame): Pair<NavigationFrame, BackConsumptionLayer> = when {
        frame.contextMenuOpen ->
            frame.copy(contextMenuOpen = false) to BackConsumptionLayer.DISMISSED_CONTEXT_MENU
        frame.activeModalId != null ->
            frame.copy(activeModalId = null) to BackConsumptionLayer.DISMISSED_MODAL
        frame.routeHistory.size > 1 ->
            frame.copy(routeHistory = frame.routeHistory.dropLast(1)) to BackConsumptionLayer.POPPED_ROUTE
        else ->
            frame to BackConsumptionLayer.AT_ROOT_EXIT_PROMPT
    }
}
