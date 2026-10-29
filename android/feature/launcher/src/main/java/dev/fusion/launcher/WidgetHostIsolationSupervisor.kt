// SPDX-License-Identifier: Apache-2.0
package dev.fusion.launcher

/**
 * Clean-room Desktop Widget Host Crash-Isolation Supervisor (`SH-05`), Hot-Corner / Screen-Edge
 * Trigger Recognizer (`SH-06`), and Own-Data / App Inventory Helper (`AP-02`, `AP-04`, `AP-05`).
 */
class WidgetHostIsolationSupervisor(
    val crashLimitInWindow: Int = 3,
    val crashWindowMs: Long = 60_000L,
) {
    enum class WidgetState { ACTIVE_LISTENING, SUSPENDED_WORKSPACE_HIDDEN, QUARANTINED_CRASH_LOOP }
    enum class HotCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    data class HostedWidget(
        val appWidgetId: Int,
        val providerPackage: String,
        val workspaceId: String,
        val state: WidgetState,
        val crashCountInWindow: Int,
    )

    data class OwnFootprintSummary(
        val cacheBytes: Long,
        val notesAndDocsBytes: Long,
        val encryptedVaultBytes: Long,
        val activeBackgroundJobs: List<String>,
    ) {
        val totalOwnBytes: Long get() = cacheBytes + notesAndDocsBytes + encryptedVaultBytes
    }

    private val widgets = LinkedHashMap<Int, HostedWidget>()
    private val crashes = HashMap<Int, ArrayDeque<Long>>()

    fun registerWidget(appWidgetId: Int, providerPackage: String, workspaceId: String) {
        widgets[appWidgetId] = HostedWidget(appWidgetId, providerPackage, workspaceId, WidgetState.ACTIVE_LISTENING, 0)
    }

    fun onActiveWorkspaceChanged(activeWorkspaceId: String, desktopVisible: Boolean): List<HostedWidget> {
        for ((id, w) in widgets.entries) {
            if (w.state == WidgetState.QUARANTINED_CRASH_LOOP) continue
            val next = if (desktopVisible && w.workspaceId == activeWorkspaceId) {
                WidgetState.ACTIVE_LISTENING
            } else {
                WidgetState.SUSPENDED_WORKSPACE_HIDDEN
            }
            widgets[id] = w.copy(state = next)
        }
        return widgets.values.toList()
    }

    fun recordRemoteViewsException(appWidgetId: Int, timestampMs: Long): HostedWidget {
        val w = widgets[appWidgetId] ?: throw IllegalArgumentException("unknown widget $appWidgetId")
        val q = crashes.getOrPut(appWidgetId) { ArrayDeque() }
        while (q.isNotEmpty() && timestampMs - q.first() > crashWindowMs) q.removeFirst()
        q.addLast(timestampMs)
        val quarantined = q.size >= crashLimitInWindow
        val updated = w.copy(
            state = if (quarantined) WidgetState.QUARANTINED_CRASH_LOOP else w.state,
            crashCountInWindow = q.size,
        )
        widgets[appWidgetId] = updated
        return updated
    }

    companion object {
        fun detectHotCornerDwell(
            pointerX: Int,
            pointerY: Int,
            displayWidthPx: Int,
            displayHeightPx: Int,
            dwellMs: Long,
            cornerRadiusPx: Int = 12,
            minDwellMs: Long = 180L,
        ): HotCorner? {
            if (dwellMs < minDwellMs || displayWidthPx <= 0 || displayHeightPx <= 0) return null
            val left = pointerX in 0..cornerRadiusPx
            val right = pointerX in (displayWidthPx - 1 - cornerRadiusPx)..<displayWidthPx
            val top = pointerY in 0..cornerRadiusPx
            val bottom = pointerY in (displayHeightPx - 1 - cornerRadiusPx)..<displayHeightPx
            return when {
                left && top -> HotCorner.TOP_LEFT
                right && top -> HotCorner.TOP_RIGHT
                left && bottom -> HotCorner.BOTTOM_LEFT
                right && bottom -> HotCorner.BOTTOM_RIGHT
                else -> null
            }
        }
    }
}
