// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser

/**
 * Clean-room Desktop Browser Tab Groups, Pinned & Sleeping Tabs, Horizontal/Vertical Split View,
 * Cross-Window Tab Drag, Batch Actions, and Named Session Snapshot Engine (`PR-07b..g,j,q,r`).
 */
object BrowserSplitTabWorkspace {
    enum class SplitOrientation { NONE, HORIZONTAL, VERTICAL }

    data class TabGroup(
        val groupId: String,
        val title: String,
        val colorHex: String,
        val collapsed: Boolean = false,
    ) {
        init {
            require(groupId.isNotBlank() && title.isNotBlank()) { "groupId and title required" }
            require(colorHex.matches(Regex("#[0-9A-Fa-f]{6}"))) { "colorHex must be #RRGGBB" }
        }
    }

    data class BrowserTab(
        val tabId: String,
        val title: String,
        val url: String,
        val pinned: Boolean = false,
        val sleeping: Boolean = false,
        val groupId: String? = null,
        val lastActiveEpochMs: Long = 0L,
    ) {
        init {
            require(tabId.isNotBlank() && url.isNotBlank()) { "tabId and url required" }
        }
    }

    data class BrowserWindowState(
        val windowId: String,
        val tabs: List<BrowserTab>,
        val groups: Map<String, TabGroup>,
        val primaryTabId: String?,
        val secondarySplitTabId: String? = null,
        val splitOrientation: SplitOrientation = SplitOrientation.NONE,
        val splitRatio: Double = 0.5,
    ) {
        init {
            require(splitRatio in 0.2..0.8) { "splitRatio must be in 0.2..0.8" }
        }

        /** Pinned tabs always precede unpinned tabs in the vertical/horizontal strip (`PR-07f`). */
        fun orderedTabs(): List<BrowserTab> =
            tabs.filter { it.pinned } + tabs.filter { !it.pinned }
    }

    fun putIdleTabsToSleep(
        window: BrowserWindowState,
        nowEpochMs: Long,
        idleThresholdMs: Long = 15 * 60_000L,
    ): BrowserWindowState {
        val activeIds = setOfNotNull(window.primaryTabId, window.secondarySplitTabId)
        val updated = window.tabs.map { t ->
            if (!t.pinned && t.tabId !in activeIds && nowEpochMs - t.lastActiveEpochMs >= idleThresholdMs) {
                t.copy(sleeping = true)
            } else {
                t
            }
        }
        return window.copy(tabs = updated)
    }

    fun wakeTab(window: BrowserWindowState, tabId: String, nowEpochMs: Long): BrowserWindowState {
        require(window.tabs.any { it.tabId == tabId }) { "unknown tabId '$tabId'" }
        return window.copy(
            primaryTabId = tabId,
            tabs = window.tabs.map {
                if (it.tabId == tabId) it.copy(sleeping = false, lastActiveEpochMs = nowEpochMs) else it
            },
        )
    }

    fun configureSplit(
        window: BrowserWindowState,
        orientation: SplitOrientation,
        leftOrTopTabId: String,
        rightOrBottomTabId: String?,
        ratio: Double = 0.5,
    ): BrowserWindowState {
        val ids = window.tabs.map { it.tabId }.toSet()
        require(leftOrTopTabId in ids) { "primary split tab '$leftOrTopTabId' missing" }
        if (orientation == SplitOrientation.NONE) {
            return window.copy(primaryTabId = leftOrTopTabId, secondarySplitTabId = null, splitOrientation = SplitOrientation.NONE)
        }
        require(rightOrBottomTabId != null && rightOrBottomTabId in ids && rightOrBottomTabId != leftOrTopTabId) {
            "split view requires two distinct existing tabs"
        }
        return window.copy(
            primaryTabId = leftOrTopTabId,
            secondarySplitTabId = rightOrBottomTabId,
            splitOrientation = orientation,
            splitRatio = ratio.coerceIn(0.2, 0.8),
            tabs = window.tabs.map {
                if (it.tabId == leftOrTopTabId || it.tabId == rightOrBottomTabId) it.copy(sleeping = false) else it
            },
        )
    }

    fun dragTabBetweenWindows(
        source: BrowserWindowState,
        target: BrowserWindowState,
        tabId: String,
    ): Pair<BrowserWindowState, BrowserWindowState> {
        val moving = source.tabs.firstOrNull { it.tabId == tabId }
            ?: throw IllegalArgumentException("tab '$tabId' not in source window")
        val remSourceTabs = source.tabs.filterNot { it.tabId == tabId }
        val nextPrimary = if (source.primaryTabId == tabId) remSourceTabs.firstOrNull()?.tabId else source.primaryTabId
        val nextSec = if (source.secondarySplitTabId == tabId) null else source.secondarySplitTabId
        val nextOrient = if (nextSec == null) SplitOrientation.NONE else source.splitOrientation
        val newSource = source.copy(
            tabs = remSourceTabs,
            primaryTabId = nextPrimary,
            secondarySplitTabId = nextSec,
            splitOrientation = nextOrient,
        )
        val cleanMoved = moving.copy(groupId = if (moving.groupId in target.groups) moving.groupId else null)
        val newTarget = target.copy(
            tabs = target.tabs + cleanMoved,
            primaryTabId = cleanMoved.tabId,
        )
        return newSource to newTarget
    }

    fun batchCloseSelected(window: BrowserWindowState, selectedIds: Set<String>, includePinned: Boolean = false): BrowserWindowState {
        val survivors = window.tabs.filter { tab ->
            tab.tabId !in selectedIds || (tab.pinned && !includePinned)
        }
        val nextPrim = if (window.primaryTabId in survivors.map { it.tabId }) window.primaryTabId else survivors.firstOrNull()?.tabId
        val nextSec = if (window.secondarySplitTabId in survivors.map { it.tabId }) window.secondarySplitTabId else null
        return window.copy(
            tabs = survivors,
            primaryTabId = nextPrim,
            secondarySplitTabId = nextSec,
            splitOrientation = if (nextSec == null) SplitOrientation.NONE else window.splitOrientation,
        )
    }

    fun serializeNamedSession(sessionName: String, windows: List<BrowserWindowState>): String = buildString {
        require(sessionName.isNotBlank()) { "sessionName required" }
        append("SESSION\t").append(esc(sessionName)).append('\n')
        for (w in windows) {
            append("WIN\t${esc(w.windowId)}\t${w.primaryTabId ?: ""}\t${w.secondarySplitTabId ?: ""}\t${w.splitOrientation.name}\t${w.splitRatio}\n")
            for (g in w.groups.values) {
                append("GRP\t${esc(w.windowId)}\t${esc(g.groupId)}\t${esc(g.title)}\t${g.colorHex}\t${g.collapsed}\n")
            }
            for (t in w.tabs) {
                append("TAB\t${esc(w.windowId)}\t${esc(t.tabId)}\t${esc(t.title)}\t${esc(t.url)}\t${t.pinned}\t${t.sleeping}\t${t.groupId ?: ""}\t${t.lastActiveEpochMs}\n")
            }
        }
    }

    fun restoreNamedSession(serialized: String): Pair<String, List<BrowserWindowState>> {
        val lines = serialized.lines().filter { it.isNotBlank() }
        require(lines.isNotEmpty() && lines[0].startsWith("SESSION\t")) { "invalid browser session header" }
        val sessionName = unesc(lines[0].substringAfter("SESSION\t"))
        data class WinAcc(
            val id: String,
            val prim: String?,
            val sec: String?,
            val orient: SplitOrientation,
            val ratio: Double,
            val groups: LinkedHashMap<String, TabGroup> = LinkedHashMap(),
            val tabs: ArrayList<BrowserTab> = ArrayList(),
        )
        val wins = LinkedHashMap<String, WinAcc>()
        for (i in 1 until lines.size) {
            val cols = lines[i].split('\t')
            when (cols[0]) {
                "WIN" -> {
                    val wid = unesc(cols[1])
                    wins[wid] = WinAcc(
                        wid,
                        cols[2].ifEmpty { null },
                        cols[3].ifEmpty { null },
                        SplitOrientation.valueOf(cols[4]),
                        cols[5].toDouble(),
                    )
                }
                "GRP" -> {
                    val acc = wins.getValue(unesc(cols[1]))
                    val gid = unesc(cols[2])
                    acc.groups[gid] = TabGroup(gid, unesc(cols[3]), cols[4], cols[5].toBooleanStrict())
                }
                "TAB" -> {
                    val acc = wins.getValue(unesc(cols[1]))
                    acc.tabs.add(
                        BrowserTab(
                            tabId = unesc(cols[2]),
                            title = unesc(cols[3]),
                            url = unesc(cols[4]),
                            pinned = cols[5].toBooleanStrict(),
                            sleeping = cols[6].toBooleanStrict(),
                            groupId = cols[7].ifEmpty { null },
                            lastActiveEpochMs = cols[8].toLong(),
                        ),
                    )
                }
            }
        }
        val built = wins.values.map {
            BrowserWindowState(it.id, it.tabs, it.groups, it.prim, it.sec, it.orient, it.ratio)
        }
        return sessionName to built
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
    private fun unesc(s: String): String = s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
}
