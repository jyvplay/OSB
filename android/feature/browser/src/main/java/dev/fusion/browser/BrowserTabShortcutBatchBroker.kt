// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser

/**
 * Clean-room Full Desktop Browser Keyboard Shortcut Map (`PR-07i`), Cross-Window Tab Drag-Out /
 * Drag-In Transaction Broker (`PR-07j`), and Batch Multi-Tab Action Coordinator (`PR-07q`).
 */
object BrowserTabShortcutBatchBroker {
    const val SCOPE =
        "Pure-JVM browser keyboard shortcut chord dispatcher (PR-07i), compositor window tab drag-out/in broker (PR-07j), and batch multi-tab close/bookmark/group/send coordinator (PR-07q)."

    enum class BrowserCommand {
        NEW_TAB, CLOSE_TAB, REOPEN_CLOSED_TAB, FOCUS_OMNIBOX, FIND_IN_PAGE,
        RELOAD, HARD_RELOAD, NEXT_TAB, PREV_TAB, TOGGLE_READER, OPEN_DOWNLOADS,
        NEW_PRIVATE_WINDOW, BOOKMARK_TAB, ZOOM_IN, ZOOM_OUT, ZOOM_RESET,
    }

    data class TabEntry(
        val tabId: String,
        val url: String,
        val title: String,
        val groupId: String? = null,
        val pinned: Boolean = false,
        val bookmarked: Boolean = false,
    )

    data class WindowTabsState(
        val windows: Map<String, List<TabEntry>>, // windowId -> ordered tabs
        val closedRecycleBin: List<TabEntry> = emptyList(),
    )

    private val SHORTCUTS: Map<String, BrowserCommand> = mapOf(
        "CTRL+T" to BrowserCommand.NEW_TAB,
        "CTRL+W" to BrowserCommand.CLOSE_TAB,
        "CTRL+SHIFT+T" to BrowserCommand.REOPEN_CLOSED_TAB,
        "CTRL+L" to BrowserCommand.FOCUS_OMNIBOX,
        "ALT+D" to BrowserCommand.FOCUS_OMNIBOX,
        "CTRL+F" to BrowserCommand.FIND_IN_PAGE,
        "CTRL+R" to BrowserCommand.RELOAD,
        "F5" to BrowserCommand.RELOAD,
        "CTRL+SHIFT+R" to BrowserCommand.HARD_RELOAD,
        "CTRL+TAB" to BrowserCommand.NEXT_TAB,
        "CTRL+SHIFT+TAB" to BrowserCommand.PREV_TAB,
        "CTRL+ALT+R" to BrowserCommand.TOGGLE_READER,
        "CTRL+J" to BrowserCommand.OPEN_DOWNLOADS,
        "CTRL+SHIFT+N" to BrowserCommand.NEW_PRIVATE_WINDOW,
        "CTRL+D" to BrowserCommand.BOOKMARK_TAB,
        "CTRL+PLUS" to BrowserCommand.ZOOM_IN,
        "CTRL+MINUS" to BrowserCommand.ZOOM_OUT,
        "CTRL+0" to BrowserCommand.ZOOM_RESET,
    )

    fun resolveShortcut(chord: String): BrowserCommand? =
        SHORTCUTS[chord.trim().uppercase().replace(" ", "")]

    fun dragTabBetweenWindows(
        state: WindowTabsState,
        srcWindowId: String,
        tabId: String,
        dstWindowId: String,
        insertIndex: Int,
    ): WindowTabsState {
        val srcList = state.windows[srcWindowId] ?: throw IllegalArgumentException("unknown source window '$srcWindowId'")
        val moving = srcList.firstOrNull { it.tabId == tabId } ?: throw IllegalArgumentException("unknown tab '$tabId'")
        val nextWindows = LinkedHashMap(state.windows)
        if (srcWindowId == dstWindowId) {
            val without = srcList.filterNot { it.tabId == tabId }.toMutableList()
            without.add(insertIndex.coerceIn(0, without.size), moving)
            nextWindows[srcWindowId] = without
        } else {
            val remainingSrc = srcList.filterNot { it.tabId == tabId }
            if (remainingSrc.isEmpty()) nextWindows.remove(srcWindowId) else nextWindows[srcWindowId] = remainingSrc
            val dstList = (nextWindows[dstWindowId] ?: emptyList()).toMutableList()
            dstList.add(insertIndex.coerceIn(0, dstList.size), moving)
            nextWindows[dstWindowId] = dstList
        }
        return state.copy(windows = nextWindows)
    }

    fun applyBatchAction(
        state: WindowTabsState,
        windowId: String,
        selectedTabIds: Set<String>,
        action: String,
        param: String? = null,
    ): WindowTabsState {
        val list = state.windows[windowId] ?: throw IllegalArgumentException("unknown window '$windowId'")
        val nextWindows = LinkedHashMap(state.windows)
        return when (action.uppercase()) {
            "CLOSE" -> {
                val closing = list.filter { it.tabId in selectedTabIds && !it.pinned }
                val kept = list.filterNot { it.tabId in selectedTabIds && !it.pinned }
                nextWindows[windowId] = kept
                state.copy(windows = nextWindows, closedRecycleBin = (closing.reversed() + state.closedRecycleBin).take(50))
            }
            "BOOKMARK" -> {
                nextWindows[windowId] = list.map { if (it.tabId in selectedTabIds) it.copy(bookmarked = true) else it }
                state.copy(windows = nextWindows)
            }
            "GROUP" -> {
                val grp = param?.trim()?.takeIf { it.isNotEmpty() } ?: "group-default"
                nextWindows[windowId] = list.map { if (it.tabId in selectedTabIds) it.copy(groupId = grp) else it }
                state.copy(windows = nextWindows)
            }
            "MOVE_TO_NEW_WINDOW" -> {
                val newWinId = param?.trim()?.takeIf { it.isNotEmpty() } ?: "${windowId}-detached"
                val moving = list.filter { it.tabId in selectedTabIds }
                val kept = list.filterNot { it.tabId in selectedTabIds }
                if (moving.isNotEmpty()) {
                    nextWindows[windowId] = kept
                    nextWindows[newWinId] = moving
                }
                state.copy(windows = nextWindows)
            }
            else -> throw IllegalArgumentException("unsupported batch tab action '$action'")
        }
    }
}
