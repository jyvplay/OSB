// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * Clean-room Desktop Sticky Notes spatial canvas engine (`PR-13`).
 *
 * Manages workspace-scoped sticky cards with:
 * - Deterministic snap-to-grid (`gridPx = 16`) & non-overlapping auto-cascade placement.
 * - Z-order elevation (`bringToFront`) and pinned-across-workspaces sticky notes.
 * - Inline Markdown `- [ ]` / `- [x]` checklist item toggling without mutating surrounding prose.
 */
object StickyNoteBoardLayout {
    enum class NoteColor(val hexBg: String, val hexFg: String) {
        CANARY("#FFF3B0", "#221E0B"),
        MINT("#D8F3DC", "#0B2915"),
        SKY("#DBEAFE", "#0F2547"),
        CORAL("#FFE4E6", "#3B0D15"),
        SLATE("#E2E8F0", "#0F172A"),
    }

    data class StickyCard(
        val id: String,
        val workspaceId: String,
        val pinnedAllWorkspaces: Boolean,
        val color: NoteColor,
        val xPx: Int,
        val yPx: Int,
        val widthPx: Int = 240,
        val heightPx: Int = 180,
        val zIndex: Int = 0,
        val markdownBody: String,
    ) {
        init {
            require(id.isNotBlank() && workspaceId.isNotBlank()) { "sticky note id/workspaceId required" }
            require(widthPx in 120..960 && heightPx in 96..960) { "sticky dimensions out of bounds" }
        }

        fun overlaps(other: StickyCard): Boolean =
            xPx < other.xPx + other.widthPx &&
                xPx + widthPx > other.xPx &&
                yPx < other.yPx + other.heightPx &&
                yPx + heightPx > other.yPx
    }

    fun snapCoordinate(v: Int, gridPx: Int = 16): Int {
        require(gridPx in 4..64) { "invalid gridPx $gridPx" }
        val rem = ((v % gridPx) + gridPx) % gridPx
        return if (rem >= gridPx / 2) v + (gridPx - rem) else v - rem
    }

    fun placeWithoutOverlap(
        existing: List<StickyCard>,
        candidate: StickyCard,
        canvasWidthPx: Int = 1280,
        canvasHeightPx: Int = 800,
        gridPx: Int = 16,
    ): StickyCard {
        val visiblePeers = existing.filter {
            it.id != candidate.id && (it.pinnedAllWorkspaces || it.workspaceId == candidate.workspaceId)
        }
        val nextZ = (existing.maxOfOrNull { it.zIndex } ?: 0) + 1
        var x = snapCoordinate(candidate.xPx.coerceIn(0, maxOf(0, canvasWidthPx - candidate.widthPx)), gridPx)
        var y = snapCoordinate(candidate.yPx.coerceIn(0, maxOf(0, canvasHeightPx - candidate.heightPx)), gridPx)

        repeat(128) {
            val probe = candidate.copy(xPx = x, yPx = y, zIndex = nextZ)
            if (visiblePeers.none { it.overlaps(probe) }) return probe
            x += gridPx * 2
            y += gridPx * 2
            if (x + candidate.widthPx > canvasWidthPx) x = gridPx
            if (y + candidate.heightPx > canvasHeightPx) y = gridPx
        }
        return candidate.copy(xPx = x, yPx = y, zIndex = nextZ)
    }

    fun bringToFront(cards: List<StickyCard>, targetId: String): List<StickyCard> {
        val topZ = (cards.maxOfOrNull { it.zIndex } ?: 0) + 1
        return cards.map { if (it.id == targetId) it.copy(zIndex = topZ) else it }
            .sortedBy { it.zIndex }
    }

    fun toggleChecklistLine(markdown: String, zeroBasedCheckIndex: Int): String {
        val lines = markdown.split('\n').toMutableList()
        var seen = 0
        for (i in lines.indices) {
            val l = lines[i]
            val unchecked = Regex("""^(\s*-\s+)\[ \](\s+.*)$""").matchEntire(l)
            val checked = Regex("""^(\s*-\s+)\[[xX]\](\s+.*)$""").matchEntire(l)
            if (unchecked != null || checked != null) {
                if (seen == zeroBasedCheckIndex) {
                    lines[i] = if (unchecked != null) {
                        "${unchecked.groupValues[1]}[x]${unchecked.groupValues[2]}"
                    } else {
                        "${checked!!.groupValues[1]}[ ]${checked.groupValues[2]}"
                    }
                    return lines.joinToString("\n")
                }
                seen++
            }
        }
        throw IllegalArgumentException("checklist index $zeroBasedCheckIndex out of range (found $seen)")
    }
}
