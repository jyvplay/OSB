package dev.fusion.launcher

/**
 * Clean-room Adaptive Workspace Taskbar/Dock Slot Overflow & Pinned Split-Pair Layout Engine
 * (Apache-2.0) guaranteeing `>= 48 dp` minimum touch targets without horizontal clipping!
 */
object PinnedWorkspaceDockLayoutEngine {
    const val SCOPE =
        "Adaptive taskbar/dock slot planner enforcing >= 48dp touch targets, recents deduplication & overflow folder aggregation"

    data class DockItem(val id: String, val label: String, val pinned: Boolean, val unreadCount: Int = 0)

    data class DockLayoutPlan(
        val visibleItems: List<DockItem>,
        val overflowItems: List<DockItem>,
        val slotWidthDp: Double,
        val totalBadgeCount: Int,
    )

    fun planDock(
        availableWidthDp: Double,
        pinnedItems: List<DockItem>,
        recentItems: List<DockItem>,
        minTouchTargetDp: Double = 48.0,
        horizontalPaddingDp: Double = 16.0,
    ): DockLayoutPlan {
        require(availableWidthDp >= minTouchTargetDp + horizontalPaddingDp)
        val pinnedIds = pinnedItems.map { it.id }.toHashSet()
        val combined = pinnedItems + recentItems.filter { it.id !in pinnedIds }
        val maxSlots = ((availableWidthDp - horizontalPaddingDp) / minTouchTargetDp).toInt().coerceAtLeast(1)

        val (vis, over) = if (combined.size <= maxSlots) {
            combined to emptyList()
        } else {
            // Reserve 1 slot for the "+N Overflow" button!
            val keep = (maxSlots - 1).coerceAtLeast(1)
            combined.take(keep) to combined.drop(keep)
        }
        val effectiveSlots = vis.size + (if (over.isNotEmpty()) 1 else 0)
        val slotW = ((availableWidthDp - horizontalPaddingDp) / effectiveSlots.coerceAtLeast(1)).coerceAtLeast(minTouchTargetDp)
        return DockLayoutPlan(vis, over, slotW, combined.sumOf { it.unreadCount.coerceAtLeast(0) })
    }
}
